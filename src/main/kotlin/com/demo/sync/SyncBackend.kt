package com.demo.sync

import com.demo.model.QueryNode

/**
 * Abstraction layer for all sync back-ends.
 *
 * Implementations:
 *   - [RestSyncBackend]     — HTTP REST server (self-hosted or cloud)
 *   - [DatabaseSyncBackend] — Direct JDBC connection (PostgreSQL or SQLite)
 *
 * The optimistic-locking contract is the same regardless of back-end:
 *   • pull()  → returns the latest (version, tree) for a workspace
 *   • push()  → atomically writes if client version == server version;
 *               returns conflict=true + server tree when the client is behind
 */
interface SyncBackend {

    /** Snapshot returned by [pull]. */
    data class Snapshot(
        val version: Int    = 0,
        val root: QueryNode = QueryNode(name = "Root", isFolder = true)
    )

    /** Result returned by [push]. */
    data class PushResult(
        val version:  Int       = 0,
        val conflict: Boolean   = false,
        val root:     QueryNode? = null   // only present when conflict == true
    )

    /** Returns true if the back-end is reachable and credentials are valid. */
    fun testConnection(): Boolean

    /** Fetch the latest workspace snapshot. */
    fun pull(workspaceId: String): Snapshot

    /**
     * Push [root] to the workspace.
     * [clientVersion] must equal the server's current version for the write to succeed.
     */
    fun push(workspaceId: String, clientVersion: Int, root: QueryNode): PushResult

    // ── Optional real-time hooks ───────────────────────────────────────────────

    /**
     * Start listening for remote pushes from other clients.
     * [onRemoteChange] is invoked on a background thread whenever another client pushes.
     * Default implementation is a no-op — polling is always used as fallback.
     */
    fun startListening(onRemoteChange: () -> Unit) { /* no-op */ }

    /** Stop the listener started by [startListening]. */
    fun stopListening() { /* no-op */ }
}

