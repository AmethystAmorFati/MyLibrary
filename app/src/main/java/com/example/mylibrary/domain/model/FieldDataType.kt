package com.example.mylibrary.domain.model

enum class FieldDataType(val storageValue: String) {
    TEXT("text"),
    NUMBER("number"),
    DATE("date"),
    BOOLEAN("boolean"),
    SINGLE_SELECT("single_select"),
    MULTI_SELECT("multi_select"),
    RATING("rating");

    companion object {
        fun fromStorageValue(value: String): FieldDataType =
            entries.firstOrNull { it.storageValue == value } ?: TEXT
    }
}
