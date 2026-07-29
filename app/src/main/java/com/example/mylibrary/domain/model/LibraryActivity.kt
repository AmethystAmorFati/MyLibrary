package com.example.mylibrary.domain.model

data class LibraryActivity(
    val id: Long,
    val date: Long,
    val itemId: Long,
    val typeId: Long,
    val recordId: Long?,
    val recordCreatedAt: Long,
    val title: String,
    val typeName: String,
    val thumbnailPath: String?
)
