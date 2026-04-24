package com.demo.model

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Project-level persistent storage for the QueryBook tree.
 * The [root] node is a virtual root folder that is never displayed directly.
 */
@State(name = "QueryBookSettings", storages = [Storage("QueryBook.xml")])
@Service(Service.Level.PROJECT)
class QueryStorage : PersistentStateComponent<QueryStorage> {

    var root: QueryNode = QueryNode(name = "Root", isFolder = true)

    override fun getState(): QueryStorage = this

    override fun loadState(state: QueryStorage) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): QueryStorage =
            project.getService(QueryStorage::class.java)
    }
}