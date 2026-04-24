package com.demo.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.util.IconUtil
import javax.swing.Icon

object MyIcons {
    private fun loadAndScale(path: String, fallback: Icon): Icon {
        return try {
            val icon = IconLoader.findIcon(path, MyIcons::class.java)
            if (icon != null && icon.iconWidth > 1) IconUtil.toSize(icon, 16, 16) else fallback
        } catch (e: Exception) {
            fallback
        }
    }

    @JvmField val DatabaseColored = loadAndScale("/icons/db_colored.svg", AllIcons.Nodes.DataTables)
    @JvmField val SaveColored = loadAndScale("/icons/save_colored.svg", AllIcons.Actions.Refresh)

    @JvmField val TrashColored = AllIcons.Actions.GC
}