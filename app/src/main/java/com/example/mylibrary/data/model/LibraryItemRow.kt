package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class LibraryItemRow(
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
    @ColumnInfo(name = "created_time")
    val createdTime: Long,
    @ColumnInfo(name = "updated_time")
    val updatedTime: Long,
    @ColumnInfo(name = "current_status_id")
    val currentStatusId: Long?,
    @ColumnInfo(name = "current_status_name")
    val currentStatusName: String?,
    @ColumnInfo(name = "latest_rating_half_stars")
    val latestRatingHalfStars: Int?,
    @ColumnInfo(name = "total_duration_minutes")
    val totalDurationMinutes: Long?
)
