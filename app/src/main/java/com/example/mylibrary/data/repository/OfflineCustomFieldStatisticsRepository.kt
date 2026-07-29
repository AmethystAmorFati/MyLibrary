package com.example.mylibrary.data.repository

import com.example.mylibrary.data.dao.DynamicFieldDao
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.model.StatisticFieldValueRow
import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.DistributionEntry
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldNumberFormatter
import com.example.mylibrary.domain.model.FieldValueParser
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.NumericMetric
import com.example.mylibrary.domain.model.activeFieldOptions
import com.example.mylibrary.domain.model.compatibleWith
import com.example.mylibrary.domain.repository.CustomFieldStatisticsRepository
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class OfflineCustomFieldStatisticsRepository(
    private val fieldDao: DynamicFieldDao
) : CustomFieldStatisticsRepository {
    override fun observeStatistics(): Flow<List<CustomFieldStatistic>> =
        combine(
            fieldDao.observeStatisticDefinitions(),
            fieldDao.observeItemStatisticValues(),
            fieldDao.observeRecordStatisticValues()
        ) { definitions, itemValues, recordValues ->
            calculateCustomFieldStatistics(
                definitions = definitions,
                itemValues = itemValues,
                recordValues = recordValues
            )
        }
}

internal fun calculateCustomFieldStatistics(
    definitions: List<FieldDefinitionEntity>,
    itemValues: List<StatisticFieldValueRow>,
    recordValues: List<StatisticFieldValueRow>
): List<CustomFieldStatistic> {
    val itemByField = itemValues.groupBy(StatisticFieldValueRow::fieldId)
    val recordByField = recordValues.groupBy(StatisticFieldValueRow::fieldId)
    return definitions
        .asSequence()
        .filter { it.enabled && !it.isFixed }
        .mapNotNull { definition ->
            val values = when (definition.scope) {
                FieldScope.RECORD -> recordByField[definition.id]
                FieldScope.ITEM -> itemByField[definition.id]
            }.orEmpty()
            statisticFor(definition, values)
        }
        .toList()
}

private fun statisticFor(
    definition: FieldDefinitionEntity,
    rows: List<StatisticFieldValueRow>
): CustomFieldStatistic? {
    val aggregations = definition.aggregations.compatibleWith(definition.dataType)
    if (aggregations.isEmpty()) return null
    return when (definition.dataType) {
        FieldDataType.NUMBER -> numericStatistic(definition, rows, aggregations)
        FieldDataType.SINGLE_SELECT,
        FieldDataType.MULTI_SELECT ->
            optionStatistic(definition, rows, aggregations)
        FieldDataType.RATING -> ratingStatistic(definition, rows, aggregations)
        FieldDataType.TEXT,
        FieldDataType.DATE,
        FieldDataType.BOOLEAN -> null
    }
}

private fun numericStatistic(
    definition: FieldDefinitionEntity,
    rows: List<StatisticFieldValueRow>,
    aggregations: Set<FieldAggregation>
): CustomFieldStatistic.Numeric? {
    val values = rows.mapNotNull { FieldValueParser.parseNumber(it.value) }
    if (values.isEmpty()) return null
    val sum = values.fold(BigDecimal.ZERO, BigDecimal::add)
    val average = sum.divide(
        BigDecimal(values.size),
        12,
        RoundingMode.HALF_UP
    )
    val resolved = mapOf(
        FieldAggregation.SUM to sum,
        FieldAggregation.AVERAGE to average,
        FieldAggregation.MAXIMUM to requireNotNull(values.maxOrNull()),
        FieldAggregation.MINIMUM to requireNotNull(values.minOrNull())
    )
    val order = listOf(
        FieldAggregation.SUM,
        FieldAggregation.AVERAGE,
        FieldAggregation.MAXIMUM,
        FieldAggregation.MINIMUM
    )
    return CustomFieldStatistic.Numeric(
        fieldId = definition.id,
        fieldName = definition.name,
        sortOrder = definition.sortOrder,
        metrics = order.filter { it in aggregations }.map { aggregation ->
            NumericMetric(
                aggregation = aggregation,
                value = FieldNumberFormatter.format(requireNotNull(resolved[aggregation])),
                unit = definition.unit
            )
        }
    )
}

private fun optionStatistic(
    definition: FieldDefinitionEntity,
    rows: List<StatisticFieldValueRow>,
    aggregations: Set<FieldAggregation>
): CustomFieldStatistic.OptionDistribution? {
    if (FieldAggregation.OPTION_DISTRIBUTION !in aggregations) return null
    val activeOptions = definition.optionDefinitions.activeFieldOptions()
    val activeIds = activeOptions.mapTo(mutableSetOf()) { it.id }
    val counts = mutableMapOf<Long, Int>()
    rows.forEach { row ->
        FieldValueParser.optionIds(
            row.value,
            definition.dataType,
            definition.optionDefinitions
        ).distinct().filter { it in activeIds }.forEach { optionId ->
            counts[optionId] = counts.getOrDefault(optionId, 0) + 1
        }
    }
    val entries = activeOptions
        .mapNotNull { option ->
            counts[option.id]?.takeIf { it > 0 }?.let { count ->
                Triple(option, count, option.sortOrder)
            }
        }
        .sortedWith(
            compareByDescending<Triple<com.example.mylibrary.domain.model.FieldOptionDefinition, Int, Int>> {
                it.second
            }.thenBy { it.third }.thenBy { it.first.id }
        )
        .map { (option, count) -> DistributionEntry(option.name, count) }
    if (entries.isEmpty()) return null
    return CustomFieldStatistic.OptionDistribution(
        fieldId = definition.id,
        fieldName = definition.name,
        sortOrder = definition.sortOrder,
        entries = entries
    )
}

private fun ratingStatistic(
    definition: FieldDefinitionEntity,
    rows: List<StatisticFieldValueRow>,
    aggregations: Set<FieldAggregation>
): CustomFieldStatistic.Rating? {
    val values = rows.mapNotNull { FieldValueParser.parseRatingHalfStars(it.value) }
    if (values.isEmpty()) return null
    val average = if (FieldAggregation.RATING_AVERAGE in aggregations) {
        FieldNumberFormatter.format(
            BigDecimal(values.sum())
                .divide(BigDecimal(values.size * 2L), 12, RoundingMode.HALF_UP)
        )
    } else {
        null
    }
    val distribution = if (FieldAggregation.RATING_DISTRIBUTION in aggregations) {
        (10 downTo 1).map { halfStars ->
            val label = FieldNumberFormatter.format(
                BigDecimal(halfStars).divide(BigDecimal(2))
            ) + " 星"
            DistributionEntry(label, values.count { it == halfStars })
        }
    } else {
        emptyList()
    }
    return CustomFieldStatistic.Rating(
        fieldId = definition.id,
        fieldName = definition.name,
        sortOrder = definition.sortOrder,
        average = average,
        distribution = distribution
    )
}
