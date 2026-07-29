package com.example.mylibrary.domain.model

data class LibraryTag(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
    val enabled: Boolean
)
