package com.example.mylibrary.data.repository

import androidx.room.withTransaction
import com.example.mylibrary.data.dao.ActivityDao
import com.example.mylibrary.data.dao.DynamicFieldDao
import com.example.mylibrary.data.dao.ItemDao
import com.example.mylibrary.data.dao.ItemTypeDao
import com.example.mylibrary.data.dao.QuoteDao
import com.example.mylibrary.data.dao.RecordDao
import com.example.mylibrary.data.dao.StatusDao
import com.example.mylibrary.data.dao.TagDao
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.ActivityEntity
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.entity.FieldValueEntity
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.data.entity.ItemTagEntity
import com.example.mylibrary.data.entity.QuoteEntity
import com.example.mylibrary.data.entity.RecordEntity
import com.example.mylibrary.data.entity.RecordFieldValueEntity
import com.example.mylibrary.domain.model.ItemChanges
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.FieldValueParser
import com.example.mylibrary.domain.model.ItemDetail
import com.example.mylibrary.domain.model.DynamicFieldValue
import com.example.mylibrary.domain.model.ItemSaveRequest
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.model.NewRecord
import com.example.mylibrary.domain.model.RecordChanges
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

class OfflineLibraryRepository(
    private val database: LibraryDatabase,
    private val itemDao: ItemDao,
    private val itemTypeDao: ItemTypeDao,
    private val statusDao: StatusDao,
    private val recordDao: RecordDao,
    private val activityDao: ActivityDao,
    private val dynamicFieldDao: DynamicFieldDao,
    private val tagDao: TagDao,
    private val quoteDao: QuoteDao = database.quoteDao()
) : LibraryRepository {
    override fun observeItems(
        query: String,
        statusId: Long?,
        tagIds: Set<Long>
    ): Flow<List<LibraryItem>> =
        combine(
            itemDao.observeLibraryRows(
                query = query.trim(),
                statusId = statusId,
                tagIds = tagIds.sorted(),
                tagCount = tagIds.size
            ),
            tagDao.observeActiveItemTagNames(),
            dynamicFieldDao.observeActiveItemValues()
        ) { rows, tagRows, valueRows ->
            val tagsByItem = tagRows.groupBy({ it.itemId }, { it.name })
            val valuesByItem = valueRows.groupBy { it.itemId }
            rows.map { row ->
                row.toDomain().copy(
                    tagNames = tagsByItem[row.id].orEmpty(),
                    dynamicValues = valueRowsByItem(valuesByItem, row.id)
                )
            }
        }

    override fun observeItemTypes(): Flow<List<ItemType>> =
        itemTypeDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeStatuses(scope: StatusScope): Flow<List<LibraryStatus>> =
        statusDao.observeEnabled(scope).map { rows -> rows.map { it.toDomain() } }

    override fun observeItemDetail(itemId: Long): Flow<ItemDetail?> =
        combine(
            itemDao.observeById(itemId),
            recordDao.observeForItem(itemId),
            dynamicFieldDao.observeFieldsForItem(itemId),
            tagDao.observeForItem(itemId),
            dynamicFieldDao.observeRecordValuesForItem(itemId)
        ) { item, records, fields, tags, recordValues ->
            val valuesByRecord = recordValues.groupBy { it.recordId }
            item?.let {
                ItemDetail(
                    item = it.toDomain(),
                    records = records.map { row ->
                        val recordFields = valuesByRecord[row.id].orEmpty()
                            .map { value ->
                                DynamicFieldValue(
                                    definitionId = value.fieldId,
                                    name = value.name,
                                    dataType = value.dataType,
                                    value = FieldValueParser.displaySelection(
                                        value = value.value,
                                        dataType = value.dataType,
                                        options = value.optionDefinitions
                                    ),
                                    sortOrder = value.sortOrder,
                                    isFixed = false,
                                    unit = value.unit
                                )
                            }
                        row.toDomain().copy(
                            dynamicValues = recordFields.associate {
                                it.definitionId to it.value
                            },
                            dynamicFields = recordFields
                        )
                    },
                    fields = fields.map { row -> row.toDomain() },
                    tags = tags.map { row -> row.toDomain() }
                )
            }
        }.flowOn(Dispatchers.Default)

    override fun observeActivities(
        startDate: Long,
        endDate: Long
    ): Flow<List<LibraryActivity>> =
        activityDao.observeRowsBetween(startDate, endDate)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeTimelineRecords(
        startDate: Long,
        endDate: Long
    ): Flow<List<LibraryTimelineRecord>> =
        combine(
            recordDao.observeTimelineBetween(startDate, endDate),
            activityDao.observeRecordDates()
        ) { rows, activityDateRows ->
            val datesByRecord = activityDateRows.groupBy(
                keySelector = { it.recordId },
                valueTransform = { it.date }
            )
            rows.map { row ->
                row.toDomain(datesByRecord[row.recordId].orEmpty())
            }
        }

    override suspend fun createItem(item: NewItem): Long =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val currentStatusId = item.currentStatusId
                ?: DefaultLibraryData.WANT_TO_WATCH_STATUS_ID
            requireItemStatus(currentStatusId)
            val itemId = itemDao.insert(
                ItemEntity(
                    typeId = item.typeId,
                    title = item.title.trim(),
                    coverPath = item.coverPath.cleaned(),
                    thumbnailPath = item.thumbnailPath.cleaned(),
                    currentStatusId = currentStatusId,
                    createdTime = item.createdTime ?: now,
                    updatedTime = now
                )
            )
            saveCreator(itemId, item.typeId, item.creator)
            saveDynamicValues(itemId, item.typeId, item.dynamicValues)
            itemId
        }

    override suspend fun updateItem(itemId: Long, changes: ItemChanges) {
        database.withTransaction {
            val current = requireNotNull(itemDao.getActiveEntity(itemId)) {
                "作品不存在"
            }
            val currentStatusId = changes.currentStatusId
                ?: current.currentStatusId
            currentStatusId?.let { requireItemStatus(it) }
            itemDao.update(
                current.copy(
                    title = changes.title.trim(),
                    coverPath = changes.coverPath.cleaned(),
                    thumbnailPath = changes.thumbnailPath.cleaned(),
                    currentStatusId = currentStatusId,
                    createdTime = current.createdTime,
                    updatedTime = System.currentTimeMillis()
                )
            )
            saveCreator(itemId, current.typeId, changes.creator)
            saveDynamicValues(itemId, current.typeId, changes.dynamicValues)
        }
    }

    override suspend fun saveItem(request: ItemSaveRequest): Long =
        database.withTransaction {
            validateRequest(request)
            val now = System.currentTimeMillis()
            val itemId = upsertItem(request, now)
            saveCreator(itemId, request.typeId, request.creator)
            saveDynamicValues(itemId, request.typeId, request.dynamicValues)
            replaceTags(itemId, request.tagIds)
            if (reconcileRecords(itemId, request, now)) {
                rebuildActivitiesForItem(itemId)
            }
            reconcileQuotes(itemId, request)
            itemId
        }

    override suspend fun updateItemStatus(itemId: Long, statusId: Long) {
        requireItemStatus(statusId)
        check(
            itemDao.updateCurrentStatus(itemId, statusId, System.currentTimeMillis()) == 1
        ) { "作品不存在" }
    }

    override suspend fun deleteItem(itemId: Long) {
        check(itemDao.softDelete(itemId, System.currentTimeMillis()) == 1) {
            "作品不存在或已经删除"
        }
    }

    override suspend fun addRecord(itemId: Long, record: NewRecord): Long =
        database.withTransaction {
            requireNotNull(itemDao.getActiveEntity(itemId)) { "作品不存在" }
            requireValidHalfStarRating(record.ratingHalfStars)
            requireValidDuration(record.durationMinutes)
            val recordId = recordDao.insert(
                RecordEntity(
                    itemId = itemId,
                    startDate = record.startDate,
                    endDate = record.endDate,
                    ratingHalfStars = record.ratingHalfStars,
                    review = record.review.cleaned(),
                    statusSnapshot = record.statusSnapshot.cleaned(),
                    durationMinutes = record.durationMinutes,
                    createdAt = System.currentTimeMillis()
                )
            )
            saveRecordDynamicValues(
                recordId = recordId,
                typeId = requireNotNull(itemDao.getActiveEntity(itemId)).typeId,
                values = record.dynamicValues
            )
            rebuildActivitiesForItem(itemId)
            recordId
        }

    override suspend fun updateRecord(recordId: Long, changes: RecordChanges) {
        database.withTransaction {
            requireValidHalfStarRating(changes.ratingHalfStars)
            requireValidDuration(changes.durationMinutes)
            val current = requireNotNull(recordDao.getById(recordId)) {
                "记录不存在"
            }
            recordDao.update(
                current.copy(
                    startDate = changes.startDate,
                    endDate = changes.endDate,
                    ratingHalfStars = changes.ratingHalfStars,
                    review = changes.review.cleaned(),
                    statusSnapshot = changes.statusSnapshot.cleaned(),
                    durationMinutes = changes.durationMinutes,
                    createdAt = current.createdAt
                )
            )
            saveRecordDynamicValues(
                recordId = recordId,
                typeId = requireNotNull(itemDao.getActiveEntity(current.itemId)).typeId,
                values = changes.dynamicValues
            )
            rebuildActivitiesForItem(current.itemId)
        }
    }

    private fun requireValidHalfStarRating(ratingHalfStars: Int?) {
        require(ratingHalfStars == null || ratingHalfStars in 1..10) {
            "评分必须是 1 到 10 的半星值"
        }
    }

    private fun requireValidDuration(durationMinutes: Long?) {
        require(durationMinutes == null || durationMinutes >= 0L) {
            "记录时长不能为负数"
        }
    }

    private suspend fun requireItemStatus(statusId: Long) {
        requireNotNull(statusDao.getById(statusId)) { "状态不存在" }
            .also {
                check(it.scope == StatusScope.ITEM) {
                    "不能将记录状态用于作品"
                }
            }
    }

    override suspend fun deleteRecord(recordId: Long) {
        database.withTransaction {
            val current = requireNotNull(recordDao.getById(recordId)) {
                "记录不存在"
            }
            recordDao.delete(current)
            rebuildActivitiesForItem(current.itemId)
        }
    }

    private suspend fun validateRequest(request: ItemSaveRequest) {
        require(itemTypeDao.getAll().any { it.id == request.typeId }) {
            "作品类型不存在"
        }
        requireItemStatus(request.currentStatusId)
        request.tagIds.forEach { tagId ->
            require(tagDao.getById(tagId)?.enabled == true) {
                "标签不存在或已停用"
            }
        }
        request.records.forEach { record ->
            require(record.endDate == null || record.endDate >= record.startDate) {
                "结束日期不得早于开始日期"
            }
            require(record.ratingHalfStars == null || record.ratingHalfStars in 1..10) {
                "评分必须是 1 到 10 的半星值"
            }
        }
        val persistedQuoteIds = request.quotes.mapNotNull { it.persistedId }
        require(persistedQuoteIds.size == persistedQuoteIds.distinct().size) {
            "摘录草稿包含重复摘录"
        }
        require(persistedQuoteIds.toSet().intersect(request.deletedQuoteIds).isEmpty()) {
            "摘录不能同时保存和删除"
        }
        request.quotes.forEach { quote ->
            require(quote.localKey.isNotBlank()) { "摘录草稿缺少稳定标识" }
            require(quote.content.isNotBlank()) { "摘录内容不能为空" }
            require(quote.createdTime > 0L) { "摘录创建时间无效" }
        }
    }

    private suspend fun upsertItem(
        request: ItemSaveRequest,
        now: Long
    ): Long {
        if (request.itemId == null) {
            return itemDao.insert(
                ItemEntity(
                    typeId = request.typeId,
                    title = request.title.trim(),
                    coverPath = request.coverPath.cleaned(),
                    thumbnailPath = request.thumbnailPath.cleaned(),
                    currentStatusId = request.currentStatusId,
                    createdTime = now,
                    updatedTime = now
                )
            )
        }
        val current = requireNotNull(itemDao.getActiveEntity(request.itemId)) {
            "作品不存在"
        }
        require(current.typeId == request.typeId) { "不能修改作品类型" }
        itemDao.update(
                current.copy(
                    title = request.title.trim(),
                coverPath = request.coverPath.cleaned(),
                thumbnailPath = request.thumbnailPath.cleaned(),
                currentStatusId = request.currentStatusId,
                    createdTime = current.createdTime,
                updatedTime = now
            )
        )
        return current.id
    }

    private suspend fun replaceTags(itemId: Long, tagIds: Set<Long>) {
        tagDao.unlinkAllForItem(itemId)
        tagIds.sorted().forEach { tagId ->
            tagDao.linkItem(ItemTagEntity(itemId, tagId))
        }
    }

    private suspend fun reconcileRecords(
        itemId: Long,
        request: ItemSaveRequest,
        savedAt: Long
    ): Boolean {
        val existing = recordDao.getAllForItem(itemId).associateBy { it.id }
        val requestedIds = request.records.mapNotNull { it.id }
        require(requestedIds.size == requestedIds.distinct().size) {
            "记录草稿包含重复记录"
        }
        require(requestedIds.all(existing::containsKey)) {
            "记录不存在或不属于当前作品"
        }

        val hasRecordChanges =
            existing.keys != requestedIds.toSet() ||
                request.records.any { draft ->
                    val current = draft.id?.let(existing::get)
                    current == null ||
                        current.startDate != draft.startDate ||
                        current.endDate != draft.endDate ||
                        current.ratingHalfStars != draft.ratingHalfStars ||
                        current.review.cleaned() != draft.review.cleaned() ||
                        current.statusSnapshot.cleaned() != draft.statusSnapshot.cleaned() ||
                        current.durationMinutes != draft.durationMinutes ||
                        draft.dynamicValues.isNotEmpty()
                }
        if (!hasRecordChanges) return false

        (existing.keys - requestedIds.toSet()).forEach { id ->
            recordDao.delete(requireNotNull(existing[id]))
        }
        request.records.forEachIndexed { index, draft ->
            val current = draft.id?.let(existing::get)
            requireValidDuration(draft.durationMinutes)
            val recordId = if (current == null) {
                recordDao.insert(
                    RecordEntity(
                        itemId = itemId,
                        startDate = draft.startDate,
                        endDate = draft.endDate,
                        ratingHalfStars = draft.ratingHalfStars,
                        review = draft.review.cleaned(),
                        statusSnapshot = draft.statusSnapshot.cleaned(),
                        durationMinutes = draft.durationMinutes,
                        createdAt = savedAt + index
                    )
                )
            } else {
                recordDao.update(
                    current.copy(
                        startDate = draft.startDate,
                        endDate = draft.endDate,
                        ratingHalfStars = draft.ratingHalfStars,
                        review = draft.review.cleaned(),
                        statusSnapshot = draft.statusSnapshot.cleaned(),
                        durationMinutes = draft.durationMinutes,
                        createdAt = current.createdAt
                    )
                )
                current.id
            }
            saveRecordDynamicValues(recordId, request.typeId, draft.dynamicValues)
        }
        return true
    }

    private suspend fun reconcileQuotes(
        itemId: Long,
        request: ItemSaveRequest
    ) {
        val existing = quoteDao.getAllForItem(itemId).associateBy { it.id }
        val persistedIds = request.quotes.mapNotNull { it.persistedId }
        require(persistedIds.all(existing::containsKey)) {
            "摘录不存在或不属于当前作品"
        }
        require(request.deletedQuoteIds.all(existing::containsKey)) {
            "待删除摘录不存在或不属于当前作品"
        }

        request.deletedQuoteIds.forEach { quoteId ->
            quoteDao.delete(requireNotNull(existing[quoteId]))
        }
        request.quotes.forEach { draft ->
            val content = draft.content.trim()
            val chapter = draft.chapter.cleaned()
            val page = draft.page.cleaned()
            val current = draft.persistedId?.let(existing::get)
            if (current == null) {
                quoteDao.insert(
                    QuoteEntity(
                        itemId = itemId,
                        content = content,
                        source = null,
                        chapter = chapter,
                        page = page,
                        createdTime = draft.createdTime
                    )
                )
            } else if (
                current.content != content ||
                current.chapter != chapter ||
                current.page != page
            ) {
                quoteDao.update(
                    current.copy(
                        content = content,
                        chapter = chapter,
                        page = page
                    )
                )
            }
        }
    }

    private suspend fun rebuildActivitiesForItem(itemId: Long) {
        val ownerByDate = linkedMapOf<Long, Long>()
        recordDao.getAllForItem(itemId)
            .sortedWith(
                compareByDescending<RecordEntity> { it.createdAt }
                    .thenByDescending { it.id }
            )
            .forEach { record ->
            activityDates(record.startDate, record.endDate).forEach { date ->
                ownerByDate.putIfAbsent(date, record.id)
            }
        }
        activityDao.deleteForItem(itemId)
        if (ownerByDate.isNotEmpty()) {
            activityDao.insertAll(
                ownerByDate.map { (date, recordId) ->
                    ActivityEntity(date = date, itemId = itemId, recordId = recordId)
                }
            )
        }
    }

    private suspend fun saveCreator(itemId: Long, typeId: Long, creator: String) {
        val definition = requireNotNull(
            dynamicFieldDao.getCreatorDefinition(typeId)
        ) { "作品类型缺少作者或导演字段" }
        dynamicFieldDao.replaceValue(
            FieldValueEntity(
                itemId = itemId,
                fieldId = definition.id,
                value = creator.trim()
            )
        )
    }

    private suspend fun saveDynamicValues(
        itemId: Long,
        typeId: Long,
        values: Map<Long, String>
    ) {
        if (values.isEmpty()) return
        val definitions = dynamicFieldDao.getDefinitions(typeId).associateBy { it.id }
        values.forEach { (fieldId, rawValue) ->
            val definition = requireNotNull(definitions[fieldId]) {
                "动态字段不存在"
            }
            require(definition.enabled && !definition.isFixed) {
                "动态字段不可编辑"
            }
            require(definition.scope == FieldScope.ITEM) {
                "记录字段不能保存到作品"
            }
            val existingValue = dynamicFieldDao.getValue(itemId, fieldId)?.value
            val value = normalizeDynamicValue(
                definition = definition,
                rawValue = rawValue,
                existingValue = existingValue
            )
            if (value.isEmpty()) {
                dynamicFieldDao.deleteValue(itemId, fieldId)
            } else {
                dynamicFieldDao.replaceValue(
                    FieldValueEntity(itemId = itemId, fieldId = fieldId, value = value)
                )
            }
        }
    }

    private suspend fun normalizeDynamicValue(
        definition: FieldDefinitionEntity,
        rawValue: String,
        existingValue: String?
    ): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return ""
        return when (definition.dataType) {
            FieldDataType.TEXT -> trimmed
            FieldDataType.NUMBER -> requireNotNull(
                FieldValueParser.normalizeNumber(trimmed)
            ) { "数字字段格式无效" }
            FieldDataType.DATE -> {
                require(runCatching { LocalDate.parse(trimmed) }.isSuccess) {
                    "日期字段格式无效"
                }
                trimmed
            }
            FieldDataType.BOOLEAN -> {
                require(trimmed == "true" || trimmed == "false") {
                    "开关字段格式无效"
                }
                trimmed
            }
            FieldDataType.RATING -> requireNotNull(
                FieldValueParser.parseRatingHalfStars(trimmed)
            ) { "评分必须是 1 到 10 的半星值" }.toString()
            FieldDataType.SINGLE_SELECT,
            FieldDataType.MULTI_SELECT -> normalizeSelectionValue(
                definition = definition,
                value = trimmed,
                existingValue = existingValue
            )
        }
    }

    private fun normalizeSelectionValue(
        definition: FieldDefinitionEntity,
        value: String,
        existingValue: String?
    ): String {
        val multiple = definition.dataType == FieldDataType.MULTI_SELECT
        val requestedIds = FieldValueParser.optionIds(
            value,
            definition.dataType,
            definition.optionDefinitions
        )
        require(requestedIds.isNotEmpty()) { "选择字段包含不存在的选项" }
        require(multiple || requestedIds.size == 1) {
            "单选字段只能选择一个选项"
        }
        val historicalIds = existingValue?.let {
            FieldValueParser.optionIds(
                it,
                definition.dataType,
                definition.optionDefinitions
            )
        }.orEmpty().toSet()
        val byId = definition.optionDefinitions.associateBy { it.id }
        requestedIds.forEach { optionId ->
            val option = requireNotNull(byId[optionId]) {
                "选择字段包含不存在的选项"
            }
            require(option.isActive || optionId in historicalIds) {
                "已停用选项不能用于新选择"
            }
        }
        return FieldValueParser.encodeOptionIds(requestedIds, multiple)
    }

    private suspend fun saveRecordDynamicValues(
        recordId: Long,
        typeId: Long,
        values: Map<Long, String>
    ) {
        if (values.isEmpty()) return
        val definitions = dynamicFieldDao.getDefinitions(typeId).associateBy { it.id }
        values.forEach { (fieldId, rawValue) ->
            val definition = requireNotNull(definitions[fieldId]) {
                "动态字段不存在"
            }
            require(
                definition.enabled &&
                    !definition.isFixed &&
                    definition.scope == FieldScope.RECORD
            ) { "动态字段不可用于记录" }
            val existingValue =
                dynamicFieldDao.getRecordValue(recordId, fieldId)?.value
            val value = normalizeDynamicValue(definition, rawValue, existingValue)
            if (value.isEmpty()) {
                dynamicFieldDao.deleteRecordValue(recordId, fieldId)
            } else {
                dynamicFieldDao.replaceRecordValue(
                    RecordFieldValueEntity(
                        recordId = recordId,
                        fieldId = fieldId,
                        value = value
                    )
                )
            }
        }
    }
}

private fun valueRowsByItem(
    rows: Map<Long, List<com.example.mylibrary.data.model.ItemDynamicValueRow>>,
    itemId: Long
): Map<Long, String> =
    rows[itemId].orEmpty().associate {
        it.fieldId to FieldValueParser.displaySelection(
            value = it.value,
            dataType = it.dataType,
            options = it.optionDefinitions
        )
    }

private fun String?.cleaned(): String? = this?.trim()?.takeIf(String::isNotEmpty)
