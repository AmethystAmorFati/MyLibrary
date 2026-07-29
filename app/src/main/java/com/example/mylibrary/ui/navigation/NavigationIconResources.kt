package com.example.mylibrary.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationIconSlot { HOME, LIBRARY, STATISTICS, SETTINGS }

enum class NavigationIconState { NORMAL, SELECTED }

enum class ThemeIconRendering {
    ORIGINAL,
    MONOCHROME;

    companion object {
        fun fromManifestValue(value: String): ThemeIconRendering? =
            entries.firstOrNull { it.name == value }
    }
}

@Immutable
data class ThemeNavigationIconCacheKey(
    val themeId: String,
    val themeVersion: String,
    val themeGeneration: Long,
    val slot: NavigationIconSlot,
    val state: NavigationIconState,
    val relativePath: String,
    val canonicalPath: String,
    val fileSize: Long,
    val lastModified: Long,
    val decodeBucket: String
)

@Immutable
sealed interface NavigationIconAsset {
    @Immutable
    data class Vector(val imageVector: ImageVector) : NavigationIconAsset

    @Immutable
    data class Bitmap(
        val imageBitmap: ImageBitmap?,
        val rendering: ThemeIconRendering,
        val cacheKey: ThemeNavigationIconCacheKey,
        val unavailableReason: String? = null
    ) : NavigationIconAsset {
        val estimatedByteCount: Long
            get() = imageBitmap?.let {
                it.width.toLong() * it.height.toLong() * 4L
            } ?: 0L

        init {
            if (imageBitmap == null) {
                require(!unavailableReason.isNullOrBlank())
            }
        }
    }
}

@Immutable
data class NavigationIconResource(
    val normal: NavigationIconAsset,
    val selected: NavigationIconAsset? = null
) {
    fun forSelection(isSelected: Boolean): NavigationIconAsset =
        if (isSelected) selected ?: normal else normal
}

interface NavigationIconResolver {
    fun resolve(slot: NavigationIconSlot): NavigationIconResource
}

object DefaultNavigationIconResolver : NavigationIconResolver {
    private val resources = mapOf(
        NavigationIconSlot.HOME to NavigationIconResource(
            normal = NavigationIconAsset.Vector(Icons.Outlined.Home),
            selected = NavigationIconAsset.Vector(Icons.Filled.Home)
        ),
        NavigationIconSlot.LIBRARY to NavigationIconResource(
            normal = NavigationIconAsset.Vector(Icons.Outlined.LocalLibrary),
            selected = NavigationIconAsset.Vector(Icons.Filled.LocalLibrary)
        ),
        NavigationIconSlot.STATISTICS to NavigationIconResource(
            normal = NavigationIconAsset.Vector(Icons.Outlined.BarChart),
            selected = NavigationIconAsset.Vector(Icons.Filled.BarChart)
        ),
        NavigationIconSlot.SETTINGS to NavigationIconResource(
            normal = NavigationIconAsset.Vector(Icons.Outlined.Settings),
            selected = NavigationIconAsset.Vector(Icons.Filled.Settings)
        )
    )

    override fun resolve(slot: NavigationIconSlot): NavigationIconResource =
        requireNotNull(resources[slot])
}

@Immutable
class ResolvedNavigationIconResolver(
    customResources: Map<NavigationIconSlot, NavigationIconResource>
) : NavigationIconResolver {
    private val frozenResources = customResources.toMap()

    override fun resolve(slot: NavigationIconSlot): NavigationIconResource =
        frozenResources[slot] ?: DefaultNavigationIconResolver.resolve(slot)

    fun hasCustomResource(slot: NavigationIconSlot): Boolean =
        slot in frozenResources
}
