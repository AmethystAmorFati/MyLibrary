package com.example.mylibrary.export.calendar

import com.example.mylibrary.export.visual.VISUAL_EXPORT_HEIGHT
import com.example.mylibrary.export.visual.VISUAL_EXPORT_WIDTH
import com.example.mylibrary.export.visual.VisualExportActivity
import com.example.mylibrary.ui.home.CalendarCoverLayout
import com.example.mylibrary.ui.home.calendarCoverLayout
import com.example.mylibrary.ui.home.calendarCoverPlacements
import com.example.mylibrary.ui.theme.CalendarCellAspectRatio
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarExportSnapshotTest {
    @Test
    fun monthGridSupportsLeapYearsAndAllMonthLengths() {
        assertEquals(28, YearMonth.of(2023, 2).lengthOfMonth())
        assertEquals(29, YearMonth.of(2024, 2).lengthOfMonth())
        assertEquals(30, YearMonth.of(2024, 4).lengthOfMonth())
        assertEquals(31, YearMonth.of(2024, 1).lengthOfMonth())

        assertEquals(4, snapshot(2021, 2).rowCount)
        assertEquals(5, snapshot(2024, 2).rowCount)
        assertEquals(6, snapshot(2021, 5).rowCount)
    }

    @Test
    fun gridIsMondayFirstAndHandlesFirstAndLastColumns() {
        val mondayFirst = snapshot(2021, 11)
        assertEquals(LocalDate.of(2021, 11, 1), mondayFirst.cells.first()?.date)

        val sundayFirst = snapshot(2021, 8)
        repeat(6) { assertNull(sundayFirst.cells[it]) }
        assertEquals(LocalDate.of(2021, 8, 1), sundayFirst.cells[6]?.date)
    }

    @Test
    fun dateNumberIsShownOnlyWhenThereIsNoHomeCalendarCover() {
        val date = LocalDate.of(2026, 7, 8)
        val withoutCover = buildCalendarExportSnapshot(
            YearMonth.of(2026, 7),
            listOf(activity(1, date, thumbnailPath = null))
        ).day(8)
        val withCover = buildCalendarExportSnapshot(
            YearMonth.of(2026, 7),
            listOf(activity(1, date, thumbnailPath = "thumb.webp"))
        ).day(8)

        assertTrue(withoutCover.showsDateNumber)
        assertTrue(withoutCover.covers.isEmpty())
        assertFalse(withCover.showsDateNumber)
        assertEquals(1, withCover.covers.size)
    }

    @Test
    fun dailyCoversUseSharedStableOrderDeduplicateItemsAndStopAtFour() {
        val date = LocalDate.of(2026, 7, 9)
        val input = listOf(
            activity(1, date, itemId = 1, createdAt = 100),
            activity(2, date, itemId = 1, createdAt = 600),
            activity(3, date, itemId = 2, createdAt = 500),
            activity(4, date, itemId = 3, createdAt = 400),
            activity(5, date, itemId = 4, createdAt = 300),
            activity(6, date, itemId = 5, createdAt = 200)
        )

        val ordered = buildCalendarExportSnapshot(
            YearMonth.of(2026, 7),
            input.shuffled()
        ).day(9).covers.map { it.itemId }
        val reversed = buildCalendarExportSnapshot(
            YearMonth.of(2026, 7),
            input.reversed()
        ).day(9).covers.map { it.itemId }

        assertEquals(listOf(1L, 2L, 3L, 4L), ordered)
        assertEquals(ordered, reversed)
    }

    @Test
    fun oneThroughFourCoversUseTheExactHomePlacements() {
        val target = ExportIntRect(0, 0, 132, 176)
        assertEquals(CalendarCoverLayout.SINGLE, calendarCoverLayout(1))
        assertEquals(CalendarCoverLayout.TWO_ROWS, calendarCoverLayout(2))
        assertEquals(CalendarCoverLayout.TWO_OVER_ONE, calendarCoverLayout(3))
        assertEquals(CalendarCoverLayout.TWO_BY_TWO, calendarCoverLayout(4))

        (1..4).forEach { count ->
            val sharedPlacements = calendarCoverPlacements(count)
            val slots = calendarCoverSlotRects(target, count)
            assertEquals(count, slots.size)
            assertEquals((0 until count).toList(), sharedPlacements.map { it.zIndex })
            assertEquals(
                sharedPlacements.map { placement ->
                    ExportIntRect(
                        left = (
                            placement.leftFraction * target.width
                            ).roundToInt(),
                        top = (
                            placement.topFraction * target.height
                            ).roundToInt(),
                        right = (
                            (placement.leftFraction + placement.widthFraction) *
                                target.width
                            ).roundToInt(),
                        bottom = (
                            (placement.topFraction + placement.heightFraction) *
                                target.height
                            ).roundToInt()
                    )
                },
                slots
            )
        }
    }

    @Test
    fun sharedMultiCoverGeometryKeepsTheHomeCoverOccupancy() {
        val target = ExportIntRect(0, 0, 117, 156)
        val two = calendarCoverSlotRects(target, 2)
        val three = calendarCoverSlotRects(target, 3)
        val four = calendarCoverSlotRects(target, 4)

        assertEquals(listOf(117, 117), two.map { it.width })
        assertEquals(listOf(77, 76), two.map { it.height })
        assertEquals(listOf(57, 57, 117), three.map { it.width })
        assertEquals(listOf(77, 77, 76), three.map { it.height })
        assertEquals(listOf(57, 57, 57, 57), four.map { it.width })
        assertEquals(listOf(77, 77, 76, 76), four.map { it.height })
        assertTrue(two.all { it.width > target.width / 2 })
    }

    @Test
    fun layoutUsesHomeDayRatioWithoutStretchingFourRowMonths() {
        val layouts = (4..6).map(::calendarExportLayout)
        layouts.forEach { layout ->
            assertEquals(layout.rowCount * 7, layout.dayCells.size)
            assertEquals(
                CalendarCellAspectRatio.toDouble(),
                layout.cellWidth.toDouble() / layout.cellHeight,
                0.001
            )
            assertTrue(layout.dayCells.all { it.right <= VISUAL_EXPORT_WIDTH })
            assertTrue(layout.dayCells.all { it.bottom <= VISUAL_EXPORT_HEIGHT })
        }
        assertEquals(layouts[0].cellWidth, layouts[2].cellWidth)
        assertEquals(layouts[0].cellHeight, layouts[2].cellHeight)
        assertTrue(layouts[2].gridBottom <= VISUAL_EXPORT_HEIGHT)
        assertTrue(layouts.all { it.weekdayGridGap <= 16 })
        assertEquals(1_080, VISUAL_EXPORT_WIDTH)
        assertEquals(1_440, VISUAL_EXPORT_HEIGHT)
    }

    @Test
    fun exportVisualPolicyUsesSmallerCoverCornersAndQuietEmptyCells() {
        assertTrue(calendarExportVisualPolicy.coverCornerRadius < 12f)
        assertFalse(calendarExportVisualPolicy.emptyCellDrawsStroke)
        assertTrue(calendarExportVisualPolicy.emptyDateUsesSecondaryText)
        assertTrue(calendarExportVisualPolicy.emptyCellAlpha < 128)
    }

    private fun snapshot(year: Int, month: Int) =
        buildCalendarExportSnapshot(YearMonth.of(year, month), emptyList())

    private fun CalendarExportSnapshot.day(day: Int): CalendarExportDay =
        requireNotNull(cells.filterNotNull().firstOrNull { it.date.dayOfMonth == day })

    private fun activity(
        id: Long,
        date: LocalDate,
        itemId: Long = id,
        createdAt: Long = id,
        thumbnailPath: String? = "thumb-$id.webp"
    ) = VisualExportActivity(
        activityId = id,
        date = date.toStartOfDayMillis(),
        itemId = itemId,
        typeId = 1,
        recordId = id,
        recordCreatedAt = createdAt,
        title = "Item $itemId",
        coverPath = "cover-$id.webp",
        thumbnailPath = thumbnailPath
    )
}
