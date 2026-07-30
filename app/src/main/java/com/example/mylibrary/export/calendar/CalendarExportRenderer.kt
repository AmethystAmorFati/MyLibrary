package com.example.mylibrary.export.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.example.mylibrary.data.image.CoverImageProcessor
import com.example.mylibrary.data.image.resolveStoredCoverFile
import com.example.mylibrary.export.visual.VISUAL_EXPORT_HEIGHT
import com.example.mylibrary.export.visual.VISUAL_EXPORT_WIDTH
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.export.visual.requireVisualExportCanvas
import com.example.mylibrary.ui.poster.centerCropSourceBounds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CalendarExportRenderer {
    private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    suspend fun render(
        context: Context,
        snapshot: CalendarExportSnapshot,
        theme: VisualExportThemeSnapshot,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        renderDispatcher: CoroutineDispatcher = Dispatchers.Default
    ): Bitmap {
        require(snapshot.cells.size == snapshot.rowCount * 7)
        requireVisualExportCanvas()
        val layout = calendarExportLayout(snapshot.rowCount)
        val output = withContext(renderDispatcher) {
            Bitmap.createBitmap(
                VISUAL_EXPORT_WIDTH,
                VISUAL_EXPORT_HEIGHT,
                Bitmap.Config.ARGB_8888
            ).also { bitmap ->
                val canvas = Canvas(bitmap)
                drawBackground(canvas, theme)
                drawHeader(canvas, snapshot, layout, theme)
            }
        }
        var completed = false
        try {
            withContext(renderDispatcher) {
                CalendarSnapshotSceneRenderer.drawCells(
                    canvas = Canvas(output),
                    snapshot = snapshot,
                    dayCells = layout.dayCells.map { it.toRectF() },
                    style = CalendarSceneStyle(
                        dateTextSize = 34f,
                        emptyCellInset = 3f,
                        emptyCellCornerRadius = 12f,
                        emptyCellAlpha = calendarExportVisualPolicy.emptyCellAlpha,
                        coverCornerRadius =
                            calendarExportVisualPolicy.coverCornerRadius
                    ),
                    theme = theme,
                    coverLoader = CalendarSceneCoverLoader { cover, maxDimension ->
                        withContext(ioDispatcher) {
                            loadCover(context, cover, maxDimension)
                        }
                    }
                )
            }
            completed = true
            return output
        } finally {
            if (!completed) output.recycle()
        }
    }

    private fun drawBackground(
        canvas: Canvas,
        theme: VisualExportThemeSnapshot
    ) {
        canvas.drawColor(theme.backgroundColor)
        val background = theme.backgroundBitmap ?: return
        drawCenterCrop(
            canvas,
            background,
            ExportIntRect(0, 0, VISUAL_EXPORT_WIDTH, VISUAL_EXPORT_HEIGHT)
        )
    }

    private fun drawHeader(
        canvas: Canvas,
        snapshot: CalendarExportSnapshot,
        layout: CalendarExportLayout,
        theme: VisualExportThemeSnapshot
    ) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textPrimary
            textAlign = Paint.Align.CENTER
            textSize = 64f
            typeface = theme.headingTypeface
        }
        canvas.drawText(
            "${snapshot.yearMonth.year}年${snapshot.yearMonth.monthValue}月",
            layout.titleCenterX,
            layout.titleBaseline,
            titlePaint
        )
        val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textSecondary
            textAlign = Paint.Align.CENTER
            textSize = 30f
            typeface = theme.contentTypeface
        }
        weekdayLabels.forEachIndexed { index, label ->
            canvas.drawText(
                label,
                layout.weekdayCenters[index],
                layout.weekdayBaseline,
                weekdayPaint
            )
        }
    }

    private fun loadCover(
        context: Context,
        cover: CalendarExportCover,
        maxDimension: Int
    ): Bitmap? {
        listOf(cover.coverPath, cover.thumbnailPath)
            .filterNotNull()
            .filter(String::isNotBlank)
            .distinct()
            .forEach { path ->
                try {
                    val bitmap = resolveStoredCoverFile(context, path)?.let { file ->
                        CoverImageProcessor.decodeSampledFile(
                            file,
                            maxDimension
                        )
                    }
                    if (bitmap != null) return bitmap
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to decode calendar cover: $path", error)
                }
            }
        return null
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        target: ExportIntRect
    ) {
        val source = centerCropSourceBounds(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            targetWidth = target.width,
            targetHeight = target.height
        )
        canvas.drawBitmap(
            bitmap,
            Rect(
                source.left.toInt(),
                source.top.toInt(),
                source.right.toInt(),
                source.bottom.toInt()
            ),
            target.toRectF(),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun ExportIntRect.toRectF(): RectF =
        RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private const val TAG = "CalendarExport"
}
