package com.example.mylibrary.backup

import com.example.mylibrary.data.preferences.ThemePreferenceStore
import com.example.mylibrary.ui.theme.DefaultResolvedTheme
import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.ResolvedTheme
import com.example.mylibrary.ui.theme.ThemeApplyError
import com.example.mylibrary.ui.theme.ThemeApplyResult
import com.example.mylibrary.ui.theme.ThemeManifest
import com.example.mylibrary.ui.theme.ThemeResolveError
import com.example.mylibrary.ui.theme.ThemeRepository
import com.example.mylibrary.ui.theme.importer.FileInstalledThemeCatalog
import com.example.mylibrary.ui.theme.importer.InstalledTheme
import com.example.mylibrary.ui.theme.importer.StreamThemePackageSource
import com.example.mylibrary.ui.theme.importer.ThemeChecksumManifest
import com.example.mylibrary.ui.theme.importer.ThemeInstaller
import com.example.mylibrary.ui.theme.importer.ThemePackageError
import com.example.mylibrary.ui.theme.importer.ThemePackageImportResult
import com.example.mylibrary.ui.theme.importer.ThemePackageImporter
import com.example.mylibrary.ui.theme.importer.ThemePackageJsonCodec
import com.example.mylibrary.ui.theme.importer.ThemePackageResult
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
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

class BackupThemeTransferTest {

    // -------------------------------------------------------------------------
    // Export tests
    // -------------------------------------------------------------------------

    @Test
    fun exportIncludesMultipleInstalledThemes() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.alpha", "Alpha")
        installTheme(root, installer, "theme.beta", "Beta")

        val collection = runBlocking {
            makeTransfer(installer).collectThemesForExport()
        }

