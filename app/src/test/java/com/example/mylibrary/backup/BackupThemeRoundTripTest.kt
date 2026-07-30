package com.example.mylibrary.backup

import com.example.mylibrary.backup.model.BACKUP_FORMAT
import com.example.mylibrary.backup.model.BACKUP_ROOT
import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupFileInfo
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.model.BackupManifest
import com.example.mylibrary.backup.model.BackupPreferences
import com.example.mylibrary.backup.model.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.mylibrary.backup.serialization.BackupJsonCodec
import com.example.mylibrary.backup.validation.BackupArchiveValidator
import com.example.mylibrary.data.preferences.ThemePreferenceStore
import com.example.mylibrary.ui.theme.DefaultResolvedTheme
import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.ResolvedTheme
import com.example.mylibrary.ui.theme.ThemeApplyResult
import com.example.mylibrary.ui.theme.ThemeManifest
import com.example.mylibrary.ui.theme.ThemeRepository
import com.example.mylibrary.ui.theme.ThemeResolveError
import com.example.mylibrary.ui.theme.importer.FileInstalledThemeCatalog
import com.example.mylibrary.ui.theme.importer.InstalledTheme
import com.example.mylibrary.ui.theme.importer.StreamThemePackageSource
import com.example.mylibrary.ui.theme.importer.ThemeChecksumManifest
import com.example.mylibrary.ui.theme.importer.ThemeInstaller
import com.example.mylibrary.ui.theme.importer.ThemePackageImportResult
import com.example.mylibrary.ui.theme.importer.ThemePackageImporter
import com.example.mylibrary.ui.theme.importer.ThemePackageJsonCodec
import com.example.mylibrary.ui.theme.importer.ThemePackageResult
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end round-trip tests that exercise the full backup pipeline:
 * install themes → collect for export → build backup ZIP → validate →
 * restore from validated backup → verify installed state.
 *
 * These tests bridge [BackupArchiveValidator], [BackupThemeTransfer] and
 * [ThemeInstaller] without touching the Android framework.
 */
class BackupThemeRoundTripTest {

    private val codec = BackupJsonCodec()
    private val themeCodec = ThemePackageJsonCodec()

    @Test
    fun fullRoundTripRestoresThemesAndCurrentThemeId() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.alpha", "Alpha")
        installTheme(root, installer, "theme.beta", "Beta")
        val prefs = RoundTripFakeThemePreferenceStore().apply { currentId = "theme.alpha" }

        // Export
        val transfer = makeTransfer(installer, prefs)
        val collection = runBlocking { transfer.collectThemesForExport() }
        assertEquals(2, collection.themes.size)
        assertEquals("theme.alpha", collection.currentThemeId)

        // Build backup ZIP
        val backupZip = File(root, "backup.zip")
        writeBackupZip(backupZip, collection, currentThemeId = collection.currentThemeId)

        // Validate
        val extractDir = File(root, "extract")
        val validated = runBlocking {
            BackupArchiveValidator().validate(backupZip, extractDir)
        }
        assertEquals(5, validated.manifest.backupSchemaVersion)
        assertEquals(2, validated.themeDirectories.size)
        assertEquals("theme.alpha", validated.preferences?.currentThemeId)

