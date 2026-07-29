package com.example.mylibrary.ui.home

import com.example.mylibrary.ui.theme.CalendarAnchorThreshold
import com.example.mylibrary.ui.theme.CalendarVelocityThreshold

enum class CalendarAnchor {
    COLLAPSED,
    EXPANDED
}

object CalendarDragLogic {
    fun targetAnchor(
        current: CalendarAnchor,
        fraction: Float,
        velocityDpPerSecond: Float
    ): CalendarAnchor = when {
        velocityDpPerSecond >= CalendarVelocityThreshold.value ->
            CalendarAnchor.EXPANDED
        velocityDpPerSecond <= -CalendarVelocityThreshold.value ->
            CalendarAnchor.COLLAPSED
        fraction >= CalendarAnchorThreshold -> CalendarAnchor.EXPANDED
        fraction < CalendarAnchorThreshold -> CalendarAnchor.COLLAPSED
        else -> current
    }

}
