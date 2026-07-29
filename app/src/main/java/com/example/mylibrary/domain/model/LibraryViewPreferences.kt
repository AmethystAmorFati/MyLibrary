package com.example.mylibrary.domain.model

enum class LibraryViewMode(
    val storageValue: String,
    val displayName: String
) {
    SHELF("shelf", "网格"),
    LIST("list", "列表"),
    COVER("cover", "纯图");

    companion object {
        fun fromStorageValue(value: String?): LibraryViewMode =
            entries.firstOrNull { it.storageValue == value } ?: SHELF
    }
}

object LibraryDisplayFieldKey {
    const val CREATOR = "creator"
    const val CURRENT_STATUS = "current_status"
    const val TAGS = "tags"
    const val DYNAMIC_PREFIX = "dynamic:"

    fun dynamic(fieldId: Long): String = "$DYNAMIC_PREFIX$fieldId"
    fun dynamicId(key: String): Long? =
        key.removePrefix(DYNAMIC_PREFIX).takeIf { key.startsWith(DYNAMIC_PREFIX) }?.toLongOrNull()
}

data class LibraryViewPreferences(
    val viewMode: LibraryViewMode = LibraryViewMode.SHELF,
    val gridColumns: Int = 4,
    val coverColumns: Int = 4,
    val timelineShowCreator: Boolean = false,
    val timelineShowRating: Boolean = false,
    val timelineShowStatus: Boolean = false,
    val timelineShowDuration: Boolean = true,
    val libraryShowTotalDuration: Boolean = true,
    val showQuoteChapter: Boolean = true,
    val showQuotePage: Boolean = true,
    val listDisplayFields: Set<String> = setOf(LibraryDisplayFieldKey.CREATOR)
)
