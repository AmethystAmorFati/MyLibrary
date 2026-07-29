package com.example.mylibrary.backup

import android.net.Uri
import com.example.mylibrary.backup.model.BackupPreparationResult
import com.example.mylibrary.backup.model.BackupResult

class BackupRepository(
    private val exportService: DataExportService,
    private val importService: DataImportService
) {
    suspend fun export(uri: Uri): BackupResult = exportService.exportTo(uri)

    suspend fun prepareImport(uri: Uri): BackupPreparationResult =
        importService.prepare(uri)

    suspend fun importPrepared(): BackupResult = importService.importPrepared()

    suspend fun discardPreparedImport() = importService.discardPrepared()
}
