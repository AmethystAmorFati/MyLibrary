package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.util.buildMonthDates
import com.example.mylibrary.util.toLocalDate
import java.time.LocalDate
import java.time.YearMonth

data class AnnualCalendarUiState(
    val year: Int,
    val months: List<AnnualCalendarMonthUiModel> =
        buildAnnualCalendarMonths(year, emptyList()),
    val errorMessage: String? = null
)

data class AnnualCalendarMonthUiModel(
    val yearMonth: YearMonth,
    val weeks: List<List<AnnualCalendarDayUiModel?>>
)

data class AnnualCalendarDayUiModel(
    val date: LocalDate,
    val isInDisplayedMonth: Boolean,
    val coverActivities: List<LibraryActivity>
)

internal fun buildAnnualCalendarUiState(
    year: Int,
    activities: List<LibraryActivity>,
    errorMessage: String? = null
): AnnualCalendarUiState = AnnualCalendarUiState(
    year = year,
    months = buildAnnualCalendarMonths(year, activities),
    errorMessage = errorMessage
)

internal fun buildAnnualCalendarMonths(
    year: Int,
    activities: List<LibraryActivity>
): List<AnnualCalendarMonthUiModel> {
    val coverActivitiesByDate = activities
        .asSequence()
        .filter { !it.thumbnailPath.isNullOrBlank() }
        .groupBy { it.date.toLocalDate() }
        .mapValues { (_, dailyActivities) ->
            orderedActivitiesForCoverStack(dailyActivities)
        }

    return (1..12).map { monthValue ->
        val yearMonth = YearMonth.of(year, monthValue)
        AnnualCalendarMonthUiModel(
            yearMonth = yearMonth,
            weeks = buildMonthDates(yearMonth)
                .map { date ->
                    date?.let {
                        AnnualCalendarDayUiModel(
                            date = it,
                            isInDisplayedMonth = YearMonth.from(it) == yearMonth,
                            coverActivities = coverActivitiesByDate[it].orEmpty()
                        )
                    }
                }
                .chunked(7)
        )
    }
}
