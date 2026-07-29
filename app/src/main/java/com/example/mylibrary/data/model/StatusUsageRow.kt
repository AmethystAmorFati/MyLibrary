package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class StatusUsageRow(
    @ColumnInfo(name = "status_id")
    val statusId: Long,
    @ColumnInfo(name = "usage_count")
    val usageCount: Int
)
