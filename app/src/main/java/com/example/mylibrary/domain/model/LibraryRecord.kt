package com.example.mylibrary.domain.model

data class LibraryRecord(
    val id: Long,
    val itemId: Long,
    val startDate: Long,
    val endDate: Long?,
    val ratingHalfStars: Int?,
    val review: String?,
    val createdAt: Long,
    val dynamicValues: Map<Long, String> = emptyMap(),
    val dynamicFields: List<DynamicFieldValue> = emptyList(),
    val statusSnapshot: String? = null,
    val durationMinutes: Long? = null
)
