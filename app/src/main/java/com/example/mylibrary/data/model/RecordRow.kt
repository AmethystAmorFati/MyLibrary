package com.example.mylibrary.data.model

import androidx.room.ColumnInfo

data class RecordRow(
    val id: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "start_date")
    val startDate: Long,
    @ColumnInfo(name = "end_date")
    val endDate: Long?,
    @ColumnInfo(name = "rating_half_stars")
    val ratingHalfStars: Int?,
    val review: String?,
    @ColumnInfo(name = "status_snapshot")
    val statusSnapshot: String?,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
