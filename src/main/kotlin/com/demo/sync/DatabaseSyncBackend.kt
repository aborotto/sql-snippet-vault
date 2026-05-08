package com.demo.sync

import com.demo.model.QueryNode
import com.google.gson.GsonBuilder
import org.postgresql.PGConnection
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.TimeUnit

/**
 * [SyncBackend] implementation that connects directly to a SQL database via JDBC.
 *
 * Supported databases
 * ────────────────────
 *  • PostgreSQL — jdbc:postgresql://host:5432/dbname
 *  • SQLite     — jdbc:sqlite:/absolute/path/to/file.db
 *
 * Real-time notifications
 * ────────────────────────
 *  • PostgreSQL: uses built-in LISTEN/NOTIFY — near-instant (≤ 200 ms latency).
 *  • SQLite:     uses Java NIO WatchService on the database file — near-instant.
 *  • Both require no extra libraries beyond the JDBC drivers.
 *
 * Schema (auto-created on first use — see [SCHEMA_DDL])
 * ──────────────────────────────────────────────────────
 *  sqlfolio_workspaces         — current live state (one row per workspace)
 *  sqlfolio_workspace_history  — rolling version history (last [retentionVersions] snapshots)
 *
 * Optimistic-locking rule
 * ────────────────────────
 *  • clientVersion < server version → conflict, no write, server tree returned.
 *  • clientVersion == server version → accepted, version incremented by 1.
 */
