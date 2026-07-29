package com.example.mylibrary.backup

import androidx.room.withTransaction
import com.example.mylibrary.backup.model.BackupActivity
import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupFieldDefinition
import com.example.mylibrary.backup.model.BackupFieldOption
import com.example.mylibrary.backup.model.BackupFieldValue
import com.example.mylibrary.backup.model.BackupRecordFieldValue
import com.example.mylibrary.backup.model.BackupItem
import com.example.mylibrary.backup.model.BackupItemTag
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.model.BackupQuote
import com.example.mylibrary.backup.model.BackupRecord
import com.example.mylibrary.backup.model.BackupStatus
import com.example.mylibrary.backup.model.BackupTag
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.ActivityEntity
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.entity.FieldValueEntity
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.data.entity.ItemTagEntity
import com.example.mylibrary.data.entity.ItemTypeEntity
import com.example.mylibrary.data.entity.QuoteEntity
import com.example.mylibrary.data.entity.RecordEntity
import com.example.mylibrary.data.entity.RecordFieldValueEntity
import com.example.mylibrary.data.entity.StatusEntity
import com.example.mylibrary.data.entity.TagEntity
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.StoredCoverImage
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.model.activeFieldOptions
import com.example.mylibrary.domain.model.legacyFieldOptions

class BackupDatabaseStore(
    private val database: LibraryDatabase
) {
    suspend fun readSnapshot(): BackupData = database.withTransaction {
        val dao = database.backupDao()
        BackupData(
            itemTypes = dao.getItemTypes().map {
                BackupItemType(it.id, it.name, it.sortOrder)
            },
            statuses = dao.getStatuses().map {
                BackupStatus(
                    it.id,
                    it.name,
                    it.sortOrder,
                    it.enabled,
                    it.scope.storageValue
                )
            },
            fieldDefinitions = dao.getFieldDefinitions().map {
                val activeOptions = it.optionDefinitions.activeFieldOptions()
                BackupFieldDefinition(
                    id = it.id,
                    typeId = it.typeId,
                    name = it.name,
                    dataType = it.dataType.storageValue,
                    enabled = it.enabled,
                    sortOrder = it.sortOrder,
                    isFixed = it.isFixed,
                    options = activeOptions.map { option -> option.name },
                    optionDefinitions = it.optionDefinitions.map { option ->
                        BackupFieldOption(
                            id = option.id,
                            name = option.name,
                            isActive = option.isActive,
                            sortOrder = option.sortOrder
                        )
                    },
                    scope = it.scope.storageValue,
                    unit = it.unit,
                    aggregations = it.aggregations.mapTo(linkedSetOf()) {
                        aggregation -> aggregation.storageValue
                    }
                )
            },
            tags = dao.getTags().map {
                BackupTag(it.id, it.name, it.parentId, it.sortOrder, it.enabled)
            },
            items = dao.getItems().map {
                BackupItem(
                    id = it.id,
                    typeId = it.typeId,
                    title = it.title,
                    coverRef = it.coverPath,
                    currentStatusId = it.currentStatusId,
                    createdTime = it.createdTime,
                    updatedTime = it.updatedTime,
                    deletedAt = it.deletedAt
                )
            },
            records = dao.getRecords().map {
                BackupRecord(
                    id = it.id,
                    itemId = it.itemId,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    ratingHalfStars = it.ratingHalfStars,
                    review = it.review,
                    statusSnapshot = it.statusSnapshot,
                    durationMinutes = it.durationMinutes,
                    createdAt = it.createdAt
                )
            },
            activities = dao.getActivities().map {
                BackupActivity(it.id, it.date, it.itemId, it.recordId)
            },
            itemTags = dao.getItemTags().map {
                BackupItemTag(it.itemId, it.tagId)
            },
            fieldValues = dao.getFieldValues().map {
                BackupFieldValue(it.id, it.itemId, it.fieldId, it.value)
            },
            quotes = dao.getQuotes().map {
                BackupQuote(
                    id = it.id,
                    itemId = it.itemId,
                    content = it.content,
                    source = it.source,
                    chapter = it.chapter,
                    page = it.page,
                    createdTime = it.createdTime
                )
            },
            recordFieldValues = dao.getRecordFieldValues().map {
                BackupRecordFieldValue(it.id, it.recordId, it.fieldId, it.value)
            }
        )
    }

    suspend fun readStoredCoverPaths(): List<Pair<String?, String?>> =
        database.withTransaction {
            database.backupDao().getItems().mapNotNull { item ->
                val original = item.coverPath?.takeIf(String::isNotBlank)
                val thumbnail = item.thumbnailPath?.takeIf(String::isNotBlank)
                if (original == null && thumbnail == null) null else original to thumbnail
            }.distinct()
    }

    suspend fun replace(
        data: BackupData,
        importedCovers: Map<String, StoredCoverImage>
    ) = database.withTransaction {
        val dao = database.backupDao()

        dao.deleteActivities()
        dao.deleteQuotes()
        dao.deleteItemTags()
        dao.deleteRecordFieldValues()
        dao.deleteFieldValues()
        dao.deleteRecords()
        dao.deleteItems()
        dao.deleteFieldDefinitions()
        dao.deleteTags()
        dao.deleteStatuses()
        dao.deleteItemTypes()

        dao.insertItemTypes(data.itemTypes.map {
            ItemTypeEntity(it.id, it.name, it.sortOrder)
        })
        dao.insertStatuses(data.statuses.map {
            StatusEntity(
                id = it.id,
                name = it.name,
                sortOrder = it.sortOrder,
                scope = StatusScope.fromStorageValue(it.scope),
                enabled = it.enabled
            )
        })
        dao.insertFieldDefinitions(data.fieldDefinitions.map {
            FieldDefinitionEntity(
                id = it.id,
                typeId = it.typeId,
                name = it.name,
                dataType = FieldDataType.fromStorageValue(it.dataType),
                enabled = it.enabled,
                sortOrder = it.sortOrder,
                isFixed = it.isFixed,
                optionDefinitions = it.optionDefinitions
                    .map { option ->
                        FieldOptionDefinition(
                            id = option.id,
                            name = option.name,
                            isActive = option.isActive,
                            sortOrder = option.sortOrder
                        )
                    }
                    .ifEmpty { legacyFieldOptions(it.options) },
                scope = FieldScope.fromStorageValue(it.scope),
                unit = it.unit,
                aggregations = it.aggregations.mapNotNullTo(linkedSetOf()) {
                    aggregation -> FieldAggregation.fromStorageValue(aggregation)
                }
            )
        })
        val (rootTags, childTags) = data.tags.partition { it.parentId == null }
        dao.insertTags((rootTags + childTags).map {
            TagEntity(it.id, it.name, it.parentId, it.sortOrder, it.enabled)
        })
        dao.insertItems(data.items.map {
            val cover = it.coverRef?.let(importedCovers::get)
            ItemEntity(
                id = it.id,
                typeId = it.typeId,
                title = it.title,
                coverPath = cover?.originalPath,
                thumbnailPath = cover?.thumbnailPath,
                currentStatusId = it.currentStatusId,
                createdTime = it.createdTime,
                updatedTime = it.updatedTime,
                deletedAt = it.deletedAt
            )
        })
        dao.insertRecords(data.records.map {
            RecordEntity(
                id = it.id,
                itemId = it.itemId,
                startDate = it.startDate,
                endDate = it.endDate,
                ratingHalfStars = it.ratingHalfStars,
                review = it.review,
                statusSnapshot = it.statusSnapshot,
                durationMinutes = it.durationMinutes,
                createdAt = it.createdAt
            )
        })
        dao.insertActivities(data.activities.map {
            ActivityEntity(it.id, it.date, it.itemId, it.recordId)
        })
        dao.insertItemTags(data.itemTags.map {
            ItemTagEntity(it.itemId, it.tagId)
        })
        dao.insertFieldValues(data.fieldValues.map {
            FieldValueEntity(it.id, it.itemId, it.fieldId, it.value)
        })
        dao.insertRecordFieldValues(data.recordFieldValues.map {
            RecordFieldValueEntity(it.id, it.recordId, it.fieldId, it.value)
        })
        dao.insertQuotes(data.quotes.map {
            QuoteEntity(
                id = it.id,
                itemId = it.itemId,
                content = it.content,
                source = it.source,
                chapter = it.chapter,
                page = it.page,
                createdTime = it.createdTime
            )
        })
    }
}
