package com.example.mylibrary.backup

import com.example.mylibrary.backup.model.BackupPreferences
import com.example.mylibrary.backup.model.BackupResult
import com.example.mylibrary.backup.model.BackupWarning
import com.example.mylibrary.backup.model.ImportStage
import com.example.mylibrary.backup.model.RecoveryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportExecutionTest {
    @Test
    fun successCommitsPreferencesAndDatabaseBeforeOldCoverCleanup() = runTest {
        val events = mutableListOf<String>()

        val result = executePreparedImport(
            replacementPreferences = newPreferences,
            snapshotPreferences = {
                events += "snapshot"
                oldPreferences
            },
            importCovers = { imported ->
                events += "stage-cover"
                imported("new-cover")
            },
            replacePreferences = { events += "preferences:${it.gridColumns}" },
            replaceDatabase = { events += "database:${it.single()}" },
            cleanupOldCovers = {
                events += "old-cover-cleanup"
                true
            },
            deleteImportedCover = { events += "rollback-cover:$it" },
            restoreThemes = { emptyList() },
            cleanupStaging = {
                events += "staging-cleanup"
                true
            },
            onRecoveryFailure = { _, _ -> events += "recovery-warning" }
        )

        assertTrue(result is BackupResult.Success)
        assertEquals(
            listOf(
                "snapshot",
                "stage-cover",
                "preferences:3",
                "database:new-cover",
                "old-cover-cleanup",
                "staging-cleanup"
            ),
            events
        )
    }

    @Test
    fun databaseFailureRestoresPreferencesAndDeletesStagedCovers() = runTest {
        val writtenPreferences = mutableListOf<BackupPreferences>()
        val deletedCovers = mutableListOf<String>()

        val result = execute(
            replacePreferences = { writtenPreferences += it },
            replaceDatabase = { error("database") },
            deleteImportedCover = { deletedCovers += it }
        )

        val failure = result as BackupResult.Failure
        assertEquals(ImportStage.REPLACE_DATABASE, failure.importStage)
        assertEquals(
            listOf(newPreferences, oldPreferences),
            writtenPreferences
        )
        assertEquals(listOf("new-cover"), deletedCovers)
        assertEquals(RecoveryState.PRESERVED, failure.recovery?.database)
        assertEquals(RecoveryState.RESTORED, failure.recovery?.preferences)
        assertEquals(RecoveryState.RESTORED, failure.recovery?.covers)
        assertTrue(failure.recovery?.fullyRecovered == true)
    }

    @Test
    fun dataStoreWriteFailureAttemptsPreferenceAndCoverRecoveryBeforeDatabase() =
        runTest {
            var writes = 0
            var databaseCalled = false
            val result = execute(
                replacePreferences = {
                    writes += 1
                    if (writes == 1) error("partial datastore write")
                },
                replaceDatabase = { databaseCalled = true }
            )

            val failure = result as BackupResult.Failure
            assertEquals(ImportStage.WRITE_PREFERENCES, failure.importStage)
            assertEquals(2, writes)
            assertFalse(databaseCalled)
            assertEquals(RecoveryState.RESTORED, failure.recovery?.preferences)
            assertEquals(RecoveryState.RESTORED, failure.recovery?.covers)
        }

    @Test
    fun partialRecoveryRequiresRecentBackupAndKeepsPrimaryFailure() = runTest {
        var preferenceWrites = 0
        val result = execute(
            replacePreferences = {
                preferenceWrites += 1
                if (preferenceWrites == 2) error("restore preferences")
            },
            replaceDatabase = { error("database") },
            deleteImportedCover = { error("delete staged cover") }
        )

        val failure = result as BackupResult.Failure
        assertEquals("database", failure.cause?.message)
        assertEquals(RecoveryState.FAILED, failure.recovery?.preferences)
        assertEquals(RecoveryState.FAILED, failure.recovery?.covers)
        assertTrue(failure.recovery?.requiresRecentBackup == true)
        assertFalse(failure.recovery?.fullyRecovered ?: true)
    }

    @Test
    fun stagingFailureNeverWritesPreferencesOrDatabase() = runTest {
        var preferencesWritten = false
        var databaseWritten = false
        val result = executePreparedImport<String>(
            replacementPreferences = newPreferences,
            snapshotPreferences = { oldPreferences },
            importCovers = { imported ->
                imported("partial-cover")
                error("invalid staged cover")
            },
            replacePreferences = { preferencesWritten = true },
            replaceDatabase = { databaseWritten = true },
            cleanupOldCovers = { true },
            deleteImportedCover = {},
            restoreThemes = { emptyList() },
            cleanupStaging = { true },
            onRecoveryFailure = { _, _ -> }
        )

        assertEquals(
            ImportStage.STAGE_COVERS,
            (result as BackupResult.Failure).importStage
        )
        assertFalse(preferencesWritten)
        assertFalse(databaseWritten)
    }

    @Test
    fun stagingCleanupFailureDoesNotReplaceSuccessfulImportResult() = runTest {
        val result = execute(cleanupStaging = { false })

        val success = result as BackupResult.Success
        assertTrue(BackupWarning.StagingCleanupFailed in success.warnings)
    }

    @Test
    fun cancellationPropagatesFromEveryPreCommitStageAndStopsLaterWrites() = runTest {
        val stages = listOf(
            ImportStage.READ_CURRENT_STATE,
            ImportStage.STAGE_COVERS,
            ImportStage.WRITE_PREFERENCES,
            ImportStage.REPLACE_DATABASE
        )
        stages.forEach { cancelledStage ->
            val events = mutableListOf<String>()
            try {
                executePreparedImport<String>(
                    replacementPreferences = newPreferences,
                    snapshotPreferences = {
                        events += "snapshot"
                        if (cancelledStage == ImportStage.READ_CURRENT_STATE) cancel()
                        oldPreferences
                    },
                    importCovers = { imported ->
                        events += "covers"
                        imported("new-cover")
                        if (cancelledStage == ImportStage.STAGE_COVERS) cancel()
                    },
                    replacePreferences = {
                        events += if (it == oldPreferences) "restore-prefs" else "prefs"
                        if (cancelledStage == ImportStage.WRITE_PREFERENCES &&
                            it == newPreferences
                        ) {
                            cancel()
                        }
                    },
                    replaceDatabase = {
                        events += "database"
                        if (cancelledStage == ImportStage.REPLACE_DATABASE) cancel()
                    },
                    cleanupOldCovers = {
                        events += "old-cleanup"
                        true
                    },
                    deleteImportedCover = { events += "rollback-cover" },
                    restoreThemes = { emptyList() },
                    cleanupStaging = {
                        events += "staging-cleanup"
                        true
                    },
                    onRecoveryFailure = { _, _ -> }
                )
                error("Expected cancellation")
            } catch (_: CancellationException) {
                assertTrue("staging-cleanup" in events)
                assertFalse("old-cleanup" in events)
            }
        }
    }

    @Test
    fun cancellationAfterDatabaseCommitDoesNotRollBackCommittedState() = runTest {
        val events = mutableListOf<String>()
        try {
            executePreparedImport<String>(
                replacementPreferences = newPreferences,
                snapshotPreferences = { oldPreferences },
                importCovers = { it("new-cover") },
                replacePreferences = { prefs ->
                    events += if (prefs == oldPreferences) "restore-prefs" else "prefs"
                },
                replaceDatabase = { events += "database" },
                cleanupOldCovers = { cancel() },
                deleteImportedCover = { events += "rollback-cover" },
                restoreThemes = { emptyList() },
                cleanupStaging = {
                    events += "staging-cleanup"
                    true
                },
                onRecoveryFailure = { _, _ -> }
            )
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(
                listOf("prefs", "database", "staging-cleanup"),
                events
            )
        }
    }

    @Test
    fun themeWarningsAreIncludedInSuccessResult() = runTest {
        val result = execute(
            restoreThemes = {
                listOf(
                    BackupWarning.SkippedThemes(2),
                    BackupWarning.CurrentThemeUnavailable
                )
            }
        )

        val success = result as BackupResult.Success
        assertTrue(BackupWarning.SkippedThemes(2) in success.warnings)
        assertTrue(BackupWarning.CurrentThemeUnavailable in success.warnings)
    }

    @Test
    fun themeRestorationRunsAfterDatabaseCommit() = runTest {
        val events = mutableListOf<String>()
        execute(
            replaceDatabase = { events += "database" },
            restoreThemes = {
                events += "themes"
                emptyList()
            }
        )
        assertEquals(listOf("database", "themes"), events)
    }

    @Test
    fun themeRestorationFailureDoesNotBlockDataRestore() = runTest {
        val result = execute(
            restoreThemes = { error("theme installation crashed") }
        )

        assertTrue(result is BackupResult.Success)
        val success = result as BackupResult.Success
        assertTrue(BackupWarning.ThemeRestoreFailed in success.warnings)
        // Result must not look like a complete success
        assertTrue(success.warnings.isNotEmpty())
    }

    @Test
    fun themeRestorationExceptionPreservesCommittedBusinessData() = runTest {
        val events = mutableListOf<String>()
        val result = execute(
            replaceDatabase = { events += "database" },
            restoreThemes = {
                events += "themes"
                error("theme installation crashed")
            }
        )

        assertTrue(result is BackupResult.Success)
        val success = result as BackupResult.Success
        assertTrue(BackupWarning.ThemeRestoreFailed in success.warnings)
        // Business data was committed before theme restoration was attempted
        assertEquals(listOf("database", "themes"), events)
    }

    @Test
    fun cancellationDuringThemeRestorationStillCleansUpStaging() = runTest {
        val events = mutableListOf<String>()
        try {
            execute(
                restoreThemes = {
                    events += "themes"
                    cancel()
                },
                cleanupStaging = {
                    events += "staging-cleanup"
                    true
                }
            )
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue("staging-cleanup" in events)
        }
    }

    @Test
    fun cancellationDuringThemeRestorationDoesNotProduceThemeRestoreFailed() =
        runTest {
            var result: BackupResult? = null
            try {
                result = execute(
                    restoreThemes = { cancel() }
                )
            } catch (_: CancellationException) {
                // Expected: cancellation propagates
            }

            // Result must not be Success with ThemeRestoreFailed
            assertTrue(result !is BackupResult.Success)
            if (result is BackupResult.Success) {
                assertTrue(
                    BackupWarning.ThemeRestoreFailed !in result.warnings
                )
            }
        }

    @Test
    fun ordinaryExceptionDuringThemeRestorationStillProducesThemeRestoreFailed() =
        runTest {
            val result = execute(
                restoreThemes = { throw IllegalStateException("boom") }
            )

            assertTrue(result is BackupResult.Success)
            assertTrue(
                BackupWarning.ThemeRestoreFailed in
                    (result as BackupResult.Success).warnings
            )
        }

    private suspend fun execute(
        replacePreferences: suspend (BackupPreferences) -> Unit = {},
        replaceDatabase: suspend (List<String>) -> Unit = {},
        deleteImportedCover: suspend (String) -> Unit = {},
        cleanupStaging: () -> Boolean = { true },
        restoreThemes: suspend () -> List<BackupWarning> = { emptyList() }
    ): BackupResult = executePreparedImport(
        replacementPreferences = newPreferences,
        snapshotPreferences = { oldPreferences },
        importCovers = { it("new-cover") },
        replacePreferences = replacePreferences,
        replaceDatabase = replaceDatabase,
        cleanupOldCovers = { true },
        deleteImportedCover = deleteImportedCover,
        restoreThemes = restoreThemes,
        cleanupStaging = cleanupStaging,
        onRecoveryFailure = { _, _ -> }
    )

    private fun cancel(): Nothing = throw CancellationException("cancel")

    private companion object {
        val oldPreferences = BackupPreferences(gridColumns = 4)
        val newPreferences = BackupPreferences(gridColumns = 3)
    }
}
