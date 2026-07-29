package com.example.mylibrary.ui.theme

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import java.util.LinkedHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import kotlin.math.ceil

data class ThemeImageDecodeBucket(
    val maximumWidth: Int,
    val maximumHeight: Int,
    val key: String
) {
    init {
        require(maximumWidth > 0)
        require(maximumHeight > 0)
        require(key.isNotBlank())
    }
}

data class ThemeImageDecodeProfile(
    val background: ThemeImageDecodeBucket,
    val card: ThemeImageDecodeBucket,
    val dialog: ThemeImageDecodeBucket
) {
    operator fun get(role: SurfaceRole): ThemeImageDecodeBucket = when (role) {
        SurfaceRole.BACKGROUND -> background
        SurfaceRole.CARD -> card
        SurfaceRole.DIALOG -> dialog
    }

    companion object {
        fun fromSystemDisplayMetrics(): ThemeImageDecodeProfile {
            val metrics = Resources.getSystem().displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val landscape = width >= height
            val widthLimit = if (landscape) {
                ThemeResourceLimits.BACKGROUND_DECODE_MAX_LONG_SIDE
            } else {
                ThemeResourceLimits.BACKGROUND_DECODE_MAX_SHORT_SIDE
            }
            val heightLimit = if (landscape) {
                ThemeResourceLimits.BACKGROUND_DECODE_MAX_SHORT_SIDE
            } else {
                ThemeResourceLimits.BACKGROUND_DECODE_MAX_LONG_SIDE
            }
            val backgroundWidth = quantize(
                value = width,
                maximum = widthLimit
            )
            val backgroundHeight = quantize(
                value = height,
                maximum = heightLimit
            )
            return ThemeImageDecodeProfile(
                background = ThemeImageDecodeBucket(
                    maximumWidth = backgroundWidth,
                    maximumHeight = backgroundHeight,
                    key = "background-${backgroundWidth}x$backgroundHeight"
                ),
                card = ThemeImageDecodeBucket(
                    maximumWidth = ThemeResourceLimits.CARD_DECODE_MAX_SIDE,
                    maximumHeight = ThemeResourceLimits.CARD_DECODE_MAX_SIDE,
                    key = "card-${ThemeResourceLimits.CARD_DECODE_MAX_SIDE}"
                ),
                dialog = ThemeImageDecodeBucket(
                    maximumWidth = ThemeResourceLimits.DIALOG_DECODE_MAX_SIDE,
                    maximumHeight = ThemeResourceLimits.DIALOG_DECODE_MAX_SIDE,
                    key = "dialog-${ThemeResourceLimits.DIALOG_DECODE_MAX_SIDE}"
                )
            )
        }

        private fun quantize(value: Int, maximum: Int): Int {
            val step = ThemeResourceLimits.BACKGROUND_DECODE_BUCKET_STEP
            val roundedUp = ((value + step - 1) / step) * step
            return roundedUp.coerceIn(step, maximum)
        }
    }
}

sealed interface ThemeImageLoadResult {
    data class Success(
        val images: Map<SurfaceRole, ThemeImageAsset>
    ) : ThemeImageLoadResult

    data class Failure(
        val error: ThemeResolveError
    ) : ThemeImageLoadResult
}

internal fun interface ThemeBitmapDecoder {
    fun decode(
        image: ValidatedThemeImageFile,
        bucket: ThemeImageDecodeBucket,
        cacheKey: ThemeImageCacheKey
    ): ThemeImageLoadResult
}

internal object AndroidThemeBitmapDecoder : ThemeBitmapDecoder {
    override fun decode(
        image: ValidatedThemeImageFile,
        bucket: ThemeImageDecodeBucket,
        cacheKey: ThemeImageCacheKey
    ): ThemeImageLoadResult {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        try {
            BitmapFactory.decodeFile(image.file.absolutePath, bounds)
        } catch (error: OutOfMemoryError) {
            return decodeFailure(image, "Insufficient memory while reading image bounds")
        } catch (exception: Exception) {
            return decodeFailure(
                image,
                exception.message ?: exception::class.java.simpleName
            )
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return decodeFailure(image, "Android could not read image bounds")
        }
        if (bounds.outWidth != image.width || bounds.outHeight != image.height) {
            return decodeFailure(
                image,
                "Android bounds disagree with the validated image header"
            )
        }

        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maximumWidth = bucket.maximumWidth,
            maximumHeight = bucket.maximumHeight
        )
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inMutable = false
        }
        val bitmap = try {
            BitmapFactory.decodeFile(image.file.absolutePath, options)
        } catch (error: OutOfMemoryError) {
            return decodeFailure(image, "Insufficient memory while decoding image")
        } catch (exception: Exception) {
            return decodeFailure(
                image,
                exception.message ?: exception::class.java.simpleName
            )
        } ?: return decodeFailure(image, "Android returned no decoded bitmap")

        if (
            bitmap.width > bucket.maximumWidth ||
            bitmap.height > bucket.maximumHeight
        ) {
            return decodeFailure(
                image,
                "Decoded bitmap exceeds its ${bucket.key} target bucket"
            )
        }

        return ThemeImageLoadResult.Success(
            mapOf(
                image.role to ThemeImageAsset(
                    imageBitmap = bitmap.asImageBitmap(),
                    originalWidth = image.width,
                    originalHeight = image.height,
                    decodedWidth = bitmap.width,
                    decodedHeight = bitmap.height,
                    format = image.format,
                    alphaCapable = image.alphaCapable,
                    cacheKey = cacheKey
                )
            )
        )
    }

    private fun decodeFailure(
        image: ValidatedThemeImageFile,
        reason: String
    ): ThemeImageLoadResult.Failure = ThemeImageLoadResult.Failure(
        ThemeResolveError.ImageDecodeFailed(
            role = image.role,
            relativePath = image.relativePath,
            reason = reason
        )
    )
}

