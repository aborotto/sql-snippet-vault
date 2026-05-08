package com.demo.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/** The type of back-end the user has configured. */
enum class BackendType { REST, POSTGRESQL, SQLITE }

/**
 * Application-level (global) settings for SQLFolio team sync.
 * Stored in <IDE config>/options/SQLFolioSync.xml — shared across all projects.
 *
 * Common fields:
 *   syncEnabled          — master on/off switch
 *   workspaceId          — which workspace/room to sync with
 *   syncIntervalSeconds  — how often to poll for remote changes
 *   lastSyncedVersion    — last accepted version (for conflict detection)
 *   backendType          — REST | POSTGRESQL | SQLITE
 *
 * REST-specific:
 *   serverUrl   — base URL of the sync server
 *   apiToken    — Bearer token
 *
 * Database-specific (PostgreSQL / SQLite):
 *   jdbcUrl     — full JDBC connection string
 *   dbUser      — database username (empty for SQLite)
 *   dbPassword  — database password  (empty for SQLite)
 */
@State(name = "SQLFolioSyncSettings", storages = [Storage("SQLFolioSync.xml")])
@Service(Service.Level.APP)
class SQLFolioSyncSettings : PersistentStateComponent<SQLFolioSyncSettings> {

    // ── Common ────────────────────────────────────────────────────────────────
    var syncEnabled:         Boolean = false
    var workspaceId:         String  = ""
    var syncIntervalSeconds: Int     = 60
    var lastSyncedVersion:   Int     = 0
    var backendType:         String  = BackendType.REST.name   // stored as String for XML compat

    // ── REST ──────────────────────────────────────────────────────────────────
    var serverUrl: String = ""
    var apiToken:  String = ""

    // ── Database (PostgreSQL / SQLite) ────────────────────────────────────────
    var jdbcUrl:           String = ""
    var dbUser:            String = ""
    var dbPassword:        String = ""
    /** How many historical snapshots to keep per workspace (1–100). Older ones are deleted on each push. */
    var retentionVersions: Int    = 10

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun resolvedBackendType(): BackendType =
        runCatching { BackendType.valueOf(backendType) }.getOrDefault(BackendType.REST)

    /** Build the correct [SyncBackend] from current settings, or null if not configured. */
    fun buildBackend(): SyncBackend? = when (resolvedBackendType()) {
        BackendType.REST -> {
            if (serverUrl.isBlank() || apiToken.isBlank()) null
            else RestSyncBackend(serverUrl, apiToken)
        }
        BackendType.POSTGRESQL, BackendType.SQLITE -> {
            if (jdbcUrl.isBlank()) null
            else DatabaseSyncBackend(jdbcUrl, dbUser, dbPassword, retentionVersions)
        }
    }

    override fun getState(): SQLFolioSyncSettings = this

    override fun loadState(state: SQLFolioSyncSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): SQLFolioSyncSettings =
            ApplicationManager.getApplication().getService(SQLFolioSyncSettings::class.java)
    }
}
