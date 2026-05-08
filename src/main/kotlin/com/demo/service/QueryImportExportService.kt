package com.demo.service

import com.demo.model.QueryNode
import com.demo.model.QueryStorage
import com.google.gson.GsonBuilder
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Handles JSON export and import of the entire SQLFolio tree.
 *
 * Export format (version 1):
 * {
 *   "version": 1,
 *   "exportedAt": "2026-04-10T12:00:00",
 *   "root": { ...QueryNode tree... }
 * }
 */
object QueryImportExportService {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val timestampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private data class ExportEnvelope(
        val version: Int = 1,
        val exportedAt: String = LocalDateTime.now().format(isoFmt),
        val root: QueryNode
    )

    // ── Export ─────────────────────────────────────────────────────────────────

    fun export(project: Project, storage: QueryStorage) {
        val descriptor = FileSaverDescriptor(
            "Export SQLFolio",
            "Save your entire query library as a portable JSON file",
            "json"
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val defaultName = "sqlfolio_${LocalDateTime.now().format(timestampFmt)}"
        val fileWrapper = dialog.save(null as com.intellij.openapi.vfs.VirtualFile?, defaultName) ?: return

        try {
            val json = gson.toJson(ExportEnvelope(root = storage.root))
            fileWrapper.file.also { it.parentFile?.mkdirs() }.writeText(json, Charsets.UTF_8)

            val count = countQueries(storage.root)
            Messages.showInfoMessage(
                project,
                "Successfully exported $count ${if (count == 1) "query" else "queries"} to:\n${fileWrapper.file.path}",
                "Export Complete"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Export failed:\n${e.message}", "Export Error")
        }
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    fun import(project: Project, storage: QueryStorage, onComplete: () -> Unit) {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json").apply {
            title = "Import SQLFolio"
            description = "Select a SQLFolio JSON export file"
        }

        FileChooser.chooseFile(descriptor, project, null) { virtualFile ->
            try {
                val json = virtualFile.contentsToByteArray().toString(Charsets.UTF_8)
                val envelope = gson.fromJson(json, ExportEnvelope::class.java)
                    ?: throw IllegalArgumentException("Unrecognised file format.")

                val importedCount = countQueries(envelope.root)

                val choice = Messages.showYesNoCancelDialog(
                    project,
                    "Found $importedCount ${if (importedCount == 1) "query" else "queries"} " +
                            "(exported ${envelope.exportedAt}).\n\n" +
                            "How do you want to import?\n\n" +
                            "  Merge   — add to your existing library\n" +
                            "  Replace — overwrite everything with the imported data",
                    "Import SQLFolio",
                    "Merge",
                    "Replace",
                    "Cancel",
                    Messages.getQuestionIcon()
                )

                when (choice) {
                    Messages.YES -> {
                        // Merge: append top-level imported items
                        storage.root.children.addAll(envelope.root.children)
                    }
                    Messages.NO -> {
                        // Replace: clear then populate
                        storage.root.children.clear()
                        storage.root.children.addAll(envelope.root.children)
                    }
                    else -> return@chooseFile
                }

                onComplete()
                Messages.showInfoMessage(
                    project,
                    "Successfully imported $importedCount ${if (importedCount == 1) "query" else "queries"}.",
                    "Import Complete"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Import failed:\n${e.message}", "Import Error")
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Recursively count non-folder nodes. */
    fun countQueries(node: QueryNode): Int =
        (if (!node.isFolder) 1 else 0) + node.children.sumOf { countQueries(it) }
}

