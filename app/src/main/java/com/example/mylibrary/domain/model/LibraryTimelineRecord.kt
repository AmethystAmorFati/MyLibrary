package com.example.mylibrary.domain.model

data class LibraryTimelineRecord(
    val recordId: Long,
    val recordStartDate: Long,
    val createdAt: Long,
    val itemId: Long,
    val typeId: Long,
    val title: String,
    val typeName: String,
    val creator: String,
    val ratingHalfStars: Int?,
    val thumbnailPath: String?,
    val activityDates: List<Long> = emptyList(),
    val statusSnapshot: String? = null,
    val durationMinutes: Long? = null
)
