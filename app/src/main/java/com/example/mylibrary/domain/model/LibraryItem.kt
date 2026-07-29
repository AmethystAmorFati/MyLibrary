package com.example.mylibrary.domain.model

data class LibraryItem(
    val id: Long,
    val typeId: Long,
    val typeName: String,
    val title: String,
    val creator: String,
    val coverPath: String?,
    val thumbnailPath: String?,
    val createdTime: Long,
    val updatedTime: Long,
    val currentStatusId: Long?,
    val currentStatusName: String?,
    val latestRatingHalfStars: Int?,
    val totalDurationMinutes: Long? = null,
    val tagNames: List<String> = emptyList(),
    val dynamicValues: Map<Long, String> = emptyMap()
)
