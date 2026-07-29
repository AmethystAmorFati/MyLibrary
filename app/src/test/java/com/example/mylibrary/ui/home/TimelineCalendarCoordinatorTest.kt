package com.example.mylibrary.ui.home

import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineCalendarCoordinatorTest {
    @Test
    fun timelineGroupsEveryRecordOnceByStartDateWithStableOrder() {
        val early = noon(LocalDate.of(2026, 7, 1))
        val late = noon(LocalDate.of(2026, 7, 2))
        val groups = TimelineCalendarCoordinator.groups(
            listOf(
                record(id = 3, createdAt = early, recordStartDate = late),
                record(id = 2, createdAt = late, recordStartDate = early),
                record(id = 1, createdAt = early, recordStartDate = early)
            )
        )
        val entries = TimelineCalendarCoordinator.entries(groups)

        assertEquals(
            listOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2)),
            groups.map(TimelineDateGroup::date)
        )
        assertEquals(listOf(1L, 2L, 3L), entries.map { it.record.recordId })
        assertEquals(listOf(true, false, true), entries.map { it.showDateLabel })
    }

    @Test
    fun tenDayRecordHasTenActivityDatesButOneTimelineEntry() {
        val dates = (0L..9L).map {
            LocalDate.of(2026, 7, 1).plusDays(it).toStartOfDayMillis()
        }
        val record = record(
            id = 7,
            createdAt = noon(LocalDate.of(2026, 8, 20)),
            activityDates = dates
        )

        assertEquals(
            1,
            TimelineCalendarCoordinator.entries(
                TimelineCalendarCoordinator.groups(listOf(record))
            ).size
        )
        assertEquals(10, record.activityDates.size)
    }

    @Test
    fun visibleTimelineItemReturnsItsRecordNotOnlyItsDateGroup() {
        val created = noon(LocalDate.of(2026, 7, 1))
        val entries = TimelineCalendarCoordinator.entries(
            TimelineCalendarCoordinator.groups(
                listOf(record(1, created), record(2, created))
            )
        )

        assertEquals(2L, TimelineCalendarCoordinator.visibleRecord(entries, 1)?.recordId)
        assertNull(TimelineCalendarCoordinator.visibleRecord(entries, 8))
    }

    @Test
    fun initialIndexUsesRecordStartDates() {
        val entries = TimelineCalendarCoordinator.entries(
            TimelineCalendarCoordinator.groups(
                listOf(
                    record(1, noon(LocalDate.of(2026, 7, 1))),
                    record(2, noon(LocalDate.of(2026, 7, 10))),
                    record(3, noon(LocalDate.of(2026, 7, 20)))
                )
            )
        )

        assertEquals(
            1,
            TimelineCalendarCoordinator.initialIndex(
                entries,
                LocalDate.of(2026, 7, 8)
            )
        )
        assertEquals(
            2,
            TimelineCalendarCoordinator.initialIndex(
                entries,
                LocalDate.of(2026, 8, 1)
            )
        )
        assertEquals(
            0,
            TimelineCalendarCoordinator.initialIndex(
                emptyList(),
                LocalDate.of(2026, 7, 8)
            )
        )
    }

    @Test
    fun createdAtMonthNeverReplacesTheFirstActivityMonth() {
        val target = TimelineCalendarCoordinator.calendarTargetForRecord(
            currentDisplayMonth = YearMonth.of(2026, 6),
            currentSelectedActivityDate = LocalDate.of(2026, 6, 1),
            record = record(
                id = 1,
                createdAt = noon(LocalDate.of(2026, 9, 5)),
                activityDates = listOf(
                    LocalDate.of(2026, 7, 25).toStartOfDayMillis(),
                    LocalDate.of(2026, 7, 26).toStartOfDayMillis()
                )
            )
        )

        assertEquals(YearMonth.of(2026, 7), target?.displayMonth)
        assertEquals(LocalDate.of(2026, 7, 25), target?.selectedActivityDate)
    }

    @Test
    fun currentActivityMonthIsRetainedForCrossMonthRecord() {
        val record = crossMonthRecord()

        val july = TimelineCalendarCoordinator.calendarTargetForRecord(
            currentDisplayMonth = YearMonth.of(2026, 7),
            currentSelectedActivityDate = LocalDate.of(2026, 7, 1),
            record = record
        )
        val august = TimelineCalendarCoordinator.calendarTargetForRecord(
            currentDisplayMonth = YearMonth.of(2026, 8),
            currentSelectedActivityDate = LocalDate.of(2026, 8, 2),
            record = record
        )

        assertEquals(YearMonth.of(2026, 7), july?.displayMonth)
        assertEquals(YearMonth.of(2026, 8), august?.displayMonth)
        assertEquals(LocalDate.of(2026, 8, 2), august?.selectedActivityDate)
    }

    @Test
    fun unrelatedMonthMovesOnceToFirstActivityMonth() {
        val target = TimelineCalendarCoordinator.calendarTargetForRecord(
            currentDisplayMonth = YearMonth.of(2026, 9),
            currentSelectedActivityDate = LocalDate.of(2026, 9, 1),
            record = crossMonthRecord()
        )

        assertEquals(YearMonth.of(2026, 7), target?.displayMonth)
    }

    @Test
    fun recordWithoutActivityDoesNotProduceCalendarMutation() {
        val currentMonth = YearMonth.of(2026, 11)

        assertNull(
            TimelineCalendarCoordinator.calendarTargetForRecord(
                currentDisplayMonth = currentMonth,
                currentSelectedActivityDate = currentMonth.atDay(1),
                record = record(1, noon(LocalDate.of(2026, 1, 1)))
            )
        )
        assertTrue(calendarMonthRowCount(currentMonth) in 4..6)
        assertTrue(
            calendarMonthDates(currentMonth)
                .filterNotNull()
                .all { YearMonth.from(it) == currentMonth }
        )
    }

    @Test
    fun activityDateTargetsTimelineByRecordId() {
        val records = listOf(
            record(10, noon(LocalDate.of(2026, 7, 1))),
            record(20, noon(LocalDate.of(2026, 7, 2)))
        )
        val entries = TimelineCalendarCoordinator.entries(
            TimelineCalendarCoordinator.groups(records)
        )
        val target = TimelineCalendarCoordinator.targetRecordForActivities(
            activities = listOf(activity(id = 1, recordId = 20)),
            timelineRecords = records
        )

        assertEquals(20L, target?.recordId)
        assertEquals(1, TimelineCalendarCoordinator.indexForRecord(entries, 20))
    }

    @Test
    fun sameDayActivitiesChooseLatestCreatedRecordThenHighestRecordId() {
        val sameCreatedAt = noon(LocalDate.of(2026, 7, 3))
        val records = listOf(
            record(1, noon(LocalDate.of(2026, 7, 1))),
            record(2, sameCreatedAt),
            record(3, sameCreatedAt)
        )

        val target = TimelineCalendarCoordinator.targetRecordForActivities(
            activities = records.mapIndexed { index, row ->
                activity(id = index.toLong(), recordId = row.recordId)
            }.shuffled(),
            timelineRecords = records.shuffled()
        )

        assertEquals(3L, target?.recordId)
    }

    @Test
    fun dateWithoutActivityDoesNotTargetTimeline() {
        assertNull(
            TimelineCalendarCoordinator.targetRecordForActivities(
                activities = emptyList(),
                timelineRecords = listOf(record(1, noon(LocalDate.now())))
            )
        )
    }

    @Test
    fun monthGridUsesNullablePlaceholdersAndOnlyCurrentMonthDates() {
        val month = YearMonth.of(2026, 2)
        val dates = calendarMonthDates(month)

        assertEquals(35, dates.size)
        assertEquals(5, calendarMonthRowCount(month))
        assertTrue(dates.take(6).all { it == null })
        assertEquals(month.atDay(1), dates[6])
        assertTrue(dates.filterNotNull().all { YearMonth.from(it) == month })
        assertNull(dates.last())
    }

    @Test
    fun monthGridSupportsFourFiveAndSixRows() {
        assertEquals(4, calendarMonthRowCount(YearMonth.of(2021, 2)))
        assertEquals(5, calendarMonthRowCount(YearMonth.of(2026, 2)))
        assertEquals(6, calendarMonthRowCount(YearMonth.of(2026, 8)))
    }

    @Test
    fun collapsedCalendarComposesOnlyTheSelectedWeek() {
        val month = YearMonth.of(2026, 2)
        val selected = month.atDay(1)
        val week = calendarCollapsedWeekDates(month, selected)

        assertEquals(7, week.size)
        assertTrue(week.take(6).all { it == null })
        assertEquals(selected, week.last())
    }

    @Test
    fun manualMonthSwitchKeepsSelectedActivityDate() {
        val selected = LocalDate.of(2026, 1, 31)
        val change = TimelineCalendarCoordinator.moveCalendarMonth(
            calendarDisplayMonth = YearMonth.of(2026, 1),
            calendarSelectedActivityDate = selected,
            delta = 1
        )

        assertEquals(YearMonth.of(2026, 2), change.calendarDisplayMonth)
        assertEquals(selected, change.calendarSelectedActivityDate)
        assertEquals(5, calendarMonthRowCount(change.calendarDisplayMonth))
        assertTrue(
            calendarMonthDates(change.calendarDisplayMonth)
                .filterNotNull()
                .all { YearMonth.from(it) == change.calendarDisplayMonth }
        )
    }

    private fun crossMonthRecord(): LibraryTimelineRecord = record(
        id = 8,
        createdAt = noon(LocalDate.of(2026, 9, 1)),
        activityDates = (0L..9L).map {
            LocalDate.of(2026, 7, 25).plusDays(it).toStartOfDayMillis()
        }
    )

    private fun record(
        id: Long,
        createdAt: Long,
        recordStartDate: Long = createdAt,
        activityDates: List<Long> = emptyList()
    ) = LibraryTimelineRecord(
        recordId = id,
        recordStartDate = recordStartDate,
        createdAt = createdAt,
        itemId = id,
        typeId = 1,
        title = "Item $id",
        typeName = "Book",
        creator = "",
        ratingHalfStars = null,
        thumbnailPath = null,
        activityDates = activityDates
    )

    private fun activity(id: Long, recordId: Long) = LibraryActivity(
        id = id,
        date = LocalDate.of(2026, 7, 25).toStartOfDayMillis(),
        itemId = recordId,
        typeId = 1,
        recordId = recordId,
        recordCreatedAt = 0,
        title = "Item $recordId",
        typeName = "Book",
        thumbnailPath = null
    )

    private fun noon(date: LocalDate): Long =
        date.atTime(LocalTime.NOON)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
