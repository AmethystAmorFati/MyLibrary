package com.example.mylibrary.util

import com.example.mylibrary.domain.model.ItemTypeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordDurationTest {
    @Test
    fun emptyInputsAreNull() {
        assertNull(toTotalMinutes("", ""))
        assertNull(toTotalMinutes("  ", " "))
    }

    @Test
    fun hoursAndMinutesConvertToTotalMinutes() {
        assertEquals(60L, toTotalMinutes("1", ""))
        assertEquals(30L, toTotalMinutes("", "30"))
        assertEquals(90L, toTotalMinutes("1", "30"))
        assertEquals(195L, toTotalMinutes("2", "75"))
        assertEquals(90L, toTotalMinutes(" 1 ", " 30 "))
    }

    @Test
    fun oversizedMinutesNormalizeWhenSplit() {
        assertEquals(DurationParts(1L, 30), splitTotalMinutes(90L))
        assertEquals(DurationParts(3L, 15), splitTotalMinutes(195L))
    }

    @Test
    fun invalidNegativeAndOverflowInputsAreRejected() {
        assertNull(toTotalMinutes("-1", "0"))
        assertNull(toTotalMinutes("abc", "5"))
        assertNull(toTotalMinutes(Long.MAX_VALUE.toString(), "60"))
        assertNull(splitTotalMinutes(-1L))
    }

    @Test
    fun formattingUsesHumanReadableWholeMinutes() {
        assertEquals("30 分钟", formatDuration(30L))
        assertEquals("1 小时", formatDuration(60L))
        assertEquals("1 小时 15 分钟", formatDuration(75L))
        assertEquals("2 小时 30 分钟", formatDuration(150L))
        assertNull(formatDuration(-1L))
    }

    @Test
    fun labelsUseStableTypeIds() {
        assertEquals("阅读时长", recordDurationLabel(ItemTypeKind.BOOK_TYPE_ID))
        assertEquals("观看时长", recordDurationLabel(ItemTypeKind.MOVIE_TYPE_ID))
        assertEquals("记录时长", recordDurationLabel(99L))
        assertEquals("累计阅读", totalDurationLabel(ItemTypeKind.BOOK_TYPE_ID))
        assertEquals("累计观看", totalDurationLabel(ItemTypeKind.MOVIE_TYPE_ID))
        assertEquals("累计记录", totalDurationLabel(99L))
    }
}
