package com.example.mylibrary.export.annualposter

import com.example.mylibrary.export.calendar.ExportIntRect
import com.example.mylibrary.ui.poster.requireSafePosterBitmap
import kotlin.math.abs
import kotlin.math.roundToInt

data class AnnualPosterCell(
    val itemIndex: Int,
    val bounds: ExportIntRect
)

data class AnnualPosterRow(
    val itemIndices: IntRange,
    val top: Int,
    val height: Int,
    val cells: List<AnnualPosterCell>
) {
    val bottom: Int get() = top + height
}

data class AnnualPosterLayout(
    val width: Int,
    val height: Int,
    val rows: List<AnnualPosterRow>,
    val cells: List<AnnualPosterCell>,
    val targetRowHeight: Int,
    val horizontalGap: Int = 0,
    val verticalGap: Int = 0,
    val outerMargin: Int = 0
) {
    val totalPixels: Long
        get() = width.toLong() * height.toLong()
}

class AnnualPosterTooLargeException :
    IllegalArgumentException("年度封面过多，无法安全生成")

fun annualPosterLayout(items: List<AnnualPosterItem>): AnnualPosterLayout {
    require(items.isNotEmpty())
    val ratios = items.map { item ->
        requireNotNull(item.resolvedAspectRatio) {
            "年度海报布局需要已解析的真实封面比例"
        }
    }
    return annualPosterLayoutForRatios(ratios)
}

fun annualPosterLayoutForRatios(
    aspectRatios: List<Double>
): AnnualPosterLayout {
    require(aspectRatios.isNotEmpty())
    require(aspectRatios.all { it.isFinite() && it > 0.0 })
    val attemptedRowCounts = mutableSetOf<Int>()
    TARGET_ROW_HEIGHT_CANDIDATES.forEach { targetHeight ->
        val desiredRows = (
            aspectRatios.sum() * targetHeight / ANNUAL_POSTER_WIDTH
            ).roundToInt().coerceIn(1, aspectRatios.size)
        val minimumRows = (
            aspectRatios.size.toLong() + MAX_ITEMS_PER_ROW - 1L
            ).div(MAX_ITEMS_PER_ROW)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val rowCount = maxOf(desiredRows, minimumRows)
        if (!attemptedRowCounts.add(rowCount)) return@forEach
        val candidate = buildLayout(
            aspectRatios = aspectRatios,
            rowCount = rowCount,
            targetRowHeight = targetHeight
        ) ?: return@forEach
        val isSafe = try {
            requireSafePosterBitmap(
                width = candidate.width.toLong(),
                height = candidate.height.toLong()
            )
            true
        } catch (_: IllegalArgumentException) {
            false
        }
        if (isSafe) return candidate
    }
    throw AnnualPosterTooLargeException()
}

private fun buildLayout(
    aspectRatios: List<Double>,
    rowCount: Int,
    targetRowHeight: Int
): AnnualPosterLayout? {
    val ranges = balancedSequentialRanges(aspectRatios, rowCount)
    val rows = ArrayList<AnnualPosterRow>(ranges.size)
    val cells = ArrayList<AnnualPosterCell>(aspectRatios.size)
    var top = 0L
    ranges.forEach { range ->
        if (range.count() > ANNUAL_POSTER_WIDTH) return null
        val rowRatios = range.map(aspectRatios::get)
        val ratioSum = rowRatios.sum()
        val rowHeight = (ANNUAL_POSTER_WIDTH / ratioSum)
            .roundToInt()
            .coerceAtLeast(1)
        if (top + rowHeight > Int.MAX_VALUE.toLong()) return null
        val rowTop = top.toInt()
        var left = 0
        var cumulativeRatio = 0.0
        val rowCells = range.mapIndexed { localIndex, itemIndex ->
            cumulativeRatio += aspectRatios[itemIndex]
            val remainingItems = range.last - itemIndex
            val right = if (localIndex == rowRatios.lastIndex) {
                ANNUAL_POSTER_WIDTH
            } else {
                (ANNUAL_POSTER_WIDTH * cumulativeRatio / ratioSum)
                    .roundToInt()
                    .coerceIn(
                        left + 1,
                        ANNUAL_POSTER_WIDTH - remainingItems
                    )
            }
            AnnualPosterCell(
                itemIndex = itemIndex,
                bounds = ExportIntRect(
                    left = left,
                    top = rowTop,
                    right = right,
                    bottom = rowTop + rowHeight
                )
            ).also {
                left = right
                cells += it
            }
        }
        if (rowCells.any { it.bounds.width <= 0 }) return null
        rows += AnnualPosterRow(
            itemIndices = range,
            top = rowTop,
            height = rowHeight,
            cells = rowCells
        )
        top += rowHeight
    }
    if (top !in 1..Int.MAX_VALUE.toLong()) return null
    return AnnualPosterLayout(
        width = ANNUAL_POSTER_WIDTH,
        height = top.toInt(),
        rows = rows,
        cells = cells,
        targetRowHeight = targetRowHeight
    )
}

private fun balancedSequentialRanges(
    aspectRatios: List<Double>,
    rowCount: Int
): List<IntRange> {
    require(rowCount in 1..aspectRatios.size)
    val result = ArrayList<IntRange>(rowCount)
    var start = 0
    var remainingRatio = aspectRatios.sum()
    repeat(rowCount) { rowIndex ->
        val remainingRows = rowCount - rowIndex
        if (remainingRows == 1) {
            result += start..aspectRatios.lastIndex
            return@repeat
        }
        val targetRatio = remainingRatio / remainingRows
        val maximumEndExclusive =
            aspectRatios.size - (remainingRows - 1)
        var endExclusive = start
        var rowRatio = 0.0
        while (endExclusive < maximumEndExclusive) {
            val withNext = rowRatio + aspectRatios[endExclusive]
            if (
                rowRatio > 0.0 &&
                abs(rowRatio - targetRatio) <=
                abs(withNext - targetRatio)
            ) {
                break
            }
            rowRatio = withNext
            endExclusive += 1
        }
        if (endExclusive == start) {
            rowRatio = aspectRatios[endExclusive]
            endExclusive += 1
        }
        result += start until endExclusive
        remainingRatio -= rowRatio
        start = endExclusive
    }
    check(result.size == rowCount)
    check(result.sumOf { it.count() } == aspectRatios.size)
    return result
}

const val ANNUAL_POSTER_WIDTH = 1_080
private const val MAX_ITEMS_PER_ROW = 90
private val TARGET_ROW_HEIGHT_CANDIDATES = listOf(
    480,
    520,
    420,
    360,
    320,
    280,
    240,
    200,
    160,
    120,
    96,
    72,
    48,
    32,
    24,
    18,
    12
)
