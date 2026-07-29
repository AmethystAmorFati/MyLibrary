package com.example.mylibrary.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContractTest {
    @Test
    fun defaultThemeMapsExactlyThreeSurfaceRoles() {
        val colors = DefaultResolvedTheme.appColors
        assertEquals(Color(0xFFF3F3F1), colors.surface(SurfaceRole.BACKGROUND))
        assertEquals(Color(0xFFFFFFFF), colors.surface(SurfaceRole.CARD))
        assertEquals(Color(0xFFFFFFFF), colors.surface(SurfaceRole.DIALOG))
    }

    @Test
    fun defaultThemeKeepsFiveOrdinaryContentColors() {
        val colors = DefaultResolvedTheme.colors
        assertEquals(Color(0xFF111111), colors.textPrimary)
        assertEquals(Color(0xFF555555), colors.textSecondary)
        assertEquals(Color(0xFFD2D2D2), colors.border)
        assertEquals(Color(0xFF111111), colors.accent)
        assertEquals(Color(0xFFFFFFFF), colors.onAccent)
    }

    @Test
    fun materialColorSchemeMapsEveryContainerAndContentBranch() {
        val colors = DefaultResolvedTheme.appColors
        val scheme = colors.toMaterialScheme()

        assertEquals(colors.accent, scheme.primary)
        assertEquals(colors.onAccent, scheme.onPrimary)
        assertEquals(colors.subtleCard, scheme.primaryContainer)
        assertEquals(colors.textPrimary, scheme.onPrimaryContainer)
        assertEquals(colors.textSecondary, scheme.secondary)
        assertEquals(colors.onAccent, scheme.onSecondary)
        assertEquals(colors.subtleCard, scheme.secondaryContainer)
        assertEquals(colors.textPrimary, scheme.onSecondaryContainer)
        assertEquals(colors.accent, scheme.tertiary)
        assertEquals(colors.onAccent, scheme.onTertiary)
        assertEquals(colors.subtleCard, scheme.tertiaryContainer)
        assertEquals(colors.textPrimary, scheme.onTertiaryContainer)
        assertEquals(colors.surfaces.background, scheme.background)
        assertEquals(colors.textPrimary, scheme.onBackground)
        assertEquals(colors.surfaces.card, scheme.surface)
        assertEquals(colors.textPrimary, scheme.onSurface)
        assertEquals(colors.subtleCard, scheme.surfaceVariant)
        assertEquals(colors.textSecondary, scheme.onSurfaceVariant)
        assertEquals(colors.border, scheme.outline)
        assertEquals(colors.subtleBorder, scheme.outlineVariant)
        assertEquals(colors.surfaces.card, scheme.surfaceBright)
        assertEquals(colors.subtleCard, scheme.surfaceDim)
        assertEquals(colors.surfaces.card, scheme.surfaceContainerLowest)
        assertEquals(colors.surfaces.card, scheme.surfaceContainerLow)
        assertEquals(colors.surfaces.card, scheme.surfaceContainer)
        assertEquals(colors.subtleCard, scheme.surfaceContainerHigh)
        assertEquals(colors.subtleCard, scheme.surfaceContainerHighest)
        assertEquals(AppError, scheme.error)
        assertEquals(AppOnError, scheme.onError)
        assertEquals(AppErrorContainer, scheme.errorContainer)
        assertEquals(AppOnErrorContainer, scheme.onErrorContainer)
        assertEquals(AppScrim, scheme.scrim)
        assertEquals(Color.Transparent, scheme.surfaceTint)
    }

    @Test
    fun materialTypographyMapsAllSlotsToExistingFontRoles() {
        val typography = createAppTypography(TestFontResolver).toMaterialTypography()

        listOf(
            typography.displayLarge,
            typography.displayMedium,
            typography.displaySmall
        ).forEach { assertEquals(FontFamily.SansSerif, it.fontFamily) }
        listOf(
            typography.headlineLarge,
            typography.headlineMedium,
            typography.headlineSmall,
            typography.titleLarge,
            typography.titleMedium,
            typography.titleSmall
        ).forEach { assertEquals(FontFamily.Serif, it.fontFamily) }
        assertEquals(FontFamily.Monospace, typography.bodyLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typography.bodyMedium.fontFamily)
        assertEquals(FontFamily.Cursive, typography.bodySmall.fontFamily)
        assertEquals(FontFamily.Monospace, typography.labelLarge.fontFamily)
        assertEquals(FontFamily.Cursive, typography.labelMedium.fontFamily)
        assertEquals(FontFamily.Cursive, typography.labelSmall.fontFamily)
    }

    @Test
    fun dangerAndErrorColorsAreFixedOutsideOrdinaryThemeColors() {
        val unrelatedTheme = AppColors(
            surfaces = AppSurfaceColors(Color.Red, Color.Green, Color.Blue),
            content = AppContentColors(
                textPrimary = Color.Yellow,
                textSecondary = Color.Cyan,
                border = Color.Magenta,
                accent = Color.Blue,
                onAccent = Color.Green
            )
        )

        assertEquals(Color(0xFFB3261E), AppDanger)
        assertEquals(Color(0xFFB3261E), AppError)
        assertEquals(AppError, unrelatedTheme.toMaterialScheme().error)
        assertTrue(AppDanger != unrelatedTheme.accent)
    }

    private object TestFontResolver : AppFontResolver {
        override fun composeFontFamily(role: FontRole): FontFamily = when (role) {
            FontRole.BRAND -> FontFamily.SansSerif
            FontRole.HEADING -> FontFamily.Serif
            FontRole.CONTENT -> FontFamily.Monospace
            FontRole.META -> FontFamily.Cursive
        }

        override fun androidTypeface(role: FontRole): Typeface =
            error("Android typefaces are not needed by typography mapping tests")
    }
}
