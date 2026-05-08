package com.demo.sync

import com.demo.model.QueryNode
import com.demo.model.QuerySavedListener
import com.demo.model.QueryStorage
import com.google.gson.GsonBuilder
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

// ── Status ─────────────────────────────────────────────────────────────────────

enum class SyncStatus(val label: String) {
    DISCONNECTED(""),
    SYNCING     ("⟳ Syncing…"),
    SYNCED      ("✓ Synced"),
    CONFLICT    ("⚠ Conflict"),
    ERROR       ("✗ Sync error")
}

// ── Service ────────────────────────────────────────────────────────────────────

/**
 * Project-level service that owns the full sync lifecycle.
 *
 * Back-end is resolved at connect-time via [SQLFolioSyncSettings.buildBackend],
 * so users can switch between REST, PostgreSQL, and SQLite without any code change.
 *
 * Thread model
 * ─────────────
 *  All back-end calls run on a bounded background executor (never the EDT).
 *  Storage mutations and UI updates are marshalled back to the EDT via
 *  [UIUtil.invokeLaterIfNeeded].
 */
@Service(Service.Level.PROJECT)
class SQLFolioSyncService(private val project: Project) : Disposable {

    private val executor = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SQLFolio-sync", 1
    )

    private var pollFuture:     ScheduledFuture<*>? = null
    private var debounceFuture: ScheduledFuture<*>? = null

    @Volatile private var connected = false
    @Volatile private var backend: SyncBackend? = null

    private val gson = GsonBuilder().create()

    val settings get() = SQLFolioSyncSettings.getInstance()
    private val storage get() = QueryStorage.getInstance(project)

    // ── Status ────────────────────────────────────────────────────────────────

    var status: SyncStatus = SyncStatus.DISCONNECTED
        private set(v) {
            field = v
            UIUtil.invokeLaterIfNeeded { onStatusChanged?.invoke(v) }
        }

    /** Invoked on the EDT whenever [status] changes — wire this to update toolbar label. */
    var onStatusChanged: ((SyncStatus) -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Start polling if sync is enabled and settings are complete.
     * Safe to call multiple times — will no-op if already connected.
     */
    fun connect() {
        if (connected) return
        if (!settings.syncEnabled || settings.workspaceId.isBlank()) {
            status = SyncStatus.DISCONNECTED
            return
        }
        val b = settings.buildBackend()
        if (b == null) {
            status = SyncStatus.DISCONNECTED
            return
        }
        backend  = b
        connected = true
        // Start real-time listener (PostgreSQL NOTIFY / SQLite WatchService)
        b.startListening { executor.submit { doPull() } }
        // Immediate initial pull on a background thread
        executor.submit { doPull() }
        // Schedule periodic polling
        val interval = settings.syncIntervalSeconds.toLong().coerceAtLeast(10)
        pollFuture = executor.scheduleAtFixedRate(
            { doPull() }, interval, interval, TimeUnit.SECONDS
        )
    }

    /** Stop polling and clear all timers. */
    fun disconnect() {
        connected = false
        backend?.stopListening()
        backend   = null
        pollFuture?.cancel(false);     pollFuture     = null
        debounceFuture?.cancel(false); debounceFuture = null
        status = SyncStatus.DISCONNECTED
    }

    /** Re-read settings and reconnect (called after settings are applied). */
    fun reconnect() { disconnect(); connect() }

    // ── Mutations notification ─────────────────────────────────────────────────

    /**
     * Call this after every local change (create / rename / delete / drag / edit).
     * A push will be fired 2 s after the last call (debounced).
     */
    fun notifyChanged() {
        if (!connected || !settings.syncEnabled) return
        debounceFuture?.cancel(false)
        debounceFuture = executor.schedule({ schedulePush() }, 2, TimeUnit.SECONDS)
    }

    // ── Manual triggers ───────────────────────────────────────────────────────

    fun pushNow() { executor.submit { schedulePush() } }
    fun pullNow() { executor.submit { doPull()       } }

    // ── Core operations ───────────────────────────────────────────────────────

    /**
     * Marshal a deep-copy of the current tree on the EDT, then push on the executor.
     * (Storage mutations always happen on the EDT; deep-copy isolates the HTTP call.)
     */
    private fun schedulePush() {
        // Capture a snapshot on the EDT where storage is safe to read
        ApplicationManager.getApplication().invokeAndWait {
            val version  = settings.lastSyncedVersion
            val rootCopy = deepCopy(storage.root)
            executor.submit { doPush(version, rootCopy) }
        }
    }

    private fun doPush(version: Int, rootSnapshot: QueryNode) {
        val b = backend ?: return
        status = SyncStatus.SYNCING
        try {
            val result = b.push(settings.workspaceId, version, rootSnapshot)
            if (result.conflict && result.root != null) {
                status = SyncStatus.CONFLICT
                handleConflict(result.version, result.root)
            } else {
                settings.lastSyncedVersion = result.version
                status = SyncStatus.SYNCED
            }
        } catch (e: Exception) {
            status = SyncStatus.ERROR
            showBalloon("Push failed: ${e.message}", NotificationType.WARNING)
        }
    }

    private fun doPull() {
        val b = backend ?: return
        status = SyncStatus.SYNCING
        try {
            val snapshot = b.pull(settings.workspaceId)
            if (snapshot.version > settings.lastSyncedVersion) {
                settings.lastSyncedVersion = snapshot.version
                UIUtil.invokeLaterIfNeeded {
                    storage.root.children.clear()
                    storage.root.children.addAll(snapshot.root.children)
                    project.messageBus
                        .syncPublisher(QuerySavedListener.TOPIC)
                        .querySaved()
                    showBalloon(
                        "SQLFolio updated from server (v${snapshot.version})",
                        NotificationType.INFORMATION
                    )
                }
            }
            status = SyncStatus.SYNCED
        } catch (e: Exception) {
            status = SyncStatus.ERROR
            showBalloon("Pull failed: ${e.message}", NotificationType.WARNING)
        }
    }

    // ── Conflict resolution ───────────────────────────────────────────────────

    private fun handleConflict(serverVersion: Int, serverRoot: QueryNode) {
        UIUtil.invokeLaterIfNeeded {
            val choice = Messages.showYesNoCancelDialog(
                project,
                "Your SQLFolio is behind the server (server is at v$serverVersion).\n\n" +
                "• Keep Mine   — overwrite the server with your current version\n" +
                "• Take Server — replace your local library with the server's version\n" +
                "• Cancel      — do nothing (you will be prompted again next sync)",
                "SQLFolio Sync Conflict",
                "Keep Mine", "Take Server", "Cancel",
                Messages.getWarningIcon()
            )
            when (choice) {
                Messages.YES -> {
                    settings.lastSyncedVersion = serverVersion
                    executor.submit { schedulePush() }
                }
                Messages.NO  -> {
                    settings.lastSyncedVersion = serverVersion
                    storage.root.children.clear()
                    storage.root.children.addAll(serverRoot.children)
                    project.messageBus
                        .syncPublisher(QuerySavedListener.TOPIC)
                        .querySaved()
                    status = SyncStatus.SYNCED
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Deep-copy via Gson so the HTTP thread never races with EDT mutations. */
    private fun deepCopy(node: QueryNode): QueryNode =
        gson.fromJson(gson.toJson(node), QueryNode::class.java)

    private fun showBalloon(message: String, type: NotificationType) {
        UIUtil.invokeLaterIfNeeded {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("SQLFolio")
                .createNotification("SQLFolio Sync", message, type)
                .notify(project)
        }
    }

    override fun dispose() {
        disconnect()
        executor.shutdownNow()
    }

    companion object {
        fun getInstance(project: Project): SQLFolioSyncService =
            project.getService(SQLFolioSyncService::class.java)
    }
}

