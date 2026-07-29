package com.example.mylibrary.domain.model

data class DynamicFieldValue(
    val definitionId: Long,
    val name: String,
    val dataType: FieldDataType,
    val value: String,
    val sortOrder: Int,
    val isFixed: Boolean,
    val unit: String? = null
)
