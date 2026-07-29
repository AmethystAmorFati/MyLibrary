package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class ActivityRow(
    val id: Long,
    val date: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    @ColumnInfo(name = "record_id")
    val recordId: Long?,
    @ColumnInfo(name = "record_created_at")
    val recordCreatedAt: Long,
    val title: String,
    @ColumnInfo(name = "type_name")
    val typeName: String,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?
)
