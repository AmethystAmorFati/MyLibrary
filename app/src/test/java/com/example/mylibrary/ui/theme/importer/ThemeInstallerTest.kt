package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.ThemeResolveError
import com.example.mylibrary.ui.theme.ThemeSurfaceDefinition
import com.example.mylibrary.ui.theme.ThemeSurfaceType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeInstallerTest {
    @Test
    fun minimalPackageImportsAndInstallsWithoutApplyingRepositoryState() =
        withRoot { root ->
            val archive = File(root, "minimal.mylibrarytheme")
            val manifest = ThemePackageTestFixtures.minimalManifest()
            ThemePackageTestFixtures.writePackage(archive, manifest)
            val importer = importer(root)

            val result = runBlocking {
                importer.import(source(archive))
            }

            assertTrue(result is ThemePackageImportResult.Installed)
            val installed = (result as ThemePackageImportResult.Installed).theme
            assertEquals(manifest.id, installed.id)
            assertEquals(manifest.version, installed.version)
            assertEquals(manifest.id, installed.resolvedTheme.id)
            assertTrue(File(installed.directory, "manifest.json").isFile)
            assertTrue(File(installed.directory, "checksums.json").isFile)
            assertTrue(
                File(root, "cache").listFiles().orEmpty().isEmpty()
            )
        }

    @Test
    fun sameIdUpdateAtomicallyReplacesTheOldVersion() = withRoot { root ->
        val first = File(root, "first.mylibrarytheme")
        val second = File(root, "second.mylibrarytheme")
        val firstManifest = ThemePackageTestFixtures.minimalManifest(
            id = "update.test",
            version = "1"
        )
        val secondManifest = firstManifest.copy(version = "2")
        ThemePackageTestFixtures.writePackage(first, firstManifest)
        ThemePackageTestFixtures.writePackage(second, secondManifest)
        val importer = importer(root)

        val firstResult = runBlocking { importer.import(source(first)) }
        val secondResult = runBlocking { importer.import(source(second)) }

        assertTrue(firstResult is ThemePackageImportResult.Installed)
        assertTrue(secondResult is ThemePackageImportResult.Installed)
        val installed =
            (secondResult as ThemePackageImportResult.Installed).theme
        assertEquals("2", installed.version)
        assertTrue(
            ThemePackageTestFixtures.codec
                .decodeManifest(
                    File(installed.directory, "manifest.json").readText()
                )
                .success()
                .version == "2"
        )
        assertTrue(
            File(root, "files/themes/.rollback")
                .listFiles()
                .orEmpty()
                .isEmpty()
        )
    }

    @Test
    fun strictResolutionFailureDoesNotReplaceAnInstalledTheme() =
        withRoot { root ->
            val first = File(root, "valid.mylibrarytheme")
            val invalid = File(root, "invalid.mylibrarytheme")
            val oldManifest = ThemePackageTestFixtures.minimalManifest(
                id = "preserve.test",
                version = "1"
            )
            ThemePackageTestFixtures.writePackage(first, oldManifest)
            val invalidManifest = oldManifest.copy(
                version = "2",
                surfaces = oldManifest.surfaces.copy(
                    background = ThemeSurfaceDefinition(
                        type = ThemeSurfaceType.IMAGE,
                        color = "#FF000000",
                        file = "surfaces/background.png"
                    )
                )
            )
            ThemePackageTestFixtures.writePackage(
                invalid,
                invalidManifest,
                resources = mapOf(
                    "surfaces/background.png" to
                        "not a real image payload".toByteArray()
                )
            )
            val importer = importer(root)
            assertTrue(
                runBlocking { importer.import(source(first)) } is
                    ThemePackageImportResult.Installed
            )

            val failed = runBlocking { importer.import(source(invalid)) }

            assertTrue(failed is ThemePackageImportResult.Failure)
            assertTrue(
                (failed as ThemePackageImportResult.Failure).error is
                    ThemePackageError.ThemeResolutionFailed
            )
            val installedManifest = ThemePackageTestFixtures.codec
                .decodeManifest(
                    File(
                        root,
                        "files/themes/installed/preserve.test/manifest.json"
                    ).readText()
                )
                .success()
            assertEquals("1", installedManifest.version)
        }

    @Test
    fun postMoveValidationFailureRestoresTheOldDirectory() = withRoot { root ->
        val storage = File(root, "themes")
        val failure = ThemePackageError.ThemeResolutionFailed(
            ThemeResolveError.UnexpectedFailure("simulated final validation")
        )
        val installer = ThemeInstaller(
            storage,
            InstalledThemeDirectoryLoader { _, _ ->
                ThemePackageResult.Failure(failure)
            }
        )
        val old = File(storage, "installed/rollback.test").apply {
            mkdirs()
            File(this, "marker").writeText("old")
        }
        val staging = installer.prepareStagingDirectory().success().apply {
            File(this, "marker").writeText("new")
        }

        val result = runBlocking {
            installer.install(staging, "rollback.test", 1L)
        }

        assertTrue(result is ThemePackageResult.Failure)
        assertEquals("old", File(old, "marker").readText())
        assertFalse(File(storage, ".rollback/rollback.test").exists())
    }

    @Test
    fun staleStagingIsClearedWithoutTouchingInstalledThemes() =
        withRoot { root ->
            val installer = ThemeInstaller(File(root, "themes"))
            installer.prepareStagingDirectory().success().apply {
                File(this, "partial").writeText("partial")
            }
            val installed = File(
                root,
                "themes/installed/keep.test/marker"
            ).apply {
                parentFile?.mkdirs()
                writeText("keep")
            }

            val report = installer.recoverInterruptedOperations().success()

            assertEquals(1, report.stagingEntriesRemoved)
            assertEquals("keep", installed.readText())
            assertTrue(
                installer.stagingDirectory.listFiles().orEmpty().isEmpty()
            )
        }

    @Test
    fun rollbackIsRestoredWhenFormalDirectoryIsMissing() = withRoot { root ->
        val installer = ThemeInstaller(File(root, "themes"))
        installer.prepareStagingDirectory().success()
        val rollback = File(
            installer.rollbackDirectory,
            "recover.test"
        ).apply {
            mkdirs()
            File(this, "marker").writeText("old")
        }

        val report = installer.recoverInterruptedOperations().success()

        assertEquals(1, report.rollbackThemesRestored)
        assertFalse(rollback.exists())
        assertEquals(
            "old",
            File(
                installer.installedDirectory,
                "recover.test/marker"
            ).readText()
        )
    }

    @Test
    fun existingFormalDirectoryWinsOverStaleRollback() = withRoot { root ->
        val installer = ThemeInstaller(File(root, "themes"))
        installer.prepareStagingDirectory().success()
        val installed = File(
            installer.installedDirectory,
            "recover.test"
        ).apply {
            mkdirs()
            File(this, "marker").writeText("new")
        }
        val rollback = File(
            installer.rollbackDirectory,
            "recover.test"
        ).apply {
            mkdirs()
            File(this, "marker").writeText("old")
        }

        val report = installer.recoverInterruptedOperations().success()

        assertEquals(1, report.staleRollbacksRemoved)
        assertEquals("new", File(installed, "marker").readText())
        assertFalse(rollback.exists())
    }

    @Test
    fun ambiguousFormalTargetIsNeverOverwrittenByRollback() =
        withRoot { root ->
            val installer = ThemeInstaller(File(root, "themes"))
            installer.prepareStagingDirectory().success()
            val installed = File(
                installer.installedDirectory,
                "recover.test"
            ).apply {
                parentFile?.mkdirs()
                writeText("ambiguous")
            }
            val rollback = File(
                installer.rollbackDirectory,
                "recover.test"
            ).apply {
                mkdirs()
                File(this, "marker").writeText("old")
            }

            val result = installer.recoverInterruptedOperations()

            assertTrue(result is ThemePackageResult.Failure)
            assertTrue(
                (result as ThemePackageResult.Failure).error is
                    ThemePackageError.RecoveryFailed
            )
            assertEquals("ambiguous", installed.readText())
            assertTrue(rollback.exists())
        }

    private fun importer(root: File): ThemePackageImporter =
        ThemePackageImporter(
            temporaryRoot = File(root, "cache"),
            installer = ThemeInstaller(File(root, "files/themes")),
            generationSource = { 42L }
        )

    private fun source(file: File): ThemePackageSource =
        StreamThemePackageSource { file.inputStream() }

    private fun <T> ThemePackageResult<T>.success(): T {
        assertTrue(
            "Expected success, got ${(this as? ThemePackageResult.Failure)?.error}",
            this is ThemePackageResult.Success
        )
        return (this as ThemePackageResult.Success).value
    }

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("theme-installer-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
