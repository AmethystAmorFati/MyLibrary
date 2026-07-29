package com.example.mylibrary.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

fun todayText(): String = LocalDate.now().toString()

fun parseDateText(value: String): Long? {
    if (value.isBlank()) return null
    return try {
        LocalDate.parse(value.trim())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

fun formatDate(value: Long): String =
    Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
