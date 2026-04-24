package com.demo.model

/**
 * Unified model for both folders and SQL queries.
 *
 * When [isFolder] is true the node acts as a container; [sqlCode] and [description] are ignored.
 * [createdAt] is a Unix-epoch millis timestamp stored for informational/export purposes.
 */
data class QueryNode(
    var name: String = "",
    var sqlCode: String = "",
    var description: String = "",
    var isFolder: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var children: MutableList<QueryNode> = mutableListOf()
)



