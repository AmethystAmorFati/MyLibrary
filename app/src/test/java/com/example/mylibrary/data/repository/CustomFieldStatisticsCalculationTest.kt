package com.example.mylibrary.data.repository

import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.data.model.StatisticFieldValueRow
import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.FieldValueParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFieldStatisticsCalculationTest {
    @Test
    fun numericStatisticsIgnoreInvalidAndEmptyValuesButIncludeZero() {
        val field = definition(
            id = 1L,
            type = FieldDataType.NUMBER,
            unit = "页",
            aggregations = setOf(
                FieldAggregation.SUM,
                FieldAggregation.AVERAGE,
                FieldAggregation.MAXIMUM,
                FieldAggregation.MINIMUM
            )
        )
        val result = calculateCustomFieldStatistics(
            definitions = listOf(field),
            itemValues = listOf(
                value(1L, 1L, "0"),
                value(1L, 2L, "12.5"),
                value(1L, 3L, "-2"),
                value(1L, 4L, "invalid"),
                value(1L, 5L, "")
            ),
            recordValues = emptyList()
        ).single() as CustomFieldStatistic.Numeric

        assertEquals(
            listOf("10.5", "3.5", "12.5", "-2"),
            result.metrics.map { it.value }
        )
        assertTrue(result.metrics.all { it.unit == "页" })
    }

    @Test
    fun recordValuesAreCountedIndependently() {
        val field = definition(
            id = 2L,
            type = FieldDataType.NUMBER,
            scope = FieldScope.RECORD,
            aggregations = setOf(FieldAggregation.SUM, FieldAggregation.AVERAGE)
        )
        val result = calculateCustomFieldStatistics(
            definitions = listOf(field),
            itemValues = listOf(value(2L, 99L, "100")),
            recordValues = listOf(
                value(2L, 10L, "12"),
                value(2L, 11L, "9")
            )
        ).single() as CustomFieldStatistic.Numeric

        assertEquals(listOf("21", "10.5"), result.metrics.map { it.value })
    }

    @Test
    fun optionDistributionUsesStableIdsSortsCountsAndHidesInactiveOptions() {
        val options = listOf(
            FieldOptionDefinition(1L, "电子阅读", true, 0),
            FieldOptionDefinition(2L, "纸质书", true, 1),
            FieldOptionDefinition(3L, "听书", false, 2)
        )
        val field = definition(
            id = 3L,
            type = FieldDataType.SINGLE_SELECT,
            aggregations = setOf(FieldAggregation.OPTION_DISTRIBUTION),
            options = options
        )
        val result = calculateCustomFieldStatistics(
            definitions = listOf(field),
            itemValues = listOf(
                value(3L, 1L, FieldValueParser.encodeOptionIds(listOf(2L), false)),
                value(3L, 2L, FieldValueParser.encodeOptionIds(listOf(1L), false)),
                value(3L, 3L, FieldValueParser.encodeOptionIds(listOf(2L), false)),
                value(3L, 4L, FieldValueParser.encodeOptionIds(listOf(1L), false)),
                value(3L, 5L, FieldValueParser.encodeOptionIds(listOf(3L), false))
            ),
            recordValues = emptyList()
        ).single() as CustomFieldStatistic.OptionDistribution

        assertEquals(listOf("电子阅读", "纸质书"), result.entries.map { it.label })
        assertEquals(listOf(2, 2), result.entries.map { it.count })
    }

    @Test
    fun multiSelectCountsEachSelectedOptionOncePerValue() {
        val options = listOf(
            FieldOptionDefinition(1L, "成长", true, 0),
            FieldOptionDefinition(2L, "女性", true, 1)
        )
        val field = definition(
            id = 4L,
            type = FieldDataType.MULTI_SELECT,
            aggregations = setOf(FieldAggregation.OPTION_DISTRIBUTION),
            options = options
        )
        val result = calculateCustomFieldStatistics(
            definitions = listOf(field),
            itemValues = listOf(
                value(4L, 1L, FieldValueParser.encodeOptionIds(listOf(1L, 2L), true)),
                value(4L, 2L, FieldValueParser.encodeOptionIds(listOf(1L), true))
            ),
            recordValues = emptyList()
        ).single() as CustomFieldStatistic.OptionDistribution

        assertEquals(listOf("成长", "女性"), result.entries.map { it.label })
        assertEquals(listOf(2, 1), result.entries.map { it.count })
    }

    @Test
    fun ratingAverageIgnoresInvalidValuesAndDistributionUsesHalfStars() {
        val field = definition(
            id = 5L,
            type = FieldDataType.RATING,
            aggregations = setOf(
                FieldAggregation.RATING_AVERAGE,
                FieldAggregation.RATING_DISTRIBUTION
            )
        )
        val result = calculateCustomFieldStatistics(
            definitions = listOf(field),
            itemValues = listOf(
                value(5L, 1L, "10"),
                value(5L, 2L, "7"),
                value(5L, 3L, ""),
                value(5L, 4L, "invalid")
            ),
            recordValues = emptyList()
        ).single() as CustomFieldStatistic.Rating

        assertEquals("4.25", result.average)
        assertEquals(1, result.distribution.first { it.label == "5 星" }.count)
        assertEquals(1, result.distribution.first { it.label == "3.5 星" }.count)
    }

    @Test
    fun unsupportedAggregationsDoNotProduceCards() {
        val text = definition(
            id = 6L,
            type = FieldDataType.TEXT,
            aggregations = setOf(FieldAggregation.SUM)
        )
        assertTrue(
            calculateCustomFieldStatistics(
                definitions = listOf(text),
                itemValues = listOf(value(6L, 1L, "12")),
                recordValues = emptyList()
            ).isEmpty()
        )
    }

    @Test
    fun invalidFieldDoesNotHideAnotherValidStatistic() {
        val invalid = definition(
            id = 6L,
            type = FieldDataType.TEXT,
            aggregations = setOf(FieldAggregation.SUM)
        )
        val valid = definition(
            id = 7L,
            type = FieldDataType.NUMBER,
            aggregations = setOf(FieldAggregation.SUM)
        )

        val result = calculateCustomFieldStatistics(
            definitions = listOf(invalid, valid),
            itemValues = listOf(
                value(6L, 1L, "invalid"),
                value(7L, 1L, "12")
            ),
            recordValues = emptyList()
        )

        assertEquals(listOf(7L), result.map { it.fieldId })
    }

    private fun definition(
        id: Long,
        type: FieldDataType,
        scope: FieldScope = FieldScope.ITEM,
        unit: String? = null,
        aggregations: Set<FieldAggregation>,
        options: List<FieldOptionDefinition> = emptyList()
    ) = FieldDefinitionEntity(
        id = id,
        typeId = 1L,
        name = "字段$id",
        dataType = type,
        sortOrder = id.toInt(),
        optionDefinitions = options,
        scope = scope,
        unit = unit,
        aggregations = aggregations
    )

    private fun value(fieldId: Long, ownerId: Long, value: String) =
        StatisticFieldValueRow(fieldId, ownerId, value)
}
