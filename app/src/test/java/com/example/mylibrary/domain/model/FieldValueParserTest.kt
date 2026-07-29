package com.example.mylibrary.domain.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldValueParserTest {
    @Test
    fun numbersAreValidatedAndNormalizedWithoutUnits() {
        assertEquals("12", FieldValueParser.normalizeNumber("12.00"))
        assertEquals("-2.5", FieldValueParser.normalizeNumber(" -2.500 "))
        assertEquals(BigDecimal.ZERO, FieldValueParser.parseNumber("0"))
        assertNull(FieldValueParser.parseNumber(""))
        assertNull(FieldValueParser.parseNumber("NaN"))
        assertNull(FieldValueParser.parseNumber("Infinity"))
    }

    @Test
    fun optionIdsAreStableAcrossRenamesAndLegacyValuesRemainReadable() {
        val options = listOf(
            FieldOptionDefinition(7L, "电子书", isActive = true, sortOrder = 0),
            FieldOptionDefinition(9L, "纸质书", isActive = true, sortOrder = 1)
        )
        val stored = FieldValueParser.encodeOptionIds(listOf(7L, 9L), multiple = true)
        val renamed = options.map {
            if (it.id == 7L) it.copy(name = "电子阅读") else it
        }

        assertEquals(listOf(7L, 9L), FieldValueParser.optionIds(
            stored,
            FieldDataType.MULTI_SELECT,
            renamed
        ))
        assertEquals(
            encodeFieldSelection(listOf("电子阅读", "纸质书")),
            FieldValueParser.displaySelection(
                stored,
                FieldDataType.MULTI_SELECT,
                renamed
            )
        )
        assertEquals(
            listOf(7L),
            FieldValueParser.optionIds(
                "电子书",
                FieldDataType.SINGLE_SELECT,
                options
            )
        )
    }

    @Test
    fun statisticsFormattingUsesPlainNumbersWithAtMostTwoDecimals() {
        assertEquals("12", FieldNumberFormatter.format(BigDecimal("12.000")))
        assertEquals("12.5", FieldNumberFormatter.format(BigDecimal("12.500")))
        assertEquals("12.35", FieldNumberFormatter.format(BigDecimal("12.345")))
        assertEquals(
            "12345678901234567890.12",
            FieldNumberFormatter.format(BigDecimal("12345678901234567890.12"))
        )
    }
}