class DatabaseSyncBackend(
    private val jdbcUrl:           String,
    private val user:              String,
    private val password:          String,
    val          retentionVersions: Int = 10
) : SyncBackend {

    private val gson       = GsonBuilder().create()
    private val isPostgres get() = jdbcUrl.startsWith("jdbc:postgresql")

    // ── Schema ───────────────────────────────────────────────────────────────

    companion object {
        /**
         * Full DDL shown in the settings UI and applied automatically on first use.
         * Copy this into your database if you prefer to create the tables manually.
         */
        val SCHEMA_DDL = """
            |-- ┌─────────────────────────────────────────────────────────────┐
            |-- │  SQLFolio — required database schema                        │
            |-- │  Auto-created by the plugin; safe to run manually too.      │
            |-- └─────────────────────────────────────────────────────────────┘
            |
            |-- Current live state (one row per workspace)
            |CREATE TABLE IF NOT EXISTS sqlfolio_workspaces (
            |    id      TEXT    PRIMARY KEY,
            |    version INTEGER NOT NULL DEFAULT 0,
            |    data    TEXT    NOT NULL   -- JSON-serialised QueryNode tree
            |);
            |
            |-- Rolling version history (last N snapshots per workspace)
            |CREATE TABLE IF NOT EXISTS sqlfolio_workspace_history (
            |    workspace_id TEXT    NOT NULL,
            |    version      INTEGER NOT NULL,
            |    data         TEXT    NOT NULL,
            |    saved_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            |    PRIMARY KEY  (workspace_id, version)
            |);
        """.trimMargin().trim()
    }

    // ── Connection + schema helpers ──────────────────────────────────────────

    private fun openConnection(): Connection = DriverManager.getConnection(jdbcUrl, user, password)

    private fun Connection.applySchema() {
        createStatement().use { stmt ->
            SCHEMA_DDL.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("--") }
                .forEach { stmt.execute(it) }
        }
    }

    // ── SyncBackend: testConnection ──────────────────────────────────────────

    override fun testConnection(): Boolean {
        openConnection().use { conn ->
            conn.applySchema()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT 1").use { return it.next() }
            }
        }
    }

    // ── SyncBackend: pull ────────────────────────────────────────────────────

    override fun pull(workspaceId: String): SyncBackend.Snapshot {
        openConnection().use { conn ->
            conn.applySchema()
            conn.prepareStatement(
                "SELECT version, data FROM sqlfolio_workspaces WHERE id = ?"
            ).use { ps ->
                ps.setString(1, workspaceId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return SyncBackend.Snapshot()
                    val root = runCatching {
                        gson.fromJson(rs.getString("data"), QueryNode::class.java)
                    }.getOrNull() ?: QueryNode(name = "Root", isFolder = true)
                    return SyncBackend.Snapshot(rs.getInt("version"), root)
                }
            }
        }
    }

    // ── SyncBackend: push ────────────────────────────────────────────────────

    override fun push(workspaceId: String, clientVersion: Int, root: QueryNode): SyncBackend.PushResult {
        openConnection().use { conn ->
            conn.applySchema()
            conn.autoCommit = false
            try {
                // 1. Lock + read current row
                val lockSql = if (isPostgres)
                    "SELECT version, data FROM sqlfolio_workspaces WHERE id = ? FOR UPDATE"
                else
                    "SELECT version, data FROM sqlfolio_workspaces WHERE id = ?"

                data class CurrentRow(val version: Int, val data: String)

                val current: CurrentRow? = conn.prepareStatement(lockSql).use { ps ->
                    ps.setString(1, workspaceId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) CurrentRow(rs.getInt("version"), rs.getString("data") ?: "{}")
                        else null
                    }
                }

                // 2. Conflict check
                if (current != null && clientVersion < current.version) {
                    conn.rollback()
                    val serverRoot = runCatching {
                        gson.fromJson(current.data, QueryNode::class.java)
                    }.getOrNull()
                    return SyncBackend.PushResult(
                        version  = current.version,
                        conflict = true,
                        root     = serverRoot
                    )
                }

                val newVersion = (current?.version ?: 0) + 1
                val json       = gson.toJson(root)

                // 3. Archive current version to history
                if (current != null) {
                    val archiveSql = if (isPostgres)
                        """INSERT INTO sqlfolio_workspace_history (workspace_id, version, data)
                           VALUES (?, ?, ?) ON CONFLICT DO NOTHING"""
                    else
                        "INSERT OR IGNORE INTO sqlfolio_workspace_history (workspace_id, version, data) VALUES (?, ?, ?)"
                    conn.prepareStatement(archiveSql).use { ps ->
                        ps.setString(1, workspaceId)
                        ps.setInt(2, current.version)
                        ps.setString(3, current.data)
                        ps.executeUpdate()
                    }
                }

                // 4. Upsert new version into main table
                val upsertSql = if (isPostgres)
                    """INSERT INTO sqlfolio_workspaces (id, version, data) VALUES (?, ?, ?)
                       ON CONFLICT (id) DO UPDATE
                           SET version = EXCLUDED.version, data = EXCLUDED.data"""
                else
                    "INSERT OR REPLACE INTO sqlfolio_workspaces (id, version, data) VALUES (?, ?, ?)"
                conn.prepareStatement(upsertSql).use { ps ->
                    ps.setString(1, workspaceId)
                    ps.setInt(2, newVersion)
                    ps.setString(3, json)
                    ps.executeUpdate()
                }

                // 5. Data-retention: delete history older than the last retentionVersions
                conn.prepareStatement(
                    """DELETE FROM sqlfolio_workspace_history
                       WHERE workspace_id = ?
                         AND version <= (
                             SELECT COALESCE(MAX(version), 0) - ?
                             FROM sqlfolio_workspace_history
                             WHERE workspace_id = ?
                         )"""
                ).use { ps ->
                    ps.setString(1, workspaceId)
                    ps.setInt(2, retentionVersions)
                    ps.setString(3, workspaceId)
                    ps.executeUpdate()
                }

                // 6. Notify peers (PostgreSQL only — SQLite peers use WatchService)
                if (isPostgres) conn.createStatement().execute("NOTIFY sqlfolio")

                conn.commit()
                return SyncBackend.PushResult(version = newVersion, conflict = false)

            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    // ── Real-time listening ──────────────────────────────────────────────────

    @Volatile private var listening         = false
    private var listenerThread:    Thread?     = null
    private var listenerConnection: Connection? = null

    override fun startListening(onRemoteChange: () -> Unit) {
        if (isPostgres) startPostgresListener(onRemoteChange)
        else            startSqliteWatcher(onRemoteChange)
    }

    override fun stopListening() {
        listening = false
        listenerThread?.interrupt()
        listenerThread = null
        listenerConnection?.runCatching { close() }
        listenerConnection = null
    }

    /**
     * PostgreSQL LISTEN/NOTIFY:
     *  • Opens a dedicated long-lived connection and executes LISTEN sqlfolio.
     *  • [PGConnection.getNotifications] blocks for up to 200 ms, then returns any
     *    queued notifications — giving ≤ 200 ms latency with zero busy-waiting.
     *  • The NOTIFY is fired by [push] inside the write transaction.
     */
    private fun startPostgresListener(onRemoteChange: () -> Unit) {
        listening = true
        listenerThread = Thread({
            try {
                val conn = openConnection()
                listenerConnection = conn
                val pgConn = conn.unwrap(PGConnection::class.java)
                conn.createStatement().execute("LISTEN sqlfolio")
                while (listening && !conn.isClosed) {
                    // getNotifications(ms) blocks up to ms waiting for a notification
                    val notifications = pgConn.getNotifications(200)
                    if (!notifications.isNullOrEmpty()) onRemoteChange()
                }
            } catch (_: InterruptedException) { /* normal shutdown */ }
            catch (e: Exception)              { if (listening) e.printStackTrace() }
        }, "SQLFolio-pg-listener")
        listenerThread!!.isDaemon = true
        listenerThread!!.start()
    }

    /**
     * SQLite WatchService:
     *  • Watches the parent directory of the SQLite file for ENTRY_MODIFY events.
     *  • When the file is written by another process, [onRemoteChange] is called.
     *  • Polls the WatchService every 1 s, giving ≤ 1 s latency.
     */
    private fun startSqliteWatcher(onRemoteChange: () -> Unit) {
        val rawPath = jdbcUrl.removePrefix("jdbc:sqlite:").trim()
        if (rawPath.isBlank() || rawPath == ":memory:") return

        val path     = java.nio.file.Paths.get(rawPath)
        val dir      = path.parent ?: return
        val fileName = path.fileName.toString()

        listening = true
        listenerThread = Thread({
            try {
                val watcher = java.nio.file.FileSystems.getDefault().newWatchService()
                dir.register(watcher, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY)
                while (listening) {
                    val key = watcher.poll(1, TimeUnit.SECONDS) ?: continue
                    val hit = key.pollEvents().any { ev ->
                        (ev.context() as? java.nio.file.Path)?.fileName?.toString() == fileName
                    }
                    key.reset()
                    if (hit) onRemoteChange()
                }
                watcher.close()
            } catch (_: InterruptedException) { /* normal shutdown */ }
            catch (e: Exception)              { if (listening) e.printStackTrace() }
        }, "SQLFolio-sqlite-watcher")
        listenerThread!!.isDaemon = true
        listenerThread!!.start()
    }
}
