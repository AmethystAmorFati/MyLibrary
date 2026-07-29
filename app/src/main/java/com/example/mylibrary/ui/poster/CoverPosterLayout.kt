package com.example.mylibrary.ui.poster

internal object CoverPosterLimits {
    const val MAX_WIDTH = 8_192
    const val MAX_HEIGHT = 8_192
    const val MAX_TOTAL_PIXELS = 16_000_000L
    const val MAX_ESTIMATED_ARGB_BYTES = 64L * 1024L * 1024L
    const val ARGB_BYTES_PER_PIXEL = 4L
    const val MIN_CELL_WIDTH = 12
}

internal data class PosterBitmapBudget(
    val width: Int,
    val height: Int,
    val totalPixels: Long,
    val estimatedArgbBytes: Long
)

internal data class PosterGridLayout(
    val columns: Int,
    val rows: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val gap: Int,
    val budget: PosterBitmapBudget
) {
    val width: Int get() = budget.width
    val height: Int get() = budget.height
}

internal data class PosterBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal fun posterGridLayout(
    itemCount: Int,
    preferredCellWidth: Int = 480,
    gap: Int = 12
): PosterGridLayout {
    require(itemCount > 0)
    require(preferredCellWidth >= CoverPosterLimits.MIN_CELL_WIDTH)
    require(gap > 0)
    val columns = when (itemCount) {
        1 -> 1
        in 2..4 -> 2
        in 5..9 -> 3
        else -> 4
    }
    val rows = (itemCount.toLong() + columns - 1L) / columns
    require(rows <= Int.MAX_VALUE.toLong()) { "海报行数过多" }

    val firstEvenWidth = preferredCellWidth - preferredCellWidth % 2
    for (cellWidth in firstEvenWidth downTo CoverPosterLimits.MIN_CELL_WIDTH step 2) {
        val cellHeight = cellWidth * 3 / 2
        for (candidateGap in gap downTo 1) {
            val width = candidateGap.toLong() +
                columns.toLong() * (cellWidth.toLong() + candidateGap)
            val height = candidateGap.toLong() +
                rows * (cellHeight.toLong() + candidateGap)
            val budget = posterBitmapBudgetOrNull(width, height) ?: continue
            return PosterGridLayout(
                columns = columns,
                rows = rows.toInt(),
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                gap = candidateGap,
                budget = budget
            )
        }
    }
    throw IllegalArgumentException(
        "封面数量过多，无法在安全的海报尺寸和内存限制内生成"
    )
}

internal fun requireSafePosterBitmap(
    width: Long,
    height: Long
): PosterBitmapBudget = posterBitmapBudgetOrNull(width, height)
    ?: throw IllegalArgumentException("海报尺寸或内存占用超过安全上限")

private fun posterBitmapBudgetOrNull(
    width: Long,
    height: Long
): PosterBitmapBudget? {
    if (width !in 1..CoverPosterLimits.MAX_WIDTH.toLong()) return null
    if (height !in 1..CoverPosterLimits.MAX_HEIGHT.toLong()) return null
    val totalPixels = width * height
    if (totalPixels > CoverPosterLimits.MAX_TOTAL_PIXELS) return null
    val estimatedBytes = totalPixels * CoverPosterLimits.ARGB_BYTES_PER_PIXEL
    if (estimatedBytes > CoverPosterLimits.MAX_ESTIMATED_ARGB_BYTES) return null
    return PosterBitmapBudget(
        width = width.toInt(),
        height = height.toInt(),
        totalPixels = totalPixels,
        estimatedArgbBytes = estimatedBytes
    )
}

internal fun fitCenterBounds(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): PosterBounds {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(targetWidth > 0 && targetHeight > 0)
    val sourceRatio = sourceWidth.toFloat() / sourceHeight
    val targetRatio = targetWidth.toFloat() / targetHeight
    return if (sourceRatio > targetRatio) {
        val height = targetWidth / sourceRatio
        val top = (targetHeight - height) / 2f
        PosterBounds(0f, top, targetWidth.toFloat(), top + height)
    } else {
        val width = targetHeight * sourceRatio
        val left = (targetWidth - width) / 2f
        PosterBounds(left, 0f, left + width, targetHeight.toFloat())
    }
}

internal fun centerCropSourceBounds(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): PosterBounds {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(targetWidth > 0 && targetHeight > 0)
    val sourceRatio = sourceWidth.toFloat() / sourceHeight
    val targetRatio = targetWidth.toFloat() / targetHeight
    return if (sourceRatio > targetRatio) {
        val width = sourceHeight * targetRatio
        val left = (sourceWidth - width) / 2f
        PosterBounds(left, 0f, left + width, sourceHeight.toFloat())
    } else {
        val height = sourceWidth / targetRatio
        val top = (sourceHeight - height) / 2f
        PosterBounds(0f, top, sourceWidth.toFloat(), top + height)
    }
}
