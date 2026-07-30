package com.example.mylibrary.backup

import android.content.Context
import android.net.Uri
import com.example.mylibrary.BuildConfig
import com.example.mylibrary.backup.model.BACKUP_FORMAT
import com.example.mylibrary.backup.model.BACKUP_ROOT
import com.example.mylibrary.backup.model.BackupArchiveLimits
import com.example.mylibrary.backup.model.BackupFileInfo
import com.example.mylibrary.backup.model.BackupManifest
import com.example.mylibrary.backup.model.BackupResult
import com.example.mylibrary.backup.model.BackupWarning
import com.example.mylibrary.backup.model.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.mylibrary.backup.serialization.BackupJsonCodec
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.image.CoverInputValidator
import com.example.mylibrary.data.repository.UserPreferencesRepository
import com.example.mylibrary.domain.repository.CoverImageRepository
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DataExportService(
    context: Context,
    private val databaseStore: BackupDatabaseStore,
    private val preferencesRepository: UserPreferencesRepository,
    private val coverImageRepository: CoverImageRepository,
    private val themeTransfer: BackupThemeTransfer? = null,
    private val codec: BackupJsonCodec = BackupJsonCodec()
) {
    private val appContext = context.applicationContext

    suspend fun exportTo(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val tempDirectory = File(
            appContext.cacheDir,
            "backup-export-${UUID.randomUUID()}"
        )
        try {
            tempDirectory.mkdirs()
            val snapshot = databaseStore.readSnapshot()
            val localCoverPaths = snapshot.items
                .mapNotNull { it.coverRef?.takeIf(String::isNotBlank) }
                .distinct()
            val coverFiles = linkedMapOf<String, ExportCover>()
            var missingCoverCount = 0
            localCoverPaths.forEach { localPath ->
                coroutineContext.ensureActive()
                val source = coverImageRepository.resolveOriginal(localPath)
                val extension = source?.let { file ->
                    runCatching {
                        CoverInputValidator.validate(
                            file = file,
                            declaredExtension = file.extension
                        ).format
                    }.getOrNull()
                }
                if (source == null || extension == null) {
                    missingCoverCount += 1
                } else {
                    val archivePath = "covers/original/cover_" +
                        (coverFiles.size + 1).toString().padStart(6, '0') +
                        ".$extension"
                    coverFiles[localPath] = ExportCover(archivePath, source)
                }
            }

            val exportData = snapshot.copy(
                items = snapshot.items.map { item ->
                    item.copy(coverRef = item.coverRef?.let { coverFiles[it]?.archivePath })
                }
            )
            val dataFile = File(tempDirectory, "data.json")
            dataFile.writeText(codec.encodeData(exportData), Charsets.UTF_8)
            require(dataFile.length() <= BackupArchiveLimits.MAX_DATA_JSON_BYTES) {
                "Backup data is too large"
            }

            val themeCollection = themeTransfer?.collectThemesForExport()
            val preferencesSnapshot = preferencesRepository
                .snapshotForBackup()
                .copy(currentThemeId = themeCollection?.currentThemeId)
            val preferencesFile = File(tempDirectory, "preferences.json")
            preferencesFile.writeText(
                codec.encodePreferences(preferencesSnapshot),
                Charsets.UTF_8
            )

            val themeFileEntries = themeCollection
                ?.themes
                ?.flatMap { theme ->
                    theme.files.map { (relativePath, file) ->
                        val archivePath = "themes/${theme.themeId}/$relativePath"
                        ThemeArchiveFile(archivePath, file)
                    }
                }
                .orEmpty()

            val fileInfo = buildList {
                add(dataFile.fileInfo("data.json"))
                add(preferencesFile.fileInfo("preferences.json"))
                coverFiles.values.forEach { cover ->
                    coroutineContext.ensureActive()
                    add(cover.file.fileInfo(cover.archivePath))
                }
                themeFileEntries.forEach { entry ->
                    coroutineContext.ensureActive()
                    add(entry.file.fileInfo(entry.archivePath))
                }
            }
            val manifest = BackupManifest(
                format = BACKUP_FORMAT,
                backupSchemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
                createdAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                databaseVersion = LibraryDatabase.SCHEMA_VERSION,
                counts = exportData.counts(coverFiles.size),
                files = fileInfo,
                missingCoverCount = missingCoverCount
            )
            val manifestBytes = codec.encodeManifest(manifest).toByteArray(Charsets.UTF_8)
            require(manifestBytes.size <= BackupArchiveLimits.MAX_MANIFEST_BYTES) {
                "Backup manifest is too large"
            }

            val output = appContext.contentResolver.openOutputStream(uri, "w")
                ?: error("Unable to open destination")
            output.use { stream ->
                ZipOutputStream(stream.buffered()).use { zip ->
                    zip.writeBytes("manifest.json", manifestBytes)
                    zip.writeFile("data.json", dataFile)
                    zip.writeFile("preferences.json", preferencesFile)
                    coverFiles.values.forEach { cover ->
                        coroutineContext.ensureActive()
                        zip.writeFile(cover.archivePath, cover.file)
                    }
                    themeFileEntries.forEach { entry ->
                        coroutineContext.ensureActive()
                        zip.writeFile(entry.archivePath, entry.file)
                    }
                }
            }
            BackupResult.Success(
                warnings = buildList {
                    if (missingCoverCount > 0) {
                        add(BackupWarning.MissingCovers(missingCoverCount))
                    }
                    val skippedThemes = themeCollection?.skippedCount ?: 0
                    if (skippedThemes > 0) {
                        add(BackupWarning.SkippedThemes(skippedThemes))
                    }
                }
            )
        } catch (cancelled: CancellationException) {
            runCatching { appContext.contentResolver.delete(uri, null, null) }
            throw cancelled
        } catch (error: Throwable) {
            runCatching { appContext.contentResolver.delete(uri, null, null) }
            BackupResult.Failure(
                reason = com.example.mylibrary.backup.model.BackupFailureReason.IO_ERROR,
                cause = error
            )
        } finally {
            tempDirectory.deleteRecursively()
        }
    }

    private fun File.fileInfo(path: String): BackupFileInfo = BackupFileInfo(
        path = path,
        size = length(),
        sha256 = inputStream().buffered().use(::sha256)
    )

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun ZipOutputStream.writeFile(path: String, file: File) {
        putNextEntry(ZipEntry(BACKUP_ROOT + path))
        FileInputStream(file).buffered().use { input ->
            copyCancellable(input, this)
        }
        closeEntry()
    }

    private fun ZipOutputStream.writeBytes(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(BACKUP_ROOT + path))
        write(bytes)
        closeEntry()
    }

    private suspend fun copyCancellable(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) output.write(buffer, 0, count)
        }
    }

    private data class ExportCover(
        val archivePath: String,
        val file: File
    )

    private data class ThemeArchiveFile(
        val archivePath: String,
        val file: File
    )
}
