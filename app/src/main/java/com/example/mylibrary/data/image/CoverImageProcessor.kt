package com.example.mylibrary.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

object CoverImageProcessor {
    const val THUMBNAIL_MAX_EDGE = 480

    fun decodeThumbnail(
        openStream: () -> InputStream,
        maxEdge: Int = THUMBNAIL_MAX_EDGE
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片格式" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        val decoded = openStream().use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("无法解码图片")
        return createThumbnail(decoded, maxEdge)
    }

    fun createThumbnail(source: Bitmap, maxEdge: Int = THUMBNAIL_MAX_EDGE): Bitmap {
        val (width, height) = calculateThumbnailSize(source.width, source.height, maxEdge)
        if (source.width == width && source.height == height && !source.hasAlpha()) {
            return source
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                source,
                null,
                Rect(0, 0, width, height),
                null
            )
        }
        if (output !== source) source.recycle()
        return output
    }

    fun calculateThumbnailSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxEdge > 0)
        val scale = minOf(1f, maxEdge.toFloat() / max(width, height))
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    fun calculateSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (max(width / (sample * 2), height / (sample * 2)) >= maxEdge) {
            sample *= 2
        }
        return sample
    }

    fun decodeSampledFile(file: File, maxEdge: Int): Bitmap? {
        require(maxEdge > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
        if (max(decoded.width, decoded.height) <= maxEdge) return decoded
        val (width, height) = calculateThumbnailSize(decoded.width, decoded.height, maxEdge)
        return Bitmap.createScaledBitmap(decoded, width, height, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }
}
