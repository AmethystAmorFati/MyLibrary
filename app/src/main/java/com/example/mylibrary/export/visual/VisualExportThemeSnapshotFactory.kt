package com.example.mylibrary.export.visual

import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.example.mylibrary.ui.theme.FontRole
import com.example.mylibrary.ui.theme.ResolvedSurface
import com.example.mylibrary.ui.theme.ResolvedTheme
import com.example.mylibrary.ui.theme.SurfaceRole

/**
 * Freezes the already-resolved theme once at the user action boundary.
 * Renderers receive only this immutable value and never access Compose state.
 */
object VisualExportThemeSnapshotFactory {
    fun create(theme: ResolvedTheme): VisualExportThemeSnapshot {
        val backgroundSurface = theme.surfaces[SurfaceRole.BACKGROUND]
        val backgroundBitmap = try {
            (backgroundSurface as? ResolvedSurface.ImageSurface)
                ?.image
                ?.imageBitmap
                ?.asAndroidBitmap()
        } catch (error: Exception) {
            Log.w(
                TAG,
                "Theme background cannot be frozen; using fallback color",
                error
            )
            null
        }
        return VisualExportThemeSnapshot(
            backgroundColor = backgroundSurface.fallbackColor.toArgb(),
            textPrimary = theme.colors.textPrimary.toArgb(),
            textSecondary = theme.colors.textSecondary.toArgb(),
            border = theme.colors.border.toArgb(),
            accent = theme.colors.accent.toArgb(),
            placeholderColor =
                theme.surfaces[SurfaceRole.CARD].fallbackColor.toArgb(),
            headingTypeface = resolveTypeface(theme, FontRole.HEADING),
            contentTypeface = resolveTypeface(theme, FontRole.CONTENT),
            backgroundBitmap = backgroundBitmap
        )
    }

    private fun resolveTypeface(
        theme: ResolvedTheme,
        role: FontRole
    ): Typeface = try {
        theme.fontResolver.androidTypeface(role)
    } catch (error: Exception) {
        Log.w(TAG, "Theme font cannot be frozen for $role; using system font", error)
        Typeface.DEFAULT
    }

    private const val TAG = "VisualExportTheme"
}
