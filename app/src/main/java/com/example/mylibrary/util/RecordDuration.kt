package com.example.mylibrary.util

import com.example.mylibrary.domain.model.ItemTypeKind

data class DurationParts(
    val hours: Long,
    val minutes: Int
)

fun toTotalMinutes(hoursText: String, minutesText: String): Long? {
    val hoursRaw = hoursText.trim()
    val minutesRaw = minutesText.trim()
    if (hoursRaw.isEmpty() && minutesRaw.isEmpty()) return null

    val hours = if (hoursRaw.isEmpty()) 0L else hoursRaw.toLongOrNull() ?: return null
    val minutes =
        if (minutesRaw.isEmpty()) 0L else minutesRaw.toLongOrNull() ?: return null
    if (hours < 0L || minutes < 0L) return null
    if (hours > (Long.MAX_VALUE - minutes) / MINUTES_PER_HOUR) return null
    return hours * MINUTES_PER_HOUR + minutes
}

fun splitTotalMinutes(totalMinutes: Long): DurationParts? {
    if (totalMinutes < 0L) return null
    return DurationParts(
        hours = totalMinutes / MINUTES_PER_HOUR,
        minutes = (totalMinutes % MINUTES_PER_HOUR).toInt()
    )
}

fun formatDuration(totalMinutes: Long): String? {
    val parts = splitTotalMinutes(totalMinutes) ?: return null
    return when {
        parts.hours == 0L -> "${parts.minutes} 分钟"
        parts.minutes == 0 -> "${parts.hours} 小时"
        else -> "${parts.hours} 小时 ${parts.minutes} 分钟"
    }
}

fun recordDurationLabel(typeId: Long): String = when (typeId) {
    ItemTypeKind.BOOK_TYPE_ID -> "阅读时长"
    ItemTypeKind.MOVIE_TYPE_ID -> "观看时长"
    else -> "记录时长"
}

fun totalDurationLabel(typeId: Long): String = when (typeId) {
    ItemTypeKind.BOOK_TYPE_ID -> "累计阅读"
    ItemTypeKind.MOVIE_TYPE_ID -> "累计观看"
    else -> "累计记录"
}

private const val MINUTES_PER_HOUR = 60L
