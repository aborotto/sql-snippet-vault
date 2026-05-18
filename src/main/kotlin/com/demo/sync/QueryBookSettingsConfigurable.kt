package com.demo.sync

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

/**
 * IDE Settings → Tools → SQLFolio Sync
 *
 * Lets the user choose between three back-end types (REST, PostgreSQL, SQLite)
 * and configure the appropriate credentials.  A "Test Connection" button
 * validates settings before saving.
 */
class SQLFolioSettingsConfigurable : Configurable {

    // ── Shared ────────────────────────────────────────────────────────────────
    private lateinit var enabledCheckbox:     JCheckBox
    private lateinit var backendTypeCombo:    JComboBox<String>
    private lateinit var workspaceIdField:    JTextField
    private lateinit var syncIntervalSpinner: JSpinner
    private lateinit var statusLabel:         JLabel
    private lateinit var cardPanel:           JPanel
    private val cardLayout = CardLayout()

    // ── REST panel ────────────────────────────────────────────────────────────
    private lateinit var serverUrlField: JTextField
    private lateinit var apiTokenField:  JPasswordField

    // ── PostgreSQL panel ──────────────────────────────────────────────────────
    private lateinit var jdbcUrlField:         JTextField
    private lateinit var dbUserField:          JTextField
    private lateinit var dbPasswordField:      JPasswordField
    private lateinit var dbSchemaField:        JTextField
    private lateinit var retentionSpinner:     JSpinner
    private lateinit var maxPayloadSpinner:    JSpinner
    private lateinit var jdbcUrlInfoLabel:     JLabel

    // ── SQLite panel (separate fields — a Swing component can only have one parent) ──
    private lateinit var sqliteJdbcUrlField:      JTextField
    private lateinit var sqliteRetentionSpinner:  JSpinner
    private lateinit var sqliteMaxPayloadSpinner: JSpinner

    override fun getDisplayName() = "SQLFolio Sync"

    override fun createComponent(): JComponent {
        // ── Shared controls ───────────────────────────────────────────────────
        enabledCheckbox     = JCheckBox("Enable real-time team sync")
        backendTypeCombo    = JComboBox(arrayOf("REST API", "PostgreSQL", "SQLite"))
        workspaceIdField    = JTextField(30)
        syncIntervalSpinner = JSpinner(SpinnerNumberModel(60, 10, 3600, 10))
        statusLabel         = JLabel(" ")

        val browseWorkspacesButton = JButton("Browse…").apply {
            toolTipText = "Load existing workspaces from the database and pick one"
            addActionListener { browseWorkspaces() }
        }
        val workspaceRow = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            add(workspaceIdField,       BorderLayout.CENTER)
            add(browseWorkspacesButton, BorderLayout.EAST)
        }

        // ── REST panel ────────────────────────────────────────────────────────
        serverUrlField = JTextField(40)
        apiTokenField  = JPasswordField(40)

        val restHint = JLabel(
            "<html><small><i>Point to any server implementing the SQLFolio REST API.<br>" +
            "See README for the full API contract.</i></small></html>"
        ).apply { border = JBUI.Borders.emptyTop(4) }

        val restForm = FormBuilder.createFormBuilder()
            .addLabeledComponent("Server URL:", serverUrlField, true)
            .addLabeledComponent("API Token:",  apiTokenField,  true)
            .addComponent(restHint)
            .panel

        // ── PostgreSQL panel ──────────────────────────────────────────────────
        jdbcUrlField    = JTextField(40)
        dbUserField     = JTextField(40)
        dbPasswordField = JPasswordField(40)
        dbSchemaField   = JTextField(40)
        retentionSpinner = JSpinner(SpinnerNumberModel(10, 1, 100, 1))
        maxPayloadSpinner = JSpinner(SpinnerNumberModel(5120, 256, 102400, 256))
        jdbcUrlInfoLabel = JLabel("<html><small>You can paste a full JDBC URL. SQLFolio can read <code>user</code> and <code>password</code> from it.</small></html>")

