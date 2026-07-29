package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldNumberFormatter
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.FieldValueParser
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.domain.model.activeFieldOptions
import com.example.mylibrary.ui.settings.ReportExportConfig
import com.example.mylibrary.ui.settings.ReportStatisticOption
import java.math.BigDecimal
import java.math.RoundingMode
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
        val loadQuotes = resolved.includeQuotes ||
            ReportStatisticOption.QUOTE_COUNT in resolved.basicStatistics
        val itemFieldIds = buildSet {
            resolved.workFields.mapTo(this) { it.fieldId }
            resolved.statisticFields
                .filter { it.scope == FieldScope.ITEM }
                .mapTo(this) { it.fieldId }
        }
        val recordFieldIds = resolved.statisticFields
            .filter { it.scope == FieldScope.RECORD }
            .mapTo(linkedSetOf()) { it.fieldId }
        val data = if (resolved.selectedItemTypeIds.isEmpty()) {
            ReportSourceData(
                records = emptyList(),
                activities = emptyList(),
                itemFieldValues = emptyList(),
                recordFieldValues = emptyList(),
                itemTags = emptyList(),
                quotes = emptyList()
            )
        } else {
            source.loadData(
                range = resolved.period.epochRange(zoneId),
                selectedItemTypeIds = resolved.selectedItemTypeIds,
                itemFieldIds = itemFieldIds,
                recordFieldIds = recordFieldIds,
                includeQuotes = loadQuotes
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
        val recordValues = data.recordFieldValues.associateBy {
            it.ownerId to it.fieldId
        }
        val tagsByItem = data.itemTags.groupBy(ReportSourceItemTag::itemId)
        val records = data.records.map { row ->
            ReportRecordSnapshot(
                recordId = row.recordId,
                itemId = row.itemId,
                startDate = row.startDate,
                endDate = row.endDate,
                ratingHalfStars = row.ratingHalfStars,
                review = row.review,
                customFields = config.statisticFields
                    .filter {
                        it.itemTypeId == row.typeId && it.scope == FieldScope.RECORD
                    }
                    .mapNotNull { field ->
                        val raw = recordValues[row.recordId to field.fieldId]?.value
                            ?: return@mapNotNull null
                        fieldValueSnapshot(field, raw)
                    }
            )
        }
        val items = data.records
            .groupBy(ReportSourceRecord::itemId)
            .values
            .map { itemRecords ->
                val item = itemRecords.first()
                ReportItemSnapshot(
                    itemId = item.itemId,
                    typeId = item.typeId,
                    typeName = item.typeName,
                    typeKind = ItemTypeKind.fromTypeId(item.typeId),
                    title = item.title,
                    creator = item.creator,
                    coverPath = item.coverPath,
                    currentStatus = item.currentStatusName,
                    tags = tagsByItem[item.itemId].orEmpty().map { it.name },
                    customFields = config.workFields
                        .filter { it.itemTypeId == item.typeId }
                        .mapNotNull { field ->
                            val raw = itemValues[item.itemId to field.fieldId]?.value
                                ?: return@mapNotNull null
                            fieldValueSnapshot(field, raw)
                        },
                    recordIds = itemRecords.map { it.recordId }
                )
            }
            .sortedWith(
                compareBy<ReportItemSnapshot> {
                    data.records.first { row -> row.itemId == it.itemId }.typeSortOrder
                }.thenBy { it.title.lowercase() }.thenBy { it.itemId }
            )
        val statistics = config.statisticFields.mapNotNull { field ->
            val values = when (field.scope) {
                FieldScope.ITEM -> data.itemFieldValues
                    .filter { it.fieldId == field.fieldId }
                    .map { it.value }
                FieldScope.RECORD -> data.recordFieldValues
                    .filter { it.fieldId == field.fieldId }
                    .map { it.value }
            }
            calculateStatistic(field, values)
        }
        val selectedStatusIds = config.statusIds
        val statusRows = data.records
            .groupBy(ReportSourceRecord::itemId)
            .values
            .map(List<ReportSourceRecord>::first)
            .filter { item ->
                config.includeAllStatuses ||
                    item.currentStatusId in selectedStatusIds
            }
        val summary = ReportSummarySnapshot(
            readingItemCount = items.count { it.typeKind == ItemTypeKind.BOOK },
            viewingItemCount = items.count { it.typeKind == ItemTypeKind.MOVIE },
            recordCount = records.size,
            activeDayCount = data.activities.map { it.date }.distinct().size,
            quoteCount = data.quotes.size,
            statusCounts = statusRows
                .mapNotNull { it.currentStatusName }
                .groupingBy(String::toString)
                .eachCount()
                .map { ReportNamedCount(it.key, it.value) }
                .sortedWith(compareByDescending<ReportNamedCount> { it.count }.thenBy { it.name }),
            tagCounts = items
                .flatMap { item -> item.tags.distinct().map { it to item.itemId } }
                .distinct()
                .groupingBy { it.first }
                .eachCount()
                .map { ReportNamedCount(it.key, it.value) }
                .sortedWith(compareByDescending<ReportNamedCount> { it.count }.thenBy { it.name }),
            creatorCounts = items
                .mapNotNull { it.creator?.trim()?.takeIf(String::isNotEmpty) }
                .groupingBy { it }
                .eachCount()
                .map { ReportNamedCount(it.key, it.value) }
                .sortedWith(compareByDescending<ReportNamedCount> { it.count }.thenBy { it.name }),
            topActivityDays = data.activities
                .groupingBy { it.date }
                .eachCount()
                .map { ReportDateCount(it.key, it.value) }
                .sortedWith(
                    compareByDescending<ReportDateCount> { it.count }
                        .thenBy { it.date }
                )
                .take(3)
        )
        return ReportDataSnapshot(
            config = config,
            summary = summary,
            items = items,
            records = records,
            statistics = statistics,
            quotes = data.quotes.map {
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
            }
        )
    }

    private fun fieldValueSnapshot(
        field: ResolvedReportField,
        raw: String
    ): ReportFieldValueSnapshot? =
        ReportFieldValueFormatter.formatFieldValue(field, raw)?.let { formatted ->
            ReportFieldValueSnapshot(field, raw, formatted)
        }

    private fun calculateStatistic(
        field: ResolvedReportField,
        rawValues: List<String>
    ): ReportStatisticResult? {
        val aggregation = requireNotNull(field.aggregation)
        return when (field.fieldType) {
            FieldDataType.NUMBER -> numericStatistic(field, aggregation, rawValues)
            FieldDataType.SINGLE_SELECT,
            FieldDataType.MULTI_SELECT ->
                optionStatistic(field, aggregation, rawValues)
            FieldDataType.RATING -> ratingStatistic(field, aggregation, rawValues)
            FieldDataType.TEXT,
            FieldDataType.DATE,
            FieldDataType.BOOLEAN -> null
        }
    }

    private fun numericStatistic(
        field: ResolvedReportField,
        aggregation: FieldAggregation,
        rawValues: List<String>
    ): ReportStatisticResult? {
        val nonBlank = rawValues.filterNot(String::isBlank)
        val numbers = nonBlank.mapNotNull(FieldValueParser::parseNumber)
        if (numbers.isEmpty()) return null
        val sum = numbers.fold(BigDecimal.ZERO, BigDecimal::add)
        val value = when (aggregation) {
            FieldAggregation.SUM -> sum
            FieldAggregation.AVERAGE -> sum.divide(
                BigDecimal(numbers.size),
                12,
                RoundingMode.HALF_UP
            )
            FieldAggregation.MAXIMUM -> requireNotNull(numbers.maxOrNull())
            FieldAggregation.MINIMUM -> requireNotNull(numbers.minOrNull())
            else -> return null
        }
        return ReportStatisticResult(
            field = field,
            aggregation = aggregation,
            rawResult = ReportStatisticValue.Number(
                value = value,
                validValueCount = numbers.size,
                invalidValueCount = nonBlank.size - numbers.size
            ),
            formattedValue = ReportFieldValueFormatter.formatStatisticNumber(
                field,
                aggregation,
                value
            )
        )
    }

    private fun optionStatistic(
        field: ResolvedReportField,
        aggregation: FieldAggregation,
        rawValues: List<String>
    ): ReportStatisticResult? {
        if (aggregation != FieldAggregation.OPTION_DISTRIBUTION) return null
        val activeOptions = field.optionDefinitions.activeFieldOptions()
        val activeIds = activeOptions.mapTo(mutableSetOf()) { it.id }
        val counts = mutableMapOf<Long, Int>()
        var invalidCount = 0
        rawValues.filterNot(String::isBlank).forEach { raw ->
            val parsed = FieldValueParser.optionIds(
                raw,
                field.fieldType,
                field.optionDefinitions
            ).distinct().filter { it in activeIds }
            if (parsed.isEmpty()) invalidCount += 1
            parsed.forEach { id -> counts[id] = counts.getOrDefault(id, 0) + 1 }
        }
        val entries = activeOptions
            .mapNotNull { option ->
                counts[option.id]?.takeIf { it > 0 }?.let { count ->
                    Triple(option.name, count, option.sortOrder)
                }
            }
            .sortedWith(
                compareByDescending<Triple<String, Int, Int>> { it.second }
                    .thenBy { it.third }
                    .thenBy { it.first }
            )
            .map { ReportDistributionEntry(it.first, it.second) }
        if (entries.isEmpty()) return null
        return ReportStatisticResult(
            field = field,
            aggregation = aggregation,
            rawResult = ReportStatisticValue.Distribution(entries, invalidCount),
            formattedValue = entries.joinToString("  ") { "${it.key} ${it.count}" }
        )
    }

    private fun ratingStatistic(
        field: ResolvedReportField,
        aggregation: FieldAggregation,
        rawValues: List<String>
    ): ReportStatisticResult? {
        val nonBlank = rawValues.filterNot(String::isBlank)
        val values = nonBlank.mapNotNull(FieldValueParser::parseRatingHalfStars)
        if (values.isEmpty()) return null
        return when (aggregation) {
            FieldAggregation.RATING_AVERAGE -> {
                val average = BigDecimal(values.sum()).divide(
                    BigDecimal(values.size * 2L),
                    12,
                    RoundingMode.HALF_UP
                )
                ReportStatisticResult(
                    field = field,
                    aggregation = aggregation,
                    rawResult = ReportStatisticValue.Number(
                        average,
                        values.size,
                        nonBlank.size - values.size
                    ),
                    formattedValue = FieldNumberFormatter.formatGrouped(average)
                )
            }
            FieldAggregation.RATING_DISTRIBUTION -> {
                val entries = (10 downTo 1).map { halfStars ->
                    val label = FieldNumberFormatter.formatGrouped(
                        BigDecimal(halfStars).divide(BigDecimal(2))
                    ) + " 星"
                    ReportDistributionEntry(label, values.count { it == halfStars })
                }
                ReportStatisticResult(
                    field = field,
                    aggregation = aggregation,
                    rawResult = ReportStatisticValue.Distribution(
                        entries,
                        nonBlank.size - values.size
                    ),
                    formattedValue = entries.joinToString("  ") {
                        "${it.key} ${it.count}"
                    }
                )
            }
            else -> null
        }
    }
}
