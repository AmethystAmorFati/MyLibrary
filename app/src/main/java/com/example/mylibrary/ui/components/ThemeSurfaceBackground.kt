package com.example.mylibrary.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.ResolvedSurface
import com.example.mylibrary.ui.theme.SurfaceRole
import java.util.LinkedHashSet
import kotlin.math.roundToInt

private const val ThemeSurfaceLogTag = "MyLibraryThemeSurface"
private const val MaximumRememberedRuntimeFailures = 64
private val loggedRuntimeFailures = LinkedHashSet<String>()

internal data class ThemeSurfaceContainerPolicy(
    val materialColor: Color,
    val drawsResolvedBackground: Boolean
)

internal fun resolvedThemeSurfaceForContainer(
    surface: ResolvedSurface,
    drawImageSurface: Boolean,
    forceOpaqueFallback: Boolean
): ResolvedSurface {
    val fallbackColor = if (forceOpaqueFallback) {
        surface.fallbackColor.copy(alpha = 1f)
    } else {
        surface.fallbackColor
    }
    return when {
        !drawImageSurface -> ResolvedSurface.ColorSurface(fallbackColor)
        surface is ResolvedSurface.ImageSurface -> surface.copy(
            fallbackColor = fallbackColor
        )
        else -> ResolvedSurface.ColorSurface(fallbackColor)
    }
}

internal fun themeSurfaceContainerPolicy(
    surface: ResolvedSurface,
    containerAlpha: Float
): ThemeSurfaceContainerPolicy {
    val alpha = containerAlpha.coerceIn(0f, 1f)
    val drawsResolvedBackground =
        surface is ResolvedSurface.ImageSurface || alpha < 1f
    return ThemeSurfaceContainerPolicy(
        materialColor = if (drawsResolvedBackground) {
            Color.Transparent
        } else {
            surface.fallbackColor
        },
        drawsResolvedBackground = drawsResolvedBackground
    )
}

@Composable
internal fun Modifier.appThemeSurfaceBackground(
    role: SurfaceRole,
    shape: Shape,
    containerAlpha: Float = 1f
): Modifier = themeSurfaceBackground(
    surface = AppTheme.surface(role),
    expectedRole = role,
    shape = shape,
    containerAlpha = containerAlpha
)

internal fun Modifier.themeSurfaceBackground(
    surface: ResolvedSurface,
    expectedRole: SurfaceRole,
    shape: Shape,
    containerAlpha: Float = 1f
): Modifier {
    val alpha = containerAlpha.coerceIn(0f, 1f)
    return drawWithCache {
        val outlinePath = shape
            .createOutline(size, layoutDirection, this)
            .asPath()
        val layerBounds = Rect(Offset.Zero, size)
        val layerPaint = Paint().apply {
            this.alpha = alpha
        }
        onDrawBehind {
            clipPath(outlinePath) {
                val usesLayer = alpha < 1f
                if (usesLayer) {
                    drawContext.canvas.saveLayer(layerBounds, layerPaint)
                }
                try {
                    drawRect(surface.fallbackColor)
                    val imageSurface = surface as? ResolvedSurface.ImageSurface
                    if (imageSurface != null) {
                        if (imageSurface.role != expectedRole) {
                            logRuntimeFailureOnce(
                                key = "role:${imageSurface.image.cacheKey}",
                                message = "Resolved image role ${imageSurface.role} was used as " +
                                    expectedRole
                            )
                        } else {
                            val asset = imageSurface.image
                            val bitmap = asset.imageBitmap
                            if (bitmap == null) {
                                logRuntimeFailureOnce(
                                    key = "unavailable:${asset.cacheKey}",
                                    message = asset.unavailableReason
                                        ?: "Resolved theme image is temporarily unavailable"
                                )
                            } else {
                                val crop = calculateCenterCropSource(
                                    sourceWidth = asset.decodedWidth,
                                    sourceHeight = asset.decodedHeight,
                                    destinationWidth =
                                        size.width.roundToInt().coerceAtLeast(1),
                                    destinationHeight =
                                        size.height.roundToInt().coerceAtLeast(1)
                                )
                                try {
                                    drawImage(
                                        image = bitmap,
                                        srcOffset = crop.offset,
                                        srcSize = crop.size,
                                        dstOffset = IntOffset.Zero,
                                        dstSize = IntSize(
                                            size.width
                                                .roundToInt()
                                                .coerceAtLeast(1),
                                            size.height
                                                .roundToInt()
                                                .coerceAtLeast(1)
                                        )
                                    )
                                } catch (throwable: Throwable) {
                                    logRuntimeFailureOnce(
                                        key = "draw:${asset.cacheKey}",
                                        message = throwable.message
                                            ?: throwable::class.java.simpleName
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    if (usesLayer) drawContext.canvas.restore()
                }
            }
        }
    }
}

internal data class ThemeSurfaceCrop(
    val offset: IntOffset,
    val size: IntSize
)

internal fun calculateCenterCropSource(
    sourceWidth: Int,
    sourceHeight: Int,
    destinationWidth: Int,
    destinationHeight: Int
): ThemeSurfaceCrop {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(destinationWidth > 0 && destinationHeight > 0)
    val sourceAspect = sourceWidth.toDouble() / sourceHeight.toDouble()
    val destinationAspect =
        destinationWidth.toDouble() / destinationHeight.toDouble()
    return if (sourceAspect > destinationAspect) {
        val croppedWidth = (sourceHeight * destinationAspect)
            .roundToInt()
            .coerceIn(1, sourceWidth)
        ThemeSurfaceCrop(
            offset = IntOffset((sourceWidth - croppedWidth) / 2, 0),
            size = IntSize(croppedWidth, sourceHeight)
        )
    } else {
        val croppedHeight = (sourceWidth / destinationAspect)
            .roundToInt()
            .coerceIn(1, sourceHeight)
        ThemeSurfaceCrop(
            offset = IntOffset(0, (sourceHeight - croppedHeight) / 2),
            size = IntSize(sourceWidth, croppedHeight)
        )
    }
}

private fun Outline.asPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

private fun logRuntimeFailureOnce(key: String, message: String) {
    val shouldLog = synchronized(loggedRuntimeFailures) {
        if (key in loggedRuntimeFailures) {
            false
        } else {
            if (loggedRuntimeFailures.size >= MaximumRememberedRuntimeFailures) {
                val oldest = loggedRuntimeFailures.iterator().next()
                loggedRuntimeFailures.remove(oldest)
            }
            loggedRuntimeFailures.add(key)
            true
        }
    }
    if (shouldLog) {
        Log.e(ThemeSurfaceLogTag, message)
    }
}
