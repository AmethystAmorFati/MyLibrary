package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class TimelineRecordRow(
    @ColumnInfo(name = "record_id")
    val recordId: Long,
    @ColumnInfo(name = "record_start_date")
    val recordStartDate: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    val title: String,
    @ColumnInfo(name = "type_name")
    val typeName: String,
    val creator: String?,
    @ColumnInfo(name = "rating_half_stars")
    val ratingHalfStars: Int?,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    @ColumnInfo(name = "status_snapshot")
    val statusSnapshot: String?,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Long?
)
