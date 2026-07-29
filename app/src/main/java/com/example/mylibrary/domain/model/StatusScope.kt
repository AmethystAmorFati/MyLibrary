package com.example.mylibrary.domain.model

enum class StatusScope(val storageValue: String) {
    ITEM("item"),
    RECORD("record");

    companion object {
        fun fromStorageValue(value: String): StatusScope =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) }
                ?: error("Unknown status scope: $value")
    }
}
