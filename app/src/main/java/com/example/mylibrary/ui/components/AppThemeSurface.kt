package com.example.mylibrary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.SurfaceRole

@Composable
fun AppThemeSurface(
    role: SurfaceRole,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    tonalElevation: Dp = 0.dp,
    containerAlpha: Float = 1f,
    drawImageSurface: Boolean = true,
    forceOpaqueFallback: Boolean = false,
    compositeOverBaseColor: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val surface = resolvedThemeSurfaceForContainer(
        surface = AppTheme.surface(role),
        drawImageSurface = drawImageSurface,
        forceOpaqueFallback = forceOpaqueFallback,
        compositeOverBaseColor = compositeOverBaseColor
    )
    val containerPolicy = themeSurfaceContainerPolicy(
        surface = surface,
        containerAlpha = containerAlpha
    )
    val backgroundModifier = if (containerPolicy.drawsResolvedBackground) {
        modifier.themeSurfaceBackground(
            surface = surface,
            expectedRole = role,
            shape = shape,
            containerAlpha = containerAlpha
        )
    } else {
        modifier
    }
    Surface(
        modifier = backgroundModifier,
        shape = shape,
        color = containerPolicy.materialColor,
        contentColor = AppTheme.colors.textPrimary,
        border = border,
        shadowElevation = shadowElevation,
        tonalElevation = tonalElevation
    ) {
        Box(
            propagateMinConstraints = true,
            content = content
        )
    }
}
