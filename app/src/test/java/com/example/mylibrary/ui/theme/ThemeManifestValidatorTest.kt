package com.example.mylibrary.ui.theme

import com.example.mylibrary.ui.navigation.NavigationIconSlot
import com.example.mylibrary.ui.navigation.ThemeIconRendering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeManifestValidatorTest {
    @Test
    fun defaultManifestPassesValidation() {
        assertTrue(ThemeManifestValidator.validate(DefaultThemeManifest).isValid)
    }

    @Test
    fun malformedArgbColorIsRejected() {
        val manifest = DefaultThemeManifest.copy(
            colors = DefaultThemeManifest.colors.copy(textPrimary = "#111111")
        )

        val result = ThemeManifestValidator.validate(manifest)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.field == "colors.textPrimary" })
    }

    @Test
    fun surfaceTypeAndFileMustAgree() {
        val colorWithFile = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                card = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.COLOR,
                    color = "#FFFFFFFF",
                    file = "surfaces/card/paper.png"
                )
            )
        )
        val imageWithoutFile = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                dialog = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FFFFFFFF",
                    file = null
                )
            )
        )

        assertFalse(ThemeManifestValidator.validate(colorWithFile).isValid)
        assertFalse(ThemeManifestValidator.validate(imageWithoutFile).isValid)
    }

    @Test
    fun jpegExtensionIsLimitedToBackgroundSurface() {
        val backgroundJpeg = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                background = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FFF3F3F1",
                    file = "surfaces/background/paper.jpeg"
                )
            )
        )
        val cardJpeg = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                card = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FFFFFFFF",
                    file = "surfaces/card/paper.jpeg"
                )
            )
        )

        assertTrue(ThemeManifestValidator.validate(backgroundJpeg).isValid)
        assertFalse(ThemeManifestValidator.validate(cardJpeg).isValid)
    }

    @Test
    fun resourcePathTraversalIsRejected() {
        val manifest = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                background = ThemeSurfaceDefinition(
                    type = ThemeSurfaceType.IMAGE,
                    color = "#FFF3F3F1",
                    file = "surfaces/background/../escaped.png"
                )
            )
        )

        val result = ThemeManifestValidator.validate(manifest)

        assertFalse(result.isValid)
        assertTrue(
            result.issues.any {
                it.field == "surfaces.background.file" &&
                    it.message.contains("traversal", ignoreCase = true)
            }
        )
    }

    @Test
    fun absoluteAndBackslashPathsAreRejected() {
        val absolutePath = DefaultThemeManifest.copy(
            fonts = ThemeFontManifest(fontA = "C:/fonts/primary.ttf", fontB = null)
        )
        val backslashPath = DefaultThemeManifest.copy(
            fonts = ThemeFontManifest(fontA = "fonts\\primary.ttf", fontB = null)
        )

        assertFalse(ThemeManifestValidator.validate(absolutePath).isValid)
        assertFalse(ThemeManifestValidator.validate(backslashPath).isValid)
    }

    @Test
    fun missingFontRoleAssignmentIsRejected() {
        val manifest = DefaultThemeManifest.copy(
            fontAssignments = DefaultThemeManifest.fontAssignments - FontRole.META
        )

        val result = ThemeManifestValidator.validate(manifest)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.field == "fontAssignments.META" })
    }

    @Test
    fun configuredNavigationSlotWithoutNormalIconIsRejected() {
        val manifest = DefaultThemeManifest.copy(
            navigationIcons = ThemeNavigationManifest(
                home = NavigationIconDefinition(normal = "", selected = null)
            )
        )

        val result = ThemeManifestValidator.validate(manifest)

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.field == "navigationIcons.home.normal" })
    }

    @Test
    fun absentAndPartialNavigationConfigurationsAreValid() {
        val absent = DefaultThemeManifest.copy(navigationIcons = null)
        val partial = DefaultThemeManifest.copy(
            navigationIcons = ThemeNavigationManifest(
                rendering = ThemeIconRendering.MONOCHROME,
                library = NavigationIconDefinition(
                    normal = "icons/library.webp",
                    selected = null
                )
            )
        )

        assertTrue(ThemeManifestValidator.validate(absent).isValid)
        assertTrue(ThemeManifestValidator.validate(partial).isValid)
    }

    @Test
    fun selectedCannotRepairAMissingNormalIcon() {
        val manifest = DefaultThemeManifest.copy(
            navigationIcons = ThemeNavigationManifest(
                settings = NavigationIconDefinition(
                    normal = "",
                    selected = "icons/settings_selected.png"
                )
            )
        )

        val result = ThemeManifestValidator.validate(manifest)

        assertFalse(result.isValid)
        assertTrue(
            result.issues.any {
                it.field == "navigationIcons.settings.normal"
            }
        )
    }

    @Test
    fun navigationIconsMustStayUnderIconsDirectory() {
        val wrongPrefix = DefaultThemeManifest.copy(
            navigationIcons = ThemeNavigationManifest(
                home = NavigationIconDefinition(
                    normal = "navigation/home.png",
                    selected = null
                )
            )
        )
        val traversal = DefaultThemeManifest.copy(
            navigationIcons = ThemeNavigationManifest(
                home = NavigationIconDefinition(
                    normal = "icons/../home.png",
                    selected = null
                )
            )
        )

        assertFalse(ThemeManifestValidator.validate(wrongPrefix).isValid)
        assertFalse(ThemeManifestValidator.validate(traversal).isValid)
    }

    @Test
    fun unsupportedSchemaVersionIsRejected() {
        val result = ThemeManifestValidator.validate(
            DefaultThemeManifest.copy(schemaVersion = 2)
        )

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.field == "schemaVersion" })
    }

    @Test
    fun fontSlotBFallsBackToAAndMissingAFallsBackToSystem() {
        val fontsWithOnlyA = ThemeFontManifest(
            fontA = "fonts/primary.ttf",
            fontB = null
        )

        assertEquals(
            "fonts/primary.ttf",
            ThemeFontFallbackPolicy.resolveFile(
                role = FontRole.CONTENT,
                fonts = fontsWithOnlyA,
                assignments = DefaultThemeManifest.fontAssignments
            )
        )
        assertNull(
            ThemeFontFallbackPolicy.resolveFile(
                role = FontRole.BRAND,
                fonts = ThemeFontManifest(fontA = null, fontB = null),
                assignments = DefaultThemeManifest.fontAssignments
            )
        )
    }

    @Test
    fun navigationManifestExposesOnlyTheFourFrozenSlots() {
        val manifest = ThemeNavigationManifest(
            home = NavigationIconDefinition("icons/home.png", null),
            library = NavigationIconDefinition("icons/library.png", null),
            statistics = NavigationIconDefinition("icons/statistics.png", null),
            settings = NavigationIconDefinition("icons/settings.png", null)
        )

        assertEquals(
            NavigationIconSlot.entries.toSet(),
            manifest.entries().map { it.first }.toSet()
        )
    }

    @Test
    fun resourceLimitsFreezeCurrentContract() {
        assertEquals(3, ThemeResourceLimits.MAX_SURFACE_IMAGES)
        assertEquals(2, ThemeResourceLimits.MAX_FONT_FILES)
        assertEquals(8, ThemeResourceLimits.MAX_NAVIGATION_IMAGES)
        assertEquals(28L, ThemeResourceLimits.MIN_FONT_FILE_BYTES)
        assertEquals(
            20L * 1024L * 1024L,
            ThemeResourceLimits.MAX_SINGLE_FONT_FILE_BYTES
        )
        assertEquals(
            32L * 1024L * 1024L,
            ThemeResourceLimits.MAX_TOTAL_FONT_FILE_BYTES
        )
        assertEquals(setOf("png", "webp"), ThemeResourceLimits.COMMON_IMAGE_EXTENSIONS)
        assertEquals(
            setOf("png", "webp", "jpg", "jpeg"),
            ThemeResourceLimits.BACKGROUND_IMAGE_EXTENSIONS
        )
        assertEquals(setOf("ttf"), ThemeResourceLimits.FONT_EXTENSIONS)
        assertEquals(
            12L * 1024L * 1024L,
            ThemeResourceLimits.MAX_BACKGROUND_IMAGE_FILE_BYTES
        )
        assertEquals(
            8L * 1024L * 1024L,
            ThemeResourceLimits.MAX_CARD_IMAGE_FILE_BYTES
        )
        assertEquals(
            8L * 1024L * 1024L,
            ThemeResourceLimits.MAX_DIALOG_IMAGE_FILE_BYTES
        )
        assertEquals(
            24L * 1024L * 1024L,
            ThemeResourceLimits.MAX_TOTAL_SURFACE_IMAGE_BYTES
        )
        assertEquals(8192, ThemeResourceLimits.MAX_IMAGE_SIDE_PIXELS)
        assertEquals(16, ThemeResourceLimits.MIN_IMAGE_SIDE_PIXELS)
        assertEquals(
            16_000_000L,
            ThemeResourceLimits.MAX_BACKGROUND_IMAGE_PIXELS
        )
        assertEquals(
            8_000_000L,
            ThemeResourceLimits.MAX_CARD_IMAGE_PIXELS
        )
        assertEquals(
            8_000_000L,
            ThemeResourceLimits.MAX_DIALOG_IMAGE_PIXELS
        )
        assertEquals(
            64L * 1024L * 1024L,
            ThemeResourceLimits.MAX_THEME_IMAGE_CACHE_BYTES
        )
        assertEquals(
            512L * 1024L,
            ThemeResourceLimits.MAX_NAVIGATION_IMAGE_FILE_BYTES
        )
        assertEquals(
            2L * 1024L * 1024L,
            ThemeResourceLimits.MAX_TOTAL_NAVIGATION_IMAGE_BYTES
        )
        assertEquals(1024, ThemeResourceLimits.MAX_NAVIGATION_IMAGE_SIDE_PIXELS)
        assertEquals(262_144L, ThemeResourceLimits.MAX_NAVIGATION_IMAGE_PIXELS)
        assertEquals(4.0, ThemeResourceLimits.MAX_NAVIGATION_IMAGE_ASPECT_RATIO, 0.0)
        assertEquals(128, ThemeResourceLimits.NAVIGATION_DECODE_MAX_SIDE)
        assertEquals("icons/", ThemeResourceLimits.NAVIGATION_PREFIX)
    }

    @Test
    fun navigationRenderingAcceptsOnlyFrozenEnumValues() {
        assertEquals(
            ThemeIconRendering.ORIGINAL,
            ThemeIconRendering.fromManifestValue("ORIGINAL")
        )
        assertEquals(
            ThemeIconRendering.MONOCHROME,
            ThemeIconRendering.fromManifestValue("MONOCHROME")
        )
        assertNull(ThemeIconRendering.fromManifestValue("DUOTONE"))
    }
}
