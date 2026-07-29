package com.example.mylibrary.data.database

import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseConvertersTest {
    private val converters = DatabaseConverters()

    @Test
    fun legacyStringOptionsReceiveStableActiveIds() {
        val first = converters.stringToFieldOptions("纸质书\u001F电子书")
        val second = converters.stringToFieldOptions("纸质书\u001F电子书")

        assertEquals(first, second)
        assertEquals(listOf(1L, 2L), first.map { it.id })
        assertTrue(first.all { it.isActive })
    }

    @Test
    fun versionedOptionStoragePreservesIdNameOrderAndInactiveState() {
        val source = listOf(
            FieldOptionDefinition(7, "纸质书", true, 1),
            FieldOptionDefinition(12, "电子书", false, 0)
        )

        val encoded = converters.fieldOptionsToString(source)
        val restored = converters.stringToFieldOptions(encoded)

        assertTrue(encoded.startsWith("v2:"))
        assertEquals(source, restored)
        assertFalse(restored.last().isActive)
    }

    @Test
    fun scopeAndAggregationConvertersPreserveConfiguration() {
        val aggregations = linkedSetOf(
            FieldAggregation.SUM,
            FieldAggregation.AVERAGE,
            FieldAggregation.MAXIMUM
        )

        assertEquals(
            FieldScope.RECORD,
            converters.stringToFieldScope(
                converters.fieldScopeToString(FieldScope.RECORD)
            )
        )
        assertEquals(
            aggregations,
            converters.stringToFieldAggregations(
                converters.fieldAggregationsToString(aggregations)
            )
        )
        assertTrue(converters.stringToFieldAggregations("").isEmpty())
    }
}
