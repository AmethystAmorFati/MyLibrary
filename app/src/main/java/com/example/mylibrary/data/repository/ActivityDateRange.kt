package com.example.mylibrary.data.repository

import com.example.mylibrary.util.toLocalDate
import com.example.mylibrary.util.toStartOfDayMillis

fun activityDates(startDate: Long, endDate: Long?): List<Long> {
    val start = startDate.toLocalDate()
    val end = (endDate ?: startDate).toLocalDate()
    require(!end.isBefore(start)) { "结束日期不能早于开始日期" }
    return generateSequence(start) { current ->
        current.plusDays(1).takeUnless { it.isAfter(end) }
    }.map { it.toStartOfDayMillis() }.toList()
}

