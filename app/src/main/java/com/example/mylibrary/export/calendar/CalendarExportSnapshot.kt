package com.example.mylibrary.export.calendar

import com.example.mylibrary.domain.model.orderedDistinctActivityCovers
import com.example.mylibrary.export.visual.VisualExportActivity
import com.example.mylibrary.util.buildMonthDates
import com.example.mylibrary.util.toLocalDate
import java.time.LocalDate
import java.time.YearMonth

data class CalendarExportCover(
    val itemId: Long,
    val typeId: Long,
    val title: String,
    val coverPath: String?,
    val thumbnailPath: String?
)

data class CalendarExportDay(
    val date: LocalDate,
    val covers: List<CalendarExportCover>
) {
    val showsDateNumber: Boolean
        get() = covers.isEmpty()
}

data class CalendarExportSnapshot(
    val yearMonth: YearMonth,
    val cells: List<CalendarExportDay?>,
    val sourceActivityCount: Int
) {
    val rowCount: Int
        get() = cells.size / 7
}

fun buildCalendarExportSnapshot(
    yearMonth: YearMonth,
    activities: List<VisualExportActivity>
): CalendarExportSnapshot {
    val inMonth = activities.filter {
        YearMonth.from(it.date.toLocalDate()) == yearMonth
    }
    val coversByDate = inMonth
        .filter { !it.thumbnailPath.isNullOrBlank() }
        .groupBy { it.date.toLocalDate() }
        .mapValues { (_, dailyActivities) ->
            orderedDistinctActivityCovers(
                activities = dailyActivities,
                recordCreatedAt = VisualExportActivity::recordCreatedAt,
                recordId = VisualExportActivity::recordId,
                activityId = VisualExportActivity::activityId,
                itemId = VisualExportActivity::itemId
            ).map { activity ->
                CalendarExportCover(
                    itemId = activity.itemId,
                    typeId = activity.typeId,
                    title = activity.title,
                    coverPath = activity.coverPath,
                    thumbnailPath = activity.thumbnailPath
                )
            }
        }
    return CalendarExportSnapshot(
        yearMonth = yearMonth,
        cells = buildMonthDates(yearMonth).map { date ->
            date?.let {
                CalendarExportDay(
                    date = it,
                    covers = coversByDate[it].orEmpty()
                )
            }
        },
        sourceActivityCount = inMonth.size
    )
}
