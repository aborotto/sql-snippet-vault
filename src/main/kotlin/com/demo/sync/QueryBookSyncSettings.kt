package com.demo.sync

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Application-level (global) settings for QueryBook team sync.
 * Stored in <IDE config>/options/QueryBookSync.xml — shared across all projects.
 *
 * Fields:
 *   serverUrl   — base URL of your sync server (e.g. "https://querybook.yourteam.com")
 *   apiToken    — Bearer token for authentication
 *   workspaceId — which workspace/room to sync with (supports multiple teams)
 *   syncEnabled — master on/off switch
 *   syncIntervalSeconds — how often to poll for remote changes
 *   lastSyncedVersion   — the last server version the client has accepted (for conflict detection)
 */
@State(name = "QueryBookSyncSettings", storages = [Storage("QueryBookSync.xml")])
@Service(Service.Level.APP)
class QueryBookSyncSettings : PersistentStateComponent<QueryBookSyncSettings> {

    var serverUrl: String = ""
    var apiToken: String = ""
    var workspaceId: String = ""
    var syncEnabled: Boolean = false
    var syncIntervalSeconds: Int = 60
    var lastSyncedVersion: Int = 0

    override fun getState(): QueryBookSyncSettings = this

    override fun loadState(state: QueryBookSyncSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): QueryBookSyncSettings =
            ApplicationManager.getApplication().getService(QueryBookSyncSettings::class.java)
    }
}

