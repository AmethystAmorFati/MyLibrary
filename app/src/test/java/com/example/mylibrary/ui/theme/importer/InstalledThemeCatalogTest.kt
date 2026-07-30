package com.example.mylibrary.ui.theme.importer

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledThemeCatalogTest {
    @Test
    fun currentThemeSortsFirstThenNameAndIdStably() = withRoot { root ->
        val importer = importer(root)
        install(importer, root, "z.theme", "Beta")
        install(importer, root, "b.theme", "Alpha")
        install(importer, root, "a.theme", "Alpha")
        val catalog = catalog(root)

        val result = runBlocking {
            catalog.listInstalledThemes("z.theme")
        }

        assertEquals(
            listOf("z.theme", "a.theme", "b.theme"),
            result.map { it.id }
        )
        assertTrue(result.all { it.status == InstalledThemeStatus.VALID })
    }

    @Test
    fun damagedThemeDoesNotHideOtherInstalledThemes() = withRoot { root ->
        val importer = importer(root)
        install(importer, root, "valid.theme", "Valid")
        val broken = File(
            root,
            "files/themes/installed/broken.theme"
        ).apply { mkdirs() }
        File(broken, "manifest.json").writeText("{not-json")
        val catalog = catalog(root)

        val result = runBlocking {
            catalog.listInstalledThemes()
        }

        assertEquals(2, result.size)
        assertEquals(
            InstalledThemeStatus.INVALID,
            result.single { it.id == "broken.theme" }.status
        )
        assertEquals(
            InstalledThemeStatus.VALID,
            result.single { it.id == "valid.theme" }.status
        )
    }

    @Test
    fun nonCurrentInstalledThemeCanBeDeletedAndDisappears() =
        withRoot { root ->
            val importer = importer(root)
            install(importer, root, "delete.theme", "Delete")
            val catalog = catalog(root)

            val deleted = runBlocking {
                catalog.delete("delete.theme")
            }
            val remaining = runBlocking {
                catalog.listInstalledThemes()
            }

            assertEquals(ThemeDeleteResult.Success, deleted)
            assertFalse(
                File(
                    root,
                    "files/themes/installed/delete.theme"
                ).exists()
            )
            assertTrue(remaining.isEmpty())
        }

    private fun install(
        importer: ThemePackageImporter,
        root: File,
        id: String,
        name: String
    ) {
        val archive = File(root, "$id.mylibrarytheme")
        ThemePackageTestFixtures.writePackage(
            archive = archive,
            manifest = ThemePackageTestFixtures.minimalManifest(id)
                .copy(name = name)
        )
        val result = runBlocking {
            importer.import(
                StreamThemePackageSource { archive.inputStream() }
            )
        }
        assertTrue(result is ThemePackageImportResult.Installed)
    }

    private fun importer(root: File): ThemePackageImporter =
        ThemePackageImporter(
            temporaryRoot = File(root, "cache"),
            installer = ThemeInstaller(
                File(root, "files/themes")
            ),
            generationSource = { 1L }
        )

    private fun catalog(root: File): InstalledThemeCatalog =
        FileInstalledThemeCatalog(
            rootDirectory = File(root, "files/themes/installed"),
            ioDispatcher = Dispatchers.Unconfined
        )

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory(
            "installed-theme-catalog"
        ).toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
