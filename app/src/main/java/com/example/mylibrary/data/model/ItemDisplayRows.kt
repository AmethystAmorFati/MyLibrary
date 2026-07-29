package com.example.mylibrary.data.model

import androidx.room.ColumnInfo
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldOptionDefinition

data class ItemTagNameRow(
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    val name: String
)

data class TagUsageRow(
    @ColumnInfo(name = "tag_id")
    val tagId: Long,
    @ColumnInfo(name = "usage_count")
    val usageCount: Int
)

data class ItemDynamicValueRow(
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "field_id")
    val fieldId: Long,
    val value: String,
    @ColumnInfo(name = "data_type")
    val dataType: FieldDataType,
    @ColumnInfo(name = "options")
    val optionDefinitions: List<FieldOptionDefinition>
)
