package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.FieldValueParser
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportFieldValueFormatterTest {
    @Test
    fun numberFormattingGroupsDigitsDropsZerosAndAppendsUnitOnce() {
        val field = field(FieldDataType.NUMBER, unit = "页")

        assertEquals(
            "12,345.5 页",
            ReportFieldValueFormatter.formatFieldValue(field, "12345.500")
        )
        assertEquals(
            "12,345.5 页",
            ReportFieldValueFormatter.formatStatisticNumber(
                field.copy(aggregation = FieldAggregation.SUM),
                FieldAggregation.SUM,
                BigDecimal("12345.500")
            )
        )
        assertEquals(
            "12,345.5",
            ReportFieldValueFormatter.formatFieldValue(field.copy(unit = null), "12345.5")
        )
        assertNull(ReportFieldValueFormatter.formatFieldValue(field, "12 页"))
    }

    @Test
    fun unsetBooleanDiffersFromFalse() {
        val field = field(FieldDataType.BOOLEAN)

        assertNull(ReportFieldValueFormatter.formatFieldValue(field, null))
        assertEquals("否", ReportFieldValueFormatter.formatFieldValue(field, "false"))
        assertEquals("是", ReportFieldValueFormatter.formatFieldValue(field, "true"))
    }

    @Test
    fun multiSelectUsesStableOptionOrderAndInactiveHistoryStillResolves() {
        val options = listOf(
            FieldOptionDefinition(1, "成长", true, 2),
            FieldOptionDefinition(2, "女性", true, 0),
            FieldOptionDefinition(3, "历史项", false, 1)
        )
        val field = field(FieldDataType.MULTI_SELECT, options = options)

        assertEquals(
            "女性  成长",
            ReportFieldValueFormatter.formatFieldValue(
                field,
                FieldValueParser.encodeOptionIds(listOf(1, 2), multiple = true)
            )
        )
        assertEquals(
            "历史项",
            ReportFieldValueFormatter.formatFieldValue(
                field,
                FieldValueParser.encodeOptionIds(listOf(3), multiple = true)
            )
        )
    }

    @Test
    fun ratingUsesExistingHalfStarParser() {
        val field = field(FieldDataType.RATING)

        assertEquals("4.5 星", ReportFieldValueFormatter.formatFieldValue(field, "9"))
        assertNull(ReportFieldValueFormatter.formatFieldValue(field, "11"))
    }

    private fun field(
        type: FieldDataType,
        unit: String? = null,
        options: List<FieldOptionDefinition> = emptyList()
    ) = ResolvedReportField(
        fieldId = 1,
        itemTypeId = 1,
        itemTypeName = "书",
        itemTypeSortOrder = 0,
        fieldName = "字段",
        fieldType = type,
        scope = FieldScope.ITEM,
        unit = unit,
        aggregation = null,
        fieldSortOrder = 0,
        optionDefinitions = options
    )
}
