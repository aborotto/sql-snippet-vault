package com.demo.ui

import com.demo.model.QueryNode
import com.demo.model.QueryStorage
import com.demo.model.QuerySavedListener
import com.demo.action.SaveToQueryBookDialog
import com.demo.service.QueryImportExportService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import kotlin.math.abs

/**
 * Left-side panel that hosts:
 * - a toolbar with New Query / New Folder / Delete actions
 * - a live search field that filters the tree by name
 * - a JTree with full drag-and-drop support (reorder within folder + move across folders)
 *
 * The [onNodeSelected] callback is invoked whenever a query leaf is selected,
 * or with `null` when the selection is cleared / a folder is selected.
 */
class QueryTreePanel(
    private val project: Project,
    private val onNodeSelected: (QueryNode?) -> Unit
) {

    val component: JPanel = JPanel(BorderLayout())

    private val treeRoot = DefaultMutableTreeNode("Root")
    private val treeModel = DefaultTreeModel(treeRoot)

    /** Visual drop-position states used while a drag is live. */
    private enum class DropZone { BEFORE_ROW, INTO_FOLDER, AFTER_ROW }
    private var dndDropRow      = -1
    private var dndDropZone     = DropZone.AFTER_ROW

    val tree = object : Tree(treeModel) {

        init { setToolTipText("") }   // activates per-cell tooltips

        /** Draw the DnD drop indicator on top of the normal tree content. */
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (!dndActive || dndDropRow < 0) return
            val bounds = getRowBounds(dndDropRow) ?: return
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val color = UIManager.getColor("Tree.selectionBackground")
                ?: JBColor(0x3773E0, 0x4B82C9)
            when (dndDropZone) {
                DropZone.INTO_FOLDER -> {
                    // Semi-transparent fill + border on the target folder row
                    g2.color = color
                    g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f)
                    g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 4, 4)
                    g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f)
                    g2.stroke = BasicStroke(1.5f)
                    g2.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 4, 4)
                }
                DropZone.BEFORE_ROW, DropZone.AFTER_ROW -> {
                    // Horizontal insert line with dots at both ends:  ●────────────●
                    val lineY = if (dndDropZone == DropZone.BEFORE_ROW) bounds.y
                                else bounds.y + bounds.height
                    val x1 = bounds.x + 2
                    val x2 = bounds.x + bounds.width - 8
                    g2.color = color
                    g2.stroke = BasicStroke(2f)
                    g2.drawLine(x1 + 6, lineY, x2, lineY)  // line between the two dots
                    g2.fillOval(x1, lineY - 3, 6, 6)       // left dot
                    g2.fillOval(x2, lineY - 3, 6, 6)       // right dot
                }
            }
            g2.dispose()
        }

        /** Show the first few SQL lines as a tooltip when hovering a query node. */
        override fun getToolTipText(e: MouseEvent): String? {
            val path = getPathForLocation(e.x, e.y) ?: return null
            val obj  = (path.lastPathComponent as? DefaultMutableTreeNode)
                ?.userObject as? QueryNode ?: return null
            if (obj.isFolder || obj.sqlCode.isBlank()) return null
            val lines   = obj.sqlCode.trim().lines()
            val preview = lines.take(6)
                .joinToString("<br>") { it.replace("&", "&amp;").replace("<", "&lt;") }
            val more = if (lines.size > 6) "<br><i style='color:gray'>…${lines.size - 6} more lines</i>" else ""
            return "<html><code>$preview$more</code></html>"
        }
    }

    private val searchField = SearchTextField(false)

    private val storage get() = QueryStorage.getInstance(project)

    // ── Drag & drop state (mouse-listener based, bypasses Swing/IntelliJ DnD) ──
    private var dndSource: DefaultMutableTreeNode? = null
    private var dndStartPoint = Point()
    private var dndActive = false

    /** When true, the search filter also scans each query's SQL body. */
    private var searchInSql = false

    init {
        component.isOpaque = false
        buildToolbar()
        buildTree()
        buildSearchAndScrollPane()
        refresh()
    }

    // ── Build helpers ──────────────────────────────────────────────────────────

    private fun buildToolbar() {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("New Query", "Create an empty query", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) = createNode(isFolder = false)
            })
            add(object : AnAction("New Folder", "Create a folder", AllIcons.Nodes.Folder) {
                override fun actionPerformed(e: AnActionEvent) = createNode(isFolder = true)
            })
            addSeparator()
            add(object : AnAction("Delete", "Delete selected item  [Delete]", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.let(::deleteNode)
                }
            })
            addSeparator()
            add(object : AnAction(
                "Capture from Editor",
                "Save the active editor's SQL (or selection) to QueryBook  [Ctrl+Alt+Q]",
                AllIcons.Actions.Download
            ) {
                override fun actionPerformed(e: AnActionEvent) = captureFromEditor()
            })
            addSeparator()
            add(object : AnAction("Collapse All", "Collapse all folders", AllIcons.Actions.Collapseall) {
                override fun actionPerformed(e: AnActionEvent) = collapseAll()
            })
            add(object : AnAction("Export to JSON", "Export your query library to a JSON file", AllIcons.Actions.Upload) {
                override fun actionPerformed(e: AnActionEvent) {
                    QueryImportExportService.export(project, storage)
                }
            })
            add(object : AnAction("Import from JSON", "Import a previously exported query library", AllIcons.Actions.Install) {
                override fun actionPerformed(e: AnActionEvent) {
                    QueryImportExportService.import(project, storage) { refresh() }
                }
            })
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("QueryBookToolbar", group, true)
            .also { it.targetComponent = component }

        component.add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(toolbar.component, BorderLayout.WEST)
            },
            BorderLayout.NORTH
        )
    }

    private fun buildTree() {
        tree.apply {
            isRootVisible = false
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            setCellRenderer(QueryNodeCellRenderer())
        }

        tree.addTreeSelectionListener {
            val obj = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)
                ?.userObject as? QueryNode
            // Only propagate leaf (query) selections
            onNodeSelected(obj?.takeIf { !it.isFolder })
        }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y)
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (path != null) tree.selectionPath = path
                    showContextMenu(
                        e.component, e.x, e.y,
                        path?.lastPathComponent as? DefaultMutableTreeNode
                    )
                } else if (e.clickCount == 2 && path != null) {
                    (path.lastPathComponent as? DefaultMutableTreeNode)?.let(::renameNode)
                }
            }
        })

        // ── Keyboard shortcuts ──────────────────────────────────────────────
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                when (e.keyCode) {
                    KeyEvent.VK_DELETE,
                    KeyEvent.VK_BACK_SPACE -> deleteNode(node)
                    KeyEvent.VK_F2         -> renameNode(node)
                }
            }
        })

        setupMouseDnD()
    }

    private fun buildSearchAndScrollPane() {
        searchField.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter(searchField.text)
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter(searchField.text)
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = applyFilter(searchField.text)
        })

        // Toggle: also search inside SQL body
        val sqlToggle = JToggleButton("SQL").apply {
            toolTipText = "Also search inside SQL content"
            isFocusable = false
            addActionListener {
                searchInSql = isSelected
                if (searchField.text.isNotBlank()) applyFilter(searchField.text)
            }
        }

        val searchRow = JPanel(BorderLayout(4, 0)).apply {
            isOpaque = false
            add(searchField, BorderLayout.CENTER)
            add(sqlToggle, BorderLayout.EAST)
        }

        val centerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(searchRow, BorderLayout.NORTH)
            add(JBScrollPane(tree).apply { border = JBUI.Borders.emptyTop(4) }, BorderLayout.CENTER)
        }
        component.add(centerPanel, BorderLayout.CENTER)
    }

    // ── Tree cell renderer ─────────────────────────────────────────────────────

    private class QueryNodeCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean,
            expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            val obj = (value as? DefaultMutableTreeNode)?.userObject as? QueryNode ?: return
            append(obj.name)
            if (obj.isFolder) {
                val count = obj.children.size
                if (count > 0) append("  $count", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                icon = AllIcons.Nodes.Folder
            } else {
                icon = AllIcons.Nodes.DataTables
            }
        }
    }

    // ── CRUD operations ────────────────────────────────────────────────────────

    private fun createNode(isFolder: Boolean) {
        val label = if (isFolder) "Folder" else "Query"
        val name = Messages.showInputDialog(project, "Name:", "New $label", null)
            ?.takeIf { it.isNotBlank() } ?: return

        if (isNameTaken(name, storage.root)) {
            Messages.showErrorDialog(project, "An item named '$name' already exists.", "Duplicate Name")
            return
        }

        val target = selectedFolderOrRoot()
        target.children += QueryNode(
            name = name,
            isFolder = isFolder,
            sqlCode = if (isFolder) "" else "-- Write your SQL here"
        )
        refresh()
    }

    /**
     * Grabs SQL from the currently active editor (selected text or full document)
     * and opens the QueryBook save dialog so the user can name it and pick a folder.
     * This is the one-click alternative to right-click → Save to QueryBook.
     */
    private fun captureFromEditor() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            Messages.showInfoMessage(project, "No active editor found.\nOpen a SQL file or console first.", "QueryBook")
            return
        }

        val sql = if (editor.selectionModel.hasSelection())
            editor.selectionModel.selectedText ?: ""
        else
            editor.document.text

        if (sql.isBlank()) {
            Messages.showInfoMessage(project, "The active editor is empty.", "QueryBook")
            return
        }

        val dialog = SaveToQueryBookDialog(project, storage)
        if (!dialog.showAndGet()) return

        dialog.targetFolder.children.add(
            QueryNode(name = dialog.queryName, sqlCode = sql, description = "Captured from editor.")
        )
        project.messageBus.syncPublisher(QuerySavedListener.TOPIC).querySaved()
        refresh()
    }

    private fun deleteNode(node: DefaultMutableTreeNode) {
        val obj = node.userObject as? QueryNode ?: return
        val confirmed = Messages.showYesNoDialog(
            project, "Delete '${obj.name}'?", "Confirm Delete", Messages.getWarningIcon()
        )
        if (confirmed == Messages.YES) {
            removeFromModel(storage.root, obj)
            onNodeSelected(null)
            refresh()
        }
    }

    private fun renameNode(node: DefaultMutableTreeNode) {
        val obj = node.userObject as? QueryNode ?: return
        val newName = Messages.showInputDialog(project, "New name:", "Rename", null, obj.name, null)
            ?.takeIf { it.isNotBlank() && it != obj.name } ?: return

        if (isNameTaken(newName, storage.root)) {
            Messages.showErrorDialog(project, "An item named '$newName' already exists.", "Duplicate Name")
            return
        }
        obj.name = newName
        findUiNode(treeRoot, obj)?.let { treeModel.nodeChanged(it) }
    }

    private fun duplicateNode(node: DefaultMutableTreeNode) {
        val obj = node.userObject as? QueryNode ?: return
        if (obj.isFolder) return

        val parentUi   = node.parent as? DefaultMutableTreeNode ?: return
        val parentData = if (parentUi.isRoot) storage.root
                         else parentUi.userObject as? QueryNode ?: return

        // Generate a unique name: "Original", "Original (copy)", "Original (copy 2)", ...
        var copyName = "${obj.name} (copy)"
        var counter  = 2
        while (isNameTaken(copyName, storage.root)) {
            copyName = "${obj.name} (copy $counter)"
            counter++
        }

        val copy = QueryNode(name = copyName, sqlCode = obj.sqlCode, description = obj.description)
        val insertIdx = (parentData.children.indexOf(obj) + 1).coerceIn(0, parentData.children.size)
        parentData.children.add(insertIdx, copy)
        refresh()
    }

    // ── Context menu ───────────────────────────────────────────────────────────

    private fun showContextMenu(c: Component, x: Int, y: Int, node: DefaultMutableTreeNode?) {
        val obj = node?.userObject as? QueryNode
        JPopupMenu().apply {
            add(JMenuItem("New Query", AllIcons.General.Add).also {
                it.addActionListener { createNode(false) }
            })
            add(JMenuItem("New Folder", AllIcons.Nodes.Folder).also {
                it.addActionListener { createNode(true) }
            })
            if (node != null && obj != null) {
                addSeparator()
                add(JMenuItem("Rename", AllIcons.Actions.Edit).also {
                    it.addActionListener { renameNode(node) }
                })
                add(JMenuItem("Delete", AllIcons.Actions.GC).also {
                    it.addActionListener { deleteNode(node) }
                })
                // Copy SQL only available for query (non-folder) nodes
                if (!obj.isFolder) {
                    addSeparator()
                    add(JMenuItem("Duplicate", AllIcons.Actions.Copy).also {
                        it.addActionListener { duplicateNode(node) }
                    })
                    add(JMenuItem("Copy SQL", AllIcons.Actions.Copy).also {
                        it.addActionListener {
                            Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(StringSelection(obj.sqlCode), null)
                        }
                    })
                }
            }
        }.show(c, x, y)
    }

    // ── Search / filter ────────────────────────────────────────────────────────

    private fun applyFilter(query: String) {
        treeRoot.removeAllChildren()
        if (query.isBlank()) {
            populateTree(treeRoot, storage.root)
        } else {
            filterInto(treeRoot, storage.root, query.trim().lowercase())
        }
        treeModel.reload()
        expandAll()
    }

    /**
     * Recursively adds matching nodes to [uiParent].
     * Folders are included if they contain at least one match in their subtree.
     * When [searchInSql] is true, matches against the SQL body as well as the name.
     */
    private fun filterInto(uiParent: DefaultMutableTreeNode, dataNode: QueryNode, query: String) {
        for (child in dataNode.children) {
            val nameMatch = child.name.lowercase().contains(query)
            val sqlMatch  = searchInSql && !child.isFolder && child.sqlCode.lowercase().contains(query)
            when {
                nameMatch || sqlMatch -> {
                    val childUi = DefaultMutableTreeNode(child)
                    uiParent.add(childUi)
                    if (child.isFolder) populateTree(childUi, child)
                }
                child.isFolder -> {
                    val folderUi = DefaultMutableTreeNode(child)
                    filterInto(folderUi, child, query)
                    if (folderUi.childCount > 0) uiParent.add(folderUi)
                }
            }
        }
    }

    // ── Tree helpers ───────────────────────────────────────────────────────────

    /** Rebuild the full UI tree from the data model. */
    fun refresh() {
        treeRoot.removeAllChildren()
        populateTree(treeRoot, storage.root)
        treeModel.reload()
        expandAll()
    }

    private fun populateTree(uiNode: DefaultMutableTreeNode, dataNode: QueryNode) {
        for (child in dataNode.children) {
            val childUi = DefaultMutableTreeNode(child)
            uiNode.add(childUi)
            if (child.isFolder) populateTree(childUi, child)
        }
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) tree.expandRow(i++)
    }

    private fun collapseAll() {
        for (i in tree.rowCount - 1 downTo 0) tree.collapseRow(i)
    }

    /** Returns the selected folder node, or the storage root if nothing (or a query) is selected. */
    private fun selectedFolderOrRoot(): QueryNode {
        val sel = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.userObject as? QueryNode
        return if (sel?.isFolder == true) sel else storage.root
    }

    private fun isNameTaken(name: String, node: QueryNode): Boolean {
        if (node.name != "Root" && node.name.equals(name, ignoreCase = true)) return true
        return node.children.any { isNameTaken(name, it) }
    }

    private fun removeFromModel(parent: QueryNode, target: QueryNode): Boolean {
        if (parent.children.remove(target)) return true
        return parent.children.any { removeFromModel(it, target) }
    }

    private fun findUiNode(
        uiNode: DefaultMutableTreeNode,
        target: QueryNode
    ): DefaultMutableTreeNode? {
        if (uiNode.userObject === target) return uiNode
        for (i in 0 until uiNode.childCount) {
            val found = findUiNode(uiNode.getChildAt(i) as DefaultMutableTreeNode, target)
            if (found != null) return found
        }
        return null
    }

    // ── Drag & Drop ─────────────────────────────────────────────────────────────
    //
    // Custom mouse-based DnD that works reliably inside IntelliJ's plugin sandbox.
    // Swing's dragEnabled + TransferHandler mechanism can conflict with IntelliJ's
    // Tree class, causing createTransferable to receive a stale null selection.
    // By capturing the source node on mousePressed we bypass all of that.
    //
    // Supported gestures:
    //   • Drag onto a folder   → moves node to end of that folder
    //   • Drag onto a query    → inserts before/after it (top/bottom half of row)
    //   • Drag onto empty area → moves to end of root
    // ────────────────────────────────────────────────────────────────────────────

    private fun setupMouseDnD() {
        val threshold = 5   // pixels before we consider it a drag, not a click

        tree.addMouseListener(object : MouseAdapter() {

            override fun mousePressed(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) return
                // Capture source at press-time, before IntelliJ can change selection
                dndSource = tree.getPathForLocation(e.x, e.y)
                    ?.lastPathComponent as? DefaultMutableTreeNode
                dndStartPoint.setLocation(e.x, e.y)
                dndActive = false
            }

            override fun mouseReleased(e: MouseEvent) {
                val wasActive = dndActive
                val src = dndSource
                dndSource = null
                dndActive = false
                dndDropRow = -1          // clear visual indicator
                tree.cursor = Cursor.getDefaultCursor()
                tree.repaint()

                if (!wasActive || src == null || src.isRoot) return

                // Resolve drop target from final mouse position
                val (targetParent, insertIdx) = resolveDropTarget(e.x, e.y, src) ?: return
                val srcObj = src.userObject as? QueryNode ?: return

                if (srcObj === targetParent) return                     // can't drop into itself
                if (isAncestor(src, resolveUiNode(targetParent))) return // can't drop into descendant

                val oldIdx = targetParent.children.indexOf(srcObj)
                removeFromModel(storage.root, srcObj)

                var finalIdx = insertIdx
                if (oldIdx in 0 until finalIdx) finalIdx--              // adjust for the removed item
                finalIdx = finalIdx.coerceIn(0, targetParent.children.size)

                targetParent.children.add(finalIdx, srcObj)
                UIUtil.invokeLaterIfNeeded { refresh() }
            }
        })

        tree.addMouseMotionListener(object : MouseMotionAdapter() {

            override fun mouseDragged(e: MouseEvent) {
                dndSource ?: return   // nothing to drag — no-op
                if (!dndActive) {
                    if (abs(e.x - dndStartPoint.x) < threshold &&
                        abs(e.y - dndStartPoint.y) < threshold) return
                    dndActive = true
                }
                tree.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

                // Update the visual drop indicator
                val row = tree.getClosestRowForLocation(e.x, e.y)
                if (row < 0) { dndDropRow = -1; tree.repaint(); return }

                val bounds  = tree.getRowBounds(row)
                val topHalf = bounds != null && e.y < (bounds.y + bounds.height / 2)
                val dropObj = (tree.getPathForRow(row)?.lastPathComponent
                        as? DefaultMutableTreeNode)?.userObject as? QueryNode

                dndDropRow  = row
                dndDropZone = when {
                    dropObj?.isFolder == true && !topHalf -> DropZone.INTO_FOLDER
                    topHalf                               -> DropZone.BEFORE_ROW
                    else                                  -> DropZone.AFTER_ROW
                }
                tree.repaint()
            }
        })
    }

    /**
     * Given a mouse position and the dragged source node, returns the
     * (target parent DataNode, insertion index) pair, or null if there is
     * no sensible drop location.
     *
     * Folder row is split into two zones:
     *   • Top half  → insert BEFORE the folder in its own parent  (allows extraction)
     *   • Bottom half → insert INTO the folder at the end
     *
     * Query row is split into:
     *   • Top half  → insert before the query in its parent
     *   • Bottom half → insert after the query in its parent
     */
    private fun resolveDropTarget(
        x: Int, y: Int,
        src: DefaultMutableTreeNode
    ): Pair<QueryNode, Int>? {
        val closestRow = tree.getClosestRowForLocation(x, y)
        if (closestRow < 0) return Pair(storage.root, storage.root.children.size)

        val rowPath  = tree.getPathForRow(closestRow) ?: return null
        val dropNode = rowPath.lastPathComponent as? DefaultMutableTreeNode ?: return null
        if (dropNode == src) return null

        val dropObj   = dropNode.userObject as? QueryNode
        val rowBounds = tree.getRowBounds(closestRow)
        val isTopHalf = rowBounds != null && y < (rowBounds.y + rowBounds.height / 2)

        // Resolves to the direct parent of [dropNode] at a computed index.
        fun parentInsert(indexFn: (DefaultMutableTreeNode) -> Int): Pair<QueryNode, Int>? {
            val parentUi   = dropNode.parent as? DefaultMutableTreeNode
                ?: return Pair(storage.root, storage.root.children.size)
            val parentData = if (parentUi.isRoot) storage.root
                             else parentUi.userObject as? QueryNode ?: return null
            return Pair(parentData, indexFn(parentUi))
        }

        return when {
            dropNode.isRoot ->
                Pair(storage.root, storage.root.children.size)

            dropObj?.isFolder == true ->
                if (isTopHalf)
                    // Top half: place BEFORE this folder inside its own parent.
                    // This is how you drag an item OUT of a folder and back to root (or a parent folder).
                    parentInsert { it.getIndex(dropNode) }
                else
                    // Bottom half: drop INTO this folder, appended at the end.
                    Pair(dropObj, dropObj.children.size)

            else ->
                // Query row: insert before or after based on Y midpoint
                parentInsert { parentUi ->
                    val idx = parentUi.getIndex(dropNode)
                    if (isTopHalf) idx else idx + 1
                }
        }
    }

    // ── DnD helpers ───────────────────────────────────────────────────────────

    private fun isAncestor(ancestor: DefaultMutableTreeNode, node: DefaultMutableTreeNode?): Boolean {
        var current = node?.parent
        while (current != null) {
            if (current === ancestor) return true
            current = (current as? DefaultMutableTreeNode)?.parent
        }
        return false
    }

    /** Find the UI node that wraps [dataNode] (used only for ancestor checks). */
    private fun resolveUiNode(dataNode: QueryNode): DefaultMutableTreeNode? =
        findUiNode(treeRoot, dataNode)
}

