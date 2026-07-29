package com.example.mylibrary.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CalendarCellCornerRadius
import com.example.mylibrary.ui.theme.SurfaceRole
import java.time.LocalDate

internal enum class CalendarDayBorderStyle {
    NONE,
    STANDARD,
    TODAY,
    SELECTED
}

internal data class CalendarDayVisualPolicy(
    val showsDateNumber: Boolean,
    val showsEmptySurface: Boolean,
    val borderStyle: CalendarDayBorderStyle
)

internal fun calendarDayVisualPolicy(
    hasCover: Boolean,
    isToday: Boolean,
    isSelected: Boolean
): CalendarDayVisualPolicy =
    if (hasCover) {
        CalendarDayVisualPolicy(
            showsDateNumber = false,
            showsEmptySurface = false,
            borderStyle = CalendarDayBorderStyle.NONE
        )
    } else {
        CalendarDayVisualPolicy(
            showsDateNumber = true,
            showsEmptySurface = true,
            borderStyle = when {
                isSelected -> CalendarDayBorderStyle.SELECTED
                isToday -> CalendarDayBorderStyle.TODAY
                else -> CalendarDayBorderStyle.STANDARD
            }
        )
    }

internal fun hasCalendarCover(activities: List<LibraryActivity>): Boolean =
    activities.any { !it.thumbnailPath.isNullOrBlank() }

internal fun calendarCoverActivities(
    activities: List<LibraryActivity>
): List<LibraryActivity> = orderedActivitiesForCoverStack(
    activities.filter { !it.thumbnailPath.isNullOrBlank() }
)

@Composable
internal fun CalendarDayVisual(
    date: LocalDate,
    isInDisplayedMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    activities: List<LibraryActivity>,
    activitiesArePrepared: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverActivities = remember(activities, activitiesArePrepared) {
        if (activitiesArePrepared) {
            activities
        } else {
            calendarCoverActivities(activities)
        }
    }
    val policy = calendarDayVisualPolicy(
        hasCover = coverActivities.isNotEmpty(),
        isToday = isToday,
        isSelected = isSelected
    )
    val shape = RoundedCornerShape(CalendarCellCornerRadius)
    val colors = AppTheme.colors

    Box(
        modifier = modifier
            .clip(shape)
            .noRippleClickable(onClick = onClick)
            .testTag("calendar_day_$date"),
        contentAlignment = Alignment.Center
    ) {
        if (policy.showsEmptySurface) {
            AppThemeSurface(
                role = SurfaceRole.CARD,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("calendar_day_empty_$date"),
                shape = shape,
                containerAlpha = if (isInDisplayedMonth) 0.72f else 0.34f,
                border = BorderStroke(
                    1.dp,
                    when (policy.borderStyle) {
                        CalendarDayBorderStyle.SELECTED -> colors.accent
                        CalendarDayBorderStyle.TODAY -> colors.border
                        CalendarDayBorderStyle.STANDARD -> colors.subtleBorder
                        CalendarDayBorderStyle.NONE -> colors.subtleBorder
                    }.copy(alpha = if (isInDisplayedMonth) 1f else 0.58f)
                ),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        modifier = Modifier.testTag("calendar_day_number_$date"),
                        style = AppTheme.typography.calendarDay,
                        color = if (isInDisplayedMonth) {
                            colors.textSecondary
                        } else {
                            colors.mutedText.copy(alpha = 0.62f)
                        }
                    )
                }
            }
        } else {
            ActivityArtwork(
                activities = coverActivities,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("calendar_day_artwork_$date"),
                shape = shape
            )
        }
    }
}
