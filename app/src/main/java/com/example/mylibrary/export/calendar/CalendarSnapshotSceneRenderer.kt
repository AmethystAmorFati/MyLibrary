package com.example.mylibrary.export.calendar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.ui.home.calendarCoverPlacements
import com.example.mylibrary.ui.poster.centerCropSourceBounds

fun interface CalendarSceneCoverLoader {
    suspend fun load(cover: CalendarExportCover, maxDimension: Int): Bitmap?
}

data class CalendarSceneStyle(
    val dateTextSize: Float,
    val emptyCellInset: Float,
    val emptyCellCornerRadius: Float,
    val emptyCellAlpha: Int,
    val coverCornerRadius: Float
)

/**
 * Shared calendar-cell scene used by the full calendar export and the annual
 * report mini calendars. Snapshot semantics and every 1–4 cover placement are
 * therefore identical; callers only provide the target cell bounds and scale.
 */
object CalendarSnapshotSceneRenderer {
    suspend fun drawCells(
        canvas: Canvas,
        snapshot: CalendarExportSnapshot,
        dayCells: List<RectF>,
        style: CalendarSceneStyle,
        theme: VisualExportThemeSnapshot,
        coverLoader: CalendarSceneCoverLoader
    ) {
        require(dayCells.size == snapshot.cells.size)
        snapshot.cells.forEachIndexed { index, day ->
            day ?: return@forEachIndexed
            val target = dayCells[index]
            if (day.showsDateNumber) {
                drawDateDay(canvas, day, target, style, theme)
            } else {
                drawCoverDay(canvas, day, target, style, theme, coverLoader)
            }
        }
    }

    private fun drawDateDay(
        canvas: Canvas,
        day: CalendarExportDay,
        target: RectF,
        style: CalendarSceneStyle,
        theme: VisualExportThemeSnapshot
    ) {
        val rounded = RectF(
            target.left + style.emptyCellInset,
            target.top + style.emptyCellInset,
            target.right - style.emptyCellInset,
            target.bottom - style.emptyCellInset
        )
        if (style.emptyCellAlpha > 0) {
            canvas.drawRoundRect(
                rounded,
                style.emptyCellCornerRadius,
                style.emptyCellCornerRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.placeholderColor
                    alpha = style.emptyCellAlpha
                }
            )
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textSecondary
            textAlign = Paint.Align.CENTER
            textSize = style.dateTextSize
            typeface = theme.contentTypeface
        }
        canvas.drawText(
            day.date.dayOfMonth.toString(),
            rounded.centerX(),
            rounded.centerY() - (datePaint.ascent() + datePaint.descent()) / 2f,
            datePaint
        )
    }

    private suspend fun drawCoverDay(
        canvas: Canvas,
        day: CalendarExportDay,
        target: RectF,
        style: CalendarSceneStyle,
        theme: VisualExportThemeSnapshot,
        coverLoader: CalendarSceneCoverLoader
    ) {
        val outer = calendarCoverOuterRect(target)
        val slots = calendarCoverSlotRects(outer, day.covers.size)
        canvas.save()
        try {
            canvas.clipPath(
                Path().apply {
                    addRoundRect(
                        outer,
                        style.coverCornerRadius,
                        style.coverCornerRadius,
                        Path.Direction.CW
                    )
                }
            )
            day.covers.zip(slots).forEach { (cover, slot) ->
                val bitmap = coverLoader.load(
                    cover,
                    maxOf(slot.width(), slot.height()).toInt().coerceAtLeast(1)
                )
                try {
                    if (bitmap == null) {
                        canvas.drawRect(slot, Paint().apply {
                            color = theme.placeholderColor
                        })
                    } else {
                        drawCenterCrop(canvas, bitmap, slot)
                    }
                } finally {
                    bitmap?.recycle()
                }
            }
        } finally {
            canvas.restore()
        }
    }

    private fun calendarCoverOuterRect(target: RectF): RectF {
        val width = minOf(
            target.width(),
            target.height() * com.example.mylibrary.ui.home.CALENDAR_COVER_ASPECT_RATIO
        )
        val height = minOf(
            target.height(),
            width / com.example.mylibrary.ui.home.CALENDAR_COVER_ASPECT_RATIO
        )
        return RectF(
            target.centerX() - width / 2f,
            target.centerY() - height / 2f,
            target.centerX() + width / 2f,
            target.centerY() + height / 2f
        )
    }

    private fun calendarCoverSlotRects(
        outer: RectF,
        count: Int
    ): List<RectF> = calendarCoverPlacements(count)
        .sortedBy { it.zIndex }
        .map { placement ->
            RectF(
                outer.left + outer.width() * placement.leftFraction,
                outer.top + outer.height() * placement.topFraction,
                outer.left + outer.width() *
                    (placement.leftFraction + placement.widthFraction),
                outer.top + outer.height() *
                    (placement.topFraction + placement.heightFraction)
            )
        }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val source = centerCropSourceBounds(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            targetWidth = target.width().toInt().coerceAtLeast(1),
            targetHeight = target.height().toInt().coerceAtLeast(1)
        )
        canvas.drawBitmap(
            bitmap,
            Rect(
                source.left.toInt(),
                source.top.toInt(),
                source.right.toInt(),
                source.bottom.toInt()
            ),
            target,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }
}
