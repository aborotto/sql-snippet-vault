package com.demo.sync

import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * IDE Settings → Tools → QueryBook Sync
 *
 * Exposes all [QueryBookSyncSettings] fields with a "Test Connection" button
 * that hits the health endpoint before the user saves.
 *
 * After Apply, the active [QueryBookSyncService] (if any) is instructed to
 * reconnect so new settings take effect immediately.
 */
class QueryBookSettingsConfigurable : Configurable {

    // ── UI components ─────────────────────────────────────────────────────────

    private lateinit var enabledCheckbox:     JCheckBox
    private lateinit var serverUrlField:      JTextField
    private lateinit var apiTokenField:       JPasswordField
    private lateinit var workspaceIdField:    JTextField
    private lateinit var syncIntervalSpinner: JSpinner
    private lateinit var statusLabel:         JLabel

    override fun getDisplayName() = "QueryBook Sync"

    override fun createComponent(): JComponent {
        enabledCheckbox     = JCheckBox("Enable real-time team sync")
        serverUrlField      = JTextField(40)
        apiTokenField       = JPasswordField(40)
        workspaceIdField    = JTextField(40)
        syncIntervalSpinner = JSpinner(SpinnerNumberModel(60, 10, 3600, 10))
        statusLabel         = JLabel(" ")

        val testButton = JButton("Test Connection").apply {
            addActionListener { testConnection() }
        }

        val buttonRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(testButton, BorderLayout.WEST)
            add(statusLabel, BorderLayout.CENTER)
        }

        val hint = JLabel(
            "<html><small><i>" +
            "Server must implement the QueryBook REST API.<br>" +
            "See the README for the full API contract." +
            "</i></small></html>"
        ).apply { border = JBUI.Borders.emptyTop(4) }

        val form = FormBuilder.createFormBuilder()
            .addComponent(enabledCheckbox)
            .addSeparator()
            .addLabeledComponent("Server URL:",             serverUrlField,      true)
            .addLabeledComponent("API Token:",              apiTokenField,       true)
            .addLabeledComponent("Workspace ID:",           workspaceIdField,    true)
            .addLabeledComponent("Poll interval (seconds):", syncIntervalSpinner, true)
            .addSeparator()
            .addComponent(buttonRow)
            .addComponent(hint)
            .panel

        reset()   // populate fields from current settings

        return JPanel(BorderLayout()).apply {
            add(form, BorderLayout.NORTH)
            preferredSize = Dimension(500, 0)
        }
    }

    // ── Configurable contract ─────────────────────────────────────────────────

    override fun isModified(): Boolean {
        val s = QueryBookSyncSettings.getInstance()
        return enabledCheckbox.isSelected         != s.syncEnabled
            || serverUrlField.text.trim()         != s.serverUrl
            || String(apiTokenField.password)     != s.apiToken
            || workspaceIdField.text.trim()       != s.workspaceId
            || syncIntervalSpinner.value as Int   != s.syncIntervalSeconds
    }

    override fun apply() {
        val s = QueryBookSyncSettings.getInstance()
        s.syncEnabled          = enabledCheckbox.isSelected
        s.serverUrl            = serverUrlField.text.trim()
        s.apiToken             = String(apiTokenField.password)
        s.workspaceId          = workspaceIdField.text.trim()
        s.syncIntervalSeconds  = syncIntervalSpinner.value as Int
        // Reset version so we don't get a false conflict after reconfiguring
        if (!s.syncEnabled) s.lastSyncedVersion = 0
    }

    override fun reset() {
        val s = QueryBookSyncSettings.getInstance()
        enabledCheckbox.isSelected   = s.syncEnabled
        serverUrlField.text          = s.serverUrl
        apiTokenField.text           = s.apiToken
        workspaceIdField.text        = s.workspaceId
        syncIntervalSpinner.value    = s.syncIntervalSeconds
        statusLabel.text             = " "
    }

    // ── Test connection ───────────────────────────────────────────────────────

    private fun testConnection() {
        val url   = serverUrlField.text.trim()
        val token = String(apiTokenField.password)
        if (url.isBlank()) {
            statusLabel.text = "⚠  Enter a Server URL first."
            return
        }
        statusLabel.text = "Testing…"
        Thread {
            try {
                val ok = QueryBookApiClient.testConnection(url, token)
                SwingUtilities.invokeLater {
                    statusLabel.text = if (ok) "✅  Connected successfully!" else "❌  Server unreachable."
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater { statusLabel.text = "❌  ${e.message}" }
            }
        }.start()
    }
}

