package com.example.mylibrary.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

fun Long.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

fun LocalDate.toStartOfDayMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zoneId).toInstant().toEpochMilli()

fun buildMonthDates(
    month: YearMonth,
    fixedSixRows: Boolean = false
): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = MutableList<LocalDate?>(leading) { null }
    repeat(month.lengthOfMonth()) { index -> cells += month.atDay(index + 1) }
    val target = if (fixedSixRows) 42 else ((cells.size + 6) / 7) * 7
    while (cells.size < target) cells += null
    return cells
}

fun weekContaining(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return List(7) { monday.plusDays(it.toLong()) }
}

