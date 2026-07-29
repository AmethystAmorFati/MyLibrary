package com.example.mylibrary.domain.model

data class NewItem(
    val typeId: Long,
    val title: String,
    val creator: String,
    val coverPath: String?,
    val thumbnailPath: String? = null,
    val dynamicValues: Map<Long, String> = emptyMap(),
    val currentStatusId: Long? = null,
    val createdTime: Long? = null
)

data class ItemChanges(
    val title: String,
    val creator: String,
    val coverPath: String?,
    val thumbnailPath: String? = null,
    val dynamicValues: Map<Long, String> = emptyMap(),
    val currentStatusId: Long? = null,
    val createdTime: Long? = null
)

data class NewRecord(
    val startDate: Long,
    val endDate: Long?,
    val ratingHalfStars: Int?,
    val review: String?,
    val createdAt: Long? = null,
    val dynamicValues: Map<Long, String> = emptyMap(),
    val statusSnapshot: String? = null,
    val durationMinutes: Long? = null
)

data class RecordChanges(
    val startDate: Long,
    val endDate: Long?,
    val ratingHalfStars: Int?,
    val review: String?,
    val createdAt: Long? = null,
    val dynamicValues: Map<Long, String> = emptyMap(),
    val statusSnapshot: String? = null,
    val durationMinutes: Long? = null
)

data class ItemRecordDraft(
    val id: Long?,
    val startDate: Long,
    val endDate: Long?,
    val ratingHalfStars: Int?,
    val review: String?,
    val createdAt: Long?,
    val dynamicValues: Map<Long, String> = emptyMap(),
    val statusSnapshot: String? = null,
    val durationMinutes: Long? = null
)

data class ItemQuoteDraft(
    val localKey: String,
    val persistedId: Long?,
    val content: String,
    val chapter: String?,
    val page: String?,
    val createdTime: Long
)

data class ItemSaveRequest(
    val itemId: Long?,
    val typeId: Long,
    val title: String,
    val creator: String,
    val createdTime: Long,
    val coverPath: String?,
    val thumbnailPath: String?,
    val dynamicValues: Map<Long, String>,
    val currentStatusId: Long,
    val tagIds: Set<Long>,
    val records: List<ItemRecordDraft>,
    val quotes: List<ItemQuoteDraft> = emptyList(),
    val deletedQuoteIds: Set<Long> = emptySet()
)

data class NewFieldDefinition(
    val typeId: Long,
    val name: String,
    val dataType: FieldDataType,
    val scope: FieldScope = FieldScope.ITEM,
    val unit: String? = null,
    val aggregations: Set<FieldAggregation> = emptySet()
)

data class FieldDefinitionChanges(
    val name: String,
    val dataType: FieldDataType,
    val scope: FieldScope,
    val unit: String?,
    val aggregations: Set<FieldAggregation>
)

data class NewTag(
    val name: String,
    val parentId: Long?
)
