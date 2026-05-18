package com.demo.sync

import com.demo.model.QueryNode

/**
 * [SyncBackend] implementation that delegates to the existing [SQLFolioApiClient] (HTTP REST).
 */
class RestSyncBackend(
    private val serverUrl: String,
    private val apiToken:  String
) : SyncBackend {

    override fun testConnection(): Boolean =
        SQLFolioApiClient.testConnection(serverUrl, apiToken)

    override fun pull(workspaceId: String): SyncBackend.Snapshot {
        val r = SQLFolioApiClient.pull(serverUrl, apiToken, workspaceId)
        return SyncBackend.Snapshot(r.version, r.root)
    }

    override fun push(workspaceId: String, clientVersion: Int, root: QueryNode): SyncBackend.PushResult {
        val r = SQLFolioApiClient.push(serverUrl, apiToken, workspaceId, clientVersion, root)
        return SyncBackend.PushResult(r.version, r.conflict, r.root)
    }
}

