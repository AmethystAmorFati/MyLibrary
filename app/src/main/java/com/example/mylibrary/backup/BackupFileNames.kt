package com.example.mylibrary.backup

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object BackupFileNames {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun defaultName(now: LocalDateTime = LocalDateTime.now()): String =
        "MyLibrary_数据备份_${now.format(formatter)}.zip"
}
