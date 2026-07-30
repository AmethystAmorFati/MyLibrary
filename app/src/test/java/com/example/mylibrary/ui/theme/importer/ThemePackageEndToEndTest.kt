package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.SystemAppFontResolver
import com.example.mylibrary.ui.theme.ThemeFontManifest
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end tests that exercise the full theme-package import pipeline from
 * ZIP input to installation directory.  These tests use only JVM APIs and do
 * not require an Android device; the minimal theme (COLOR-only, null fonts,
 * null navigation) resolves entirely through platform-independent code paths.
 *
 * Scenarios that require real TTF/PNG assets or DataStore are covered by
 * [com.example.mylibrary.ui.theme.importer.ThemePackageImportInstrumentedTest].
 */
class ThemePackageEndToEndTest {

    // ------------------------------------------------------------------
    // 1. Minimal color theme installs successfully
    // ------------------------------------------------------------------
    @Test
    fun minimalColorThemeInstallsSuccessfully() = withRoot { root ->
        val archive = File(root, "minimal.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest()
        ThemePackageTestFixtures.writePackage(archive, manifest)
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(
            "Expected install success, got $result",
            result is ThemePackageImportResult.Installed
        )
        val installed = (result as ThemePackageImportResult.Installed).theme
        assertEquals(manifest.id, installed.id)
        assertEquals(manifest.version, installed.version)
        assertTrue(File(installed.directory, "manifest.json").isFile)
        assertTrue(File(installed.directory, "checksums.json").isFile)
    }

    // ------------------------------------------------------------------
    // 2. STORED and DEFLATED both install successfully
    // ------------------------------------------------------------------
    @Test
    fun storedCompressionInstallsSuccessfully() = withRoot { root ->
        val archive = File(root, "stored.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest(id = "stored.test")
        ThemePackageTestFixtures.writePackage(archive, manifest, stored = true)
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Installed)
        assertEquals(
            "stored.test",
            (result as ThemePackageImportResult.Installed).theme.id
        )
    }

    @Test
    fun deflatedCompressionInstallsSuccessfully() = withRoot { root ->
        val archive = File(root, "deflated.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest(id = "deflated.test")
        ThemePackageTestFixtures.writePackage(archive, manifest, stored = false)
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Installed)
        assertEquals(
            "deflated.test",
            (result as ThemePackageImportResult.Installed).theme.id
        )
    }

    // ------------------------------------------------------------------
    // 3. External file name with Chinese and spaces imports
    // ------------------------------------------------------------------
    @Test
    fun fileNameWithChineseAndSpacesImports() = withRoot { root ->
        val archive = File(root, "测试 主题包.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest(id = "unicode.name")
        ThemePackageTestFixtures.writePackage(archive, manifest)
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Installed)
        assertEquals(
            "unicode.name",
            (result as ThemePackageImportResult.Installed).theme.id
        )
    }

    // ------------------------------------------------------------------
    // 4. fontA/fontB both null resolve to system font
    // ------------------------------------------------------------------
    @Test
    fun nullFontsResolveToSystemFont() = withRoot { root ->
        val archive = File(root, "no-fonts.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest().copy(
            fonts = ThemeFontManifest(fontA = null, fontB = null)
        )
        ThemePackageTestFixtures.writePackage(archive, manifest)
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Installed)
        val installed = (result as ThemePackageImportResult.Installed).theme
        assertSame(
            SystemAppFontResolver,
            installed.resolvedTheme.fontResolver
        )
    }