        assertEquals(2, collection.themes.size)
        val ids = collection.themes.map { it.themeId }
        assertTrue("theme.alpha" in ids)
        assertTrue("theme.beta" in ids)
    }

    @Test
    fun defaultThemeNotExported() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.custom", "Custom")

        val collection = runBlocking {
            makeTransfer(installer).collectThemesForExport()
        }

        assertEquals(1, collection.themes.size)
        assertEquals("theme.custom", collection.themes[0].themeId)
        assertFalse(
            collection.themes.any { it.themeId == DefaultThemeManifest.id }
        )
    }

    @Test
    fun currentThemeIdWrittenForCustomTheme() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.active", "Active")
        val prefs = TransferFakeThemePreferenceStore().apply { currentId = "theme.active" }

        val collection = runBlocking {
            makeTransfer(installer, prefs).collectThemesForExport()
        }

        assertEquals("theme.active", collection.currentThemeId)
    }

    @Test
    fun currentThemeIdNullForDefaultTheme() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.custom", "Custom")
        val prefs = TransferFakeThemePreferenceStore().apply { currentId = null }

        val collection = runBlocking {
            makeTransfer(installer, prefs).collectThemesForExport()
        }

        assertNull(collection.currentThemeId)
    }

    @Test
    fun currentThemeIdNullWhenCurrentThemeIsCorrupted() =
        withThemeStorage { root, installer ->
            installTheme(root, installer, "theme.valid", "Valid")
            createCorruptedTheme(installer, "theme.corrupt")
            val prefs = TransferFakeThemePreferenceStore().apply {
                currentId = "theme.corrupt"
            }

            val collection = runBlocking {
                makeTransfer(installer, prefs).collectThemesForExport()
            }

            assertNull(collection.currentThemeId)
            assertTrue(collection.skippedCount > 0)
        }

    @Test
    fun exportWithNoThemesReturnsEmptyCollection() = withThemeStorage { _, installer ->
        val collection = runBlocking {
            makeTransfer(installer).collectThemesForExport()
        }

        assertTrue(collection.themes.isEmpty())
        assertEquals(0, collection.skippedCount)
        assertNull(collection.currentThemeId)
    }

    @Test
    fun exportSkipsCorruptedThemesAndCountsThem() =
        withThemeStorage { root, installer ->
            installTheme(root, installer, "theme.good1", "Good1")
            createCorruptedTheme(installer, "theme.bad1")
            installTheme(root, installer, "theme.good2", "Good2")
            createCorruptedTheme(installer, "theme.bad2")

            val collection = runBlocking {
                makeTransfer(installer).collectThemesForExport()
            }

            assertEquals(2, collection.themes.size)
            assertEquals(2, collection.skippedCount)
        }

    @Test
    fun exportIncludesThemeFiles() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.files", "Files")

        val collection = runBlocking {
            makeTransfer(installer).collectThemesForExport()
        }

        val theme = collection.themes.single()
        assertTrue(theme.files.containsKey("manifest.json"))
        assertTrue(theme.files.containsKey("checksums.json"))
    }

    @Test
    fun exportWorksWhenInstalledDirectoryDoesNotExist() =
        withThemeStorage { _, installer ->
            val emptyRoot = Files.createTempDirectory("no-themes").toFile()
            try {
                val emptyInstaller = ThemeInstaller(File(emptyRoot, "themes"))
                val collection = runBlocking {
                    makeTransfer(emptyInstaller).collectThemesForExport()
                }
                assertTrue(collection.themes.isEmpty())
            } finally {
                emptyRoot.deleteRecursively()
            }
        }

    // -------------------------------------------------------------------------
    // Restore tests
    // -------------------------------------------------------------------------

    @Test
    fun restoreInstallsThemesFromBackupDirectories() =
        withThemeStorage { root, installer ->
            val backupDir = createBackupThemeDir(
                File(root, "backup-source"),
                "theme.restore1",
                "Restore1"
            )

            val result = runBlocking {
                makeTransfer(installer).restoreThemes(
                    mapOf("theme.restore1" to backupDir),
                    currentThemeId = null
                )
            }

            assertEquals(setOf("theme.restore1"), result.installedThemeIds)
            assertEquals(0, result.skippedCount)
            assertTrue(
                File(installer.installedDirectory, "theme.restore1/manifest.json")
                    .isFile
            )
        }

    @Test
    fun sameIdThemeSafeReplacement() = withThemeStorage { root, installer ->
        installTheme(root, installer, "theme.replace", "Replace", version = "1")

        val backupDir = createBackupThemeDir(
            File(root, "backup-source"),
            "theme.replace",
            "Replace",
            version = "2"
        )

        val result = runBlocking {
            makeTransfer(installer).restoreThemes(
                mapOf("theme.replace" to backupDir),
                currentThemeId = null
            )
        }

        assertEquals(setOf("theme.replace"), result.installedThemeIds)
        val manifest = readInstalledManifest(installer, "theme.replace")
        assertEquals("2", manifest.version)
    }

    @Test
    fun localExtraThemesNotDeletedDuringRestore() =
        withThemeStorage { root, installer ->
            installTheme(root, installer, "theme.local", "Local")

            val backupDir = createBackupThemeDir(
                File(root, "backup-source"),
                "theme.backup",
                "Backup"
            )

            runBlocking {
                makeTransfer(installer).restoreThemes(
                    mapOf("theme.backup" to backupDir),
                    currentThemeId = null
                )
            }

            assertTrue(
                File(installer.installedDirectory, "theme.local/manifest.json")
                    .isFile
            )
            assertTrue(
                File(installer.installedDirectory, "theme.backup/manifest.json")
                    .isFile
            )
        }

    @Test
    fun currentThemeNotFoundFallsBackToDefault() =
        withThemeStorage { _, installer ->
            val prefs = TransferFakeThemePreferenceStore()
            val repo = TransferFakeThemeRepository()

            val result = runBlocking {
                makeTransfer(installer, prefs, repo).restoreThemes(
                    emptyMap(),
                    currentThemeId = "theme.missing"
                )
            }

            assertFalse(result.currentThemeRestored)
            assertTrue(repo.defaultApplied)
        }

    @Test
    fun currentThemeRestoredWhenThemeInstalledSuccessfully() =
        withThemeStorage { root, installer ->
            val backupDir = createBackupThemeDir(
                File(root, "backup-source"),
                "theme.current",
                "Current"
            )
            val prefs = TransferFakeThemePreferenceStore()
            val repo = TransferFakeThemeRepository()

            val result = runBlocking {
                makeTransfer(installer, prefs, repo).restoreThemes(
                    mapOf("theme.current" to backupDir),
                    currentThemeId = "theme.current"
                )
            }

            assertTrue(result.currentThemeRestored)
            assertEquals("theme.current", repo.appliedThemeId)
        }

    @Test
    fun nullCurrentThemeAppliesDefault() = withThemeStorage { _, installer ->
        val prefs = TransferFakeThemePreferenceStore()
        val repo = TransferFakeThemeRepository()

        val result = runBlocking {
            makeTransfer(installer, prefs, repo).restoreThemes(
                emptyMap(),
                currentThemeId = null
            )
        }

        assertTrue(result.currentThemeRestored)
        assertTrue(repo.defaultApplied)
    }

    @Test
    fun validAndCorruptedThemesCoexistDuringRestore() =
        withThemeStorage { root, installer ->
            val validBackup = createBackupThemeDir(
                File(root, "backup-source"),
                "theme.valid",
                "Valid"
            )
            val corruptedBackup = File(root, "corrupted-theme").apply {
                mkdirs()
                File(this, "junk.txt").writeText("not a theme")
            }

            val result = runBlocking {
                makeTransfer(installer).restoreThemes(
                    mapOf(
                        "theme.valid" to validBackup,
                        "theme.corrupt" to corruptedBackup
                    ),
                    currentThemeId = null
                )
            }

            assertEquals(setOf("theme.valid"), result.installedThemeIds)
            assertEquals(1, result.skippedCount)
            assertTrue(
                File(installer.installedDirectory, "theme.valid/manifest.json")
                    .isFile
            )
            assertFalse(
                File(installer.installedDirectory, "theme.corrupt").exists()
            )
        }

    @Test
    fun restoreMultipleThemesAllSucceed() = withThemeStorage { root, installer ->
        val backup1 = createBackupThemeDir(
            File(root, "backup-a"),
            "theme.a",
            "Theme A"
        )
        val backup2 = createBackupThemeDir(
            File(root, "backup-b"),
            "theme.b",
            "Theme B"
        )

        val result = runBlocking {
            makeTransfer(installer).restoreThemes(
                mapOf("theme.a" to backup1, "theme.b" to backup2),
                currentThemeId = null
            )
        }

        assertEquals(setOf("theme.a", "theme.b"), result.installedThemeIds)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun exportThenImportRestoresThemeManifest() =
        withThemeStorage { root, installer ->
            installTheme(root, installer, "theme.roundtrip", "RoundTrip")

            val exportCollection = runBlocking {
                makeTransfer(installer).collectThemesForExport()
            }

            val exportedTheme = exportCollection.themes.single()
            assertEquals("theme.roundtrip", exportedTheme.themeId)

            val backupDir = File(root, "backup-extract/theme.roundtrip").apply {
                mkdirs()
            }
            exportedTheme.files.forEach { (relativePath, sourceFile) ->
                val dest = File(backupDir, relativePath)
                dest.parentFile?.mkdirs()
                sourceFile.copyTo(dest, overwrite = true)
            }

            val restoreRoot = Files.createTempDirectory("restore").toFile()
            try {
                val restoreInstaller = ThemeInstaller(File(restoreRoot, "themes"))
                val result = runBlocking {
                    makeTransfer(restoreInstaller).restoreThemes(
                        mapOf("theme.roundtrip" to backupDir),
                        currentThemeId = null
                    )
                }

                assertEquals(
                    setOf("theme.roundtrip"),
                    result.installedThemeIds
                )
                val restoredManifest = readInstalledManifest(
                    restoreInstaller,
                    "theme.roundtrip"
                )
                assertEquals("theme.roundtrip", restoredManifest.id)
                assertEquals("RoundTrip", restoredManifest.name)
            } finally {
                restoreRoot.deleteRecursively()
            }
        }

    @Test
    fun currentThemeIdPreservedThroughExportImportCycle() =
        withThemeStorage { root, installer ->
            installTheme(root, installer, "theme.persist", "Persist")
            val prefs = TransferFakeThemePreferenceStore().apply {
                currentId = "theme.persist"
            }

            val exportCollection = runBlocking {
                makeTransfer(installer, prefs).collectThemesForExport()
            }
            assertEquals("theme.persist", exportCollection.currentThemeId)

            val backupDir = File(root, "backup-extract/theme.persist").apply {
                mkdirs()
            }
            exportCollection.themes.single().files.forEach { (rel, src) ->
                val dest = File(backupDir, rel)
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
            }

            val restoreRoot = Files.createTempDirectory("restore").toFile()
            try {
                val restoreInstaller = ThemeInstaller(File(restoreRoot, "themes"))
                val restorePrefs = TransferFakeThemePreferenceStore()
                val restoreRepo = TransferFakeThemeRepository()
                val result = runBlocking {
                    makeTransfer(restoreInstaller, restorePrefs, restoreRepo)
                        .restoreThemes(
                            mapOf("theme.persist" to backupDir),
                            currentThemeId = exportCollection.currentThemeId
                        )
                }

                assertTrue(result.currentThemeRestored)
                assertEquals("theme.persist", restoreRepo.appliedThemeId)
            } finally {
                restoreRoot.deleteRecursively()
            }
        }

    // -------------------------------------------------------------------------
    // CancellationException propagation tests
    // -------------------------------------------------------------------------

    @Test
    fun fallbackToDefaultThemePropagatesCancellationFromClearPreference() =
        withThemeStorage { _, installer ->
            val prefs = CancellingThemePreferenceStore(
                cancelOnClear = true
            )
            val repo = TransferFakeThemeRepository()

            var cancelled = false
            try {
                runBlocking {
                    makeTransfer(installer, prefs, repo).fallbackToDefaultTheme()
                }
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
        }

    @Test
    fun fallbackToDefaultThemePropagatesCancellationFromApplyDefault() =
        withThemeStorage { _, installer ->
            val prefs = TransferFakeThemePreferenceStore()
            val repo = CancellingThemeRepository(cancelOnApplyDefault = true)

            var cancelled = false
            try {
                runBlocking {
                    makeTransfer(installer, prefs, repo).fallbackToDefaultTheme()
                }
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
        }

    @Test
    fun collectThemesForExportPropagatesCancellationFromReadPreference() =
        withThemeStorage { _, installer ->
            val prefs = CancellingThemePreferenceStore(cancelOnRead = true)

            var cancelled = false
            try {
                runBlocking {
                    makeTransfer(installer, prefs).collectThemesForExport()
                }
            } catch (_: CancellationException) {
                cancelled = true
            }

            assertTrue(cancelled)
        }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun withThemeStorage(block: (File, ThemeInstaller) -> Unit) {
        val root = Files.createTempDirectory("backup-theme-test").toFile()
        try {
            val installer = ThemeInstaller(File(root, "themes"))
            block(root, installer)
        } finally {
            root.deleteRecursively()
        }
    }

    private val themeCodec = ThemePackageJsonCodec()

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
            "Theme installation failed for $themeId: " +
                "${(result as? ThemePackageImportResult.Failure)?.error}"
        }
    }

    private fun createCorruptedTheme(installer: ThemeInstaller, themeId: String) {
        val dir = File(installer.installedDirectory, themeId)
        dir.mkdirs()
        File(dir, "junk.txt").writeText("not a valid theme")
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
        check(result is ThemePackageResult.Success) {
            "Failed to decode manifest: " +
                "${(result as? ThemePackageResult.Failure)?.error}"
        }
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

// -------------------------------------------------------------------------
// Fakes
// -------------------------------------------------------------------------

private class TransferFakeThemePreferenceStore : ThemePreferenceStore {
    @Volatile var currentId: String? = null

    override suspend fun readCurrentThemeId(): String? = currentId

    override suspend fun setCurrentThemeId(themeId: String) {
        currentId = themeId
    }

    override suspend fun clearCurrentThemeId() {
        currentId = null
    }
}

private class TransferFakeThemeRepository : ThemeRepository {
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

    override fun acknowledgeRestoreError() {
        _lastRestoreError.value = null
    }
}

/**
 * Fake [ThemePreferenceStore] that throws [CancellationException] from
 * configured methods to verify cancellation propagation.
 */
private class CancellingThemePreferenceStore(
    private val cancelOnRead: Boolean = false,
    private val cancelOnClear: Boolean = false
) : ThemePreferenceStore {
    override suspend fun readCurrentThemeId(): String? {
        if (cancelOnRead) throw CancellationException("test-cancel-read")
        return null
    }

    override suspend fun setCurrentThemeId(themeId: String) {}

    override suspend fun clearCurrentThemeId() {
        if (cancelOnClear) throw CancellationException("test-cancel-clear")
    }
}

/**
 * Fake [ThemeRepository] that throws [CancellationException] from
 * [applyDefaultTheme] to verify cancellation propagation.
 */
private class CancellingThemeRepository(
    private val cancelOnApplyDefault: Boolean = false
) : ThemeRepository {
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

    override suspend fun applyInstalledTheme(themeId: String): ThemeApplyResult =
        ThemeApplyResult.Applied(themeId, DefaultResolvedTheme)

    override suspend fun applyInstalledTheme(
        installedTheme: InstalledTheme
    ): ThemeApplyResult = applyInstalledTheme(installedTheme.id)

    override suspend fun applyDefaultTheme(): ThemeApplyResult {
        if (cancelOnApplyDefault) throw CancellationException("test-cancel-default")
        return ThemeApplyResult.Applied(null, DefaultResolvedTheme)
    }

    override fun acknowledgeRestoreError() {}
}
