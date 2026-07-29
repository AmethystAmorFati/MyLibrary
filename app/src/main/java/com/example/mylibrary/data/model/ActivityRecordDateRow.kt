package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class ActivityRecordDateRow(
    @ColumnInfo(name = "record_id")
    val recordId: Long,
    val date: Long
)
