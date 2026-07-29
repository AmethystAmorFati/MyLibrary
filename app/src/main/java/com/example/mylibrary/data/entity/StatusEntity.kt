package com.example.mylibrary.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mylibrary.domain.model.StatusScope

@Entity(
    tableName = "statuses",
    indices = [
        Index(value = ["scope", "name"], unique = true),
        Index(value = ["scope", "sort_order"])
    ]
)
data class StatusEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "'item'")
    val scope: StatusScope = StatusScope.ITEM,
    val enabled: Boolean = true
)
