package com.example.mylibrary.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.SurfaceRole

/**
 * Provides the root background layer's [LayoutCoordinates] to descendants.
 *
 * [AppScreenContainer] captures its full-screen background coordinates and
 * provides them here so that sticky overlays (e.g. the home calendar) can
 * align their background image drawing with the root's viewport.
 *
 * Defaults to `null` when no root background is available (e.g. in previews
 * or before the first layout pass).
 */
val LocalRootBackgroundCoordinates =
    compositionLocalOf<LayoutCoordinates?> { null }

/**
 * Draws the BACKGROUND surface aligned to the root viewport.
 *
 * Both COLOR and IMAGE backgrounds are handled:
 *
 * - **COLOR**: The fallback color is composited over the app base background
 *   and forced to alpha = 1, producing an opaque layer identical to the root.
 *   No viewport alignment is needed because the color is uniform.
 *
 * - **IMAGE**: The fallback color is drawn first (opaque, composited), then
 *   the background image is drawn using the **root viewport size** for
 *   center-crop calculation.  The canvas is translated by
 *   `-(positionInRoot)` so the image appears at the same position as in the
 *   root, then clipped to this composable's own bounds.  This guarantees
 *   the calendar shows the exact slice of the image that appears behind it
 *   in the root, with no independent re-cropping or misalignment.
 *
 * When [rootCoordinates] is `null` (first frame, or outside a screen
 * container), the modifier falls back to drawing the opaque fallback color
 * only.  Once coordinates become available the image is drawn on the next
 * recomposition.
 *
 * @param rootCoordinates The root background layer's coordinates, typically
 *   obtained from [LocalRootBackgroundCoordinates].
 * @param shape Clip shape for the background.  Defaults to [RectangleShape].
 */
@Composable
fun Modifier.alignedThemeBackground(
    rootCoordinates: LayoutCoordinates? = LocalRootBackgroundCoordinates.current,
    shape: Shape = RectangleShape
): Modifier {
    val surface = resolvedThemeSurfaceForContainer(
        surface = AppTheme.surface(SurfaceRole.BACKGROUND),
        drawImageSurface = true,
        forceOpaqueFallback = false,
        compositeOverBaseColor = true
    )

    var selfCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val viewportSize: IntSize? = rootCoordinates?.size
    val offsetInViewport: Offset = if (rootCoordinates != null && selfCoordinates != null) {
        selfCoordinates!!.positionInWindow() - rootCoordinates.positionInWindow()
    } else {
        Offset.Zero
    }

    return this
        .onGloballyPositioned { selfCoordinates = it }
        .themeSurfaceBackground(
            surface = surface,
            expectedRole = SurfaceRole.BACKGROUND,
            shape = shape,
            containerAlpha = 1f,
            viewportSize = viewportSize,
            offsetInViewport = offsetInViewport
        )
}
