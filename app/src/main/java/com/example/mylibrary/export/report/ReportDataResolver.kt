package com.example.mylibrary.export.report

import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.model.MediaItemStatisticsRow
import com.example.mylibrary.data.model.StatisticFieldValueRow
import com.example.mylibrary.data.repository.buildFixedMediaStatistics
import com.example.mylibrary.data.repository.calculateCustomFieldStatistics
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.ui.settings.ReportExportConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface ReportPreparationResult {
    data class Ready(val snapshot: ReportDataSnapshot) : ReportPreparationResult
    data class InvalidConfig(val message: String) : ReportPreparationResult
}

class ReportDataResolver(
    private val source: ReportDataSource,
    private val configResolver: ReportConfigResolver = ReportConfigResolver(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    suspend fun resolve(config: ReportExportConfig): ReportPreparationResult {
        val metadata = source.loadMetadata()
        val resolved = when (
            val result = configResolver.resolve(config, metadata.itemTypes, metadata.fields)
        ) {
            is ReportConfigResolution.Invalid ->
                return ReportPreparationResult.InvalidConfig(result.message)
            is ReportConfigResolution.Success -> result.config
        }
        val itemFieldIds = buildSet {
            resolved.workFields.mapTo(this) { it.fieldId }
            resolved.statisticFields.mapTo(this) { it.fieldId }
        }
        val data = if (resolved.selectedItemTypeIds.isEmpty()) {
            ReportSourceData(
                records = emptyList(),
                activities = emptyList(),
                itemFieldValues = emptyList(),
                itemTags = emptyList(),
                quotes = emptyList()
            )
        } else {
            source.loadData(
                range = resolved.period.epochRange(zoneId),
                selectedItemTypeIds = resolved.selectedItemTypeIds,
                itemFieldIds = itemFieldIds,
                includeQuotes = resolved.includeQuotes || resolved.includeBasicStatistics
            )
        }
        return ReportPreparationResult.Ready(buildSnapshot(resolved, data))
    }

    private fun buildSnapshot(
        config: ResolvedReportConfig,
        data: ReportSourceData
    ): ReportDataSnapshot {
        val itemValues = data.itemFieldValues.associateBy {
            it.ownerId to it.fieldId
        }
        val tagsByItem = data.itemTags.groupBy(ReportSourceItemTag::itemId)
        val activityDatesByItem = data.activities
            .groupBy(ReportSourceActivity::itemId)
            .mapValues { (_, activities) ->
                activities.map { localDate(it.date) }.distinct().sorted()
            }
        val records = data.records.sortedWith(recordOrder())
        val items = data.records
            .groupBy(ReportSourceRecord::itemId)
            .values
            .map { itemRecords ->
                val orderedRecords = itemRecords.sortedWith(recordOrder())
                val item = orderedRecords.first()
                val activityDates = activityDatesByItem[item.itemId].orEmpty()
                ReportItemSnapshot(
                    itemId = item.itemId,
                    typeId = item.typeId,
                    typeName = item.typeName,
                    typeKind = ItemTypeKind.fromTypeId(item.typeId),
                    title = item.title,
                    creator = item.creator,
                    coverPath = item.coverPath,
                    currentStatusId = item.currentStatusId,
                    currentStatus = item.currentStatusName,
                    currentStatusSortOrder = item.currentStatusSortOrder,
                    tags = tagsByItem[item.itemId].orEmpty()
                        .distinctBy { it.tagId }
                        .sortedWith(compareBy({ it.sortOrder }, { it.tagId }))
                        .map { it.name },
                    customFields = config.workFields
                        .filter { it.itemTypeId == item.typeId }
                        .mapNotNull { field ->
                            val raw = itemValues[item.itemId to field.fieldId]?.value
                                ?: return@mapNotNull null
                            fieldValueSnapshot(field, raw)
                        },
                    firstActivityDate = activityDates.firstOrNull()
                        ?.atStartOfDay(zoneId)
                        ?.toInstant()
                        ?.toEpochMilli()
                        ?: item.startDate,
                    firstRecordCreatedAt = orderedRecords.first().recordCreatedAt,
                    activityDayCount = activityDates.size,
                    periodDurationMinutes = orderedRecords
                        .mapNotNull(ReportSourceRecord::durationMinutes)
                        .takeIf { it.isNotEmpty() }
                        ?.sum()
                )
            }
            .sortedWith(
                compareBy<ReportItemSnapshot> { it.firstActivityDate }
                    .thenBy { it.firstRecordCreatedAt }
                    .thenBy { it.itemId }
            )
        val selectedItemIds = items.mapTo(linkedSetOf()) { it.itemId }
        val selectedActivities = data.activities.filter { it.itemId in selectedItemIds }
        val selectedQuotes = data.quotes
            .filter { it.itemId in selectedItemIds }
            .sortedWith(
                compareByDescending<ReportSourceQuote> { it.createdTime }
                    .thenByDescending { it.quoteId }
            )
        val distinctTagBindings = data.itemTags
            .filter { it.itemId in selectedItemIds }
            .distinctBy { it.itemId to it.tagId }
        val durationValues = records.mapNotNull(ReportSourceRecord::durationMinutes)

        val summary = ReportSummarySnapshot(
            itemCount = items.size,
            readingItemCount = items.count { it.typeKind == ItemTypeKind.BOOK },
            viewingItemCount = items.count { it.typeKind == ItemTypeKind.MOVIE },
            recordCount = records.size,
            activeDayCount = selectedActivities.map { localDate(it.date) }.distinct().size,
            quoteCount = selectedQuotes.size,
            totalDurationMinutes = durationValues.takeIf { it.isNotEmpty() }?.sum(),
            statusCounts = currentItemStatusCounts(items, config),
            tagCounts = distinctTagBindings
                .groupBy(ReportSourceItemTag::tagId)
                .values
                .map { bindings ->
                    val tag = bindings.first()
                    Triple(ReportNamedCount(tag.name, bindings.size), tag.sortOrder, tag.tagId)
                }
                .sortedWith(
                    compareByDescending<Triple<ReportNamedCount, Int, Long>> {
                        it.first.count
                    }.thenBy { it.second }.thenBy { it.third }
                )
                .map { it.first },
            creatorCounts = items
                .mapNotNull { it.creator?.trim()?.takeIf(String::isNotEmpty) }
                .groupingBy { it }
                .eachCount()
                .map { ReportNamedCount(it.key, it.value) }
                .sortedWith(compareByDescending<ReportNamedCount> { it.count }.thenBy { it.name }),
            topActivityDays = selectedActivities
                .groupingBy { localDate(it.date) }
                .eachCount()
                .map {
                    ReportDateCount(
                        it.key.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                        it.value
                    )
                }
                .sortedWith(
                    compareByDescending<ReportDateCount> { it.count }
                        .thenBy { it.date }
                )
        )
        val recordsByItem = records.groupBy(ReportSourceRecord::itemId)
        val quoteCountsByItem = selectedQuotes
            .groupingBy(ReportSourceQuote::itemId)
            .eachCount()
        val mediaStatistics = buildFixedMediaStatistics(
            items.map { item ->
                val itemRecords = recordsByItem[item.itemId].orEmpty()
                val valuedRecords = itemRecords.mapNotNull { it.durationMinutes }
                MediaItemStatisticsRow(
                    itemId = item.itemId,
                    typeId = item.typeId,
                    itemTitle = item.title,
                    recordCount = itemRecords.size.toLong(),
                    quoteCount = quoteCountsByItem[item.itemId]?.toLong() ?: 0L,
                    valuedRecordCount = valuedRecords.size.toLong(),
                    totalDurationMinutes = valuedRecords
                        .takeIf { it.isNotEmpty() }
                        ?.sum(),
                    maximumSingleDurationMinutes = valuedRecords.maxOrNull()
                )
            }
        )
        return ReportDataSnapshot(
            config = config,
            summary = summary,
            items = items,
            quotes = selectedQuotes.map {
                ReportQuoteSnapshot(
                    quoteId = it.quoteId,
                    itemId = it.itemId,
                    itemTitle = it.itemTitle,
                    content = it.content,
                    source = it.source,
                    chapter = it.chapter,
                    page = it.page,
                    createdTime = it.createdTime
                )
            },
            representativeItemId = items
                .sortedWith(companionItemOrder())
                .firstOrNull { it.coverPath?.isNotBlank() == true }
                ?.itemId,
            monthlySummaries = monthlySummaries(
                config.period,
                items,
                selectedActivities,
                records
            ),
            companionItems = items
                .sortedWith(companionItemOrder())
                .take(3)
                .map {
                    ReportCompanionSnapshot(
                        itemId = it.itemId,
                        title = it.title,
                        creator = it.creator,
                        activityDayCount = it.activityDayCount
                    )
                },
            mediaStatistics = mediaStatistics,
            customFieldStatistics = itemFieldStatistics(
                config = config,
                values = data.itemFieldValues.filter { it.ownerId in selectedItemIds }
            )
        )
    }

    private fun currentItemStatusCounts(
        items: List<ReportItemSnapshot>,
        config: ResolvedReportConfig
    ): List<ReportNamedCount> {
        val included = if (config.includeAllStatuses) {
            items
        } else {
            items.filter {
                it.currentStatusId?.let(config.statusIds::contains) == true
            }
        }
        val resolved = included
            .filter { it.currentStatusId != null }
            .groupBy { requireNotNull(it.currentStatusId) }
            .values
            .map { statusItems ->
                val item = statusItems.first()
                Triple(
                    ReportNamedCount(
                        item.currentStatus
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: "状态不可用",
                        statusItems.size
                    ),
                    item.currentStatusSortOrder ?: Int.MAX_VALUE,
                    requireNotNull(item.currentStatusId)
                )
            }
            .sortedWith(
                compareBy<Triple<ReportNamedCount, Int, Long>> { it.second }
                    .thenBy { it.third }
            )
            .map { it.first }
        val unsetCount = included.count { it.currentStatusId == null }
        return if (config.includeAllStatuses && unsetCount > 0) {
            resolved + ReportNamedCount("未设置", unsetCount)
        } else {
            resolved
        }
    }

    private fun itemFieldStatistics(
        config: ResolvedReportConfig,
        values: List<ReportSourceFieldValue>
    ): List<ReportFieldStatisticGroup> {
        if (!config.includeFieldStatistics) return emptyList()
        return config.statisticFields
            .groupBy(ResolvedReportField::itemTypeId)
            .mapNotNull { (typeId, selectedFields) ->
                val definitions = selectedFields
                    .groupBy(ResolvedReportField::fieldId)
                    .map { (_, selections) ->
                        val field = selections.first()
                        FieldDefinitionEntity(
                            id = field.fieldId,
                            typeId = field.itemTypeId,
                            name = field.fieldName,
                            dataType = field.fieldType,
                            enabled = true,
                            sortOrder = field.fieldSortOrder,
                            isFixed = false,
                            optionDefinitions = field.optionDefinitions,
                            scope = FieldScope.ITEM,
                            unit = field.unit,
                            aggregations = selections.mapNotNullTo(linkedSetOf()) {
                                it.aggregation
                            }
                        )
                    }
                val definitionIds = definitions.mapTo(hashSetOf()) { it.id }
                val statistics = calculateCustomFieldStatistics(
                    definitions = definitions,
                    itemValues = values
                        .filter { value -> value.fieldId in definitionIds }
                        .map {
                            StatisticFieldValueRow(
                                fieldId = it.fieldId,
                                ownerId = it.ownerId,
                                value = it.value
                            )
                        },
                    recordValues = emptyList()
                )
                statistics.takeIf { it.isNotEmpty() }?.let {
                    ReportFieldStatisticGroup(
                        typeId = typeId,
                        typeKind = ItemTypeKind.fromTypeId(typeId),
                        statistics = statistics
                    )
                }
            }
            .sortedWith(compareBy { it.typeKind.ordinal })
    }

    private fun monthlySummaries(
        period: ReportPeriod,
        items: List<ReportItemSnapshot>,
        activities: List<ReportSourceActivity>,
        records: List<ReportSourceRecord>
    ): List<ReportMonthSnapshot> {
        if (period !is ReportPeriod.Year) return emptyList()
        val itemsById = items.associateBy(ReportItemSnapshot::itemId)
        return (1..12).map { month ->
            val monthRecords = records.filter {
                localDate(it.startDate).monthValue == month
            }
            val monthByItem = activities
                .filter { localDate(it.date).monthValue == month }
                .groupBy(ReportSourceActivity::itemId)
            val candidates: List<MonthlyRepresentativeCandidate> = monthByItem.keys
                .mapNotNull { itemId ->
                    val item = itemsById[itemId] ?: return@mapNotNull null
                    val dates = monthByItem[itemId].orEmpty()
                        .map { localDate(it.date) }
                        .distinct()
                    val firstDate = dates.minOrNull()
                        ?: return@mapNotNull null
                    MonthlyRepresentativeCandidate(
                        item = item,
                        activityDayCount = dates.size,
                        firstDate = firstDate
                    )
                }
                .sortedWith(
                    compareByDescending<MonthlyRepresentativeCandidate> {
                        it.activityDayCount
                    }.thenBy { it.firstDate }.thenBy { it.item.itemId }
                )
            ReportMonthSnapshot(
                month = month,
                itemCount = (
                    monthByItem.keys +
                        monthRecords.map(ReportSourceRecord::itemId)
                    ).distinct().size,
                recordCount = monthRecords.size,
                totalDurationMinutes = monthRecords
                    .mapNotNull(ReportSourceRecord::durationMinutes)
                    .takeIf { it.isNotEmpty() }
                    ?.sum(),
                representativeItemId = candidates
                    .firstOrNull { it.item.coverPath?.isNotBlank() == true }
                    ?.item
                    ?.itemId,
                representativeCandidateItemIds = candidates.map { it.item.itemId }
            )
        }
    }

    private data class MonthlyRepresentativeCandidate(
        val item: ReportItemSnapshot,
        val activityDayCount: Int,
        val firstDate: LocalDate
    )

    private fun recordOrder(): Comparator<ReportSourceRecord> =
        compareBy<ReportSourceRecord> { it.startDate }
            .thenBy { it.recordCreatedAt }
            .thenBy { it.recordId }

    private fun companionItemOrder(): Comparator<ReportItemSnapshot> =
        compareByDescending<ReportItemSnapshot> { it.activityDayCount }
            .thenBy { it.firstActivityDate }
            .thenBy { it.itemId }

    private fun localDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    private fun fieldValueSnapshot(
        field: ResolvedReportField,
        raw: String
    ): ReportFieldValueSnapshot? =
        ReportFieldValueFormatter.formatFieldValue(field, raw)?.let { formatted ->
            ReportFieldValueSnapshot(field, raw, formatted)
        }
}
