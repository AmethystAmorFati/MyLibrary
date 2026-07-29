package com.example.mylibrary.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldScope

@Entity(
    tableName = "field_definitions",
    foreignKeys = [
        ForeignKey(
            entity = ItemTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["type_id"]),
        Index(value = ["type_id", "name"], unique = true)
    ]
)
data class FieldDefinitionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    val name: String,
    @ColumnInfo(name = "data_type")
    val dataType: FieldDataType,
    val enabled: Boolean = true,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "is_fixed", defaultValue = "0")
    val isFixed: Boolean = false,
    @ColumnInfo(name = "options", defaultValue = "''")
    val optionDefinitions: List<FieldOptionDefinition> = emptyList(),
    @ColumnInfo(name = "scope", defaultValue = "'item'")
    val scope: FieldScope = FieldScope.ITEM,
    val unit: String? = null,
    @ColumnInfo(name = "aggregations", defaultValue = "''")
    val aggregations: Set<FieldAggregation> = emptySet()
)
