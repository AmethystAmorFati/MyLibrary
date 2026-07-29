package com.example.mylibrary.data.model

import androidx.room.ColumnInfo
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldOptionDefinition

data class DynamicFieldValueRow(
    @ColumnInfo(name = "definition_id")
    val definitionId: Long,
    val name: String,
    @ColumnInfo(name = "data_type")
    val dataType: FieldDataType,
    val value: String?,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "is_fixed")
    val isFixed: Boolean,
    val unit: String?,
    @ColumnInfo(name = "options")
    val optionDefinitions: List<FieldOptionDefinition>
)
