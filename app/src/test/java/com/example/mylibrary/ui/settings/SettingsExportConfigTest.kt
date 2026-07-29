package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsExportConfigTest {
    @Test
    fun reportDefaultsSplitWorkFieldsFromStatisticSelections() {
        val monthly = ReportExportConfig(year = 2026, month = 7, typeId = null)
        val yearly = monthly.copy(month = null)

        assertEquals(DEFAULT_REPORT_STATISTICS, monthly.statistics)
        assertEquals(DEFAULT_REPORT_WORK_FIELDS, monthly.workFields)
        assertTrue(monthly.workCustomFieldIds.isEmpty())
        assertTrue(monthly.statisticSelections.isEmpty())
        assertNull(yearly.month)
    }

    @Test
    fun capabilitiesFilterScopeDisabledFixedAndAggregationlessFields() {
        val fields = listOf(
            field(11, 1, scope = FieldScope.ITEM),
            field(12, 1, scope = FieldScope.RECORD),
            field(13, 1, enabled = false),
            field(14, 1, fixed = true),
            field(15, 1, aggregations = emptySet())
        )

        assertEquals(
            listOf(11L, 15L),
            availableReportWorkFields(fields, 1).map { it.id }
        )
        assertEquals(
            listOf(11L, 12L),
            availableReportStatisticFields(fields, 1).map { it.id }
        )
    }

    @Test
    fun categoryChangeAndRemovedAggregationCleanBothSelectionKinds() {
        val fields = listOf(
            field(11, 1),
            field(21, 2, aggregations = setOf(FieldAggregation.AVERAGE))
        )
        val cleaned = validReportSelections(
            workCustomFieldIds = setOf(11L, 21L, 999L),
            statisticSelections = setOf(
                ReportStatisticSelection(11, FieldAggregation.SUM),
                ReportStatisticSelection(11, FieldAggregation.AVERAGE),
                ReportStatisticSelection(21, FieldAggregation.AVERAGE),
                ReportStatisticSelection(999, FieldAggregation.SUM)
            ),
            fields = fields,
            typeId = 1L
        )

        assertEquals(setOf(11L), cleaned.workCustomFieldIds)
        assertEquals(
            setOf(
                ReportStatisticSelection(11, FieldAggregation.SUM),
                ReportStatisticSelection(11, FieldAggregation.AVERAGE)
            ),
            cleaned.statisticSelections
        )
    }

    @Test
    fun sameFieldCanSelectMultipleDeclaredAggregations() {
        val fields = listOf(field(11, 1))
        val selections = setOf(
            ReportStatisticSelection(11, FieldAggregation.SUM),
            ReportStatisticSelection(11, FieldAggregation.AVERAGE),
            ReportStatisticSelection(11, FieldAggregation.MAXIMUM)
        )

        assertEquals(
            selections,
            validReportSelections(emptySet(), selections, fields, 1)
                .statisticSelections
        )
    }

    @Test
    fun fourthWorkCustomFieldIsRejectedWithoutReplacingExistingSelection() {
        val selected = setOf(1L, 2L, 3L)
        val rejected = toggleWorkCustomField(selected, 4L)
            as WorkCustomFieldToggleResult.Rejected
        val removed = toggleWorkCustomField(selected, 2L)
            as WorkCustomFieldToggleResult.Updated

        assertEquals(REPORT_WORK_FIELD_LIMIT_MESSAGE, rejected.message)
        assertEquals(setOf(1L, 3L), removed.selectedIds)
    }

    @Test
    fun emptyContentIsRejectedByConfigModel() {
        val empty = ReportExportConfig(
            year = 2026,
            month = 7,
            typeId = null,
            statistics = emptySet(),
            workFields = emptySet(),
            includeAllStatuses = false,
            includeQuotes = false
        )

        assertFalse(empty.hasContent())
        assertTrue(empty.copy(includeQuotes = true).hasContent())
    }

    @Test
    fun squareOptionUsesOnlySolidOrOutlineState() {
        assertEquals(SquareOptionState.SELECTED, squareOptionState(true))
        assertEquals(SquareOptionState.UNSELECTED, squareOptionState(false))
    }

    private fun field(
        id: Long,
        typeId: Long,
        enabled: Boolean = true,
        fixed: Boolean = false,
        scope: FieldScope = FieldScope.ITEM,
        aggregations: Set<FieldAggregation> = setOf(
            FieldAggregation.SUM,
            FieldAggregation.AVERAGE,
            FieldAggregation.MAXIMUM
        )
    ) = DynamicFieldDefinition(
        id = id,
        typeId = typeId,
        typeName = if (typeId == 1L) "Book" else "Movie",
        name = "Field $id",
        dataType = FieldDataType.NUMBER,
        enabled = enabled,
        sortOrder = id.toInt(),
        isFixed = fixed,
        scope = scope,
        aggregations = aggregations
    )
}
