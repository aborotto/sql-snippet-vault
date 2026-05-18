package com.demo.ui

import com.demo.model.QuerySavedListener
import com.demo.sync.SQLFolioSyncService
import com.demo.sync.SyncStatus
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane

/**
 * Entry-point registered in plugin.xml.
 * Delegates all UI construction to [SQLFolioPanel].
 */
class QueryToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project): Boolean = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SQLFolioPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Main panel that wires [QueryTreePanel] (left) and [QueryEditorPanel] (right)
 * together inside a resizable split pane.
 *
 * A thin sync status bar is anchored to the bottom — it is only visible when
 * sync is enabled in Settings → Tools → SQLFolio Sync.
 */
class SQLFolioPanel(private val project: Project) {

    val component: JPanel = JPanel(BorderLayout())

    private val editorPanel = QueryEditorPanel(project)
    private val treePanel   = QueryTreePanel(project) { node -> editorPanel.load(node) }

    // ── Sync status bar ───────────────────────────────────────────────────────
    private val statusLabel = JLabel("").apply {
        border = JBUI.Borders.emptyLeft(6)
        font   = font.deriveFont(font.size - 1f)
    }

    init {
        val splitter = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            treePanel.component,
            editorPanel.component
        ).apply {
            dividerLocation = 260
            dividerSize     = 3
            border          = null
            isOpaque        = false
        }

        component.add(splitter,        BorderLayout.CENTER)
        component.add(buildStatusBar(), BorderLayout.SOUTH)

        // Re-render the tree whenever a query is saved via the editor action
        project.messageBus.connect().subscribe(
            QuerySavedListener.TOPIC,
            QuerySavedListener { UIUtil.invokeLaterIfNeeded { treePanel.refresh() } }
        )

        // Wire sync status updates → status bar label
        val syncService = SQLFolioSyncService.getInstance(project)
        syncService.onStatusChanged = { status -> updateStatus(status) }
        updateStatus(syncService.status)   // show current state immediately
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateStatus(status: SyncStatus) {
        val bar = component.getComponent(1) as? JPanel ?: return
        bar.isVisible   = status != SyncStatus.DISCONNECTED
        statusLabel.text = status.label
    }

    private fun buildStatusBar(): JPanel {
        val syncService = SQLFolioSyncService.getInstance(project)

        val group = DefaultActionGroup().apply {
            add(object : AnAction("Push Now", "Push local changes to sync backend", AllIcons.Actions.Upload) {
                override fun actionPerformed(e: AnActionEvent) = syncService.pushNow()
            })
            add(object : AnAction("Pull Now", "Pull latest from sync backend", AllIcons.Actions.Download) {
                override fun actionPerformed(e: AnActionEvent) = syncService.pullNow()
            })
            addSeparator()
            add(object : AnAction("Sync Settings…", "Open SQLFolio Sync settings", AllIcons.General.Settings) {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, "SQLFolio Sync")
                }
            })
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("SQLFolioSyncBar", group, true)
            .also { it.targetComponent = component }

        return JPanel(BorderLayout()).apply {
            isVisible = false   // hidden until sync is active
            border    = JBUI.Borders.customLine(
                com.intellij.ui.JBColor.border(), 1, 0, 0, 0
            )
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(statusLabel)
            }, BorderLayout.WEST)
            add(toolbar.component, BorderLayout.EAST)
        }
    }
}
