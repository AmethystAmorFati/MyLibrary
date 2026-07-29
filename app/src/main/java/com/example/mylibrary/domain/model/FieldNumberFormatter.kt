package com.example.mylibrary.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

object FieldNumberFormatter {
    fun format(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    fun formatGrouped(value: BigDecimal): String {
        val plain = format(value)
        val negative = plain.startsWith('-')
        val unsigned = plain.removePrefix("-")
        val integer = unsigned.substringBefore('.')
        val fraction = unsigned.substringAfter('.', missingDelimiterValue = "")
        val grouped = integer
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
        return buildString {
            if (negative) append('-')
            append(grouped)
            if (fraction.isNotEmpty()) {
                append('.')
                append(fraction)
            }
        }
    }
}