        val pgHint = JLabel(
            "<html><small><i>Example: <code>jdbc:postgresql://localhost:5432/mydb</code><br>" +
            "You may also paste a full PostgreSQL JDBC URL with inline credentials.<br>" +
            "Tables are created automatically. Real-time via LISTEN/NOTIFY (≤ 200 ms).</i></small></html>"
        ).apply { border = JBUI.Borders.emptyTop(4) }

        val pgForm = FormBuilder.createFormBuilder()
            .addLabeledComponent("JDBC URL:",              jdbcUrlField,     true)
            .addComponent(jdbcUrlInfoLabel)
            .addLabeledComponent("User:",                  dbUserField,      true)
            .addLabeledComponent("Password:",              dbPasswordField,  true)
            .addLabeledComponent("Schema (optional):",     dbSchemaField,    true)
            .addLabeledComponent("Keep last N versions:",  retentionSpinner, true)
            .addLabeledComponent("Max payload (KB):",      maxPayloadSpinner, true)
            .addComponent(pgHint)
            .addComponent(buildSchemaPanel())
            .panel

        // ── SQLite panel ──────────────────────────────────────────────────────
        // Uses its OWN separate field instances — a Swing component can only belong to one parent
        sqliteJdbcUrlField    = JTextField(40)
        sqliteRetentionSpinner = JSpinner(SpinnerNumberModel(10, 1, 100, 1))
        sqliteMaxPayloadSpinner = JSpinner(SpinnerNumberModel(5120, 256, 102400, 256))

        val sqliteHint = JLabel(
            "<html><small><i>Example: <code>jdbc:sqlite:C:/shared/sqlfolio.db</code><br>" +
            "Leave User/Password empty. Real-time via file watcher (≤ 1 s).</i></small></html>"
        ).apply { border = JBUI.Borders.emptyTop(4) }

        val sqliteForm = FormBuilder.createFormBuilder()
            .addLabeledComponent("SQLite file (JDBC URL):", sqliteJdbcUrlField,        true)
            .addLabeledComponent("Keep last N versions:",   sqliteRetentionSpinner,    true)
            .addLabeledComponent("Max payload (KB):",       sqliteMaxPayloadSpinner,   true)
            .addComponent(sqliteHint)
            .addComponent(buildSchemaPanel())
            .panel

        // ── Card panel ────────────────────────────────────────────────────────
        cardPanel = JPanel(cardLayout)
        cardPanel.add(restForm,    BackendType.REST.name)
        cardPanel.add(pgForm,      BackendType.POSTGRESQL.name)
        cardPanel.add(sqliteForm,  BackendType.SQLITE.name)

        backendTypeCombo.addActionListener {
            val key = when (backendTypeCombo.selectedIndex) {
                1    -> BackendType.POSTGRESQL.name
                2    -> BackendType.SQLITE.name
                else -> BackendType.REST.name
            }
            cardLayout.show(cardPanel, key)
            refreshJdbcUrlInfo()
        }

        jdbcUrlField.document.addDocumentListener(simpleDocumentListener {
            maybeAutofillCredentialsFromJdbcUrl()
            refreshJdbcUrlInfo()
        })