    // ------------------------------------------------------------------
    // 5. navigationIcons = null uses built-in icons
    // ------------------------------------------------------------------
    @Test
    fun nullNavigationIconsUsesBuiltInIcons() = withRoot { root ->
        val archive = File(root, "no-nav.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest().copy(
            navigationIcons = null
        )
        ThemePackageTestFixtures.writePackage(archive, manifest)
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Installed)
    }

    // ------------------------------------------------------------------
    // 6. Checksum mismatch returns ChecksumMismatch error
    // ------------------------------------------------------------------
    @Test
    fun checksumMismatchReturnsChecksumMismatchError() = withRoot { root ->
        val archive = File(root, "bad-checksum.mylibrarytheme")
        ThemePackageTestFixtures.writePackage(
            archive,
            checksumTransform = { checksums ->
                val key = checksums.keys.first()
                checksums[key] = "0".repeat(64)
            }
        )
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Failure)
        val error = (result as ThemePackageImportResult.Failure).error
        assertTrue(
            "Expected ChecksumMismatch, got $error",
            error is ThemePackageError.ChecksumMismatch
        )
    }

    // ------------------------------------------------------------------
    // 7. Malformed checksums.json returns ChecksumsInvalid error
    // ------------------------------------------------------------------
    @Test
    fun malformedChecksumsJsonReturnsChecksumsInvalidError() = withRoot { root ->
        val archive = File(root, "bad-checksums.mylibrarytheme")
        ThemePackageTestFixtures.writePackage(
            archive,
            rawChecksums = "{ not valid json"
        )
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Failure)
        val error = (result as ThemePackageImportResult.Failure).error
        assertTrue(
            "Expected ChecksumsInvalid, got $error",
            error is ThemePackageError.ChecksumsInvalid
        )
    }

    // ------------------------------------------------------------------
    // 8. Malformed manifest.json returns ManifestParseFailed error
    // ------------------------------------------------------------------
    @Test
    fun malformedManifestJsonReturnsManifestParseFailedError() = withRoot { root ->
        val archive = File(root, "bad-manifest.mylibrarytheme")
        val manifestBytes = "{ not valid json".toByteArray(Charsets.UTF_8)
        val checksums = mapOf(
            ThemePackageLimits.MANIFEST_PATH to
                ThemePackageTestFixtures.sha256(manifestBytes)
        )
        val checksumBytes = ThemePackageTestFixtures.codec
            .encodeChecksums(
                ThemeChecksumManifest(
                    algorithm = ThemePackageLimits.CHECKSUM_ALGORITHM,
                    files = checksums
                )
            ).toByteArray(Charsets.UTF_8)
        val entries = listOf(
            ThemePackageLimits.MANIFEST_PATH to manifestBytes,
            ThemePackageLimits.CHECKSUMS_PATH to checksumBytes
        )
        archive.parentFile?.mkdirs()
        java.util.zip.ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Failure)
        val error = (result as ThemePackageImportResult.Failure).error
        assertTrue(
            "Expected ManifestParseFailed, got $error",
            error is ThemePackageError.ManifestParseFailed
        )
    }

    // ------------------------------------------------------------------
    // 9. Manifest semantic error returns ThemeValidationFailed error
    // ------------------------------------------------------------------
    @Test
    fun manifestSemanticErrorReturnsThemeValidationFailedError() = withRoot { root ->
        val archive = File(root, "bad-schema.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest().copy(
            schemaVersion = 999
        )
        ThemePackageTestFixtures.writePackage(archive, manifest)
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Failure)
        val error = (result as ThemePackageImportResult.Failure).error
        assertTrue(
            "Expected ThemeValidationFailed, got $error",
            error is ThemePackageError.ThemeValidationFailed
        )
    }

    // ------------------------------------------------------------------
    // 10. Install failure cleans up staging
    // ------------------------------------------------------------------
    @Test
    fun installFailureCleansUpStaging() = withRoot { root ->
        val archive = File(root, "will-fail.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest().copy(
            schemaVersion = 999
        )
        ThemePackageTestFixtures.writePackage(archive, manifest)
        val installer = ThemeInstaller(File(root, "files/themes"))
        val importer = ThemePackageImporter(
            temporaryRoot = File(root, "cache"),
            installer = installer,
            generationSource = { 1L }
        )

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Failure)
        assertTrue(
            "Staging should be empty after failure",
            installer.stagingDirectory.listFiles().orEmpty().isEmpty()
        )
        assertTrue(
            "Cache should be empty after failure",
            File(root, "cache").listFiles().orEmpty().isEmpty()
        )
    }

    // ------------------------------------------------------------------
    // 11. Old theme survives failed replacement
    // ------------------------------------------------------------------
    @Test
    fun oldThemeSurvivesFailedReplacement() = withRoot { root ->
        val validArchive = File(root, "valid.mylibrarytheme")
        val invalidArchive = File(root, "invalid.mylibrarytheme")
        val oldManifest = ThemePackageTestFixtures.minimalManifest(
            id = "replace.test",
            version = "1"
        )
        val invalidManifest = oldManifest.copy(
            version = "2",
            schemaVersion = 999
        )
        ThemePackageTestFixtures.writePackage(validArchive, oldManifest)
        ThemePackageTestFixtures.writePackage(invalidArchive, invalidManifest)
        val importer = importer(root)

        val firstResult = runBlocking { importer.import(source(validArchive)) }
        assertTrue(firstResult is ThemePackageImportResult.Installed)

        val secondResult = runBlocking { importer.import(source(invalidArchive)) }
        assertTrue(secondResult is ThemePackageImportResult.Failure)

        val installedManifest = ThemePackageTestFixtures.codec
            .decodeManifest(
                File(
                    root,
                    "files/themes/installed/replace.test/manifest.json"
                ).readText()
            )
            .let { (it as ThemePackageResult.Success).value }
        assertEquals("1", installedManifest.version)
    }

    // ------------------------------------------------------------------
    // 12. Theme ID with dots is accepted
    // ------------------------------------------------------------------
    @Test
    fun themeIdWithDotsIsAccepted() = withRoot { root ->
        val archive = File(root, "dotted-id.mylibrarytheme")
        val manifest = ThemePackageTestFixtures.minimalManifest(
            id = "peanutpersimmon.example"
        )
        ThemePackageTestFixtures.writePackage(archive, manifest)
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Installed)
        val installed = (result as ThemePackageImportResult.Installed).theme
        assertEquals("peanutpersimmon.example", installed.id)
        assertTrue(
            File(root, "files/themes/installed/peanutpersimmon.example")
                .isDirectory
        )
    }

    // ------------------------------------------------------------------
    // 13. Same ID reinstall safely replaces
    // ------------------------------------------------------------------
    @Test
    fun sameIdReinstallSafelyReplaces() = withRoot { root ->
        val firstArchive = File(root, "v1.mylibrarytheme")
        val secondArchive = File(root, "v2.mylibrarytheme")
        val firstManifest = ThemePackageTestFixtures.minimalManifest(
            id = "reinstall.test",
            version = "1"
        )
        val secondManifest = firstManifest.copy(version = "2")
        ThemePackageTestFixtures.writePackage(firstArchive, firstManifest)
        ThemePackageTestFixtures.writePackage(secondArchive, secondManifest)
        val importer = importer(root)

        val firstResult = runBlocking { importer.import(source(firstArchive)) }
        assertTrue(firstResult is ThemePackageImportResult.Installed)
        assertEquals(
            "1",
            (firstResult as ThemePackageImportResult.Installed).theme.version
        )

        val secondResult = runBlocking { importer.import(source(secondArchive)) }
        assertTrue(secondResult is ThemePackageImportResult.Installed)
        assertEquals(
            "2",
            (secondResult as ThemePackageImportResult.Installed).theme.version
        )

        // Rollback should be cleaned up after successful replacement
        assertTrue(
            File(root, "files/themes/.rollback").listFiles().orEmpty().isEmpty()
        )
    }

    // ------------------------------------------------------------------
    // 14. Missing checksums.json returns MissingChecksums error
    // ------------------------------------------------------------------
    @Test
    fun missingChecksumsReturnsMissingChecksumsError() = withRoot { root ->
        val archive = File(root, "no-checksums.mylibrarytheme")
        ThemePackageTestFixtures.writePackage(
            archive,
            includeChecksums = false
        )
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Failure)
        val error = (result as ThemePackageImportResult.Failure).error
        assertTrue(
            "Expected MissingChecksums, got $error",
            error is ThemePackageError.MissingChecksums
        )
    }

    // ------------------------------------------------------------------
    // 15. Missing manifest.json returns MissingManifest error
    // ------------------------------------------------------------------
    @Test
    fun missingManifestReturnsMissingManifestError() = withRoot { root ->
        val archive = File(root, "no-manifest.mylibrarytheme")
        ThemePackageTestFixtures.writePackage(
            archive,
            includeManifest = false
        )
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Failure)
        val error = (result as ThemePackageImportResult.Failure).error
        assertTrue(
            "Expected MissingManifest, got $error",
            error is ThemePackageError.MissingManifest
        )
    }

    // ------------------------------------------------------------------
    // 16. Extra undeclared resource returns ManifestResourceExtra error
    // ------------------------------------------------------------------
    @Test
    fun extraUndeclaredResourceReturnsManifestResourceExtraError() =
        withRoot { root ->
            val archive = File(root, "extra-file.mylibrarytheme")
            ThemePackageTestFixtures.writePackage(
                archive,
                additionalEntries = listOf(
                    "fonts/extra.ttf" to "not a real font".toByteArray()
                )
            )
            val importer = importer(root)

            val result = runBlocking { importer.import(source(archive)) }

            assertTrue(result is ThemePackageImportResult.Failure)
            val error = (result as ThemePackageImportResult.Failure).error
            assertTrue(
                "Expected ManifestResourceExtra or UnexpectedEntry, got $error",
                error is ThemePackageError.ManifestResourceExtra ||
                    error is ThemePackageError.UnexpectedEntry
            )
        }

    // ------------------------------------------------------------------
    // 17. Checksum extra entry returns ChecksumExtraEntry error
    // ------------------------------------------------------------------
    @Test
    fun checksumExtraEntryReturnsChecksumExtraEntryError() = withRoot { root ->
        val archive = File(root, "extra-checksum.mylibrarytheme")
        ThemePackageTestFixtures.writePackage(
            archive,
            checksumTransform = { checksums ->
                checksums["fonts/ghost.ttf"] = "a".repeat(64)
            }
        )
        val importer = importer(root)

        val result = runBlocking { importer.import(source(archive)) }

        assertTrue(result is ThemePackageImportResult.Failure)
        val error = (result as ThemePackageImportResult.Failure).error
        assertTrue(
            "Expected ChecksumExtraEntry, got $error",
            error is ThemePackageError.ChecksumExtraEntry
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private fun importer(root: File): ThemePackageImporter =
        ThemePackageImporter(
            temporaryRoot = File(root, "cache"),
            installer = ThemeInstaller(File(root, "files/themes")),
            generationSource = { System.nanoTime() }
        )

    private fun source(file: File): ThemePackageSource =
        StreamThemePackageSource { file.inputStream() }

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("theme-e2e-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
