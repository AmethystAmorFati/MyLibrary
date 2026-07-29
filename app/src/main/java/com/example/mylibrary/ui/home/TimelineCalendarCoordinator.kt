package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.util.toLocalDate
import java.time.LocalDate
import java.time.YearMonth

data class TimelineDateGroup(
    val date: LocalDate,
    val records: List<LibraryTimelineRecord>
)

data class TimelineListEntry(
    val recordStartDate: LocalDate,
    val record: LibraryTimelineRecord,
    val showDateLabel: Boolean,
    val isLastInDateGroup: Boolean
)

data class ManualMonthChange(
    val calendarDisplayMonth: YearMonth,
    val calendarSelectedActivityDate: LocalDate
)

data class CalendarRecordTarget(
    val displayMonth: YearMonth,
    val selectedActivityDate: LocalDate
)

object TimelineCalendarCoordinator {
    fun groups(records: List<LibraryTimelineRecord>): List<TimelineDateGroup> =
        records
            .sortedWith(
                compareBy<LibraryTimelineRecord> { it.recordStartDate }
                    .thenBy { it.createdAt }
                    .thenBy { it.recordId }
            )
            .groupBy { it.recordStartDate.toLocalDate() }
            .entries
            .sortedBy { it.key }
            .map { (date, rows) -> TimelineDateGroup(date, rows) }

    fun entries(groups: List<TimelineDateGroup>): List<TimelineListEntry> =
        groups.flatMap { group ->
            group.records.mapIndexed { index, record ->
                TimelineListEntry(
                    recordStartDate = group.date,
                    record = record,
                    showDateLabel = index == 0,
                    isLastInDateGroup = index == group.records.lastIndex
                )
            }
        }

    fun initialIndex(entries: List<TimelineListEntry>, today: LocalDate): Int {
        if (entries.isEmpty()) return 0
        return entries.indexOfFirst { !it.recordStartDate.isBefore(today) }
            .takeIf { it >= 0 }
            ?: entries.lastIndex
    }

    fun visibleRecord(
        entries: List<TimelineListEntry>,
        firstVisibleItemIndex: Int
    ): LibraryTimelineRecord? =
        entries.getOrNull(firstVisibleItemIndex)?.record

    fun indexForRecord(entries: List<TimelineListEntry>, recordId: Long): Int? =
        entries.indexOfFirst { it.record.recordId == recordId }
            .takeIf { it >= 0 }

    fun targetRecordForActivities(
        activities: List<LibraryActivity>,
        timelineRecords: List<LibraryTimelineRecord>
    ): LibraryTimelineRecord? {
        val activityRecordIds = activities.mapNotNull { it.recordId }.toSet()
        return timelineRecords
            .asSequence()
            .filter { it.recordId in activityRecordIds }
            .maxWithOrNull(
                compareBy<LibraryTimelineRecord> { it.recordStartDate }
                    .thenBy { it.createdAt }
                    .thenBy { it.recordId }
            )
    }

    fun calendarTargetForRecord(
        currentDisplayMonth: YearMonth,
        currentSelectedActivityDate: LocalDate,
        record: LibraryTimelineRecord
    ): CalendarRecordTarget? {
        val activityDates = record.activityDates
            .asSequence()
            .map { it.toLocalDate() }
            .distinct()
            .sorted()
            .toList()
        if (activityDates.isEmpty()) return null

        val datesInCurrentMonth = activityDates.filter {
            YearMonth.from(it) == currentDisplayMonth
        }
        val targetMonth = if (datesInCurrentMonth.isNotEmpty()) {
            currentDisplayMonth
        } else {
            YearMonth.from(activityDates.first())
        }
        val targetDate = currentSelectedActivityDate.takeIf {
            it in activityDates && YearMonth.from(it) == targetMonth
        } ?: activityDates.first { YearMonth.from(it) == targetMonth }

        return CalendarRecordTarget(
            displayMonth = targetMonth,
            selectedActivityDate = targetDate
        )
    }

    fun moveCalendarMonth(
        calendarDisplayMonth: YearMonth,
        calendarSelectedActivityDate: LocalDate,
        delta: Long
    ): ManualMonthChange =
        ManualMonthChange(
            calendarDisplayMonth = calendarDisplayMonth.plusMonths(delta),
            calendarSelectedActivityDate = calendarSelectedActivityDate
        )
}
