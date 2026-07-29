package com.example.mylibrary.data.repository

import com.example.mylibrary.data.entity.ItemTypeEntity
import com.example.mylibrary.data.entity.StatusEntity
import com.example.mylibrary.data.entity.TagEntity
import com.example.mylibrary.data.model.DynamicFieldValueRow
import com.example.mylibrary.data.model.FieldDefinitionRow
import com.example.mylibrary.data.model.LibraryItemRow
import com.example.mylibrary.data.model.RecordRow
import com.example.mylibrary.data.model.TimelineRecordRow
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.DynamicFieldValue
import com.example.mylibrary.domain.model.activeFieldOptions
import com.example.mylibrary.domain.model.FieldValueParser
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryRecord
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.LibraryTimelineRecord

internal fun LibraryItemRow.toDomain(): LibraryItem = LibraryItem(
    id = id,
    typeId = typeId,
    typeName = typeName,
    title = title,
    creator = creator.orEmpty(),
    coverPath = coverPath,
    thumbnailPath = thumbnailPath,
    createdTime = createdTime,
    updatedTime = updatedTime,
    currentStatusId = currentStatusId,
    currentStatusName = currentStatusName,
    latestRatingHalfStars = latestRatingHalfStars,
    totalDurationMinutes = totalDurationMinutes
)

internal fun ItemTypeEntity.toDomain(): ItemType =
    ItemType(id = id, name = name, sortOrder = sortOrder)

internal fun StatusEntity.toDomain(): LibraryStatus =
    LibraryStatus(
        id = id,
        name = name,
        sortOrder = sortOrder,
        enabled = enabled,
        scope = scope
    )

internal fun TagEntity.toDomain(): LibraryTag =
    LibraryTag(
        id = id,
        name = name,
        parentId = parentId,
        sortOrder = sortOrder,
        enabled = enabled
    )

internal fun FieldDefinitionRow.toDomain(): DynamicFieldDefinition {
    val activeOptions = optionDefinitions.activeFieldOptions()
    return DynamicFieldDefinition(
        id = id,
        typeId = typeId,
        typeName = typeName,
        name = name,
        dataType = dataType,
        enabled = enabled,
        sortOrder = sortOrder,
        isFixed = isFixed,
        options = activeOptions.map { it.name },
        optionDefinitions = optionDefinitions,
        scope = scope,
        unit = unit,
        aggregations = aggregations,
        hasValues = hasValues
    )
}

internal fun DynamicFieldValueRow.toDomain(): DynamicFieldValue =
    DynamicFieldValue(
        definitionId = definitionId,
        name = name,
        dataType = dataType,
        value = FieldValueParser.displaySelection(
            value = value.orEmpty(),
            dataType = dataType,
            options = optionDefinitions
        ),
        sortOrder = sortOrder,
        isFixed = isFixed,
        unit = unit
    )

internal fun RecordRow.toDomain(): LibraryRecord = LibraryRecord(
    id = id,
    itemId = itemId,
    startDate = startDate,
    endDate = endDate,
    ratingHalfStars = ratingHalfStars,
    review = review,
    statusSnapshot = statusSnapshot,
    durationMinutes = durationMinutes,
    createdAt = createdAt
)

internal fun TimelineRecordRow.toDomain(
    activityDates: List<Long> = emptyList()
): LibraryTimelineRecord =
    LibraryTimelineRecord(
        recordId = recordId,
        recordStartDate = recordStartDate,
        createdAt = createdAt,
        itemId = itemId,
        typeId = typeId,
        title = title,
        typeName = typeName,
        creator = creator.orEmpty(),
        ratingHalfStars = ratingHalfStars,
        thumbnailPath = thumbnailPath,
        activityDates = activityDates,
        statusSnapshot = statusSnapshot,
        durationMinutes = durationMinutes
    )
