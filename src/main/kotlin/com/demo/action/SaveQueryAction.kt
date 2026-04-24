package com.demo.action

import com.demo.model.QueryNode
import com.demo.model.QueryStorage
import com.demo.model.QuerySavedListener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/**
 * Saves SQL to QueryBook.
 *
 * • If text is selected in the editor → saves the selection.
 * • If nothing is selected            → saves the entire document.
 *
 * Triggered via right-click → "Save to QueryBook"
 * or the keyboard shortcut  Ctrl+Alt+Q.
 */
class SaveQueryAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        // Available whenever an editor with content is open — no selection required
        e.presentation.isEnabledAndVisible =
            editor != null && editor.document.textLength > 0
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor  = e.getData(CommonDataKeys.EDITOR) ?: return

        val sql = if (editor.selectionModel.hasSelection())
            editor.selectionModel.selectedText ?: return
        else
            editor.document.text

        if (sql.isBlank()) return

        val storage = QueryStorage.getInstance(project)
        val dialog  = SaveToQueryBookDialog(project, storage)

        if (!dialog.showAndGet()) return   // user cancelled

        dialog.targetFolder.children.add(
            QueryNode(name = dialog.queryName, sqlCode = sql, description = "Saved from editor.")
        )
        project.messageBus.syncPublisher(QuerySavedListener.TOPIC).querySaved()
    }
}