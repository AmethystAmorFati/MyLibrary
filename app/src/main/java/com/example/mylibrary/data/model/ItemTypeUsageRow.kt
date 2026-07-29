package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class ItemTypeUsageRow(
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    @ColumnInfo(name = "usage_count")
    val usageCount: Int
)
