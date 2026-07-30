package com.example.mylibrary.export.annualposter

import com.example.mylibrary.ui.poster.CoverPosterLimits
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualPosterLayoutTest {
    @Test
    fun everyRowExactlyFillsTheFixedWidthWithoutGapsOrMargins() {
        val layout = annualPosterLayoutForRatios(
            listOf(2.0 / 3.0, 0.72, 1.0, 1.5, 0.6, 0.8, 1.2)
        )

        assertEquals(ANNUAL_POSTER_WIDTH, layout.width)
        assertEquals(0, layout.horizontalGap)
        assertEquals(0, layout.verticalGap)
        assertEquals(0, layout.outerMargin)
        layout.rows.forEach { row ->
            assertEquals(0, row.cells.first().bounds.left)
            assertEquals(ANNUAL_POSTER_WIDTH, row.cells.last().bounds.right)
            assertEquals(
                ANNUAL_POSTER_WIDTH,
                row.cells.sumOf { it.bounds.width }
            )
            row.cells.zipWithNext().forEach { (left, right) ->
                assertEquals(left.bounds.right, right.bounds.left)
            }
        }
    }

    @Test
    fun destinationRatiosFollowRealCoverRatiosWithinPixelRounding() {
        val ratios = listOf(0.55, 2.0 / 3.0, 0.8, 1.2, 1.6)
        val layout = annualPosterLayoutForRatios(ratios)

        layout.cells.forEach { cell ->
            val renderedRatio =
                cell.bounds.width.toDouble() / cell.bounds.height
            assertTrue(abs(renderedRatio - ratios[cell.itemIndex]) < 0.01)
        }
    }

    @Test
    fun lastRowUsesOnlyRemainingItemsAndStillFillsTheWidth() {
        val layout = annualPosterLayoutForRatios(
            List(10) { 2.0 / 3.0 }
        )
        val lastRow = layout.rows.last()

        assertTrue(lastRow.cells.isNotEmpty())
        assertEquals(0, lastRow.cells.first().bounds.left)
        assertEquals(ANNUAL_POSTER_WIDTH, lastRow.cells.last().bounds.right)
        assertEquals(10, layout.cells.size)
        assertEquals((0 until 10).toList(), layout.cells.map { it.itemIndex })
    }

    @Test
    fun dynamicHeightIsTheSumOfRowsAndIsNotForcedToCalendarHeight() {
        val layout = annualPosterLayoutForRatios(
            List(10) { 2.0 / 3.0 }
        )

        assertEquals(layout.rows.sumOf { it.height }, layout.height)
        assertEquals(layout.height, layout.rows.last().bottom)
        assertNotEquals(1_440, layout.height)
    }

    @Test
    fun largeSetsAutomaticallyUseDenserRowsWithinBitmapLimits() {
        val layout = annualPosterLayoutForRatios(
            List(600) { 2.0 / 3.0 }
        )

        assertTrue(layout.height <= CoverPosterLimits.MAX_HEIGHT)
        assertTrue(layout.totalPixels <= CoverPosterLimits.MAX_TOTAL_PIXELS)
        assertTrue(
            layout.totalPixels * CoverPosterLimits.ARGB_BYTES_PER_PIXEL <=
                CoverPosterLimits.MAX_ESTIMATED_ARGB_BYTES
        )
        assertTrue(layout.targetRowHeight < 480)
    }

    @Test(expected = AnnualPosterTooLargeException::class)
    fun impossibleAspectRatioFailsBeforeBitmapAllocation() {
        annualPosterLayoutForRatios(listOf(0.000_001))
    }
}
