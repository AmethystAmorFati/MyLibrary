package com.example.mylibrary.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.mylibrary.backup.model.BackupArchiveLimits
import com.example.mylibrary.backup.model.BackupFailureReason
import com.example.mylibrary.backup.model.BackupPreparationResult
import com.example.mylibrary.backup.model.BackupResult
import com.example.mylibrary.backup.model.ImportPreview
import com.example.mylibrary.backup.validation.BackupArchiveValidator
import com.example.mylibrary.backup.validation.NewerBackupVersionException
import com.example.mylibrary.backup.validation.ValidatedBackup
import com.example.mylibrary.data.repository.UserPreferencesRepository
import com.example.mylibrary.domain.repository.CoverImageRepository
import com.example.mylibrary.ui.components.clearCoverImageMemoryCache
import com.example.mylibrary.util.runBestEffortCleanup
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DataImportService(
    context: Context,
    private val databaseStore: BackupDatabaseStore,
    private val preferencesRepository: UserPreferencesRepository,
    private val coverImageRepository: CoverImageRepository,
    private val archiveValidator: BackupArchiveValidator = BackupArchiveValidator()
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var prepared: PreparedBackup? = null

    suspend fun prepare(uri: Uri): BackupPreparationResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            discardPreparedLocked()
            val workDirectory = File(
                appContext.cacheDir,
                "backup-import-${UUID.randomUUID()}"
            )
            val archive = File(workDirectory, "selected.zip")
            try {
                workDirectory.mkdirs()
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: error("Unable to open selected backup")
                input.buffered().use { source ->
                    FileOutputStream(archive).buffered().use { output ->
                        copyWithLimit(
                            source = source,
                            output = output,
                            limit = BackupArchiveLimits.MAX_ARCHIVE_BYTES
                        )
                    }
                }
                val validated = archiveValidator.validate(
                    archive = archive,
                    extractionDirectory = File(workDirectory, "extracted")
                )
                prepared = PreparedBackup(workDirectory, validated)
                BackupPreparationResult.Ready(
                    ImportPreview(
                        createdAt = validated.manifest.createdAt,
                        itemCount = validated.data.items.size.toLong(),
                        quoteCount = validated.data.quotes.size.toLong()
                    )
                )
            } catch (cancelled: CancellationException) {
                cleanupWorkDirectory(workDirectory)
                throw cancelled
            } catch (newer: NewerBackupVersionException) {
                cleanupWorkDirectory(workDirectory)
                BackupPreparationResult.Failure(
                    BackupFailureReason.UNSUPPORTED_NEWER_VERSION,
                    newer
                )
            } catch (invalid: ZipException) {
                cleanupWorkDirectory(workDirectory)
                BackupPreparationResult.Failure(BackupFailureReason.INVALID_ARCHIVE, invalid)
            } catch (invalid: IllegalArgumentException) {
                cleanupWorkDirectory(workDirectory)
                BackupPreparationResult.Failure(BackupFailureReason.INVALID_ARCHIVE, invalid)
            } catch (error: Throwable) {
                cleanupWorkDirectory(workDirectory)
                BackupPreparationResult.Failure(BackupFailureReason.IO_ERROR, error)
            }
        }
    }

    suspend fun importPrepared(): BackupResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = prepared
                ?: return@withLock BackupResult.Failure(BackupFailureReason.INVALID_ARCHIVE)
            var oldCovers: List<Pair<String?, String?>> = emptyList()
            val result = executePreparedImport(
                replacementPreferences =
                    current.validated.preferences
                        ?: com.example.mylibrary.backup.model.BackupPreferences(),
                snapshotPreferences = {
                    oldCovers = databaseStore.readStoredCoverPaths()
                    preferencesRepository.snapshotForBackup()
                },
                importCovers = { onImported ->
                    current.validated.coverFiles.toSortedMap()
                        .forEach { (reference, source) ->
                            coroutineContext.ensureActive()
                            val imported = coverImageRepository.importOriginal(source)
                            onImported(reference to imported)
                        }
                },
                replacePreferences = preferencesRepository::replaceFromBackup,
                replaceDatabase = { covers ->
                    databaseStore.replace(
                        current.validated.data,
                        covers.toMap()
                    )
                },
                cleanupOldCovers = {
                    var allCleaned = true
                    oldCovers.forEach { (originalPath, thumbnailPath) ->
                        coroutineContext.ensureActive()
                        val cleaned = runBestEffortCleanup(
                            cleanup = {
                                coverImageRepository.delete(
                                    originalPath,
                                    thumbnailPath
                                )
                            },
                            onFailure = { error ->
                                Log.w(
                                    LOG_TAG,
                                    "Import committed, but an old cover could not be deleted.",
                                    error
                                )
                            }
                        )
                        allCleaned = allCleaned && cleaned
                    }
                    clearCoverImageMemoryCache()
                    allCleaned
                },
                deleteImportedCover = { (_, cover) ->
                    coverImageRepository.delete(
                        cover.originalPath,
                        cover.thumbnailPath
                    )
                },
                cleanupStaging = {
                    try {
                        cleanupWorkDirectory(current.workDirectory)
                    } finally {
                        if (prepared === current) prepared = null
                    }
                },
                onRecoveryFailure = { stage, error ->
                    Log.w(
                        LOG_TAG,
                        "Backup import recovery or cleanup failed at $stage.",
                        error
                    )
                }
            )
            if (result is BackupResult.Failure) {
                Log.e(
                    LOG_TAG,
                    "Backup import failed at ${result.importStage}; " +
                        "recovery=${result.recovery}.",
                    result.cause
                )
            }
            result
        }
    }

    suspend fun discardPrepared() = withContext(Dispatchers.IO) {
        mutex.withLock { discardPreparedLocked() }
    }

    private fun discardPreparedLocked() {
        prepared?.workDirectory?.let(::cleanupWorkDirectory)
        prepared = null
    }

    private fun cleanupWorkDirectory(directory: File): Boolean {
        val cleaned = runCatching { directory.deleteRecursively() }
            .onFailure { error ->
                Log.w(LOG_TAG, "Backup staging directory cleanup failed.", error)
            }
            .getOrDefault(false)
        if (!cleaned && directory.exists()) {
            Log.w(LOG_TAG, "Backup staging directory remains: ${directory.name}")
        }
        return cleaned
    }

    private suspend fun copyWithLimit(
        source: InputStream,
        output: java.io.OutputStream,
        limit: Long
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive()
            val count = source.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= limit) { "Backup archive is too large" }
            output.write(buffer, 0, count)
        }
    }

    private data class PreparedBackup(
        val workDirectory: File,
        val validated: ValidatedBackup
    )

    private companion object {
        const val LOG_TAG = "MyLibraryBackup"
    }
}
