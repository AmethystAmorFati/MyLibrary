package com.example.mylibrary.ui.theme

import com.example.mylibrary.ui.navigation.NavigationIconSlot
import com.example.mylibrary.ui.navigation.ThemeIconRendering

const val THEME_MANIFEST_SCHEMA_VERSION = 1

data class ThemeManifest(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val author: String?,
    val version: String,
    val surfaces: ThemeSurfaceManifest,
    val colors: ThemeColorManifest,
    val fonts: ThemeFontManifest,
    val fontAssignments: Map<FontRole, FontSlot>,
    val navigationIcons: ThemeNavigationManifest?,
    val darkSystemBarIcons: Boolean
)

data class ThemeSurfaceManifest(
    val background: ThemeSurfaceDefinition,
    val card: ThemeSurfaceDefinition,
    val dialog: ThemeSurfaceDefinition
) {
    fun entries(): List<Pair<SurfaceRole, ThemeSurfaceDefinition>> = listOf(
        SurfaceRole.BACKGROUND to background,
        SurfaceRole.CARD to card,
        SurfaceRole.DIALOG to dialog
    )
}

enum class ThemeSurfaceType {
    COLOR,
    IMAGE
}

data class ThemeSurfaceDefinition(
    val type: ThemeSurfaceType,
    val color: String,
    val file: String?
)

data class ThemeColorManifest(
    val textPrimary: String,
    val textSecondary: String,
    val border: String,
    val accent: String,
    val onAccent: String
) {
    fun entries(): List<Pair<String, String>> = listOf(
        "textPrimary" to textPrimary,
        "textSecondary" to textSecondary,
        "border" to border,
        "accent" to accent,
        "onAccent" to onAccent
    )
}

data class ThemeFontManifest(
    val fontA: String?,
    val fontB: String?
) {
    operator fun get(slot: FontSlot): String? = when (slot) {
        FontSlot.A -> fontA
        FontSlot.B -> fontB
    }
}

data class ThemeNavigationManifest(
    val rendering: ThemeIconRendering = ThemeIconRendering.ORIGINAL,
    val home: NavigationIconDefinition? = null,
    val library: NavigationIconDefinition? = null,
    val statistics: NavigationIconDefinition? = null,
    val settings: NavigationIconDefinition? = null
) {
    fun entries(): List<Pair<NavigationIconSlot, NavigationIconDefinition>> = listOfNotNull(
        home?.let { NavigationIconSlot.HOME to it },
        library?.let { NavigationIconSlot.LIBRARY to it },
        statistics?.let { NavigationIconSlot.STATISTICS to it },
        settings?.let { NavigationIconSlot.SETTINGS to it }
    )
}

data class NavigationIconDefinition(
    val normal: String,
    val selected: String?
)
