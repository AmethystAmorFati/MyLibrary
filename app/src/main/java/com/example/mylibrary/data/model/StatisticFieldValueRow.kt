package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class StatisticFieldValueRow(
    @ColumnInfo(name = "field_id")
    val fieldId: Long,
    @ColumnInfo(name = "owner_id")
    val ownerId: Long,
    val value: String
)
