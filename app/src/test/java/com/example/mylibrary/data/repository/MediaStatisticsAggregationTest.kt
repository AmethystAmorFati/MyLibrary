package com.example.mylibrary.data.repository

import com.example.mylibrary.data.model.MediaItemStatisticsRow
import com.example.mylibrary.domain.model.ItemTypeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStatisticsAggregationTest {
    @Test
    fun aggregatesBookAndMovieFromOneItemLevelResultWithoutCrossMultiplication() {
        val result = buildFixedMediaStatistics(
            listOf(
                row(
                    itemId = 1,
                    typeId = ItemTypeKind.BOOK_TYPE_ID,
                    title = "书 A",
                    records = 2,
                    quotes = 3,
                    valuedRecords = 2,
                    total = 150,
                    maximum = 90
                ),
                row(
                    itemId = 2,
                    typeId = ItemTypeKind.BOOK_TYPE_ID,
                    title = "书 B",
                    records = 1,
                    quotes = 4,
                    valuedRecords = 0,
                    total = null,
                    maximum = null
                ),
                row(
                    itemId = 3,
                    typeId = ItemTypeKind.MOVIE_TYPE_ID,
                    title = "电影",
                    records = 1,
                    quotes = 2,
                    valuedRecords = 1,
                    total = 120,
                    maximum = 120
                )
            )
        )

        assertEquals(2L, result.reading.itemCount)
        assertEquals(3L, result.reading.recordCount)
        assertEquals(7L, result.reading.quoteCount)
        assertEquals(150L, result.reading.totalDurationMinutes)
        assertEquals(75L, result.reading.averagePerRecordMinutes)
        assertEquals(150L, result.reading.averagePerItemMinutes)
        assertEquals(1L, result.watching.itemCount)
        assertEquals(120L, result.watching.totalDurationMinutes)
    }

    @Test
    fun explicitZeroIsValuedButNullIsNot() {
        val result = buildFixedMediaStatistics(
            listOf(
                row(1, 1, "零分钟", 1, 0, 1, 0, 0),
                row(2, 1, "未填写", 1, 0, 0, null, null)
            )
        ).reading

        assertEquals(1L, result.valuedRecordCount)
        assertEquals(1L, result.valuedItemCount)
        assertEquals(0L, result.totalDurationMinutes)
        assertEquals(0L, result.averagePerRecordMinutes)
        assertEquals(0L, result.longestItemDurationMinutes)
    }

    @Test
    fun categoryWithoutValuedDurationsKeepsCountsAndHidesDurationValues() {
        val reading = buildFixedMediaStatistics(
            listOf(row(1, 1, "无时长", 2, 5, 0, null, null))
        ).reading

        assertEquals(1L, reading.itemCount)
        assertEquals(2L, reading.recordCount)
        assertEquals(5L, reading.quoteCount)
        assertNull(reading.totalDurationMinutes)
        assertNull(reading.averagePerRecordMinutes)
        assertNull(reading.longestItemTitle)
    }

    @Test
    fun longestItemTieUsesTitleThenId() {
        val reading = buildFixedMediaStatistics(
            listOf(
                row(9, 1, "B", 1, 0, 1, 60, 60),
                row(8, 1, "A", 2, 0, 2, 60, 40),
                row(7, 1, "A", 1, 0, 1, 60, 60)
            )
        ).reading

        assertEquals(7L, reading.longestItemId)
        assertEquals("A", reading.longestItemTitle)
        assertEquals(60L, reading.longestItemDurationMinutes)
    }

    @Test
    fun longestItemTieUsesCaseInsensitiveThenOriginalTitleAndIsInputStable() {
        val rows = listOf(
            row(9, 1, "alpha", 1, 0, 1, 60, 60),
            row(8, 1, "Alpha", 1, 0, 1, 60, 60),
            row(7, 1, "Alpha", 1, 0, 1, 60, 60)
        )

        listOf(rows, rows.reversed()).forEach { input ->
            val reading = buildFixedMediaStatistics(input).reading
            assertEquals(7L, reading.longestItemId)
            assertEquals("Alpha", reading.longestItemTitle)
        }
    }

    @Test
    fun longerTotalWinsBeforeTitleOrdering() {
        val reading = buildFixedMediaStatistics(
            listOf(
                row(9, 1, "A", 1, 0, 1, 60, 60),
                row(7, 1, "Z", 2, 0, 2, 61, 40)
            )
        ).reading

        assertEquals(7L, reading.longestItemId)
        assertEquals("Z", reading.longestItemTitle)
        assertEquals(61L, reading.longestItemDurationMinutes)
    }

    @Test
    fun durationAveragesRoundToTheNearestWholeMinute() {
        val reading = buildFixedMediaStatistics(
            listOf(row(1, 1, "四舍五入", 2, 0, 2, 5, 3))
        ).reading

        assertEquals(3L, reading.averagePerRecordMinutes)
        assertEquals(5L, reading.averagePerItemMinutes)
    }

    private fun row(
        itemId: Long,
        typeId: Long,
        title: String,
        records: Long,
        quotes: Long,
        valuedRecords: Long,
        total: Long?,
        maximum: Long?
    ) = MediaItemStatisticsRow(
        itemId = itemId,
        typeId = typeId,
        itemTitle = title,
        recordCount = records,
        quoteCount = quotes,
        valuedRecordCount = valuedRecords,
        totalDurationMinutes = total,
        maximumSingleDurationMinutes = maximum
    )
}
