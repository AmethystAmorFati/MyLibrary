package com.example.mylibrary.ui.navigation

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import java.util.concurrent.ConcurrentHashMap

/**
 * The single rendering path for the four themeable bottom-navigation icons.
 * Manifest interpretation, file access, validation, and decoding all happen
 * before this component receives an immutable resolver.
 */
@Composable
fun AppNavigationIcon(
    slot: NavigationIconSlot,
    selected: Boolean,
    iconResolver: NavigationIconResolver,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val fallback = DefaultNavigationIconResolver
        .resolve(slot)
        .forSelection(selected) as NavigationIconAsset.Vector
    val resolved = remember(iconResolver, slot) {
        try {
            iconResolver.resolve(slot)
        } catch (exception: RuntimeException) {
            NavigationIconRuntimeFailures.logOnce(
                key = "resolver:$slot:${exception::class.java.name}",
                message = "Theme navigation resolver failed for $slot",
                exception = exception
            )
            DefaultNavigationIconResolver.resolve(slot)
        }
    }
    when (val asset = resolved.forSelection(selected)) {
        is NavigationIconAsset.Vector -> {
            Icon(
                imageVector = asset.imageVector,
                contentDescription = contentDescription,
                tint = tint,
                modifier = modifier
            )
        }

        is NavigationIconAsset.Bitmap -> {
            val image = asset.imageBitmap
            if (image == null) {
                NavigationIconRuntimeFailures.logOnce(
                    key = "unavailable:${asset.cacheKey}",
                    message = asset.unavailableReason
                        ?: "Theme navigation bitmap is unavailable"
                )
                Icon(
                    imageVector = fallback.imageVector,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = modifier
                )
            } else {
                val primary = remember(image) { BitmapPainter(image) }
                val fallbackPainter = rememberVectorPainter(fallback.imageVector)
                val primaryFilter = when (asset.rendering) {
                    ThemeIconRendering.ORIGINAL -> null
                    ThemeIconRendering.MONOCHROME -> ColorFilter.tint(tint)
                }
                val painter = remember(
                    primary,
                    fallbackPainter,
                    primaryFilter,
                    tint,
                    asset.cacheKey
                ) {
                    RuntimeFallbackNavigationPainter(
                        primary = primary,
                        fallback = fallbackPainter,
                        primaryColorFilter = primaryFilter,
                        fallbackColorFilter = ColorFilter.tint(tint),
                        failureKey = "draw:${asset.cacheKey}"
                    )
                }
                Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = modifier,
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private class RuntimeFallbackNavigationPainter(
    private val primary: Painter,
    private val fallback: Painter,
    private val primaryColorFilter: ColorFilter?,
    private val fallbackColorFilter: ColorFilter,
    private val failureKey: String
) : Painter() {
    override val intrinsicSize: Size
        get() = primary.intrinsicSize

    override fun DrawScope.onDraw() {
        try {
            with(primary) {
                draw(
                    size = size,
                    colorFilter = primaryColorFilter
                )
            }
        } catch (exception: RuntimeException) {
            NavigationIconRuntimeFailures.logOnce(
                key = failureKey,
                message = "Theme navigation bitmap draw failed; using built-in icon",
                exception = exception
            )
            with(fallback) {
                draw(
                    size = size,
                    colorFilter = fallbackColorFilter
                )
            }
        }
    }
}

private object NavigationIconRuntimeFailures {
    private const val TAG = "MyLibraryTheme"
    private val loggedKeys = ConcurrentHashMap.newKeySet<String>()

    fun logOnce(
        key: String,
        message: String,
        exception: Throwable? = null
    ) {
        if (!loggedKeys.add(key)) return
        if (exception == null) {
            Log.w(TAG, message)
        } else {
            Log.w(TAG, message, exception)
        }
    }
}
