package com.example.mylibrary.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.example.mylibrary.ui.navigation.NavigationIconAsset
import com.example.mylibrary.ui.navigation.NavigationIconResource
import com.example.mylibrary.ui.navigation.NavigationIconSlot
import com.example.mylibrary.ui.navigation.NavigationIconState
import com.example.mylibrary.ui.navigation.ThemeNavigationIconCacheKey
import java.util.LinkedHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

data class ThemeNavigationIconDecodeBucket(
    val maximumWidth: Int = ThemeResourceLimits.NAVIGATION_DECODE_MAX_SIDE,
    val maximumHeight: Int = ThemeResourceLimits.NAVIGATION_DECODE_MAX_SIDE,
    val key: String = "navigation-${ThemeResourceLimits.NAVIGATION_DECODE_MAX_SIDE}"
) {
    init {
        require(maximumWidth > 0)
        require(maximumHeight > 0)
        require(key.isNotBlank())
    }
}

sealed interface ThemeNavigationIconDecodeResult {
    data class Success(
        val asset: NavigationIconAsset.Bitmap
    ) : ThemeNavigationIconDecodeResult

    data class Failure(
        val error: ThemeResolveError
    ) : ThemeNavigationIconDecodeResult
}

sealed interface ThemeNavigationIconLoadResult {
    data class Success(
        val resources: Map<NavigationIconSlot, NavigationIconResource>
    ) : ThemeNavigationIconLoadResult

    data class Failure(
        val error: ThemeResolveError
    ) : ThemeNavigationIconLoadResult
}

internal fun interface ThemeNavigationBitmapDecoder {
    fun decode(
        image: ValidatedThemeNavigationIconFile,
        bucket: ThemeNavigationIconDecodeBucket,
        cacheKey: ThemeNavigationIconCacheKey
    ): ThemeNavigationIconDecodeResult
}

internal object AndroidThemeNavigationBitmapDecoder :
    ThemeNavigationBitmapDecoder {
    override fun decode(
        image: ValidatedThemeNavigationIconFile,
        bucket: ThemeNavigationIconDecodeBucket,
        cacheKey: ThemeNavigationIconCacheKey
    ): ThemeNavigationIconDecodeResult {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        try {
            BitmapFactory.decodeFile(image.file.absolutePath, bounds)
        } catch (error: OutOfMemoryError) {
            return failure(image, "Insufficient memory while reading icon bounds")
        } catch (exception: Exception) {
            return failure(
                image,
                exception.message ?: exception::class.java.simpleName
            )
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return failure(image, "Android could not read icon bounds")
        }
        if (bounds.outWidth != image.width || bounds.outHeight != image.height) {
            return failure(
                image,
                "Android bounds disagree with the validated icon header"
            )
        }
        val actualFormat = when (bounds.outMimeType?.lowercase()) {
            "image/png" -> ThemeImageFormat.PNG
            "image/webp" -> ThemeImageFormat.WEBP
            else -> null
        }
        if (actualFormat != image.format) {
            return failure(
                image,
                "Android decoded ${bounds.outMimeType ?: "an unknown format"} " +
                    "instead of ${image.format}"
            )
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maximumWidth = bucket.maximumWidth,
                maximumHeight = bucket.maximumHeight
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inMutable = false
        }
        val bitmap = try {
            BitmapFactory.decodeFile(image.file.absolutePath, options)
        } catch (error: OutOfMemoryError) {
            return failure(image, "Insufficient memory while decoding icon")
        } catch (exception: Exception) {
            return failure(
                image,
                exception.message ?: exception::class.java.simpleName
            )
        } ?: return failure(image, "Android returned no decoded icon bitmap")

        if (
            bitmap.width > bucket.maximumWidth ||
            bitmap.height > bucket.maximumHeight
        ) {
            return failure(
                image,
                "Decoded icon exceeds its ${bucket.key} target bucket"
            )
        }

        return ThemeNavigationIconDecodeResult.Success(
            NavigationIconAsset.Bitmap(
                imageBitmap = bitmap.asImageBitmap(),
                rendering = image.rendering,
                cacheKey = cacheKey
            )
        )
    }

    private fun failure(
        image: ValidatedThemeNavigationIconFile,
        reason: String
    ): ThemeNavigationIconDecodeResult.Failure =
        ThemeNavigationIconDecodeResult.Failure(
            ThemeResolveError.NavigationIconDecodeFailed(
                slot = image.variant.slot,
                state = image.variant.state,
                relativePath = image.relativePath,
                reason = reason
            )
        )
}

