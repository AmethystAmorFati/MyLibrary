package com.example.mylibrary.ui.poster

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.FontRole
import com.example.mylibrary.ui.theme.ResolvedTheme
import com.example.mylibrary.ui.theme.SurfaceRole

internal data class CoverPosterThemeSnapshotSpec(
    val canvasBackground: Int,
    val cardSurface: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val border: Int,
    val headingFontRole: FontRole,
    val contentFontRole: FontRole
)

internal fun coverPosterThemeSnapshotSpec(
    theme: ResolvedTheme
): CoverPosterThemeSnapshotSpec = CoverPosterThemeSnapshotSpec(
    canvasBackground = theme.surfaces[SurfaceRole.BACKGROUND].fallbackColor.toArgb(),
    cardSurface = theme.surfaces[SurfaceRole.CARD].fallbackColor.toArgb(),
    primaryText = theme.colors.textPrimary.toArgb(),
    secondaryText = theme.colors.textSecondary.toArgb(),
    border = theme.colors.border.toArgb(),
    headingFontRole = FontRole.HEADING,
    contentFontRole = FontRole.CONTENT
)

object CoverPosterThemeSnapshotFactory {
    fun create(theme: ResolvedTheme): CoverPosterPalette {
        val spec = coverPosterThemeSnapshotSpec(theme)
        return CoverPosterPalette(
            canvasBackground = spec.canvasBackground,
            cardSurface = spec.cardSurface,
            primaryText = spec.primaryText,
            secondaryText = spec.secondaryText,
            border = spec.border,
            headingTypeface = theme.fontResolver.androidTypeface(spec.headingFontRole),
            contentTypeface = theme.fontResolver.androidTypeface(spec.contentFontRole)
        )
    }
}

@Composable
fun rememberCoverPosterThemeSnapshot(): CoverPosterPalette {
    val theme = AppTheme.resolvedTheme
    return remember(theme) {
        CoverPosterThemeSnapshotFactory.create(theme)
    }
}
