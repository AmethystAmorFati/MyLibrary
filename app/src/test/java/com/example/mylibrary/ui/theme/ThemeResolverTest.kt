package com.example.mylibrary.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.mylibrary.ui.navigation.DefaultNavigationIconResolver
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultManifestStrictlyResolvesToRuntimeTheme() {
        val resolved = resolveStrict(DefaultThemeManifest)

        assertEquals(DefaultResolvedTheme, resolved)
        assertSame(SystemAppFontResolver, resolved.fontResolver)
        assertSame(DefaultNavigationIconResolver, resolved.navigationIconResolver)
    }

    @Test
    fun allThreeSurfacesMapToColorSurfaces() {
        val resolved = resolveStrict(DefaultThemeManifest)

        assertTrue(resolved.surfaces.background is ResolvedSurface.ColorSurface)
        assertTrue(resolved.surfaces.card is ResolvedSurface.ColorSurface)
        assertTrue(resolved.surfaces.dialog is ResolvedSurface.ColorSurface)
        assertEquals(Color(0xFFF3F3F1), resolved.surfaces.background.fallbackColor)
        assertEquals(Color(0xFFFFFFFF), resolved.surfaces.card.fallbackColor)
        assertEquals(Color(0xFFFFFFFF), resolved.surfaces.dialog.fallbackColor)
    }

    @Test
    fun fiveOrdinaryColorsMapExactly() {
        val colors = resolveStrict(DefaultThemeManifest).colors

        assertEquals(Color(0xFF111111), colors.textPrimary)
        assertEquals(Color(0xFF555555), colors.textSecondary)
        assertEquals(Color(0xFFD2D2D2), colors.border)
        assertEquals(Color(0xFF111111), colors.accent)
        assertEquals(Color(0xFFFFFFFF), colors.onAccent)
    }

    @Test
    fun strictMissingImageSurfaceFailureDoesNotReturnDefaultTheme() {
        val manifest = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                background = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FFF3F3F1",
                    file = "surfaces/background/paper.jpg"
                )
            )
        )

        val result = ThemeResolver.resolveStrict(manifest, resources())

        assertTrue(result is ThemeResolveResult.Failure)
        assertTrue(
            (result as ThemeResolveResult.Failure).error is
                ThemeResolveError.ImageMissing
        )
    }

    @Test
    fun startupRecoveryFallsBackForTheSameMissingImageFailure() {
        val manifest = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                background = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FFF3F3F1",
                    file = "surfaces/background/paper.jpg"
                )
            )
        )
        val resources = resources()

        val strict = ThemeResolver.resolveStrict(manifest, resources)
        val startup = ThemeResolver.resolveOrDefault(manifest, resources)

        assertTrue(strict is ThemeResolveResult.Failure)
        assertTrue(
            (strict as ThemeResolveResult.Failure).error is
                ThemeResolveError.ImageMissing
        )
        assertSame(DefaultResolvedTheme, startup.theme)
        assertTrue(startup.failure is ThemeResolveError.ImageMissing)
    }

    @Test
    fun malformedImageIsStrictFailureButStartupUsesCompiledDefault() {
        val resources = resources()
        resources.resolveFile("surfaces/card/not-image.png").apply {
            parentFile?.mkdirs()
            writeText("ordinary bytes renamed as png")
        }
        val manifest = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                card = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FFFFFFFF",
                    file = "surfaces/card/not-image.png"
                )
            )
        )

        val strict = ThemeResolver.resolveStrict(manifest, resources)
        val startup = ThemeResolver.resolveOrDefault(manifest, resources)

        assertTrue(strict is ThemeResolveResult.Failure)
        assertTrue(
            (strict as ThemeResolveResult.Failure).error is
                ThemeResolveError.ImageHeaderInvalid
        )
        assertSame(DefaultResolvedTheme, startup.theme)
        assertTrue(startup.failure is ThemeResolveError.ImageHeaderInvalid)
    }

    @Test
    fun strictMissingNavigationResourceFailureIsStructured() {
        val manifest = DefaultThemeManifest.copy(
            navigationIcons = ThemeNavigationManifest(
                home = NavigationIconDefinition(
                    normal = "icons/home.png",
                    selected = null
                )
            )
        )

        val result = ThemeResolver.resolveStrict(manifest, resources())

        assertTrue(result is ThemeResolveResult.Failure)
        assertTrue(
            (result as ThemeResolveResult.Failure).error is
                ThemeResolveError.NavigationIconMissing
        )
    }

    @Test
    fun startupRecoveryUsesCompiledDefaultForMissingNavigationResource() {
        val manifest = DefaultThemeManifest.copy(
            navigationIcons = ThemeNavigationManifest(
                home = NavigationIconDefinition(
                    normal = "icons/home.png",
                    selected = null
                )
            )
        )
        val resources = resources()

        val strict = ThemeResolver.resolveStrict(manifest, resources)
        val startup = ThemeResolver.resolveOrDefault(manifest, resources)

        assertTrue(strict is ThemeResolveResult.Failure)
        assertTrue(
            (strict as ThemeResolveResult.Failure).error is
                ThemeResolveError.NavigationIconMissing
        )
        assertSame(DefaultResolvedTheme, startup.theme)
        assertTrue(
            startup.failure is ThemeResolveError.NavigationIconMissing
        )
    }

    @Test
    fun strictAndStartupFallbackKeepDifferentFailureSemantics() {
        val manifest = DefaultThemeManifest.copy(schemaVersion = Int.MAX_VALUE)
        val resources = resources()

        val strict = ThemeResolver.resolveStrict(manifest, resources)
        val startup = ThemeResolver.resolveOrDefault(manifest, resources)

        assertTrue(strict is ThemeResolveResult.Failure)
        assertTrue(
            (strict as ThemeResolveResult.Failure).error is
                ThemeResolveError.ManifestInvalid
        )
        assertSame(DefaultResolvedTheme, startup.theme)
        assertTrue(startup.failure is ThemeResolveError.ManifestInvalid)
    }

    @Test
    fun compiledDefaultThemeIsAvailableWithoutManifestResolution() {
        assertEquals("builtin.default", DefaultResolvedTheme.id)
        assertEquals(
            Color(0xFFF3F3F1),
            DefaultResolvedTheme.surfaces.background.fallbackColor
        )
        assertSame(SystemAppFontResolver, DefaultResolvedTheme.fontResolver)
        assertSame(
            DefaultNavigationIconResolver,
            DefaultResolvedTheme.navigationIconResolver
        )
    }

    @Test
    fun systemBarFieldPassesFromManifestToResolvedTheme() {
        val resolved = resolveStrict(
            DefaultThemeManifest.copy(darkSystemBarIcons = false)
        )

        assertFalse(resolved.darkSystemBarIcons)
    }

    private fun resolveStrict(manifest: ThemeManifest): ResolvedTheme {
        val result = ThemeResolver.resolveStrict(manifest, resources())
        assertTrue(result is ThemeResolveResult.Success)
        return (result as ThemeResolveResult.Success).theme
    }

    private fun resources(): ThemeResourceProvider {
        val root = File(temporaryFolder.root, "resources").apply { mkdirs() }
        return DirectoryThemeResourceProvider(root)
    }
}
