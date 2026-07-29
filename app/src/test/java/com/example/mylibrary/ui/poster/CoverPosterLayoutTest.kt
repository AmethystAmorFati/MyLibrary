package com.example.mylibrary.ui.poster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class CoverPosterLayoutTest {
    @Test
    fun everyPosterCellUsesTwoByThreeCanvas() {
        listOf(1, 4, 9, 16, 40, 100, 1_000).forEach { count ->
            val layout = posterGridLayout(count)
            assertEquals(layout.cellWidth * 3, layout.cellHeight * 2)
            assertTrue(layout.width <= CoverPosterLimits.MAX_WIDTH)
            assertTrue(layout.height <= CoverPosterLimits.MAX_HEIGHT)
            assertTrue(layout.budget.totalPixels <= CoverPosterLimits.MAX_TOTAL_PIXELS)
            assertTrue(
                layout.budget.estimatedArgbBytes <=
                    CoverPosterLimits.MAX_ESTIMATED_ARGB_BYTES
            )
        }
    }

    @Test
    fun largePosterAutomaticallyReducesCellSizeBeforeRendering() {
        val small = posterGridLayout(4)
        val large = posterGridLayout(1_000)

        assertTrue(large.cellWidth < small.cellWidth)
        assertTrue(large.width > 0)
        assertTrue(large.height > 0)
    }

    @Test
    fun impossiblePosterFailsBeforeBitmapCreation() {
        try {
            posterGridLayout(Int.MAX_VALUE)
            fail("Expected unsafe poster to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun bitmapBudgetRejectsIndependentLimitsAndLongOverflowInputs() {
        listOf(
            (CoverPosterLimits.MAX_WIDTH + 1L) to 1L,
            1L to (CoverPosterLimits.MAX_HEIGHT + 1L),
            8_000L to 8_000L,
            Long.MAX_VALUE to Long.MAX_VALUE
        ).forEach { (width, height) ->
            try {
                requireSafePosterBitmap(width, height)
                fail("Expected $width x $height to be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test
    fun failedAtomicExportLeavesNoTemporaryOrOutputFile() {
        val directory = Files.createTempDirectory("poster-export-test").toFile()
        try {
            try {
                writePosterFileAtomically(directory) { staging ->
                    staging.writeBytes(byteArrayOf(1, 2, 3))
                    error("encoding failed")
                }
                fail("Expected export failure")
            } catch (_: IllegalStateException) {
                // Expected.
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun fitCenterKeepsWholeWideCoverInsideCanvas() {
        val bounds = fitCenterBounds(2_000, 1_000, 400, 600)

        assertEquals(400f, bounds.width)
        assertEquals(200f, bounds.height)
        assertEquals(200f, bounds.top)
    }

    @Test
    fun centerCropUsesMiddleOfWideCoverForBackground() {
        val bounds = centerCropSourceBounds(2_000, 1_000, 400, 600)

        assertEquals(666.6667f, bounds.width, 0.01f)
        assertEquals(666.6667f, bounds.left, 0.01f)
    }
}
