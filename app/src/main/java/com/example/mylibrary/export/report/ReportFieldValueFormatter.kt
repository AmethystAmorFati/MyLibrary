package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldNumberFormatter
import com.example.mylibrary.domain.model.FieldValueParser
import java.math.BigDecimal

object ReportFieldValueFormatter {
    fun formatFieldValue(
        field: ResolvedReportField,
        rawValue: String?
    ): String? {
        val raw = rawValue?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return when (field.fieldType) {
            FieldDataType.NUMBER -> FieldValueParser.parseNumber(raw)
                ?.let(FieldNumberFormatter::formatGrouped)
                ?.let { appendUnit(it, field.unit) }
            FieldDataType.TEXT,
            FieldDataType.DATE -> raw
            FieldDataType.BOOLEAN -> when (raw.lowercase()) {
                "true", "1" -> "是"
                "false", "0" -> "否"
                else -> null
            }
            FieldDataType.SINGLE_SELECT,
            FieldDataType.MULTI_SELECT -> formatSelection(field, raw)
            FieldDataType.RATING -> FieldValueParser.parseRatingHalfStars(raw)
                ?.let { halfStars ->
                    FieldNumberFormatter.formatGrouped(
                        BigDecimal(halfStars).divide(BigDecimal(2))
                    ) + " 星"
                }
        }
    }

    fun formatStatisticNumber(
        field: ResolvedReportField,
        aggregation: FieldAggregation,
        value: BigDecimal
    ): String {
        val formatted = FieldNumberFormatter.formatGrouped(value)
        return if (aggregation.keepsFieldUnit()) {
            appendUnit(formatted, field.unit)
        } else {
            formatted
        }
    }

    private fun formatSelection(
        field: ResolvedReportField,
        raw: String
    ): String {
        val ids = FieldValueParser.optionIds(raw, field.fieldType, field.optionDefinitions)
            .toSet()
        val names = field.optionDefinitions
            .asSequence()
            .filter { it.id in ids }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { it.name }
            .toList()
        return names.takeIf(List<String>::isNotEmpty)
            ?.joinToString("  ")
            ?: raw
    }

    private fun appendUnit(value: String, unit: String?): String =
        unit?.trim()?.takeIf(String::isNotEmpty)?.let { "$value $it" } ?: value
}

private fun FieldAggregation.keepsFieldUnit(): Boolean = when (this) {
    FieldAggregation.SUM,
    FieldAggregation.AVERAGE,
    FieldAggregation.MAXIMUM,
    FieldAggregation.MINIMUM -> true
    FieldAggregation.OPTION_DISTRIBUTION,
    FieldAggregation.RATING_AVERAGE,
    FieldAggregation.RATING_DISTRIBUTION -> false
}
