package com.example.mylibrary.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarRatingLogicTest {
    @Test
    fun halfStarRatingOnlyAcceptsNullOrOneToTen() {
        assertTrue(isValidHalfStarRating(null))
        assertTrue(isValidHalfStarRating(1))
        assertTrue(isValidHalfStarRating(10))
        assertFalse(isValidHalfStarRating(0))
        assertFalse(isValidHalfStarRating(11))
    }

    @Test
    fun clickingSameStarCyclesHalfFullPreviousStar() {
        assertEquals(7, nextHalfStarRating(null, 4))
        assertEquals(8, nextHalfStarRating(7, 4))
        assertEquals(6, nextHalfStarRating(8, 4))
        assertEquals(null, nextHalfStarRating(2, 1))
    }

    @Test
    fun everyStarUsesTheSameThreeStepCycle() {
        (1..5).forEach { star ->
            val half = star * 2 - 1
            val full = star * 2
            val previous = (star - 1) * 2

            assertEquals(half, nextHalfStarRating(null, star))
            assertEquals(full, nextHalfStarRating(half, star))
            assertEquals(previous.takeIf { it > 0 }, nextHalfStarRating(full, star))
        }
    }

    @Test
    fun starFillStateIsContinuous() {
        assertEquals(StarFillState.FULL, starFillState(7, 1))
        assertEquals(StarFillState.FULL, starFillState(7, 2))
        assertEquals(StarFillState.FULL, starFillState(7, 3))
        assertEquals(StarFillState.HALF, starFillState(7, 4))
        assertEquals(StarFillState.EMPTY, starFillState(7, 5))
    }
}
