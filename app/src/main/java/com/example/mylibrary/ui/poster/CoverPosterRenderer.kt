package com.example.mylibrary.ui.poster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.example.mylibrary.data.image.CoverImageProcessor
import com.example.mylibrary.data.image.resolveStoredCoverFile
import com.example.mylibrary.domain.model.LibraryItem
import kotlin.math.roundToInt

data class CoverPosterPalette(
    val canvasBackground: Int,
    val cardSurface: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val border: Int,
    val headingTypeface: Typeface,
    val contentTypeface: Typeface
)

internal object CoverPosterRenderer {
    fun render(
        context: Context,
        items: List<LibraryItem>,
        palette: CoverPosterPalette
    ): Bitmap {
        require(items.isNotEmpty()) { "没有可导出的作品" }
        val layout = posterGridLayout(items.size)
        requireSafePosterBitmap(layout.width.toLong(), layout.height.toLong())
        val output = Bitmap.createBitmap(
            layout.width,
            layout.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        canvas.drawColor(palette.canvasBackground)
        items.forEachIndexed { index, item ->
            val column = index % layout.columns
            val row = index / layout.columns
            val left = layout.gap + column * (layout.cellWidth + layout.gap)
            val top = layout.gap + row * (layout.cellHeight + layout.gap)
            val target = Rect(
                left,
                top,
                left + layout.cellWidth,
                top + layout.cellHeight
            )
            drawCell(context, canvas, target, item, palette)
        }
        return output
    }

    private fun drawCell(
        context: Context,
        canvas: Canvas,
        target: Rect,
        item: LibraryItem,
        palette: CoverPosterPalette
    ) {
        val bitmap = listOf(item.coverPath, item.thumbnailPath)
            .filterNotNull()
            .filter(String::isNotBlank)
            .distinct()
            .firstNotNullOfOrNull { path ->
                resolveStoredCoverFile(context, path)?.let { file ->
                    CoverImageProcessor.decodeSampledFile(
                        file = file,
                        maxEdge = target.height() * 2
                    )
                }
            }
        if (bitmap == null) {
            drawPlaceholder(canvas, target, item, palette)
            return
        }
        try {
            drawExtendedBackground(canvas, target, bitmap, palette.cardSurface)
            val fitted = fitCenterBounds(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                targetWidth = target.width(),
                targetHeight = target.height()
            )
            val foreground = RectF(
                target.left + fitted.left,
                target.top + fitted.top,
                target.left + fitted.right,
                target.top + fitted.bottom
            )
            canvas.drawBitmap(
                bitmap,
                null,
                foreground,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawExtendedBackground(
        canvas: Canvas,
        target: Rect,
        source: Bitmap,
        overlayColor: Int
    ) {
        val scale = minOf(1f, 64f / maxOf(source.width, source.height))
        val softWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val softHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        val softened = Bitmap.createScaledBitmap(source, softWidth, softHeight, true)
        try {
            val crop = centerCropSourceBounds(
                sourceWidth = softened.width,
                sourceHeight = softened.height,
                targetWidth = target.width(),
                targetHeight = target.height()
            )
            canvas.drawBitmap(
                softened,
                Rect(
                    crop.left.roundToInt(),
                    crop.top.roundToInt(),
                    crop.right.roundToInt(),
                    crop.bottom.roundToInt()
                ),
                target,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            canvas.drawRect(
                target,
                Paint().apply {
                    color = withAlpha(overlayColor, 86)
                    style = Paint.Style.FILL
                }
            )
        } finally {
            if (softened !== source) softened.recycle()
        }
    }

    private fun drawPlaceholder(
        canvas: Canvas,
        target: Rect,
        item: LibraryItem,
        palette: CoverPosterPalette
    ) {
        canvas.drawRect(target, Paint().apply { color = palette.cardSurface })
        canvas.drawRect(
            target,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.border
                style = Paint.Style.STROKE
                strokeWidth = maxOf(1f, target.width() / 240f)
            }
        )
        val centerX = target.exactCenterX()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryText
            textAlign = Paint.Align.CENTER
            textSize = target.width() * 0.045f
            typeface = palette.contentTypeface
        }
        canvas.drawText(
            item.typeName.uppercase(),
            centerX,
            target.top + target.height() * 0.18f,
            labelPaint
        )
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textAlign = Paint.Align.CENTER
            textSize = target.width() * 0.09f
            typeface = palette.headingTypeface
        }
        canvas.drawText(
            ellipsize(item.title.ifBlank { "未命名作品" }, titlePaint, target.width() * 0.78f),
            centerX,
            target.exactCenterY(),
            titlePaint
        )
        if (item.creator.isNotBlank()) {
            val creatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textAlign = Paint.Align.CENTER
                textSize = target.width() * 0.052f
                typeface = palette.contentTypeface
            }
            canvas.drawText(
                ellipsize(item.creator, creatorPaint, target.width() * 0.78f),
                centerX,
                target.bottom - target.height() * 0.16f,
                creatorPaint
            )
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "…"
        val count = paint.breakText(
            text,
            true,
            (maxWidth - paint.measureText(suffix)).coerceAtLeast(0f),
            null
        )
        return text.take(count) + suffix
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
