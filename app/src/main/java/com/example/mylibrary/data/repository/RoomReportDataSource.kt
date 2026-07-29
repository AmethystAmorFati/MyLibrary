package com.example.mylibrary.data.repository

import androidx.room.withTransaction
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.export.report.ReportDataSource
import com.example.mylibrary.export.report.ReportEpochRange
import com.example.mylibrary.export.report.ReportSourceActivity
import com.example.mylibrary.export.report.ReportSourceData
import com.example.mylibrary.export.report.ReportSourceFieldValue
import com.example.mylibrary.export.report.ReportSourceItemTag
import com.example.mylibrary.export.report.ReportSourceMetadata
import com.example.mylibrary.export.report.ReportSourceQuote
import com.example.mylibrary.export.report.ReportSourceRecord
import com.example.mylibrary.domain.model.DynamicFieldDefinition

class RoomReportDataSource(
    private val database: LibraryDatabase
) : ReportDataSource {
    override suspend fun loadMetadata(): ReportSourceMetadata =
        database.withTransaction {
            val dao = database.reportDao()
            val types = dao.getItemTypes().map { it.toDomain() }
            val typeById = types.associateBy { it.id }
            val fields = dao.getFieldDefinitions().mapNotNull { field ->
                val type = typeById[field.typeId] ?: return@mapNotNull null
                DynamicFieldDefinition(
                    id = field.id,
                    typeId = field.typeId,
                    typeName = type.name,
                    name = field.name,
                    dataType = field.dataType,
                    enabled = field.enabled,
                    sortOrder = field.sortOrder,
                    isFixed = field.isFixed,
                    options = field.optionDefinitions
                        .filter { it.isActive }
                        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                        .map { it.name },
                    optionDefinitions = field.optionDefinitions,
                    scope = field.scope,
                    unit = field.unit,
                    aggregations = field.aggregations
                )
            }
            ReportSourceMetadata(types, fields)
        }

    override suspend fun loadData(
        range: ReportEpochRange,
        selectedItemTypeIds: Set<Long>,
        itemFieldIds: Set<Long>,
        recordFieldIds: Set<Long>,
        includeQuotes: Boolean
    ): ReportSourceData = database.withTransaction {
        val dao = database.reportDao()
        val typeIds = selectedItemTypeIds.sorted()
        val typeCount = typeIds.size
        val records = dao.getRecords(
            range.startInclusive,
            range.endExclusive,
            typeIds,
            typeCount
        )
        val itemIds = records.map(ReportSourceRecordMapper::itemId).distinct()
        val recordIds = records.map(ReportSourceRecordMapper::recordId)
        val itemValues = if (itemIds.isEmpty() || itemFieldIds.isEmpty()) {
            emptyList()
        } else {
            dao.getItemFieldValues(itemIds, itemFieldIds.sorted()).map {
                ReportSourceFieldValue(it.itemId, it.fieldId, it.value)
            }
        }
        val recordValues = if (recordIds.isEmpty() || recordFieldIds.isEmpty()) {
            emptyList()
        } else {
            dao.getRecordFieldValues(recordIds, recordFieldIds.sorted()).map {
                ReportSourceFieldValue(it.recordId, it.fieldId, it.value)
            }
        }
        val tags = if (itemIds.isEmpty()) {
            emptyList()
        } else {
            dao.getItemTags(itemIds).map { ReportSourceItemTag(it.itemId, it.name) }
        }
        val quotes = if (includeQuotes) {
            dao.getQuotes(
                range.startInclusive,
                range.endExclusive,
                typeIds,
                typeCount
            ).map {
                ReportSourceQuote(
                    quoteId = it.quoteId,
                    itemId = it.itemId,
                    itemTitle = it.itemTitle,
                    content = it.content,
                    source = it.source,
                    page = it.page,
                    createdTime = it.createdTime,
                    chapter = it.chapter
                )
            }
        } else {
            emptyList()
        }
        ReportSourceData(
            records = records.map {
                ReportSourceRecord(
                    recordId = it.recordId,
                    itemId = it.itemId,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    ratingHalfStars = it.ratingHalfStars,
                    review = it.review,
                    typeId = it.typeId,
                    typeName = it.typeName,
                    typeSortOrder = it.typeSortOrder,
                    title = it.title,
                    coverPath = it.coverPath,
                    currentStatusId = it.currentStatusId,
                    currentStatusName = it.currentStatusName,
                    creator = it.creator
                )
            },
            activities = dao.getActivities(
                range.startInclusive,
                range.endExclusive,
                typeIds,
                typeCount
            ).map { ReportSourceActivity(it.date, it.itemId, it.typeId) },
            itemFieldValues = itemValues,
            recordFieldValues = recordValues,
            itemTags = tags,
            quotes = quotes
        )
    }
}

private object ReportSourceRecordMapper {
    fun itemId(value: com.example.mylibrary.data.model.ReportRecordRow): Long =
        value.itemId

    fun recordId(value: com.example.mylibrary.data.model.ReportRecordRow): Long =
        value.recordId
}