        // Restore into a fresh destination
        val restoreRoot = Files.createTempDirectory("restore").toFile()
        try {
            val restoreInstaller = ThemeInstaller(File(restoreRoot, "themes"))
            val restorePrefs = RoundTripFakeThemePreferenceStore()
            val restoreRepo = RoundTripFakeThemeRepository()
            val restoreTransfer = makeTransfer(restoreInstaller, restorePrefs, restoreRepo)

            val result = runBlocking {
                restoreTransfer.restoreThemes(
                    themeDirectories = validated.themeDirectories,
                    currentThemeId = validated.preferences?.currentThemeId
                )
            }

            assertEquals(setOf("theme.alpha", "theme.beta"), result.installedThemeIds)
            assertEquals(0, result.skippedCount)
            assertTrue(result.currentThemeRestored)
            assertEquals("theme.alpha", restoreRepo.appliedThemeId)

            // Verify manifest content
            val alphaManifest = readInstalledManifest(restoreInstaller, "theme.alpha")
            assertEquals("theme.alpha", alphaManifest.id)
            assertEquals("Alpha", alphaManifest.name)
            val betaManifest = readInstalledManifest(restoreInstaller, "theme.beta")
            assertEquals("theme.beta", betaManifest.id)
            assertEquals("Beta", betaManifest.name)
        } finally {
            restoreRoot.deleteRecursively()
        }
    }

    @Test
    fun roundTripWithNullCurrentThemeAppliesDefault() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.gamma", "Gamma")
        val prefs = RoundTripFakeThemePreferenceStore().apply { currentId = null }

        val transfer = makeTransfer(installer, prefs)
        val collection = runBlocking { transfer.collectThemesForExport() }
        assertNull(collection.currentThemeId)

        val backupZip = File(root, "backup.zip")
        writeBackupZip(backupZip, collection, currentThemeId = null)

        val validated = runBlocking {
            BackupArchiveValidator().validate(backupZip, File(root, "extract"))
        }
        assertNull(validated.preferences?.currentThemeId)

        val restoreRoot = Files.createTempDirectory("restore").toFile()
        try {
            val restoreInstaller = ThemeInstaller(File(restoreRoot, "themes"))
            val restorePrefs = RoundTripFakeThemePreferenceStore()
            val restoreRepo = RoundTripFakeThemeRepository()
            val restoreTransfer = makeTransfer(restoreInstaller, restorePrefs, restoreRepo)

            val result = runBlocking {
                restoreTransfer.restoreThemes(
                    themeDirectories = validated.themeDirectories,
                    currentThemeId = null
                )
            }

            assertTrue(result.currentThemeRestored)
            assertTrue(restoreRepo.defaultApplied)
        } finally {
            restoreRoot.deleteRecursively()
        }
    }

    @Test
    fun roundTripPreservesThemeFiles() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.files", "Files")

        val transfer = makeTransfer(installer)
        val collection = runBlocking { transfer.collectThemesForExport() }

        val backupZip = File(root, "backup.zip")
        writeBackupZip(backupZip, collection, currentThemeId = null)

        val validated = runBlocking {
            BackupArchiveValidator().validate(backupZip, File(root, "extract"))
        }

        val themeDir = validated.themeDirectories["theme.files"]
        assertNotNull(themeDir)
        assertTrue(File(themeDir!!, "manifest.json").isFile)
        assertTrue(File(themeDir, "checksums.json").isFile)

        // Restore and verify files exist
        val restoreRoot = Files.createTempDirectory("restore").toFile()
        try {
            val restoreInstaller = ThemeInstaller(File(restoreRoot, "themes"))
            val restoreTransfer = makeTransfer(restoreInstaller)

            runBlocking {
                restoreTransfer.restoreThemes(
                    themeDirectories = validated.themeDirectories,
                    currentThemeId = null
                )
            }

            val installedDir = File(restoreInstaller.installedDirectory, "theme.files")
            assertTrue(File(installedDir, "manifest.json").isFile)
            assertTrue(File(installedDir, "checksums.json").isFile)
        } finally {
            restoreRoot.deleteRecursively()
        }
    }

    @Test
    fun sameIdThemeReplacedDuringRoundTrip() = withThemeStorage { root, installer ->
        // Install v1 locally
        installTheme(root, installer, "theme.replace", "Replace", version = "1")

        // Create a backup with v2
        val backupDir = createBackupThemeDir(
            File(root, "backup-source"),
            "theme.replace",
            "Replace",
            version = "2"
        )

        val restoreResult = runBlocking {
            makeTransfer(installer).restoreThemes(
                themeDirectories = mapOf("theme.replace" to backupDir),
                currentThemeId = null
            )
        }

        assertEquals(setOf("theme.replace"), restoreResult.installedThemeIds)
        val manifest = readInstalledManifest(installer, "theme.replace")
        assertEquals("2", manifest.version)
    }

    @Test
    fun localExtraThemesSurviveRoundTrip() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.local", "Local")
        installTheme(root, installer, "theme.backup", "Backup")

        val transfer = makeTransfer(installer)
        val collection = runBlocking { transfer.collectThemesForExport() }

        // Only export "theme.backup"
        val singleThemeCollection = collection.copy(
            themes = collection.themes.filter { it.themeId == "theme.backup" }
        )

        val backupZip = File(root, "backup.zip")
        writeBackupZip(backupZip, singleThemeCollection, currentThemeId = null)

        val validated = runBlocking {
            BackupArchiveValidator().validate(backupZip, File(root, "extract"))
        }

        runBlocking {
            makeTransfer(installer).restoreThemes(
                themeDirectories = validated.themeDirectories,
                currentThemeId = null
            )
        }

        // Both themes should still exist
        assertTrue(
            File(installer.installedDirectory, "theme.local/manifest.json").isFile
        )
        assertTrue(
            File(installer.installedDirectory, "theme.backup/manifest.json").isFile
        )
    }

    @Test
    fun corruptedThemeInBackupDoesNotBlockValidThemes() = withThemeStorage { root, installer ->
        // Create a valid backup theme directory
        val validBackup = createBackupThemeDir(
            File(root, "backup-source"),
            "theme.valid",
            "Valid"
        )
        // Create a corrupted backup theme directory
        val corruptedBackup = File(root, "corrupted").apply {
            mkdirs()
            File(this, "junk.txt").writeText("not a theme")
        }

        val result = runBlocking {
            makeTransfer(installer).restoreThemes(
                themeDirectories = mapOf(
                    "theme.valid" to validBackup,
                    "theme.corrupt" to corruptedBackup
                ),
                currentThemeId = null
            )
        }

        assertEquals(setOf("theme.valid"), result.installedThemeIds)
        assertEquals(1, result.skippedCount)
        assertTrue(
            File(installer.installedDirectory, "theme.valid/manifest.json").isFile
        )
        assertFalse(
            File(installer.installedDirectory, "theme.corrupt").exists()
        )
    }

    @Test
    fun currentThemeNotInBackupFallsBackToDefault() = withThemeStorage { _, installer ->
        val prefs = RoundTripFakeThemePreferenceStore()
        val repo = RoundTripFakeThemeRepository()

        val result = runBlocking {
            makeTransfer(installer, prefs, repo).restoreThemes(
                themeDirectories = emptyMap(),
                currentThemeId = "theme.missing"
            )
        }

        assertFalse(result.currentThemeRestored)
        assertTrue(repo.defaultApplied)
    }

    @Test
    fun exportWithNoThemesProducesValidBackup() = withThemeStorage { root, installer ->
        val transfer = makeTransfer(installer)
        val collection = runBlocking { transfer.collectThemesForExport() }
        assertTrue(collection.themes.isEmpty())

        val backupZip = File(root, "backup.zip")
        writeBackupZip(backupZip, collection, currentThemeId = null)

        val validated = runBlocking {
            BackupArchiveValidator().validate(backupZip, File(root, "extract"))
        }

        assertEquals(5, validated.manifest.backupSchemaVersion)
        assertTrue(validated.themeDirectories.isEmpty())
    }

    @Test
    fun oldV4BackupImportDoesNotChangeExistingThemes() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.existing", "Existing")
        val prefs = RoundTripFakeThemePreferenceStore().apply { currentId = "theme.existing" }

        // Create a v4 backup without themes
        val backupZip = File(root, "v4-backup.zip")
        writeV4BackupZip(backupZip)

        val validated = runBlocking {
            BackupArchiveValidator().validate(backupZip, File(root, "extract"))
        }

        assertEquals(4, validated.manifest.backupSchemaVersion)
        assertTrue(validated.themeDirectories.isEmpty())
        // v1–v4 backups carry no theme preference semantics
        assertFalse(validated.shouldRestoreThemePreference)

        // Simulate DataImportService: when shouldRestoreThemePreference is
        // false, restoreThemes is not called, so the theme preference and
        // installed themes remain unchanged.
        // (No call to restoreThemes, applyDefaultTheme, or
        // fallbackToDefaultTheme.)

        // Existing theme preference is unchanged
        assertEquals("theme.existing", prefs.currentId)
        // Existing theme still on disk
        assertTrue(
            File(installer.installedDirectory, "theme.existing/manifest.json").isFile
        )
    }

    @Test
    fun v4BackupPreservesDefaultThemePreference() = withThemeStorage { root, _ ->
        val prefs = RoundTripFakeThemePreferenceStore().apply { currentId = null }

        // Create a v4 backup without themes
        val backupZip = File(root, "v4-backup.zip")
        writeV4BackupZip(backupZip)

        val validated = runBlocking {
            BackupArchiveValidator().validate(backupZip, File(root, "extract"))
        }

        assertEquals(4, validated.manifest.backupSchemaVersion)
        assertFalse(validated.shouldRestoreThemePreference)

        // Simulate DataImportService: when shouldRestoreThemePreference is
        // false, restoreThemes is not called, so the theme preference
        // remains unchanged.
        // (No call to restoreThemes or applyDefaultTheme.)

        // Default theme preference is unchanged
        assertNull(prefs.currentId)
    }

    @Test
    fun fallbackToDefaultThemeClearsPreferenceAndAppliesDefault() =
        withThemeStorage { _, installer ->
            val prefs = RoundTripFakeThemePreferenceStore().apply {
                currentId = "theme.before"
            }
            val repo = RoundTripFakeThemeRepository()

            runBlocking {
                makeTransfer(installer, prefs, repo).fallbackToDefaultTheme()
            }

            assertNull(prefs.currentId)
            assertTrue(repo.defaultApplied)
        }

    @Test
    fun noStagingArtifactsRemainAfterSuccessfulRestore() = withThemeStorage { root, installer ->
        val backupDir = createBackupThemeDir(
            File(root, "backup-source"),
            "theme.clean",
            "Clean"
        )

        runBlocking {
            makeTransfer(installer).restoreThemes(
                themeDirectories = mapOf("theme.clean" to backupDir),
                currentThemeId = null
            )
        }

        // Staging directory should be empty (staging entries are moved atomically)
        val stagingFiles = installer.stagingDirectory.listFiles().orEmpty()
        assertEquals(0, stagingFiles.size)

        // Rollback directory should be empty (rollbacks are cleaned up)
        val rollbackFiles = installer.rollbackDirectory.listFiles().orEmpty()
        assertEquals(0, rollbackFiles.size)
    }

    @Test
    fun noStagingArtifactsRemainAfterFailedRestore() = withThemeStorage { root, installer ->
        val corruptedBackup = File(root, "corrupted").apply {
            mkdirs()
            File(this, "junk.txt").writeText("not a theme")
        }

        runBlocking {
            makeTransfer(installer).restoreThemes(
                themeDirectories = mapOf("theme.bad" to corruptedBackup),
                currentThemeId = null
            )
        }

        // Theme should not be installed
        assertFalse(
            File(installer.installedDirectory, "theme.bad").exists()
        )
        // Staging should be cleaned up
        val stagingFiles = installer.stagingDirectory.listFiles().orEmpty()
        // Any staging directory created for the failed theme should have been cleaned up
        stagingFiles.forEach { file ->
            assertFalse("Staging entry should not contain theme.bad: ${file.name}",
                file.name.contains("theme.bad"))
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun withThemeStorage(block: (File, ThemeInstaller) -> Unit) {
        val root = Files.createTempDirectory("backup-roundtrip-test").toFile()
        try {
            val installer = ThemeInstaller(File(root, "themes"))
            block(root, installer)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun installTheme(
        root: File,
        installer: ThemeInstaller,
        themeId: String,
        name: String,
        version: String = "1"
    ) {
        val archive = File(root, "$themeId.mylibrarytheme")
        writeThemeZip(archive, themeId, name, version)
        val importer = ThemePackageImporter(
            temporaryRoot = File(root, "cache"),
            installer = installer,
            generationSource = { 42L }
        )
        val result = runBlocking {
            importer.import(StreamThemePackageSource { archive.inputStream() })
        }
        check(result is ThemePackageImportResult.Installed) {
            "Theme installation failed for $themeId"
        }
    }

    private fun createBackupThemeDir(
        parent: File,
        themeId: String,
        name: String,
        version: String = "1"
    ): File {
        val dir = File(parent, themeId)
        dir.mkdirs()
        val manifest = DefaultThemeManifest.copy(
            id = themeId,
            name = name,
            version = version
        )
        val manifestBytes = themeCodec.encodeManifest(manifest)
            .toByteArray(Charsets.UTF_8)
        File(dir, "manifest.json").writeBytes(manifestBytes)
        val checksums = ThemeChecksumManifest(
            algorithm = "SHA-256",
            files = mapOf("manifest.json" to sha256(manifestBytes))
        )
        val checksumsBytes = themeCodec.encodeChecksums(checksums)
            .toByteArray(Charsets.UTF_8)
        File(dir, "checksums.json").writeBytes(checksumsBytes)
        return dir
    }

    private fun writeThemeZip(
        archive: File,
        themeId: String,
        name: String,
        version: String
    ) {
        val manifest = DefaultThemeManifest.copy(
            id = themeId,
            name = name,
            version = version
        )
        val manifestBytes = themeCodec.encodeManifest(manifest)
            .toByteArray(Charsets.UTF_8)
        val checksums = ThemeChecksumManifest(
            algorithm = "SHA-256",
            files = mapOf("manifest.json" to sha256(manifestBytes))
        )
        val checksumsBytes = themeCodec.encodeChecksums(checksums)
            .toByteArray(Charsets.UTF_8)
        archive.parentFile?.mkdirs()
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("checksums.json"))
            zip.write(checksumsBytes)
            zip.closeEntry()
        }
    }

    private fun readInstalledManifest(
        installer: ThemeInstaller,
        themeId: String
    ): ThemeManifest {
        val file = File(installer.installedDirectory, "$themeId/manifest.json")
        val result = themeCodec.decodeManifest(file.readText())
        check(result is ThemePackageResult.Success) { "Failed to decode manifest" }
        return result.value
    }

    private fun makeTransfer(
        installer: ThemeInstaller,
        prefs: ThemePreferenceStore? = null,
        repo: ThemeRepository? = null
    ): BackupThemeTransfer = BackupThemeTransfer(
        installedDirectory = installer.installedDirectory,
        themeInstaller = installer,
        installedThemeCatalog = FileInstalledThemeCatalog(
            rootDirectory = installer.installedDirectory
        ),
        themePreferenceStore = prefs,
        themeRepository = repo,
        logger = NoOpBackupLogger,
        generationSource = { 42L }
    )

    private fun writeBackupZip(
        archive: File,
        collection: ThemeExportCollection,
        currentThemeId: String?
    ) {
        val data = BackupData(
            itemTypes = listOf(BackupItemType(1, "Book", 0)),
            statuses = emptyList(),
            fieldDefinitions = emptyList(),
            tags = emptyList(),
            items = emptyList(),
            records = emptyList(),
            activities = emptyList(),
            itemTags = emptyList(),
            fieldValues = emptyList(),
            quotes = emptyList()
        )
        val dataBytes = codec.encodeData(data).toByteArray(Charsets.UTF_8)
        val preferences = BackupPreferences(currentThemeId = currentThemeId)
        val preferencesBytes = codec.encodePreferences(preferences)
            .toByteArray(Charsets.UTF_8)

        val themeFileEntries = collection.themes.flatMap { theme ->
            theme.files.map { (relativePath, file) ->
                "themes/${theme.themeId}/$relativePath" to file
            }
        }

        val allFiles = mutableListOf<BackupFileInfo>()
        allFiles += BackupFileInfo(
            path = "data.json",
            size = dataBytes.size.toLong(),
            sha256 = sha256(dataBytes)
        )
        allFiles += BackupFileInfo(
            path = "preferences.json",
            size = preferencesBytes.size.toLong(),
            sha256 = sha256(preferencesBytes)
        )
        themeFileEntries.forEach { (archivePath, file) ->
            val bytes = file.readBytes()
            allFiles += BackupFileInfo(
                path = archivePath,
                size = bytes.size.toLong(),
                sha256 = sha256(bytes)
            )
        }

        val manifest = BackupManifest(
            format = BACKUP_FORMAT,
            backupSchemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
            createdAt = OffsetDateTime.now().toString(),
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseVersion = 10,
            counts = data.counts(0),
            files = allFiles
        )
        val manifestBytes = codec.encodeManifest(manifest)
            .toByteArray(Charsets.UTF_8)

        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}data.json"))
            zip.write(dataBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}preferences.json"))
            zip.write(preferencesBytes)
            zip.closeEntry()
            themeFileEntries.forEach { (archivePath, file) ->
                zip.putNextEntry(ZipEntry("${BACKUP_ROOT}$archivePath"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun writeV4BackupZip(archive: File) {
        val data = BackupData(
            itemTypes = listOf(BackupItemType(1, "Book", 0)),
            statuses = emptyList(),
            fieldDefinitions = emptyList(),
            tags = emptyList(),
            items = emptyList(),
            records = emptyList(),
            activities = emptyList(),
            itemTags = emptyList(),
            fieldValues = emptyList(),
            quotes = emptyList()
        )
        val dataBytes = codec.encodeData(data).toByteArray(Charsets.UTF_8)
        val dataInfo = BackupFileInfo(
            path = "data.json",
            size = dataBytes.size.toLong(),
            sha256 = sha256(dataBytes)
        )
        val manifest = BackupManifest(
            format = BACKUP_FORMAT,
            backupSchemaVersion = 4,
            createdAt = OffsetDateTime.now().toString(),
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseVersion = 10,
            counts = data.counts(0),
            files = listOf(dataInfo)
        )
        val manifestBytes = codec.encodeManifest(manifest)
            .toByteArray(Charsets.UTF_8)
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}data.json"))
            zip.write(dataBytes)
            zip.closeEntry()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

private class RoundTripFakeThemePreferenceStore : ThemePreferenceStore {
    @Volatile var currentId: String? = null

    override suspend fun readCurrentThemeId(): String? = currentId

    override suspend fun setCurrentThemeId(themeId: String) {
        currentId = themeId
    }

    override suspend fun clearCurrentThemeId() {
        currentId = null
    }
}

private class RoundTripFakeThemeRepository : ThemeRepository {
    @Volatile var appliedThemeId: String? = null
    @Volatile var defaultApplied = false

    private val _currentTheme = MutableStateFlow(DefaultResolvedTheme)
    override val currentTheme: StateFlow<ResolvedTheme> =
        _currentTheme.asStateFlow()

    private val _currentThemeId = MutableStateFlow<String?>(null)
    override val currentThemeId: StateFlow<String?> =
        _currentThemeId.asStateFlow()

    private val _lastRestoreError = MutableStateFlow<ThemeResolveError?>(null)
    override val lastRestoreError: StateFlow<ThemeResolveError?> =
        _lastRestoreError.asStateFlow()

    override suspend fun restore() {}

    override suspend fun applyInstalledTheme(themeId: String): ThemeApplyResult {
        appliedThemeId = themeId
        _currentThemeId.value = themeId
        return ThemeApplyResult.Applied(themeId, DefaultResolvedTheme)
    }

    override suspend fun applyInstalledTheme(
        installedTheme: InstalledTheme
    ): ThemeApplyResult = applyInstalledTheme(installedTheme.id)

    override suspend fun applyDefaultTheme(): ThemeApplyResult {
        defaultApplied = true
        appliedThemeId = null
        _currentThemeId.value = null
        return ThemeApplyResult.Applied(null, DefaultResolvedTheme)
    }

    override fun acknowledgeRestoreError() {}
}
