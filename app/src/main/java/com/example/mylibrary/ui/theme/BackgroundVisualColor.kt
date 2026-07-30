package com.example.mylibrary.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The app's inherent background color — the canvas behind every theme.
 *
 * When a user supplies a translucent BACKGROUND color (e.g. #99E8A87D),
 * the visual result depends on what sits behind it.  By compositing the
 * theme color over this fixed base we obtain a deterministic, opaque
 * "final visual background color" that looks the same everywhere it is
 * drawn, regardless of what happens to be beneath.
 */
internal val AppBaseBackgroundColor: Color = Color(0xFFF3F3F1)

/**
 * Composites a (possibly translucent) theme color over the app's base
 * background color and forces the result to be fully opaque.
 *
 * This is **not** the same as `color.copy(alpha = 1f)`: a plain alpha
 * override would darken the visual (e.g. #99E8A87D -> #FFE8A87D).
 * Compositing first blends the theme color with the base, preserving
 * the intended visual, then clamps alpha to 1 so the result can be
 * used as an opaque mask that hides anything beneath it.
 */
internal fun compositeBackgroundOverBase(
    themeColor: Color,
    baseColor: Color = AppBaseBackgroundColor
): Color = themeColor.compositeOver(baseColor).copy(alpha = 1f)
