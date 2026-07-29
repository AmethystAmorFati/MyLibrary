package com.example.mylibrary.backup

import com.example.mylibrary.backup.model.BackupFailureReason
import com.example.mylibrary.backup.model.BackupPreferences
import com.example.mylibrary.backup.model.BackupResult
import com.example.mylibrary.backup.model.BackupWarning
import com.example.mylibrary.backup.model.ImportRecoveryReport
import com.example.mylibrary.backup.model.ImportStage
import com.example.mylibrary.backup.model.RecoveryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Coordinates the deliberately non-atomic import across Room, DataStore and
 * cover files. Validation and archive extraction happen before this function.
 */
internal suspend fun <Cover> executePreparedImport(
    replacementPreferences: BackupPreferences,
    snapshotPreferences: suspend () -> BackupPreferences,
    importCovers: suspend (onImported: (Cover) -> Unit) -> Unit,
    replacePreferences: suspend (BackupPreferences) -> Unit,
    replaceDatabase: suspend (List<Cover>) -> Unit,
    cleanupOldCovers: suspend () -> Boolean,
    deleteImportedCover: suspend (Cover) -> Unit,
    cleanupStaging: () -> Boolean,
    onRecoveryFailure: (ImportStage, Throwable) -> Unit
): BackupResult {
    val importedCovers = mutableListOf<Cover>()
    var previousPreferences: BackupPreferences? = null
    var preferencesChanged = false
    var databaseCommitted = false
    var stage = ImportStage.READ_CURRENT_STATE

    try {
        previousPreferences = snapshotPreferences()

        stage = ImportStage.STAGE_COVERS
        importCovers { imported -> importedCovers += imported }

        stage = ImportStage.WRITE_PREFERENCES
        // Mark before the call because DataStore may have applied part of an
        // edit before an I/O failure is surfaced.
        preferencesChanged = true
        replacePreferences(replacementPreferences)

        stage = ImportStage.REPLACE_DATABASE
        replaceDatabase(importedCovers.toList())
        databaseCommitted = true

        stage = ImportStage.CLEANUP_OLD_COVERS
        val oldCoversCleaned = cleanupOldCoversSafely(
            cleanupOldCovers = cleanupOldCovers,
            onFailure = onRecoveryFailure
        )
        val stagingCleaned = cleanupStagingSafely(
            cleanupStaging = cleanupStaging,
            onFailure = onRecoveryFailure
        )
        return BackupResult.Success(
            warnings = buildList {
                if (!oldCoversCleaned) add(BackupWarning.OldCoverCleanupFailed)
                if (!stagingCleaned) add(BackupWarning.StagingCleanupFailed)
            }
        )
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable) {
            try {
                if (!databaseCommitted) {
                    recoverBeforeDatabaseCommit(
                        previousPreferences = previousPreferences,
                        preferencesChanged = preferencesChanged,
                        importedCovers = importedCovers,
                        replacePreferences = replacePreferences,
                        deleteImportedCover = deleteImportedCover,
                        onRecoveryFailure = onRecoveryFailure
                    )
                }
            } finally {
                cleanupStagingSafely(cleanupStaging, onRecoveryFailure)
            }
        }
        throw cancelled
    } catch (error: Throwable) {
        // No ordinary failure is expected after the Room transaction commits:
        // old-cover and staging cleanup are converted to warnings. If a future
        // post-commit step is added, never pretend the committed database was
        // rolled back.
        if (databaseCommitted) {
            cleanupStagingSafely(cleanupStaging, onRecoveryFailure)
            return BackupResult.Success(
                warnings = listOf(BackupWarning.StagingCleanupFailed)
            )
        }

        val recovery = withContext(NonCancellable) {
            val partial = recoverBeforeDatabaseCommit(
                previousPreferences = previousPreferences,
                preferencesChanged = preferencesChanged,
                importedCovers = importedCovers,
                replacePreferences = replacePreferences,
                deleteImportedCover = deleteImportedCover,
                onRecoveryFailure = onRecoveryFailure
            )
            partial.copy(
                staging = if (cleanupStagingSafely(
                        cleanupStaging,
                        onRecoveryFailure
                    )
                ) {
                    RecoveryState.RESTORED
                } else {
                    RecoveryState.FAILED
                }
            ).withBackupRequirement()
        }
        return BackupResult.Failure(
            reason = when (stage) {
                ImportStage.REPLACE_DATABASE -> BackupFailureReason.DATABASE_ERROR
                else -> BackupFailureReason.IO_ERROR
            },
            cause = error,
            importStage = stage,
            recovery = recovery
        )
    }
}

private suspend fun <Cover> recoverBeforeDatabaseCommit(
    previousPreferences: BackupPreferences?,
    preferencesChanged: Boolean,
    importedCovers: List<Cover>,
    replacePreferences: suspend (BackupPreferences) -> Unit,
    deleteImportedCover: suspend (Cover) -> Unit,
    onRecoveryFailure: (ImportStage, Throwable) -> Unit
): ImportRecoveryReport {
    val preferenceRecovery = if (!preferencesChanged || previousPreferences == null) {
        RecoveryState.NOT_REQUIRED
    } else {
        try {
            replacePreferences(previousPreferences)
            RecoveryState.RESTORED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            onRecoveryFailure(ImportStage.WRITE_PREFERENCES, error)
            RecoveryState.FAILED
        }
    }

    var coverRecovery = if (importedCovers.isEmpty()) {
        RecoveryState.NOT_REQUIRED
    } else {
        RecoveryState.RESTORED
    }
    importedCovers.asReversed().forEach { cover ->
        try {
            deleteImportedCover(cover)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            coverRecovery = RecoveryState.FAILED
            onRecoveryFailure(ImportStage.STAGE_COVERS, error)
        }
    }

    return ImportRecoveryReport(
        database = RecoveryState.PRESERVED,
        preferences = preferenceRecovery,
        covers = coverRecovery,
        staging = RecoveryState.NOT_REQUIRED,
        requiresRecentBackup = false
    ).withBackupRequirement()
}

private fun cleanupStagingSafely(
    cleanupStaging: () -> Boolean,
    onFailure: (ImportStage, Throwable) -> Unit
): Boolean = try {
    cleanupStaging()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    onFailure(ImportStage.CLEANUP_STAGING, error)
    false
}

private suspend fun cleanupOldCoversSafely(
    cleanupOldCovers: suspend () -> Boolean,
    onFailure: (ImportStage, Throwable) -> Unit
): Boolean = try {
    cleanupOldCovers()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    onFailure(ImportStage.CLEANUP_OLD_COVERS, error)
    false
}

private fun ImportRecoveryReport.withBackupRequirement(): ImportRecoveryReport =
    copy(
        requiresRecentBackup = listOf(
            database,
            preferences,
            covers,
            staging
        ).any { it == RecoveryState.FAILED }
    )
