package com.demo.model

import com.intellij.util.messages.Topic

/** Functional interface so it can be used with SAM conversion at call sites. */
fun interface QuerySavedListener {
    fun querySaved()

    companion object {
        @JvmStatic
        val TOPIC = Topic.create("Query Saved Event", QuerySavedListener::class.java)
    }
}