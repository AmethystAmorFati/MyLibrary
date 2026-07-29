package com.example.mylibrary.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDragLogicTest {
    @Test
    fun positionThresholdSettlesCollapsedToExpanded() {
        assertEquals(
            CalendarAnchor.EXPANDED,
            CalendarDragLogic.targetAnchor(
                current = CalendarAnchor.COLLAPSED,
                fraction = 0.46f,
                velocityDpPerSecond = 0f
            )
        )
    }

    @Test
    fun upwardVelocitySettlesExpandedToCollapsed() {
        assertEquals(
            CalendarAnchor.COLLAPSED,
            CalendarDragLogic.targetAnchor(
                current = CalendarAnchor.EXPANDED,
                fraction = 0.8f,
                velocityDpPerSecond = -1_100f
            )
        )
    }

}
