package com.example.mylibrary.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CalendarCellAspectRatio
import com.example.mylibrary.ui.theme.CalendarDaySpacing
import java.time.LocalDate

@Composable
fun AnnualCalendarMonth(
    month: AnnualCalendarMonthUiModel,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val yearMonth = month.yearMonth
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${yearMonth.year}\u5E74" +
                "${yearMonth.monthValue.toString().padStart(2, '0')}\u6708",
            style = AppTheme.typography.sectionTitle,
            color = AppTheme.colors.textPrimary
        )
        WeekdayHeader()
        month.weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CalendarDaySpacing)
            ) {
                repeat(7) { column ->
                    val day = week.getOrNull(column)
                    if (day == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(CalendarCellAspectRatio)
                        )
                    } else {
                        AnnualDayCell(
                            day = day,
                            isSelected = day.date == selectedDate,
                            isToday = day.date == today,
                            onClick = { onDateSelected(day.date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnualDayCell(
    day: AnnualCalendarDayUiModel,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CalendarDayVisual(
        date = day.date,
        isInDisplayedMonth = day.isInDisplayedMonth,
        isSelected = isSelected,
        isToday = isToday,
        activities = day.coverActivities,
        activitiesArePrepared = true,
        onClick = onClick,
        modifier = modifier
            .aspectRatio(CalendarCellAspectRatio)
    )
}
