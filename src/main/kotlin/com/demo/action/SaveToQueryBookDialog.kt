package com.demo.action

import com.demo.model.QueryNode
import com.demo.model.QueryStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Dialog for saving a SQL snippet to QueryBook.
 * Lets the user set a name and pick a target folder (or root).
 */
class SaveToQueryBookDialog(
    project: Project,
    private val storage: QueryStorage,
    prefilledName: String = ""
) : DialogWrapper(project) {

    private val nameField = JTextField(prefilledName, 30)

    // null entry = "Root (no folder)"
    private val folders: List<QueryNode?>
    private val folderCombo: ComboBox<String>

    init {
        title = "Save to QueryBook"

        val list = mutableListOf<QueryNode?>(null) // index 0 → root
        collectFolders(storage.root, list)
        folders = list

        folderCombo = ComboBox(folders.map { it?.name ?: "📂 Root (no folder)" }.toTypedArray())

        init()
    }

    private fun collectFolders(node: QueryNode, out: MutableList<QueryNode?>) {
        for (child in node.children) {
            if (child.isFolder) {
                out.add(child)
                collectFolders(child, out)
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        panel.add(JBLabel("Query name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(nameField, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        panel.add(JBLabel("Save to folder:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(folderCombo, gbc)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        if (name.isBlank()) return ValidationInfo("Name cannot be empty.", nameField)
        if (isNameTaken(name, storage.root))
            return ValidationInfo("An item named '$name' already exists.", nameField)
        return null
    }

    /** The trimmed name entered by the user. */
    val queryName: String get() = nameField.text.trim()

    /** The selected target folder, or storage root if the user chose "Root". */
    val targetFolder: QueryNode
        get() = folders[folderCombo.selectedIndex] ?: storage.root

    private fun isNameTaken(name: String, node: QueryNode): Boolean {
        if (node.name != "Root" && node.name.equals(name, ignoreCase = true)) return true
        return node.children.any { isNameTaken(name, it) }
    }
}

