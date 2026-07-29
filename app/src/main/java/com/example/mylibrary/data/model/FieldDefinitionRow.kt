package com.example.mylibrary.data.model

import androidx.room.ColumnInfo
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldScope

data class FieldDefinitionRow(
    val id: Long,
    @ColumnInfo(name = "type_id")
    val typeId: Long,
    @ColumnInfo(name = "type_name")
    val typeName: String,
    val name: String,
    @ColumnInfo(name = "data_type")
    val dataType: FieldDataType,
    val enabled: Boolean,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "is_fixed")
    val isFixed: Boolean,
    @ColumnInfo(name = "options")
    val optionDefinitions: List<FieldOptionDefinition>,
    val scope: FieldScope,
    val unit: String?,
    val aggregations: Set<FieldAggregation>,
    @ColumnInfo(name = "has_values")
    val hasValues: Boolean
)
