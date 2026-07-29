package com.example.mylibrary.ui.theme

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun repositoryImmediatelyExposesCompiledDefaultTheme() {
        val repository: ThemeRepository = DefaultThemeRepository()

        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
        assertNull(repository.lastRestoreError.value)
    }

    @Test
    fun startupRestoreFailureKeepsImmediateDefaultAndRecordsReason() = runBlocking {
        val resourceRoot = File(temporaryFolder.root, "resources").apply { mkdirs() }
        val repository: ThemeRepository = DefaultThemeRepository(
            restoreRequest = ThemeRestoreRequest(
                manifest = DefaultThemeManifest.copy(schemaVersion = 2),
                resources = DirectoryThemeResourceProvider(resourceRoot),
                themeGeneration = 3L
            )
        )

        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
        repository.restore()

        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
        assertTrue(repository.lastRestoreError.value is ThemeResolveError.ManifestInvalid)
    }

    @Test
    fun applyDefaultClearsPreviousRestoreFailure() = runBlocking {
        val resourceRoot = File(temporaryFolder.root, "resources").apply { mkdirs() }
        val repository: ThemeRepository = DefaultThemeRepository(
            restoreRequest = ThemeRestoreRequest(
                manifest = DefaultThemeManifest.copy(schemaVersion = 2),
                resources = DirectoryThemeResourceProvider(resourceRoot),
                themeGeneration = 4L
            )
        )

        repository.restore()
        assertTrue(repository.lastRestoreError.value is ThemeResolveError.ManifestInvalid)

        repository.applyDefaultTheme()
        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
        assertNull(repository.lastRestoreError.value)
    }

    @Test
    fun startupImageFailureKeepsDefaultAndRecordsStructuredCause() = runBlocking {
        val resourceRoot = File(temporaryFolder.root, "resources").apply { mkdirs() }
        val manifest = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                card = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FFFFFFFF",
                    file = "surfaces/card/missing.png"
                )
            )
        )
        val repository: ThemeRepository = DefaultThemeRepository(
            restoreRequest = ThemeRestoreRequest(
                manifest = manifest,
                resources = DirectoryThemeResourceProvider(resourceRoot),
                themeGeneration = 5L
            )
        )

        repository.restore()

        assertSame(DefaultResolvedTheme, repository.currentTheme.value)
        assertTrue(repository.lastRestoreError.value is ThemeResolveError.ImageMissing)
    }
}
