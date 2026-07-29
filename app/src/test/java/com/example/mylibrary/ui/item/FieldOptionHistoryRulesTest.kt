package com.example.mylibrary.ui.item

import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.decodeFieldSelection
import com.example.mylibrary.domain.model.encodeFieldSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldOptionHistoryRulesTest {
    @Test
    fun unchangedSingleSelectionKeepsDeletedHistoricalValue() {
        assertEquals(
            "电子书",
            confirmedFieldSelection(
                originalValue = "电子书",
                initialSelection = setOf("电子书"),
                temporarySelection = setOf("电子书"),
                activeOptions = listOf("纸质书"),
                multiple = false
            )
        )
    }

    @Test
    fun selectingNewSingleOptionReplacesDeletedHistoricalValue() {
        assertEquals(
            "纸质书",
            confirmedFieldSelection(
                originalValue = "电子书",
                initialSelection = setOf("电子书"),
                temporarySelection = setOf("纸质书"),
                activeOptions = listOf("纸质书", "听书"),
                multiple = false
            )
        )
    }

    @Test
    fun unchangedMultiSelectionKeepsEveryHistoricalValue() {
        val original = encodeFieldSelection(listOf("电子书", "成长"))

        assertEquals(
            original,
            confirmedFieldSelection(
                originalValue = original,
                initialSelection = setOf("电子书", "成长"),
                temporarySelection = setOf("电子书", "成长"),
                activeOptions = listOf("成长", "女性"),
                multiple = true
            )
        )
    }

    @Test
    fun modifiedMultiSelectionUsesOnlyExplicitConfirmedActiveValues() {
        val result = confirmedFieldSelection(
            originalValue = encodeFieldSelection(listOf("电子书", "成长")),
            initialSelection = setOf("电子书", "成长"),
            temporarySelection = setOf("电子书", "成长", "女性"),
            activeOptions = listOf("成长", "女性"),
            multiple = true
        )

        assertEquals(listOf("成长", "女性"), decodeFieldSelection(result))
    }

    @Test
    fun explicitClearRemovesHistoricalSelection() {
        assertEquals(
            "",
            confirmedFieldSelection(
                originalValue = "电子书",
                initialSelection = setOf("电子书"),
                temporarySelection = emptySet(),
                activeOptions = listOf("纸质书"),
                multiple = false
            )
        )
    }

    @Test
    fun existingItemOnlySubmitsFieldsTheUserActuallyChanged() {
        val state = ItemEditorUiState(
            dynamicFields = listOf(
                DynamicFieldInputState(
                    definitionId = 1,
                    name = "阅读方式",
                    dataType = FieldDataType.SINGLE_SELECT,
                    value = "电子书",
                    options = listOf("纸质书")
                ),
                DynamicFieldInputState(
                    definitionId = 2,
                    name = "出版社",
                    dataType = FieldDataType.TEXT,
                    value = "新出版社"
                )
            )
        )

        assertEquals(
            mapOf(2L to "新出版社"),
            state.dynamicValuesForSave(
                isNewItem = false,
                modifiedFieldIds = setOf(2L)
            )
        )
        assertEquals(
            mapOf(1L to "电子书", 2L to "新出版社"),
            state.dynamicValuesForSave(
                isNewItem = true,
                modifiedFieldIds = emptySet()
            )
        )
    }
}