internal class ThemeNavigationIconCache(
    private val maximumEntries: Int =
        ThemeResourceLimits.MAX_NAVIGATION_IMAGE_CACHE_ENTRIES,
    private val maximumBytes: Long =
        ThemeResourceLimits.MAX_NAVIGATION_IMAGE_CACHE_BYTES
) {
    private val entries = LinkedHashMap<
        ThemeNavigationIconCacheKey,
        NavigationIconAsset.Bitmap
        >(maximumEntries, 0.75f, true)
    private val inFlight = mutableMapOf<
        ThemeNavigationIconCacheKey,
        FutureTask<ThemeNavigationIconDecodeResult>
        >()
    private var currentBytes = 0L

    init {
        require(maximumEntries > 0)
        require(maximumBytes > 0L)
    }

    fun getOrLoad(
        key: ThemeNavigationIconCacheKey,
        loader: () -> ThemeNavigationIconDecodeResult
    ): ThemeNavigationIconDecodeResult {
        synchronized(entries) {
            entries[key]?.let {
                return ThemeNavigationIconDecodeResult.Success(it)
            }
        }

        val candidate = FutureTask(loader)
        val (task, ownsTask) = synchronized(inFlight) {
            val existing = inFlight[key]
            if (existing == null) {
                inFlight[key] = candidate
                candidate to true
            } else {
                existing to false
            }
        }
        if (ownsTask) task.run()

        val result = try {
            task.get()
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            failure(key, "Icon decode was interrupted")
        } catch (exception: ExecutionException) {
            failure(
                key,
                exception.cause?.message
                    ?: exception.message
                    ?: "Icon decode failed"
            )
        }

        if (result is ThemeNavigationIconDecodeResult.Success) {
            synchronized(entries) {
                entries.put(key, result.asset)?.let {
                    currentBytes -= it.estimatedByteCount
                }
                currentBytes += result.asset.estimatedByteCount
                while (
                    entries.size > maximumEntries ||
                    currentBytes > maximumBytes
                ) {
                    val eldestKey = entries.entries.iterator().next().key
                    entries.remove(eldestKey)?.let {
                        currentBytes -= it.estimatedByteCount
                    }
                }
            }
        }
        if (ownsTask) {
            synchronized(inFlight) {
                if (inFlight[key] === task) inFlight.remove(key)
            }
        }
        return result
    }

    fun clear() {
        synchronized(entries) {
            // Never recycle. A ResolvedTheme may still own the bitmap after the
            // cache releases its reference.
            entries.clear()
            currentBytes = 0L
        }
    }

    internal fun entryCount(): Int = synchronized(entries) { entries.size }
    internal fun byteCount(): Long = synchronized(entries) { currentBytes }

    private fun failure(
        key: ThemeNavigationIconCacheKey,
        reason: String
    ): ThemeNavigationIconDecodeResult.Failure =
        ThemeNavigationIconDecodeResult.Failure(
            ThemeResolveError.NavigationIconDecodeFailed(
                slot = key.slot,
                state = key.state,
                relativePath = key.relativePath,
                reason = reason
            )
        )
}

private object DefaultThemeNavigationIconCache {
    val instance = ThemeNavigationIconCache()
}

fun clearThemeNavigationIconCache() {
    DefaultThemeNavigationIconCache.instance.clear()
}

internal class ThemeNavigationIconLoader(
    private val resources: ThemeResourceProvider,
    private val themeId: String,
    private val themeVersion: String,
    private val themeGeneration: Long,
    private val decodeBucket: ThemeNavigationIconDecodeBucket =
        ThemeNavigationIconDecodeBucket(),
    private val cache: ThemeNavigationIconCache =
        DefaultThemeNavigationIconCache.instance,
    private val decoder: ThemeNavigationBitmapDecoder =
        AndroidThemeNavigationBitmapDecoder
) {
    fun load(
        navigation: ThemeNavigationManifest
    ): ThemeNavigationIconLoadResult {
        val validation = ThemeNavigationIconFileValidator.validateDeclaredFiles(
            navigation = navigation,
            resources = resources
        )
        if (validation is ThemeNavigationIconFileValidationResult.Failure) {
            return ThemeNavigationIconLoadResult.Failure(validation.error)
        }

        val assets = linkedMapOf<
            NavigationIconVariant,
            NavigationIconAsset.Bitmap
            >()
        (validation as ThemeNavigationIconFileValidationResult.Success)
            .images
            .forEach { (variant, image) ->
                val key = ThemeNavigationIconCacheKey(
                    themeId = themeId,
                    themeVersion = themeVersion,
                    themeGeneration = themeGeneration,
                    slot = variant.slot,
                    state = variant.state,
                    relativePath = image.relativePath,
                    canonicalPath = image.canonicalPath,
                    fileSize = image.fileSize,
                    lastModified = image.lastModified,
                    decodeBucket = decodeBucket.key
                )
                val result = cache.getOrLoad(key) {
                    decoder.decode(image, decodeBucket, key)
                }
                if (result is ThemeNavigationIconDecodeResult.Failure) {
                    return ThemeNavigationIconLoadResult.Failure(result.error)
                }
                assets[variant] =
                    (result as ThemeNavigationIconDecodeResult.Success).asset
            }

        val resolved = navigation.entries().associate { (slot, definition) ->
            val normal = assets.getValue(
                NavigationIconVariant(slot, NavigationIconState.NORMAL)
            )
            val selected = definition.selected?.let {
                assets.getValue(
                    NavigationIconVariant(slot, NavigationIconState.SELECTED)
                )
            }
            slot to NavigationIconResource(normal = normal, selected = selected)
        }
        return ThemeNavigationIconLoadResult.Success(resolved)
    }
}
