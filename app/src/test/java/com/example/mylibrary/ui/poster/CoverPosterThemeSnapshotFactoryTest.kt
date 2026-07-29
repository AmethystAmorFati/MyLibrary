package com.example.mylibrary.ui.poster

import androidx.compose.ui.graphics.toArgb
import com.example.mylibrary.ui.theme.DefaultResolvedTheme
import com.example.mylibrary.ui.theme.FontRole
import com.example.mylibrary.ui.theme.SurfaceRole
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverPosterThemeSnapshotFactoryTest {
    @Test
    fun snapshotSpecCapturesCurrentThemeColorsAndExportFontRoles() {
        val spec = coverPosterThemeSnapshotSpec(DefaultResolvedTheme)

        assertEquals(
            DefaultResolvedTheme.surfaces[SurfaceRole.BACKGROUND].fallbackColor.toArgb(),
            spec.canvasBackground
        )
        assertEquals(
            DefaultResolvedTheme.surfaces[SurfaceRole.CARD].fallbackColor.toArgb(),
            spec.cardSurface
        )
        assertEquals(DefaultResolvedTheme.colors.textPrimary.toArgb(), spec.primaryText)
        assertEquals(DefaultResolvedTheme.colors.textSecondary.toArgb(), spec.secondaryText)
        assertEquals(DefaultResolvedTheme.colors.border.toArgb(), spec.border)
        assertEquals(FontRole.HEADING, spec.headingFontRole)
        assertEquals(FontRole.CONTENT, spec.contentFontRole)
    }
}
