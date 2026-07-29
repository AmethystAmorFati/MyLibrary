package com.example.mylibrary.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ItemTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StatusEntity::class,
            parentColumns = ["id"],
            childColumns = ["current_status_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["type_id"]),
        Index(value = ["title"]),
        Index(value = ["deleted_at"]),
        Index(value = ["current_status_id"])
    ]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    val title: String,
    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    @ColumnInfo(name = "current_status_id")
    val currentStatusId: Long? = null,
    @ColumnInfo(name = "created_time")
    val createdTime: Long,
    @ColumnInfo(name = "updated_time")
    val updatedTime: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null
)
