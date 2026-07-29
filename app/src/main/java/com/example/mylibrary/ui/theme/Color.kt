package com.example.mylibrary.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class SurfaceRole {
    BACKGROUND,
    CARD,
    DIALOG
}

@Immutable
data class AppSurfaceColors(
    val background: Color,
    val card: Color,
    val dialog: Color
) {
    operator fun get(role: SurfaceRole): Color = when (role) {
        SurfaceRole.BACKGROUND -> background
        SurfaceRole.CARD -> card
        SurfaceRole.DIALOG -> dialog
    }
}

@Immutable
data class AppContentColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color
)

@Immutable
data class AppColors(
    val surfaces: AppSurfaceColors,
    val content: AppContentColors
) {
    val textPrimary: Color
        get() = content.textPrimary

    val textSecondary: Color
        get() = content.textSecondary

    val border: Color
        get() = content.border

    val accent: Color
        get() = content.accent

    val onAccent: Color
        get() = content.onAccent

    fun surface(role: SurfaceRole): Color = surfaces[role]

    // Fixed component-state treatments derived from the public theme semantics.
    internal val mutedText: Color
        get() = lerp(textSecondary, surfaces.card, 0.31f)

    internal val subtleBorder: Color
        get() = lerp(border, surfaces.card, 0.47f)

    internal val subtleCard: Color
        get() = lerp(surfaces.background, surfaces.card, 0.33f)
}

// Functional colors stay app-owned and are never supplied by a runtime theme.
val AppDanger = Color(0xFFB3261E)
val AppError = Color(0xFFB3261E)
val AppOnError = Color(0xFFFFFFFF)
val AppErrorContainer = Color(0xFFF9DEDC)
val AppOnErrorContainer = Color(0xFF410E0B)
val AppScrim = Color(0x52000000)
val AppImageScrim = Color(0x52000000)
