package com.example.mylibrary.data.repository

import com.example.mylibrary.data.model.ActivityRow
import com.example.mylibrary.domain.model.LibraryActivity

internal fun ActivityRow.toDomain() = LibraryActivity(
    id = id,
    date = date,
    itemId = itemId,
    typeId = typeId,
    recordId = recordId,
    recordCreatedAt = recordCreatedAt,
    title = title,
    typeName = typeName,
    thumbnailPath = thumbnailPath
)