        // ── Test button ───────────────────────────────────────────────────────
        val testButton = JButton("Test Connection").apply {
            addActionListener { testConnection() }
        }
        val buttonRow = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(testButton,  BorderLayout.WEST)
            add(statusLabel, BorderLayout.CENTER)
        }

        // ── Main form ─────────────────────────────────────────────────────────
        val mainForm = FormBuilder.createFormBuilder()
            .addComponent(enabledCheckbox)
            .addSeparator()
            .addLabeledComponent("Back-end type:",       backendTypeCombo,    true)
            .addLabeledComponent("Workspace ID:",        workspaceRow,        true)
            .addLabeledComponent("Poll interval (sec):", syncIntervalSpinner, true)
            .addSeparator()
            .addComponent(cardPanel)
            .addSeparator()
            .addComponent(buttonRow)
            .panel

        reset()

        val contentPanel = JPanel(BorderLayout()).apply {
            add(mainForm, BorderLayout.NORTH)
        }

        return JBScrollPane(contentPanel).apply {
            border = null
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy   = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 16
            preferredSize = Dimension(540, 600)
        }
    }

    // ── Configurable contract ─────────────────────────────────────────────────

    override fun isModified(): Boolean {
        val s = SQLFolioSyncSettings.getInstance()
        return enabledCheckbox.isSelected                != s.syncEnabled
            || backendTypeCombo.selectedIndex            != s.resolvedBackendType().ordinal
            || workspaceIdField.text.trim()              != s.workspaceId
            || syncIntervalSpinner.value as Int          != s.syncIntervalSeconds
            || serverUrlField.text.trim()                != s.serverUrl
            || String(apiTokenField.password)            != s.apiToken
            || jdbcUrlField.text.trim()                  != s.jdbcUrl
            || sqliteJdbcUrlField.text.trim()            != s.jdbcUrl
            || dbUserField.text.trim()                   != s.dbUser
            || String(dbPasswordField.password)          != s.dbPassword
            || dbSchemaField.text.trim()                 != s.dbSchema
            || retentionSpinner.value as Int             != s.retentionVersions
            || sqliteRetentionSpinner.value as Int       != s.retentionVersions
            || maxPayloadSpinner.value as Int            != s.maxPayloadKb.coerceIn(256, 102400).let { if (it < 256) 5120 else it }
            || sqliteMaxPayloadSpinner.value as Int      != s.maxPayloadKb.coerceIn(256, 102400).let { if (it < 256) 5120 else it }
    }

    override fun apply() {
        val s = SQLFolioSyncSettings.getInstance()
        s.syncEnabled         = enabledCheckbox.isSelected
        val backendIdx        = backendTypeCombo.selectedIndex
        s.backendType         = when (backendIdx) {
            1    -> BackendType.POSTGRESQL.name
            2    -> BackendType.SQLITE.name
            else -> BackendType.REST.name
        }
        s.workspaceId         = workspaceIdField.text.trim()
        s.syncIntervalSeconds = syncIntervalSpinner.value as Int
        s.serverUrl           = serverUrlField.text.trim()
        s.apiToken            = String(apiTokenField.password)
        s.dbUser              = dbUserField.text.trim()
        s.dbPassword          = String(dbPasswordField.password)
        s.dbSchema            = dbSchemaField.text.trim()
        // Pick the active panel's JDBC + retention + payload fields
        if (backendIdx == 2) {
            s.jdbcUrl           = sqliteJdbcUrlField.text.trim()
            s.retentionVersions = sqliteRetentionSpinner.value as Int
            s.maxPayloadKb      = sqliteMaxPayloadSpinner.value as Int
        } else {
            s.jdbcUrl           = jdbcUrlField.text.trim()
            s.retentionVersions = retentionSpinner.value as Int
            s.maxPayloadKb      = maxPayloadSpinner.value as Int
        }
        if (!s.syncEnabled) s.lastSyncedVersion = 0

        // Reconnect the sync service in every open project so settings take effect immediately
        ProjectManager.getInstance().openProjects.forEach { project ->
            SQLFolioSyncService.getInstance(project).reconnect()
        }
    }

    override fun reset() {
        val s = SQLFolioSyncSettings.getInstance()
        enabledCheckbox.isSelected     = s.syncEnabled
        backendTypeCombo.selectedIndex = s.resolvedBackendType().ordinal
        workspaceIdField.text          = s.workspaceId
        syncIntervalSpinner.value      = s.syncIntervalSeconds
        serverUrlField.text            = s.serverUrl
        apiTokenField.text             = s.apiToken
        jdbcUrlField.text              = s.jdbcUrl
        sqliteJdbcUrlField.text        = s.jdbcUrl
        dbUserField.text               = s.dbUser
        dbPasswordField.text           = s.dbPassword
        dbSchemaField.text             = s.dbSchema
        retentionSpinner.value         = s.retentionVersions
        sqliteRetentionSpinner.value   = s.retentionVersions
        val safePayload = if (s.maxPayloadKb < 256) 5120 else s.maxPayloadKb.coerceAtMost(102400)
        maxPayloadSpinner.value        = safePayload
        sqliteMaxPayloadSpinner.value  = safePayload
        statusLabel.text               = " "
        cardLayout.show(cardPanel, s.resolvedBackendType().name)
        refreshJdbcUrlInfo()
    }

    // ── Test connection ───────────────────────────────────────────────────────

    private fun testConnection() {
        val backendIdx = backendTypeCombo.selectedIndex
        val candidate: SyncBackend? = when (backendIdx) {
            0 -> {
                val url   = serverUrlField.text.trim()
                val token = String(apiTokenField.password)
                if (url.isBlank()) { statusLabel.text = "⚠  Enter a Server URL first."; return }
                RestSyncBackend(url, token)
            }
            1 -> {
                val jdbc   = jdbcUrlField.text.trim()
                val user   = dbUserField.text.trim()
                val pass   = String(dbPasswordField.password)
                val ret    = retentionSpinner.value as Int
                val schema = dbSchemaField.text.trim()
                val maxKb  = maxPayloadSpinner.value as Int
                if (jdbc.isBlank()) { statusLabel.text = "⚠  Enter a JDBC URL first."; return }
                DatabaseSyncBackend(jdbc, user, pass, ret, schema, maxKb)
            }
            2 -> {
                val jdbc  = sqliteJdbcUrlField.text.trim()
                val ret   = sqliteRetentionSpinner.value as Int
                val maxKb = sqliteMaxPayloadSpinner.value as Int
                if (jdbc.isBlank()) { statusLabel.text = "⚠  Enter a SQLite file path first."; return }
                DatabaseSyncBackend(jdbc, "", "", ret, "", maxKb)
            }
            else -> null
        }
        statusLabel.text = "Testing…"
        Thread {
            try {
                val ok = candidate?.testConnection() == true
                SwingUtilities.invokeLater {
                    statusLabel.text = if (ok)
                        "✅  Connected! Tables will be created automatically on first sync."
                    else
                        "❌  Could not connect. Check JDBC URL, user and password."
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater { statusLabel.text = "❌  ${e.message}" }
            }
        }.start()
    }

    // ── Browse workspaces ─────────────────────────────────────────────────────

    private fun browseWorkspaces() {
        val backendIdx = backendTypeCombo.selectedIndex
        if (backendIdx == 0) {
            statusLabel.text = "⚠  Browse is only available for PostgreSQL / SQLite backends."
            return
        }
        val jdbc   = if (backendIdx == 2) sqliteJdbcUrlField.text.trim() else jdbcUrlField.text.trim()
        val user   = dbUserField.text.trim()
        val pass   = String(dbPasswordField.password)
        val schema = dbSchemaField.text.trim()
        val ret    = if (backendIdx == 2) sqliteRetentionSpinner.value as Int else retentionSpinner.value as Int
        if (jdbc.isBlank()) {
            statusLabel.text = "⚠  Enter a JDBC URL first, then browse workspaces."
            return
        }
        statusLabel.text = "Loading workspaces…"
        Thread {
            try {
                val backend = DatabaseSyncBackend(jdbc, user, pass, ret, schema)
                val workspaces = backend.listWorkspaces()
                SwingUtilities.invokeLater {
                    showWorkspacePickerDialog(workspaces)
                    statusLabel.text = " "
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    statusLabel.text = "❌  Could not load workspaces: ${e.message}"
                }
            }
        }.start()
    }

    private fun showWorkspacePickerDialog(existing: List<String>) {
        val options = (existing + listOf("＋ Create new…")).toTypedArray()
        val choice = JOptionPane.showInputDialog(
            null,
            "Select an existing workspace or create a new one:",
            "Browse Workspaces",
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            if (workspaceIdField.text.isNotBlank() && existing.contains(workspaceIdField.text))
                workspaceIdField.text else options.firstOrNull()
        ) as? String ?: return

        if (choice == "＋ Create new…") {
            val newName = JOptionPane.showInputDialog(
                null,
                "Enter a name for the new workspace:",
                "New Workspace",
                JOptionPane.PLAIN_MESSAGE
            )?.trim() ?: return
            if (newName.isNotBlank()) workspaceIdField.text = newName
        } else {
            workspaceIdField.text = choice
        }
    }

    private fun maybeAutofillCredentialsFromJdbcUrl() {
        val resolved = DatabaseSyncBackend.resolveConnection(jdbcUrlField.text)
        if (resolved.inlineUser != null && dbUserField.text.isBlank()) {
            dbUserField.text = resolved.inlineUser
        }
        if (resolved.inlinePassword != null && String(dbPasswordField.password).isBlank()) {
            dbPasswordField.text = resolved.inlinePassword
        }
    }

    private fun extractCredentialsFromJdbcUrl() {
        val resolved = DatabaseSyncBackend.resolveConnection(jdbcUrlField.text)
        val foundUser = !resolved.inlineUser.isNullOrBlank()
        val foundPassword = !resolved.inlinePassword.isNullOrBlank()
        if (!foundUser && !foundPassword) {
            statusLabel.text = "ℹ No inline user/password found in the JDBC URL."
            refreshJdbcUrlInfo()
            return
        }

        if (foundUser) dbUserField.text = resolved.inlineUser
        if (foundPassword) dbPasswordField.text = resolved.inlinePassword
        jdbcUrlField.text = resolved.jdbcUrl
        statusLabel.text = "✅ Imported credentials from JDBC URL into separate fields."
        refreshJdbcUrlInfo()
    }

    private fun refreshJdbcUrlInfo() {
        if (!::jdbcUrlInfoLabel.isInitialized) return
        val resolved = DatabaseSyncBackend.resolveConnection(jdbcUrlField.text)
        jdbcUrlInfoLabel.text = when {
            resolved.inlineUser != null || resolved.inlinePassword != null ->
                "<html><small>Detected inline credentials in the JDBC URL.${if (resolved.inlineUser != null) " User: <code>${escapeHtml(resolved.inlineUser)}</code>." else ""} They can be used directly or extracted into the separate fields.</small></html>"
            else ->
                "<html><small>You can paste a full JDBC URL. SQLFolio can read <code>user</code> and <code>password</code> from it.</small></html>"
        }
    }

    private fun simpleDocumentListener(onChange: () -> Unit) = object : javax.swing.event.DocumentListener {
        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // ── Schema info panel ─────────────────────────────────────────────────────

    /**
     * Collapsible panel showing the exact SQL DDL the plugin will create/use.
     * Users can copy it to set up the tables manually if they prefer.
     */
    private fun buildSchemaPanel(): JPanel {
        val ddlArea = JTextArea(DatabaseSyncBackend.SCHEMA_DDL).apply {
            isEditable  = false
            font        = Font(Font.MONOSPACED, Font.PLAIN, 11)
            border      = JBUI.Borders.empty(6)
            background  = UIManager.getColor("Panel.background")
            lineWrap    = false
            rows        = 14
        }
        val copyBtn = JButton("Copy SQL").apply {
            addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(StringSelection(DatabaseSyncBackend.SCHEMA_DDL), null)
            }
        }
        val scrollPane = JScrollPane(ddlArea).apply {
            border = BorderFactory.createTitledBorder("Required database schema (auto-created on first connect)")
        }
        val content = JPanel(BorderLayout(0, 4)).apply {
            add(scrollPane, BorderLayout.CENTER)
            add(copyBtn,    BorderLayout.SOUTH)
            border = JBUI.Borders.emptyTop(8)
        }

        // Toggle: collapsed by default
        var expanded = false
        content.isVisible = false

        val toggleBtn = JButton("ℹ Show required schema ▼").apply {
            isBorderPainted = false
            isContentAreaFilled = false
            foreground = UIManager.getColor("Link.activeForeground")
            addActionListener {
                expanded = !expanded
                content.isVisible = expanded
                text = if (expanded) "ℹ Hide schema ▲" else "ℹ Show required schema ▼"
            }
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(toggleBtn, BorderLayout.NORTH)
            add(content,   BorderLayout.CENTER)
        }
    }
}
