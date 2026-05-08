package com.demo.sync

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Runs once after each project is fully loaded.
 * Starts the sync service if the user has sync enabled in Settings → Tools → SQLFolio Sync.
 */
class SQLFolioStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        SQLFolioSyncService.getInstance(project).connect()
    }
}

