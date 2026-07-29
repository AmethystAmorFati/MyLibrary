package com.example.mylibrary.domain.model

data class TrashItem(
    val id: Long,
    val typeId: Long,
    val typeName: String,
    val title: String,
    val creator: String,
    val coverPath: String?,
    val thumbnailPath: String?,
    val deletedAt: Long
)
