package com.example.mylibrary.export.calendar

import com.example.mylibrary.export.visual.VISUAL_EXPORT_HEIGHT
import com.example.mylibrary.export.visual.VISUAL_EXPORT_WIDTH
import com.example.mylibrary.ui.home.calendarCoverPlacements
import com.example.mylibrary.ui.home.CALENDAR_COVER_ASPECT_RATIO
import com.example.mylibrary.ui.theme.CalendarCellAspectRatio
import com.example.mylibrary.ui.theme.CalendarDayRowHeight
import com.example.mylibrary.ui.theme.CalendarDaySpacing
import kotlin.math.roundToInt

data class ExportIntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class CalendarExportLayout(
    val titleCenterX: Float,
    val titleBaseline: Float,
    val weekdayBaseline: Float,
    val weekdayCenters: List<Float>,
    val dayCells: List<ExportIntRect>,
    val rowCount: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val cellGap: Int,
    val gridTop: Int,
    val gridBottom: Int,
    val weekdayGridGap: Int
)

fun calendarExportLayout(rowCount: Int): CalendarExportLayout {
    require(rowCount in 4..6)
    val horizontalBudget = VISUAL_EXPORT_WIDTH - HORIZONTAL_MARGIN * 2
    val cellWidth = (horizontalBudget / 7 downTo 1).first { width ->
        val height = (width / CalendarCellAspectRatio).roundToInt()
        val gap = exportSpacingFor(height)
        width * 7 + gap * 6 <= horizontalBudget
    }
    val cellHeight = (cellWidth / CalendarCellAspectRatio).roundToInt()
    val cellGap = exportSpacingFor(cellHeight)
    val gridWidth = cellWidth * 7 + cellGap * 6
    val gridHeight = cellHeight * rowCount + cellGap * (rowCount - 1)
    val contentHeight =
        TITLE_BLOCK_HEIGHT +
            TITLE_TO_WEEKDAY_GAP +
            WEEKDAY_BLOCK_HEIGHT +
            WEEKDAY_TO_GRID_GAP +
            gridHeight
    val contentTop = (VISUAL_EXPORT_HEIGHT - contentHeight) / 2
    val weekdayTop =
        contentTop + TITLE_BLOCK_HEIGHT + TITLE_TO_WEEKDAY_GAP
    val gridTop =
        weekdayTop + WEEKDAY_BLOCK_HEIGHT + WEEKDAY_TO_GRID_GAP
    val gridLeft = (VISUAL_EXPORT_WIDTH - gridWidth) / 2
    val cells = List(rowCount * 7) { index ->
        val column = index % 7
        val row = index / 7
        ExportIntRect(
            left = gridLeft + column * (cellWidth + cellGap),
            top = gridTop + row * (cellHeight + cellGap),
            right = gridLeft + column * (cellWidth + cellGap) + cellWidth,
            bottom = gridTop + row * (cellHeight + cellGap) + cellHeight
        )
    }
    return CalendarExportLayout(
        titleCenterX = VISUAL_EXPORT_WIDTH / 2f,
        titleBaseline = contentTop + TITLE_BASELINE_IN_BLOCK,
        weekdayBaseline = weekdayTop + WEEKDAY_BASELINE_IN_BLOCK,
        weekdayCenters = List(7) { index ->
            gridLeft + index * (cellWidth + cellGap) + cellWidth / 2f
        },
        dayCells = cells,
        rowCount = rowCount,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        cellGap = cellGap,
        gridTop = gridTop,
        gridBottom = gridTop + gridHeight,
        weekdayGridGap = WEEKDAY_TO_GRID_GAP
    )
}

fun calendarCoverSlotRects(
    target: ExportIntRect,
    count: Int
): List<ExportIntRect> {
    require(count in 1..4)
    require(target.width > 0 && target.height > 0)
    val outer = calendarCoverOuterRect(target)
    return calendarCoverPlacements(count)
        .sortedBy { it.zIndex }
        .map { placement ->
            val left = outer.left +
                (placement.leftFraction * outer.width).roundToInt()
            val top = outer.top +
                (placement.topFraction * outer.height).roundToInt()
            val right = outer.left +
                (
                    (placement.leftFraction + placement.widthFraction) *
                        outer.width
                    ).roundToInt()
            val bottom = outer.top +
                (
                    (placement.topFraction + placement.heightFraction) *
                        outer.height
                    ).roundToInt()
            ExportIntRect(left, top, right, bottom)
        }
}

fun calendarCoverOuterRect(target: ExportIntRect): ExportIntRect {
    require(target.width > 0 && target.height > 0)
    val width = minOf(
        target.width,
        (target.height * CALENDAR_COVER_ASPECT_RATIO).roundToInt()
    )
    val height = minOf(
        target.height,
        (width / CALENDAR_COVER_ASPECT_RATIO).roundToInt()
    )
    val left = target.left + (target.width - width) / 2
    val top = target.top + (target.height - height) / 2
    return ExportIntRect(left, top, left + width, top + height)
}

internal data class CalendarExportVisualPolicy(
    val coverCornerRadius: Float = CALENDAR_EXPORT_COVER_CORNER_RADIUS,
    val emptyCellDrawsStroke: Boolean = false,
    val emptyDateUsesSecondaryText: Boolean = true,
    val emptyCellAlpha: Int = 84
)

internal val calendarExportVisualPolicy = CalendarExportVisualPolicy()

private fun exportSpacingFor(cellHeight: Int): Int =
    (
        cellHeight *
            CalendarDaySpacing.value /
            CalendarDayRowHeight.value
        ).roundToInt().coerceAtLeast(1)

private const val HORIZONTAL_MARGIN = 36
private const val TITLE_BLOCK_HEIGHT = 78
private const val TITLE_BASELINE_IN_BLOCK = 64f
private const val TITLE_TO_WEEKDAY_GAP = 34
private const val WEEKDAY_BLOCK_HEIGHT = 40
private const val WEEKDAY_BASELINE_IN_BLOCK = 30f
private const val WEEKDAY_TO_GRID_GAP = 14
internal const val CALENDAR_EXPORT_COVER_CORNER_RADIUS = 10f
