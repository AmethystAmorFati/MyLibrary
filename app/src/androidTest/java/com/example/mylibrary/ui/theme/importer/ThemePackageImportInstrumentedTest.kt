package com.example.mylibrary.ui.theme.importer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.test.mock.MockContentResolver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.preferences.DataStoreThemePreferenceStore
import com.example.mylibrary.data.preferences.ThemePreferenceStore
import com.example.mylibrary.ui.navigation.ResolvedNavigationIconResolver
import com.example.mylibrary.ui.navigation.ThemeIconRendering
import com.example.mylibrary.ui.theme.DefaultResolvedTheme
import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.DefaultThemeRepository
import com.example.mylibrary.ui.theme.DirectoryThemeResourceProvider
import com.example.mylibrary.ui.theme.FontRole
import com.example.mylibrary.ui.theme.FontSlot
import com.example.mylibrary.ui.theme.FontSource
import com.example.mylibrary.ui.theme.NavigationIconDefinition
import com.example.mylibrary.ui.theme.ResolvedSurface
import com.example.mylibrary.ui.theme.SystemAppFontResolver
import com.example.mylibrary.ui.theme.ThemeApplyResult
import com.example.mylibrary.ui.theme.ThemeFontManifest
import com.example.mylibrary.ui.theme.ThemeFontFileValidationResult
import com.example.mylibrary.ui.theme.ThemeFontFileValidator
import com.example.mylibrary.ui.theme.ThemeFontResolver
import com.example.mylibrary.ui.theme.ThemeManifest
import com.example.mylibrary.ui.theme.ThemeNavigationManifest
import com.example.mylibrary.ui.theme.ThemeResourceLimits
import com.example.mylibrary.ui.theme.ThemeSurfaceDefinition
import com.example.mylibrary.ui.theme.ThemeSurfaceType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePackageImportInstrumentedTest {
    private lateinit var root: File
    private val codec = ThemePackageJsonCodec()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        root = File(
            context.cacheDir,
            "theme-package-instrumented-${System.nanoTime()}"
        ).apply { check(mkdirs()) }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun contentUriSourceCopiesIntoPrivateTemporaryFileAndIsCleaned() {
        val archive = File(root, "uri-source.mylibrarytheme")
        writePackage(archive, minimalManifest("uri.test", "1"))
        val resolver = MockContentResolver()
        resolver.addProvider(
            AUTHORITY,
            FileContentProvider(archive)
        )
        val importer = importer(File(root, "uri-import"))

        val result = runBlocking {
            importer.import(
                ContentUriThemePackageSource(
                    resolver,
                    Uri.parse("content://$AUTHORITY/theme")
                )
            )
        }

        assertTrue(result is ThemePackageImportResult.Installed)
        assertTrue(
            File(root, "uri-import/cache").listFiles().orEmpty().isEmpty()
        )
    }

    @Test
    fun realPngNavigationAndDeviceTtfStrictlyInstallAndReload() {
        val font = findDeviceTtf()
        assumeTrue("No supported device TTF was available", font != null)
        val imageBytes = encodePng(192, 128, AndroidColor.CYAN)
        val iconBytes = encodePng(96, 96, AndroidColor.MAGENTA)
        val manifest = minimalManifest("resource.test", "1").copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                background = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FF010203",
                    file = "surfaces/background.png"
                )
            ),
            fonts = ThemeFontManifest("fonts/font_a.ttf", null),
            navigationIcons = ThemeNavigationManifest(
                rendering = ThemeIconRendering.ORIGINAL,
                home = NavigationIconDefinition(
                    normal = "icons/home.png",
                    selected = null
                )
            )
        )
        val archive = File(root, "resources.mylibrarytheme")
        writePackage(
            archive,
            manifest,
            mapOf(
                "surfaces/background.png" to imageBytes,
                "fonts/font_a.ttf" to requireNotNull(font).readBytes(),
                "icons/home.png" to iconBytes
            )
        )
        val work = File(root, "resource-import")
        val importer = importer(work)

        val installed = runBlocking {
            importer.import(StreamThemePackageSource { archive.inputStream() })
        }.installed()

        assertTrue(
            installed.resolvedTheme.surfaces.background is
                ResolvedSurface.ImageSurface
        )
        assertNotSame(
            SystemAppFontResolver,
            installed.resolvedTheme.fontResolver
        )
        assertTrue(
            installed.resolvedTheme.navigationIconResolver is
                ResolvedNavigationIconResolver
        )
        val reloaded = runBlocking {
            InstalledThemeLoader().load(installed.directory, 102L)
        }.success()
        assertEquals(installed.id, reloaded.id)
        assertEquals(installed.name, reloaded.name)
        assertEquals(installed.resolvedTheme.colors, reloaded.resolvedTheme.colors)
        assertEquals(
            installed.resolvedTheme.darkSystemBarIcons,
            reloaded.resolvedTheme.darkSystemBarIcons
        )

        val preferences = MemoryThemePreferenceStore()
        val repository = DefaultThemeRepository(
            preferenceStore = preferences,
            installedThemeCatalog = FileInstalledThemeCatalog(
                File(work, "files/themes/installed")
            )
        )
        val applied = runBlocking {
            repository.applyInstalledTheme(installed)
        }
        assertTrue(applied is ThemeApplyResult.Applied)
        assertEquals(installed.id, preferences.currentThemeId)
        assertTrue(
            repository.currentTheme.value.surfaces.background is
                ResolvedSurface.ImageSurface
        )
        assertNotSame(
            SystemAppFontResolver,
            repository.currentTheme.value.fontResolver
        )
        assertTrue(
            repository.currentTheme.value.navigationIconResolver is
                ResolvedNavigationIconResolver
        )
    }

    @Test
    fun realFontBOnlyFallbackStrictlyInstallsAndReloads() {
        val font = findDeviceTtf()
        assumeTrue("No supported device TTF was available", font != null)
        val manifest = minimalManifest("fontb.test", "1").copy(
            fonts = ThemeFontManifest(null, "fonts/font_b.ttf")
        )
        val archive = File(root, "fontb.mylibrarytheme")
        writePackage(
            archive,
            manifest,
            mapOf(
                "fonts/font_b.ttf" to requireNotNull(font).readBytes()
            )
        )
        val work = File(root, "fontb-import")
        val importer = importer(work)

        val installed = runBlocking {
            importer.import(StreamThemePackageSource { archive.inputStream() })
        }.installed()

        assertNotSame(
            SystemAppFontResolver,
            installed.resolvedTheme.fontResolver
        )
        val resolver = installed.resolvedTheme.fontResolver
            as ThemeFontResolver
        // BRAND/HEADING → slot A, fontA is null → system font
        assertEquals(
            FontSource.System,
            resolver.source(FontRole.BRAND)
        )
        assertEquals(
            FontSource.System,
            resolver.source(FontRole.HEADING)
        )
        // CONTENT/META → slot B, fontB exists → theme font file
        assertTrue(
            resolver.source(FontRole.CONTENT) is FontSource.ThemeFile
        )
        assertTrue(
            resolver.source(FontRole.META) is FontSource.ThemeFile
        )
        assertEquals(installed.id, reloaded.id)
        assertEquals(installed.name, reloaded.name)
        assertNotSame(
            SystemAppFontResolver,
            reloaded.resolvedTheme.fontResolver
        )

        val preferences = MemoryThemePreferenceStore()
        val repository = DefaultThemeRepository(
            preferenceStore = preferences,
            installedThemeCatalog = FileInstalledThemeCatalog(
                File(work, "files/themes/installed")
            )
        )
        val applied = runBlocking {
            repository.applyInstalledTheme(installed)
        }
        assertTrue(applied is ThemeApplyResult.Applied)
        assertEquals(installed.id, preferences.currentThemeId)
        assertNotSame(
            SystemAppFontResolver,
            repository.currentTheme.value.fontResolver
        )
    }

    @Test
    fun realDataStoreSelectionRestoresAndInvalidIdIsCleared() {
        val context =
            ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = DataStoreThemePreferenceStore(context)
        val work = File(root, "datastore-restore")
        val archive = File(root, "datastore.mylibrarytheme")
        writePackage(
            archive,
            minimalManifest("datastore.theme", "1")
        )
        val installed = runBlocking {
            preferences.clearCurrentThemeId()
            importer(work).import(
                StreamThemePackageSource { archive.inputStream() }
            )
        }.installed()
        val catalog = FileInstalledThemeCatalog(
            File(work, "files/themes/installed")
        )

        try {
            val applyingRepository = DefaultThemeRepository(
                preferenceStore = preferences,
                installedThemeCatalog = catalog
            )
            val applied = runBlocking {
                applyingRepository.applyInstalledTheme(installed.id)
            }
            assertTrue(applied is ThemeApplyResult.Applied)
            assertEquals(
                installed.id,
                runBlocking { preferences.readCurrentThemeId() }
            )

            val restoredRepository = DefaultThemeRepository(
                preferenceStore = preferences,
                installedThemeCatalog = catalog
            )
            assertEquals(
                DefaultResolvedTheme,
                restoredRepository.currentTheme.value
            )
            runBlocking { restoredRepository.restore() }
            assertEquals(
                installed.id,
                restoredRepository.currentThemeId.value
            )
            assertEquals(
                installed.id,
                restoredRepository.currentTheme.value.id
            )

            val switched = runBlocking {
                restoredRepository.applyDefaultTheme()
            }
            assertTrue(switched is ThemeApplyResult.Applied)
            assertEquals(
                DefaultResolvedTheme,
                restoredRepository.currentTheme.value
            )
            assertEquals(
                ThemeDeleteResult.Success,
                runBlocking { catalog.delete(installed.id) }
            )
            assertFalse(installed.directory.exists())

            runBlocking {
                preferences.setCurrentThemeId("missing.theme")
            }
            val failedRestoreRepository = DefaultThemeRepository(
                preferenceStore = preferences,
                installedThemeCatalog = catalog
            )
            runBlocking { failedRestoreRepository.restore() }
            assertEquals(
                DefaultResolvedTheme,
                failedRestoreRepository.currentTheme.value
            )
            assertEquals(
                null,
                runBlocking { preferences.readCurrentThemeId() }
            )
            assertTrue(
                failedRestoreRepository.lastRestoreError.value != null
            )
        } finally {
            runBlocking { preferences.clearCurrentThemeId() }
        }
    }

    @Test
    fun sameIdReplacementSucceedsAndInvalidUpdateKeepsCurrentFiles() {
        val work = File(root, "replace-import")
        val importer = importer(work)
        val firstArchive = File(root, "replace-1.mylibrarytheme")
        val secondArchive = File(root, "replace-2.mylibrarytheme")
        val invalidArchive = File(root, "replace-invalid.mylibrarytheme")
        writePackage(firstArchive, minimalManifest("replace.test", "1"))
        writePackage(secondArchive, minimalManifest("replace.test", "2"))
        val invalidManifest = minimalManifest("replace.test", "3").copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                background = ThemeSurfaceDefinition(
                    ThemeSurfaceType.IMAGE,
                    "#FF000000",
                    "surfaces/background.png"
                )
            )
        )
        writePackage(
            invalidArchive,
            invalidManifest,
            mapOf(
                "surfaces/background.png" to
                    "not an image despite its extension".toByteArray()
            )
        )

        val first = runBlocking {
            importer.import(
                StreamThemePackageSource { firstArchive.inputStream() }
            )
        }.installed()
        assertEquals("1", first.version)
        val repository = DefaultThemeRepository(
            preferenceStore = MemoryThemePreferenceStore(),
            installedThemeCatalog = FileInstalledThemeCatalog(
                File(work, "files/themes/installed")
            )
        )
        assertTrue(
            runBlocking {
                repository.applyInstalledTheme(first)
            } is ThemeApplyResult.Applied
        )
        val firstGeneration =
            repository.currentTheme.value.themeGeneration

        val second = runBlocking {
            importer.import(
                StreamThemePackageSource { secondArchive.inputStream() }
            )
        }.installed()
        assertEquals("2", second.version)
        assertTrue(
            runBlocking {
                repository.applyInstalledTheme(second)
            } is ThemeApplyResult.Applied
        )
        assertTrue(
            repository.currentTheme.value.themeGeneration !=
                firstGeneration
        )

        val failed = runBlocking {
            importer.import(
                StreamThemePackageSource { invalidArchive.inputStream() }
            )
        }
        assertTrue(failed is ThemePackageImportResult.Failure)
        val stillInstalled = runBlocking {
            InstalledThemeLoader().load(second.directory, 203L)
        }.success()
        assertEquals("2", stillInstalled.version)
    }

    @Test
    fun restartRecoveryClearsStagingRestoresRollbackAndPreservesFormal() {
        val storage = File(root, "recovery/themes")
        val installer = ThemeInstaller(storage)
        val staleStaging = installer.prepareStagingDirectory().success()
        File(staleStaging, "partial").writeText("partial")
        File(installer.rollbackDirectory, "restore.test").apply {
            mkdirs()
            File(this, "marker").writeText("restored")
        }
        File(installer.installedDirectory, "keep.test").apply {
            mkdirs()
            File(this, "marker").writeText("current")
        }
        File(installer.rollbackDirectory, "keep.test").apply {
            mkdirs()
            File(this, "marker").writeText("stale")
        }

        val report = installer.recoverInterruptedOperations().success()

        assertEquals(1, report.stagingEntriesRemoved)
        assertEquals(1, report.rollbackThemesRestored)
        assertEquals(1, report.staleRollbacksRemoved)
        assertFalse(staleStaging.exists())
        assertEquals(
            "restored",
            File(
                installer.installedDirectory,
                "restore.test/marker"
            ).readText()
        )
        assertEquals(
            "current",
            File(
                installer.installedDirectory,
                "keep.test/marker"
            ).readText()
        )
    }

    @Test
    fun postMoveValidationFailureRestoresOldFilesOnDeviceStorage() {
        val storage = File(root, "post-move/themes")
        val failure = ThemePackageError.InstallFailed(
            "simulated final installed-directory failure"
        )
        val installer = ThemeInstaller(
            storage,
            InstalledThemeDirectoryLoader { _, _ ->
                ThemePackageResult.Failure(failure)
            }
        )
        val target = File(
            installer.installedDirectory,
            "device.rollback"
        ).apply {
            mkdirs()
            File(this, "marker").writeText("old")
        }
        val staging = installer.prepareStagingDirectory().success().apply {
            File(this, "marker").writeText("new")
        }

        val result = runBlocking {
            installer.install(staging, "device.rollback", 1L)
        }

        assertTrue(result is ThemePackageResult.Failure)
        assertEquals("old", File(target, "marker").readText())
        assertFalse(
            File(installer.rollbackDirectory, "device.rollback").exists()
        )
    }

    private fun importer(work: File): ThemePackageImporter =
        ThemePackageImporter(
            temporaryRoot = File(work, "cache"),
            installer = ThemeInstaller(File(work, "files/themes")),
            generationSource = { System.nanoTime() }
        )

    private fun minimalManifest(id: String, version: String): ThemeManifest =
        DefaultThemeManifest.copy(
            id = id,
            name = "Instrumented Package",
            version = version
        )

    private fun writePackage(
        archive: File,
        manifest: ThemeManifest,
        resources: Map<String, ByteArray> = emptyMap()
    ) {
        val ordinary = linkedMapOf(
            "manifest.json" to
                codec.encodeManifest(manifest).toByteArray(Charsets.UTF_8)
        )
        ordinary.putAll(resources)
        val checksums = ThemeChecksumManifest(
            algorithm = "SHA-256",
            files = ordinary.mapValues { sha256(it.value) }
        )
        ZipOutputStream(archive.outputStream()).use { zip ->
            ordinary.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("checksums.json"))
            zip.write(codec.encodeChecksums(checksums).toByteArray())
            zip.closeEntry()
        }
    }

    private fun encodePng(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
    }

    private fun findDeviceTtf(): File? {
        val candidates = File("/system/fonts")
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter {
                it.isFile &&
                    it.extension.equals("ttf", ignoreCase = true) &&
                    it.length() in
                    ThemeResourceLimits.MIN_FONT_FILE_BYTES..
                    ThemeResourceLimits.MAX_SINGLE_FONT_FILE_BYTES
            }
            .filter(::hasTrueTypeSignature)
            .toList()
        val probeRoot = File(root, "font-probe")
        val destination = File(probeRoot, "fonts/font_a.ttf")
        for (candidate in candidates) {
            destination.parentFile?.mkdirs()
            candidate.copyTo(destination, overwrite = true)
            val validation = ThemeFontFileValidator.validateDeclaredFiles(
                fonts = ThemeFontManifest("fonts/font_a.ttf", null),
                resources = DirectoryThemeResourceProvider(probeRoot)
            )
            if (validation is ThemeFontFileValidationResult.Success) {
                return candidate
            }
        }
        return null
    }

    private fun hasTrueTypeSignature(file: File): Boolean =
        runCatching {
            FileInputStream(file).use { input ->
                val bytes = ByteArray(4)
                input.read(bytes) == 4 &&
                    (
                        bytes.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                            bytes.contentEquals(
                                byteArrayOf(0x74, 0x72, 0x75, 0x65)
                            )
                        )
            }
        }.getOrDefault(false)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun ThemePackageImportResult.installed(): InstalledTheme {
        assertTrue(
            "Expected install success, got " +
                (this as? ThemePackageImportResult.Failure)?.error,
            this is ThemePackageImportResult.Installed
        )
        return (this as ThemePackageImportResult.Installed).theme
    }

    private fun <T> ThemePackageResult<T>.success(): T {
        assertTrue(
            "Expected success, got ${(this as? ThemePackageResult.Failure)?.error}",
            this is ThemePackageResult.Success
        )
        return (this as ThemePackageResult.Success).value
    }

    private class MemoryThemePreferenceStore : ThemePreferenceStore {
        var currentThemeId: String? = null

        override suspend fun readCurrentThemeId(): String? = currentThemeId

        override suspend fun setCurrentThemeId(themeId: String) {
            currentThemeId = themeId
        }

        override suspend fun clearCurrentThemeId() {
            currentThemeId = null
        }
    }

    private class FileContentProvider(
        private val file: File
    ) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun openFile(
            uri: Uri,
            mode: String
        ): ParcelFileDescriptor =
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

        override fun getType(uri: Uri): String =
            "application/octet-stream"

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private companion object {
        const val AUTHORITY = "com.example.mylibrary.theme.test"
    }
}
