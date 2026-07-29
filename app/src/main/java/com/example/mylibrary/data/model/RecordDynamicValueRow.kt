package com.example.mylibrary.data.model

import androidx.room.ColumnInfo
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldOptionDefinition

data class RecordDynamicValueRow(
    @ColumnInfo(name = "record_id")
    val recordId: Long,
    @ColumnInfo(name = "field_id")
    val fieldId: Long,
    val name: String,
    @ColumnInfo(name = "data_type")
    val dataType: FieldDataType,
    val value: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    val unit: String?,
    @ColumnInfo(name = "options")
    val optionDefinitions: List<FieldOptionDefinition>
)
