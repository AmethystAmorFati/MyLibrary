package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.ItemType

data class ReportSourceMetadata(
    val itemTypes: List<ItemType>,
    val fields: List<DynamicFieldDefinition>
)

data class ReportSourceData(
    val records: List<ReportSourceRecord>,
    val activities: List<ReportSourceActivity>,
    val itemFieldValues: List<ReportSourceFieldValue>,
    val recordFieldValues: List<ReportSourceFieldValue>,
    val itemTags: List<ReportSourceItemTag>,
    val quotes: List<ReportSourceQuote>
)

data class ReportSourceRecord(
    val recordId: Long,
    val itemId: Long,
    val startDate: Long,
    val endDate: Long?,
    val ratingHalfStars: Int?,
    val review: String?,
    val typeId: Long,
    val typeName: String,
    val typeSortOrder: Int,
    val title: String,
    val coverPath: String?,
    val currentStatusId: Long?,
    val currentStatusName: String?,
    val creator: String?
)

data class ReportSourceActivity(
    val date: Long,
    val itemId: Long,
    val typeId: Long
)

data class ReportSourceFieldValue(
    val ownerId: Long,
    val fieldId: Long,
    val value: String
)

data class ReportSourceItemTag(
    val itemId: Long,
    val name: String
)

data class ReportSourceQuote(
    val quoteId: Long,
    val itemId: Long,
    val itemTitle: String,
    val content: String,
    val source: String?,
    val page: String?,
    val createdTime: Long,
    val chapter: String? = null
)

interface ReportDataSource {
    suspend fun loadMetadata(): ReportSourceMetadata

    suspend fun loadData(
        range: ReportEpochRange,
        selectedItemTypeIds: Set<Long>,
        itemFieldIds: Set<Long>,
        recordFieldIds: Set<Long>,
        includeQuotes: Boolean
    ): ReportSourceData
}
