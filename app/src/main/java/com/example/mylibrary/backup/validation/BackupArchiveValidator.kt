package com.example.mylibrary.backup.validation

import com.example.mylibrary.backup.model.BACKUP_FORMAT
import com.example.mylibrary.backup.model.BACKUP_ROOT
import com.example.mylibrary.backup.model.BackupArchiveLimits
import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupManifest
import com.example.mylibrary.backup.model.BackupPreferences
import com.example.mylibrary.backup.model.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.mylibrary.backup.serialization.BackupJsonCodec
import com.example.mylibrary.backup.serialization.BackupMigrationChain
import com.example.mylibrary.data.image.CoverInputValidator
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.domain.model.LibraryViewMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class BackupArchiveValidator(
    private val codec: BackupJsonCodec = BackupJsonCodec(),
    private val migrationChain: BackupMigrationChain = BackupMigrationChain(),
    private val dataValidator: BackupDataValidator = BackupDataValidator(),
    private val normalizer: BackupDefaultDataNormalizer = BackupDefaultDataNormalizer()
) {
    suspend fun validate(
        archive: File,
        extractionDirectory: File
    ): ValidatedBackup {
        require(archive.isFile && archive.length() in 1..BackupArchiveLimits.MAX_ARCHIVE_BYTES) {
            "Invalid backup archive size"
        }
        val extractedFiles = extractArchive(archive, extractionDirectory)
        val manifestFile = extractedFiles["manifest.json"]
            ?: error("Backup manifest is missing")
        val manifest = codec.decodeManifest(
            manifestFile.readTextWithLimit(BackupArchiveLimits.MAX_MANIFEST_BYTES)
        )
        require(manifest.format == BACKUP_FORMAT) { "Invalid backup format" }
        if (manifest.backupSchemaVersion > CURRENT_BACKUP_SCHEMA_VERSION) {
            throw NewerBackupVersionException()
        }
        require(manifest.backupSchemaVersion > 0) { "Invalid backup schema version" }
        OffsetDateTime.parse(manifest.createdAt)

        validateManifestFiles(manifest, extractedFiles)
        val dataFile = extractedFiles["data.json"] ?: error("Backup data is missing")
        val parsedData = codec.parseDataObject(
            dataFile.readTextWithLimit(BackupArchiveLimits.MAX_DATA_JSON_BYTES)
        )
        val migratedData = migrationChain.migrate(
            parsedData,
            manifest.backupSchemaVersion
        )
        val rawData = codec.decodeData(migratedData)
        val coverPaths = extractedFiles.keys
            .filterTo(linkedSetOf()) { it.startsWith("covers/original/") }
        dataValidator.validate(
            data = rawData,
            availableCoverPaths = coverPaths,
            declaredCounts = manifest.counts
        )
        validateCoverFiles(coverPaths, extractedFiles)
        val normalizedData = normalizer.normalize(rawData)
        dataValidator.validate(
            data = normalizedData,
            availableCoverPaths = coverPaths
        )

        val preferencesFile = extractedFiles["preferences.json"]
        val preferences = if (preferencesFile != null) {
            val preferencesText = preferencesFile.readTextWithLimit(
                BackupArchiveLimits.MAX_PREFERENCES_BYTES
            )
            // v5+ backups must explicitly include the currentThemeId key.
            // The value may be JSON null (meaning "use default theme"), but
            // the key itself must be present.  A missing key is a format
            // error, not an implicit "use default".
            if (manifest.backupSchemaVersion >= 5) {
                require(
                    codec.preferencesContainsCurrentThemeIdKey(preferencesText)
                ) {
                    "v5 backup preferences.json must contain currentThemeId"
                }
            }
            codec.decodePreferences(preferencesText)
                .also { validatePreferences(it, normalizedData) }
        } else {
            // v5+ backups must contain preferences.json.
            require(manifest.backupSchemaVersion < 5) {
                "v5 backup must contain preferences.json"
            }
            null
        }
        val themeDirectories = collectThemeDirectories(
            extractedFiles,
            extractionDirectory.canonicalFile
        )
        return ValidatedBackup(
            manifest = manifest,
            data = normalizedData,
            preferences = preferences,
            coverFiles = coverPaths.associateWith { requireNotNull(extractedFiles[it]) },
            themeDirectories = themeDirectories,
            shouldRestoreThemePreference = manifest.backupSchemaVersion >= 5
        )
    }

    private suspend fun extractArchive(
        archive: File,
        extractionDirectory: File
    ): Map<String, File> {
        extractionDirectory.mkdirs()
        val canonicalRoot = extractionDirectory.canonicalFile
        val files = linkedMapOf<String, File>()
        val seenEntries = mutableSetOf<String>()
        var entryCount = 0
        var totalBytes = 0L

        ZipInputStream(FileInputStream(archive).buffered()).use { zip ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= BackupArchiveLimits.MAX_ENTRY_COUNT) {
                    "Backup contains too many entries"
                }
                val normalized = normalizeEntryName(entry.name)
                require(seenEntries.add(normalized)) { "Duplicate ZIP entry" }
                if (entry.isDirectory) {
                    require(isAllowedDirectory(normalized)) { "Unexpected ZIP directory" }
                    zip.closeEntry()
                    continue
                }
                require(normalized.startsWith(BACKUP_ROOT)) { "Invalid backup root" }
                val relative = normalized.removePrefix(BACKUP_ROOT)
                require(isAllowedFile(relative)) { "Unexpected backup file" }
                val destination = File(canonicalRoot, relative).canonicalFile
                require(destination.path.startsWith(canonicalRoot.path + File.separator)) {
                    "ZIP entry escapes extraction directory"
                }
                destination.parentFile?.mkdirs()
                val entryLimit = entryLimit(relative)
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var entryBytes = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = zip.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        entryBytes += count
                        totalBytes += count
                        require(entryBytes <= entryLimit) { "ZIP entry is too large" }
                        require(totalBytes <= BackupArchiveLimits.MAX_TOTAL_EXTRACTED_BYTES) {
                            "Backup expands beyond the allowed size"
                        }
                        output.write(buffer, 0, count)
                    }
                }
                files[relative] = destination
                zip.closeEntry()
            }
        }
        return files
    }

    private fun validateManifestFiles(
        manifest: BackupManifest,
        extractedFiles: Map<String, File>
    ) {
        require(manifest.files.isNotEmpty()) { "Manifest contains no files" }
        require(manifest.files.map { it.path }.distinct().size == manifest.files.size) {
            "Manifest contains duplicate file paths"
        }
        val declaredPaths = manifest.files.mapTo(linkedSetOf()) { info ->
            require(isAllowedFile(info.path) && info.path != "manifest.json") {
                "Manifest contains an invalid path"
            }
            require(info.size >= 0L) { "Manifest contains an invalid file size" }
            require(info.sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                "Manifest contains an invalid SHA-256"
            }
            info.path
        }
        val actualPaths = extractedFiles.keys - "manifest.json"
        require(declaredPaths == actualPaths) {
            "Manifest file list does not match archive contents"
        }
        require("data.json" in declaredPaths) { "Manifest does not declare data.json" }
        manifest.files.forEach { info ->
            val file = extractedFiles[info.path] ?: error("Declared file is missing")
            require(file.length() == info.size) { "Backup file size does not match manifest" }
            val actualHash = file.inputStream().buffered().use(::sha256)
            require(actualHash.equals(info.sha256, ignoreCase = true)) {
                "Backup file digest does not match manifest"
            }
        }
    }

    private fun validateCoverFiles(
        coverPaths: Set<String>,
        extractedFiles: Map<String, File>
    ) {
        coverPaths.forEach { path ->
            val file = extractedFiles[path] ?: error("Cover is missing")
            val declared = path.substringAfterLast('.').lowercase()
            CoverInputValidator.validate(
                file = file,
                declaredExtension = declared
            )
        }
    }

    private fun validatePreferences(
        preferences: BackupPreferences,
        data: BackupData
    ) {
        require(
            LibraryViewMode.entries.any { it.storageValue == preferences.libraryViewMode }
        ) {
            "Invalid library view mode"
        }
        require(preferences.gridColumns in 2..6 && preferences.coverColumns in 2..6) {
            "Invalid library column count"
        }
        val fieldIds = data.fieldDefinitions.mapTo(hashSetOf()) { it.id }
        preferences.listDisplayFields.forEach { key ->
            val valid = key == LibraryDisplayFieldKey.CREATOR ||
                key == LibraryDisplayFieldKey.CURRENT_STATUS ||
                key == LibraryDisplayFieldKey.TAGS ||
                LibraryDisplayFieldKey.dynamicId(key) in fieldIds
            require(valid) { "Preferences reference a missing field" }
        }
    }

    private fun normalizeEntryName(raw: String): String {
        require(raw.isNotBlank()) { "Empty ZIP entry" }
        require('\\' !in raw) { "Backslash ZIP paths are not allowed" }
        require(!raw.startsWith('/') && !DRIVE_PATH.matches(raw)) {
            "Absolute ZIP paths are not allowed"
        }
        val hasTrailingSlash = raw.endsWith('/')
        val parts = raw.split('/').filter { it.isNotEmpty() }
        require(parts.none { it == "." || it == ".." }) { "Unsafe ZIP path" }
        val normalized = parts.joinToString("/")
        return if (hasTrailingSlash) "$normalized/" else normalized
    }

    private fun isAllowedDirectory(path: String): Boolean {
        if (path in setOf(
            BACKUP_ROOT,
            "${BACKUP_ROOT}covers/",
            "${BACKUP_ROOT}covers/original/",
            "${BACKUP_ROOT}themes/"
        )) {
            return true
        }
        if (!path.startsWith("${BACKUP_ROOT}themes/")) return false
        val relative = path.removePrefix("${BACKUP_ROOT}themes/").removeSuffix("/")
        val firstSegment = relative.substringBefore('/')
        return THEME_ID_PATTERN.matches(firstSegment)
    }

    private fun isAllowedFile(path: String): Boolean =
        path == "manifest.json" ||
            path == "data.json" ||
            path == "preferences.json" ||
            isCoverPath(path) ||
            isThemeFilePath(path)

    private fun isCoverPath(path: String): Boolean {
        if (!path.startsWith("covers/original/")) return false
        val fileName = path.removePrefix("covers/original/")
        return fileName.isNotBlank() &&
            '/' !in fileName &&
            fileName.matches(Regex("[A-Za-z0-9._-]+"))
    }

    private fun isThemeFilePath(path: String): Boolean {
        if (!path.startsWith("themes/")) return false
        val remaining = path.removePrefix("themes/")
        val slashIndex = remaining.indexOf('/')
        if (slashIndex < 0) return false
        val themeId = remaining.substring(0, slashIndex)
        if (!THEME_ID_PATTERN.matches(themeId)) return false
        val relativePath = remaining.substring(slashIndex + 1)
        if (relativePath.isBlank()) return false
        return relativePath.split('/').all { segment ->
            segment.isNotBlank() &&
                segment.matches(Regex("[A-Za-z0-9._-]+"))
        }
    }

    private fun collectThemeDirectories(
        extractedFiles: Map<String, File>,
        extractionRoot: File
    ): Map<String, File> {
        val themeIds = linkedSetOf<String>()
        extractedFiles.keys.forEach { path ->
            if (path.startsWith("themes/")) {
                val remaining = path.removePrefix("themes/")
                val themeId = remaining.substringBefore('/')
                if (themeId.isNotEmpty() && THEME_ID_PATTERN.matches(themeId)) {
                    themeIds += themeId
                }
            }
        }
        return themeIds.associateWith { themeId ->
            File(extractionRoot, "themes/$themeId")
        }
    }

    private fun entryLimit(path: String): Long = when {
        path == "manifest.json" -> BackupArchiveLimits.MAX_MANIFEST_BYTES
        path == "data.json" -> BackupArchiveLimits.MAX_DATA_JSON_BYTES
        path == "preferences.json" -> BackupArchiveLimits.MAX_PREFERENCES_BYTES
        isThemeFilePath(path) -> BackupArchiveLimits.MAX_THEME_FILE_BYTES
        else -> BackupArchiveLimits.MAX_COVER_BYTES
    }

    private fun File.readTextWithLimit(limit: Long): String {
        require(length() <= limit) { "JSON file is too large" }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return inputStream().buffered().use { input ->
            InputStreamReader(input, decoder).use { it.readText() }
        }
    }

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

    private companion object {
        val DRIVE_PATH = Regex("^[A-Za-z]:.*")
        val THEME_ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]*$")
    }
}

data class ValidatedBackup(
    val manifest: BackupManifest,
    val data: BackupData,
    val preferences: BackupPreferences?,
    val coverFiles: Map<String, File>,
    val themeDirectories: Map<String, File> = emptyMap(),
    val shouldRestoreThemePreference: Boolean = false
)

class NewerBackupVersionException : IllegalArgumentException()
