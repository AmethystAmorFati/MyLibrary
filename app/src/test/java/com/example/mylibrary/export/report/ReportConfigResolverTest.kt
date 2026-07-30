package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.ui.settings.ReportExportConfig
import com.example.mylibrary.ui.settings.ReportStatisticSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportConfigResolverTest {
    private val resolver = ReportConfigResolver()
    private val types = listOf(
        ItemType(1, "阅读", 0),
        ItemType(2, "观影", 1)
    )

    @Test
    fun resolvesByStableIdsAndKeepsSameNameFieldsIndependent() {
        val fields = listOf(
            field(11, 1, "数量", "页", 0),
            field(21, 2, "数量", "部", 0)
        )
        val result = resolver.resolve(
            config(
                typeId = null,
                workIds = setOf(11L, 21L),
                selections = setOf(
                    ReportStatisticSelection(11, FieldAggregation.SUM),
                    ReportStatisticSelection(21, FieldAggregation.AVERAGE)
                )
            ),
            types,
            fields
        ) as ReportConfigResolution.Success

        assertEquals(listOf(11L, 21L), result.config.workFields.map { it.fieldId })
        assertEquals(
            listOf(11L, 21L),
            result.config.statisticFields.map { it.fieldId }
        )
        assertEquals(listOf("页", "部"), result.config.statisticFields.map { it.unit })
        assertEquals(listOf("阅读", "观影"), result.config.statisticFields.map { it.itemTypeName })
    }

    @Test
    fun singleTypeRemovesCrossTypeDeletedDisabledAndUnsupportedSelections() {
        val fields = listOf(
            field(11, 1, "页数", "页", 0),
            field(12, 1, "禁用", null, 1, enabled = false),
            field(13, 1, "固定", null, 2, fixed = true),
            field(21, 2, "片长", "分钟", 0)
        )
        val result = resolver.resolve(
            config(
                typeId = 1,
                workIds = setOf(11L, 12L, 13L, 21L, 999L),
                selections = setOf(
                    ReportStatisticSelection(11, FieldAggregation.SUM),
                    ReportStatisticSelection(11, FieldAggregation.RATING_AVERAGE),
                    ReportStatisticSelection(12, FieldAggregation.SUM),
                    ReportStatisticSelection(13, FieldAggregation.SUM),
                    ReportStatisticSelection(21, FieldAggregation.SUM)
                )
            ),
            types,
            fields
        ) as ReportConfigResolution.Success

        assertEquals(setOf(1L), result.config.selectedItemTypeIds)
        assertEquals(listOf(11L), result.config.workFields.map { it.fieldId })
        assertEquals(
            listOf(FieldAggregation.SUM),
            result.config.statisticFields.map { it.aggregation }
        )
    }

    @Test
    fun recordScopeFieldsAreIgnoredByTheReportLayer() {
        val original = field(
            id = 11,
            typeId = 1,
            name = "本次页数",
            unit = "页",
            order = 3,
            scope = FieldScope.RECORD
        )
        val result = resolver.resolve(
            config(
                typeId = 1,
                selections = setOf(
                    ReportStatisticSelection(11, FieldAggregation.AVERAGE),
                    ReportStatisticSelection(11, FieldAggregation.SUM)
                )
            ),
            types,
            listOf(original)
        )

        assertTrue(result is ReportConfigResolution.Invalid)
    }

    @Test
    fun invalidPeriodAndEffectivelyEmptyConfigAreRejected() {
        val invalidMonth = resolver.resolve(
            config(typeId = null).copy(month = 13),
            types,
            emptyList()
        )
        val empty = resolver.resolve(
            config(typeId = null),
            types,
            emptyList()
        )

        assertTrue(invalidMonth is ReportConfigResolution.Invalid)
        assertEquals(
            "请至少选择一项报告内容",
            (empty as ReportConfigResolution.Invalid).message
        )
    }

    private fun config(
        typeId: Long?,
        workIds: Set<Long> = emptySet(),
        selections: Set<ReportStatisticSelection> = emptySet()
    ) = ReportExportConfig(
        year = 2026,
        month = 6,
        typeId = typeId,
        statistics = emptySet(),
        workFields = emptySet(),
        includeAllStatuses = false,
        workCustomFieldIds = workIds,
        statisticSelections = selections
    )

    private fun field(
        id: Long,
        typeId: Long,
        name: String,
        unit: String?,
        order: Int,
        enabled: Boolean = true,
        fixed: Boolean = false,
        scope: FieldScope = FieldScope.ITEM
    ) = DynamicFieldDefinition(
        id = id,
        typeId = typeId,
        typeName = if (typeId == 1L) "阅读" else "观影",
        name = name,
        dataType = FieldDataType.NUMBER,
        enabled = enabled,
        sortOrder = order,
        isFixed = fixed,
        scope = scope,
        unit = unit,
        aggregations = setOf(
            FieldAggregation.SUM,
            FieldAggregation.AVERAGE
        )
    )
}
