package com.example.mylibrary.domain.model

data class ItemDetail(
    val item: LibraryItem,
    val records: List<LibraryRecord>,
    val fields: List<DynamicFieldValue>,
    val tags: List<LibraryTag>
)
