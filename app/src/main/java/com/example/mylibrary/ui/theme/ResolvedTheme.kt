package com.example.mylibrary.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.example.mylibrary.ui.navigation.NavigationIconResolver

enum class ThemeImageFormat(
    val alphaCapable: Boolean
) {
    PNG(alphaCapable = true),
    WEBP(alphaCapable = true),
    JPEG(alphaCapable = false)
}

@Immutable
data class ThemeImageCacheKey(
    val themeId: String,
    val themeVersion: String,
    val themeGeneration: Long,
    val role: SurfaceRole,
    val relativePath: String,
    val fileSize: Long,
    val lastModified: Long,
    val decodeBucket: String
)

@Immutable
data class ThemeImageAsset(
    val imageBitmap: ImageBitmap?,
    val originalWidth: Int,
    val originalHeight: Int,
    val decodedWidth: Int,
    val decodedHeight: Int,
    val format: ThemeImageFormat,
    val alphaCapable: Boolean,
    val cacheKey: ThemeImageCacheKey,
    val unavailableReason: String? = null
) {
    val estimatedByteCount: Long
        get() = decodedWidth.toLong() * decodedHeight.toLong() * 4L

    init {
        require(originalWidth > 0 && originalHeight > 0)
        require(decodedWidth > 0 && decodedHeight > 0)
        require(alphaCapable == format.alphaCapable)
        if (imageBitmap == null) {
            require(!unavailableReason.isNullOrBlank())
        } else {
            require(imageBitmap.width == decodedWidth)
            require(imageBitmap.height == decodedHeight)
        }
    }
}

@Immutable
sealed interface ResolvedSurface {
    val fallbackColor: Color

    @Immutable
    data class ColorSurface(
        val color: Color
    ) : ResolvedSurface {
        override val fallbackColor: Color
            get() = color
    }

    @Immutable
    data class ImageSurface(
        override val fallbackColor: Color,
        val image: ThemeImageAsset,
        val role: SurfaceRole
    ) : ResolvedSurface {
        init {
            require(role == image.cacheKey.role)
        }
    }
}

@Immutable
data class ResolvedThemeSurfaces(
    val background: ResolvedSurface,
    val card: ResolvedSurface,
    val dialog: ResolvedSurface
) {
    operator fun get(role: SurfaceRole): ResolvedSurface = when (role) {
        SurfaceRole.BACKGROUND -> background
        SurfaceRole.CARD -> card
        SurfaceRole.DIALOG -> dialog
    }

    internal fun fallbackColors(): AppSurfaceColors = AppSurfaceColors(
        background = background.fallbackColor,
        card = card.fallbackColor,
        dialog = dialog.fallbackColor
    )
}

@Immutable
data class ResolvedTheme(
    val id: String,
    val name: String,
    val surfaces: ResolvedThemeSurfaces,
    val colors: AppContentColors,
    val typography: AppTypography,
    val fontResolver: AppFontResolver,
    val navigationIconResolver: NavigationIconResolver,
    val darkSystemBarIcons: Boolean,
    val themeGeneration: Long = 0L
) {
    internal val appColors: AppColors = AppColors(
        surfaces = surfaces.fallbackColors(),
        content = colors
    )
}