internal class ThemeSurfaceImageCache(
    private val maximumEntries: Int =
        ThemeResourceLimits.MAX_THEME_IMAGE_CACHE_ENTRIES,
    private val maximumBytes: Long =
        ThemeResourceLimits.MAX_THEME_IMAGE_CACHE_BYTES
) {
    private val entries = LinkedHashMap<ThemeImageCacheKey, ThemeImageAsset>(
        maximumEntries,
        0.75f,
        true
    )
    private val inFlight = mutableMapOf<
        ThemeImageCacheKey,
        FutureTask<ThemeImageLoadResult>
        >()
    private var currentBytes = 0L

    init {
        require(maximumEntries > 0)
        require(maximumBytes > 0L)
    }

    fun getOrLoad(
        key: ThemeImageCacheKey,
        loader: () -> ThemeImageLoadResult
    ): ThemeImageLoadResult {
        synchronized(entries) {
            entries[key]?.let { cached ->
                return ThemeImageLoadResult.Success(
                    mapOf(key.role to cached)
                )
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
            ThemeImageLoadResult.Failure(
                ThemeResolveError.ImageDecodeFailed(
                    role = key.role,
                    relativePath = key.relativePath,
                    reason = "Image decode was interrupted"
                )
            )
        } catch (exception: ExecutionException) {
            ThemeImageLoadResult.Failure(
                ThemeResolveError.ImageDecodeFailed(
                    role = key.role,
                    relativePath = key.relativePath,
                    reason = exception.cause?.message
                        ?: exception.message
                        ?: "Image decode failed"
                )
            )
        }

        if (result is ThemeImageLoadResult.Success) {
            val asset = result.images.getValue(key.role)
            synchronized(entries) {
                entries.put(key, asset)?.let { replaced ->
                    currentBytes -= replaced.estimatedByteCount
                }
                currentBytes += asset.estimatedByteCount
                while (
                    entries.size > maximumEntries ||
                    currentBytes > maximumBytes
                ) {
                    val eldestKey = entries.entries.iterator().next().key
                    entries.remove(eldestKey)?.let { removed ->
                        currentBytes -= removed.estimatedByteCount
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
            // Do not recycle bitmaps. ResolvedTheme and in-progress rendering may
            // still own assets after the cache releases its references.
            entries.clear()
            currentBytes = 0L
        }
    }

    internal fun entryCount(): Int = synchronized(entries) { entries.size }
    internal fun byteCount(): Long = synchronized(entries) { currentBytes }
}

private object DefaultThemeSurfaceImageCache {
    val instance = ThemeSurfaceImageCache()
}

fun clearThemeSurfaceImageCache() {
    DefaultThemeSurfaceImageCache.instance.clear()
}

internal class ThemeImageLoader(
    private val resources: ThemeResourceProvider,
    private val themeId: String,
    private val themeVersion: String,
    private val themeGeneration: Long,
    private val decodeProfile: ThemeImageDecodeProfile? = null,
    private val cache: ThemeSurfaceImageCache =
        DefaultThemeSurfaceImageCache.instance,
    private val decoder: ThemeBitmapDecoder = AndroidThemeBitmapDecoder
) {
    fun load(
        surfaces: ThemeSurfaceManifest
    ): ThemeImageLoadResult {
        val validation = ThemeImageFileValidator.validateDeclaredFiles(
            surfaces = surfaces,
            resources = resources
        )
        if (validation is ThemeImageFileValidationResult.Failure) {
            return ThemeImageLoadResult.Failure(validation.error)
        }

        val resolvedDecodeProfile = decodeProfile
            ?: ThemeImageDecodeProfile.fromSystemDisplayMetrics()
        val assets = linkedMapOf<SurfaceRole, ThemeImageAsset>()
        (validation as ThemeImageFileValidationResult.Success)
            .images
            .forEach { (role, image) ->
                val bucket = resolvedDecodeProfile[role]
                val key = ThemeImageCacheKey(
                    themeId = themeId,
                    themeVersion = themeVersion,
                    themeGeneration = themeGeneration,
                    role = role,
                    relativePath = image.relativePath,
                    fileSize = image.fileSize,
                    lastModified = image.lastModified,
                    decodeBucket = bucket.key
                )
                val result = cache.getOrLoad(key) {
                    decoder.decode(image, bucket, key)
                }
                if (result is ThemeImageLoadResult.Failure) return result
                assets[role] = (result as ThemeImageLoadResult.Success)
                    .images
                    .getValue(role)
            }
        return ThemeImageLoadResult.Success(assets)
    }
}

internal fun calculateInSampleSize(
    width: Int,
    height: Int,
    maximumWidth: Int,
    maximumHeight: Int
): Int {
    require(width > 0 && height > 0)
    require(maximumWidth > 0 && maximumHeight > 0)
    var sampleSize = 1
    while (
        ceil(width.toDouble() / sampleSize).toInt() > maximumWidth ||
        ceil(height.toDouble() / sampleSize).toInt() > maximumHeight
    ) {
        sampleSize = sampleSize shl 1
    }
    return sampleSize
}
