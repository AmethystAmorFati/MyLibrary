package com.example.mylibrary.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["item_id"]),
        Index(value = ["created_at"])
    ]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "start_date")
    val startDate: Long,
    @ColumnInfo(name = "end_date")
    val endDate: Long? = null,
    @ColumnInfo(name = "rating_half_stars")
    val ratingHalfStars: Int? = null,
    val review: String? = null,
    @ColumnInfo(name = "status_snapshot")
    val statusSnapshot: String? = null,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0
)
