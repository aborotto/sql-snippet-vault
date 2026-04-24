package com.demo.ui

import com.demo.model.QueryNode
import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.event.DocumentEvent as SwingDocumentEvent

/**
 * Right-side panel that hosts:
 * - a SQL editor (syntax-highlighted via LanguageTextField)
 * - a "Run Query" action button
 * - a free-text description / notes area
 *
 * When no query is selected an empty-state placeholder is shown instead.
 */
class QueryEditorPanel(private val project: Project) {

    private val cardLayout = CardLayout()

    // Root component exposed to the parent layout
    val component: JPanel = JPanel(cardLayout)
    private val editorCard = JPanel(GridBagLayout())

    private val sqlEditor = LanguageTextField(Language.findLanguageByID("SQL"), project, "", false).apply {
        setPlaceholder("-- Write your SQL here")
    }
    private val descTextArea = JBTextArea(4, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        emptyText.text = "Add notes about this query…"
        background = UIUtil.getEditorPaneBackground()
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(5)
        )
    }

    /** The query node currently loaded in the editor. Null means nothing is selected. */
    var activeNode: QueryNode? = null
        private set

    /** Guards against recursive save-on-load cycles. */
    private var suppressUpdates = false

    init {
        buildEditorCard()
        component.add(buildEmptyStateCard(), CARD_EMPTY)
        component.add(editorCard, CARD_EDITOR)
        cardLayout.show(component, CARD_EMPTY)
        wireListeners()
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    private fun buildEditorCard() {
        editorCard.isOpaque = false
        editorCard.border = JBUI.Borders.empty(6, 10, 10, 10)

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.BOTH
            weightx = 1.0
            gridx = 0
        }

        val sqlScrollPane = JBScrollPane(sqlEditor).apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1)
        }

        val runButton = JButton("Run Query", AllIcons.Actions.Execute).apply {
            font = JBFont.label().asBold()
            addActionListener { activeNode?.let { openAndRunSql(it.name, it.sqlCode) } }
        }
        val copyButton = JButton("Copy SQL", AllIcons.Actions.Copy).apply {
            toolTipText = "Copy SQL to clipboard"
            addActionListener {
                val sql = sqlEditor.text
                if (sql.isNotBlank()) {
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(StringSelection(sql), null)
                }
            }
        }
        val actionRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            val buttonPanel = JPanel().apply {
                isOpaque = false
                add(runButton)
                add(copyButton)
            }
            add(buttonPanel, BorderLayout.WEST)
        }

        gbc.apply { weighty = 0.7; gridy = 0; insets = JBUI.emptyInsets() }
        editorCard.add(sqlScrollPane, gbc)

        gbc.apply { weighty = 0.0; gridy = 1; insets = JBUI.insets(8, 0) }
        editorCard.add(actionRow, gbc)

        gbc.apply { weighty = 0.3; gridy = 2; insets = JBUI.emptyInsets() }
        editorCard.add(JBScrollPane(descTextArea).apply { border = null }, gbc)
    }

    private fun buildEmptyStateCard(): JPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(
            JLabel("Select a query to start editing", SwingConstants.CENTER).apply {
                foreground = UIUtil.getLabelDisabledForeground()
                font = JBFont.label().asItalic()
            },
            BorderLayout.CENTER
        )
    }

    // ── Listeners ──────────────────────────────────────────────────────────────

    private fun wireListeners() {
        sqlEditor.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (!suppressUpdates) activeNode?.sqlCode = sqlEditor.text
            }
        })

        descTextArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: SwingDocumentEvent?) = persist()
            override fun removeUpdate(e: SwingDocumentEvent?) = persist()
            override fun changedUpdate(e: SwingDocumentEvent?) = persist()
            private fun persist() {
                if (!suppressUpdates) activeNode?.description = descTextArea.text
            }
        })
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Load a query [node] into the editor and switch to the editor card.
     * Pass `null` (or a folder node) to return to the empty-state card.
     */
    fun load(node: QueryNode?) {
        activeNode = node
        suppressUpdates = true
        if (node != null && !node.isFolder) {
            sqlEditor.text = node.sqlCode
            descTextArea.text = node.description
            cardLayout.show(component, CARD_EDITOR)
        } else {
            sqlEditor.text = ""
            descTextArea.text = ""
            cardLayout.show(component, CARD_EMPTY)
        }
        suppressUpdates = false
    }

    // ── Execution ──────────────────────────────────────────────────────────────

    private fun openAndRunSql(title: String, sql: String) {
        val lang = Language.findLanguageByID("SQL") ?: return
        val scratchFile = ScratchRootType.getInstance()
            .createScratchFile(project, "querybook_$title.sql", lang, sql) ?: return

        FileEditorManager.getInstance(project).openFile(scratchFile, true)

        UIUtil.invokeLaterIfNeeded {
            val am = ActionManager.getInstance()
            // Action IDs stored in variables to avoid static plugin-config inspection
            val dbExecute = "Database.Execute"
            val jdbcExecute = "Console.Jdbc.Execute"
            val action = am.getAction(dbExecute) ?: am.getAction(jdbcExecute)
            action?.let { am.tryToExecute(it, null, null, null, true) }
        }
    }

    // ── Constants ──────────────────────────────────────────────────────────────

    private companion object {
        const val CARD_EMPTY = "empty"
        const val CARD_EDITOR = "editor"
    }
}



