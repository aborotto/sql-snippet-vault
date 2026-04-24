package com.demo.ui

import com.demo.model.QuerySavedListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane

/**
 * Entry-point registered in plugin.xml.
 * Delegates all UI construction to [QueryBookPanel].
 */
class QueryToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project): Boolean = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = QueryBookPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Main panel that wires [QueryTreePanel] (left) and [QueryEditorPanel] (right)
 * together inside a resizable split pane.
 *
 * Also listens to the [QuerySavedListener] topic so the tree refreshes
 * whenever a query is saved from the editor's context action.
 */
class QueryBookPanel(project: Project) {

    val component: JPanel = JPanel(BorderLayout())

    private val editorPanel = QueryEditorPanel(project)
    private val treePanel = QueryTreePanel(project) { node -> editorPanel.load(node) }

    init {
        val splitter = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            treePanel.component,
            editorPanel.component
        ).apply {
            dividerLocation = 260
            dividerSize = 3
            border = null
            isOpaque = false
        }

        component.add(splitter, BorderLayout.CENTER)

        // Re-render the tree whenever a query is saved via the editor action
        project.messageBus.connect().subscribe(
            QuerySavedListener.TOPIC,
            QuerySavedListener { UIUtil.invokeLaterIfNeeded { treePanel.refresh() } }
        )
    }
}

