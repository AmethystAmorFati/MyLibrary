package com.example.mylibrary.ui.quote

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val quoteDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

fun formatQuoteDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(quoteDateFormatter)
