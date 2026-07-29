package com.example.mylibrary.domain.model

data class DynamicFieldDefinition(
    val id: Long,
    val typeId: Long,
    val typeName: String,
    val name: String,
    val dataType: FieldDataType,
    val enabled: Boolean,
    val sortOrder: Int,
    val isFixed: Boolean,
    val options: List<String> = emptyList(),
    val optionDefinitions: List<FieldOptionDefinition> = legacyFieldOptions(options),
    val scope: FieldScope = FieldScope.ITEM,
    val unit: String? = null,
    val aggregations: Set<FieldAggregation> = emptySet(),
    val hasValues: Boolean = false
)
