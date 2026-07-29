package com.example.mylibrary.domain.model

data class ItemType(
    val id: Long,
    val name: String,
    val sortOrder: Int
) {
    val kind: ItemTypeKind
        get() = ItemTypeKind.fromTypeId(id)

    val creatorLabel: String
        get() = if (kind == ItemTypeKind.MOVIE) "导演" else "作者"
}

enum class ItemTypeKind {
    BOOK,
    MOVIE,
    CUSTOM;

    companion object {
        const val BOOK_TYPE_ID = 1L
        const val MOVIE_TYPE_ID = 2L

        fun fromTypeId(typeId: Long): ItemTypeKind = when (typeId) {
            BOOK_TYPE_ID -> BOOK
            MOVIE_TYPE_ID -> MOVIE
            else -> CUSTOM
        }
    }
}
