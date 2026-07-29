package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class TrashItemRow(
    val id: Long,
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    @ColumnInfo(name = "type_name")
    val typeName: String,
    val title: String,
    val creator: String?,
    @ColumnInfo(name = "cover_path")
    val coverPath: String?,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long
)
