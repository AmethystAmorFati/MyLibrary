package com.example.mylibrary.domain.model

import java.math.BigDecimal

object FieldValueParser {
    private const val SINGLE_OPTION_PREFIX = "option:"
    private const val MULTI_OPTION_PREFIX = "options:"

    fun parseNumber(value: String): BigDecimal? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        return runCatching { BigDecimal(normalized) }
            .getOrNull()
    }

    fun normalizeNumber(value: String): String? =
        parseNumber(value)?.stripTrailingZeros()?.toPlainString()

    fun parseRatingHalfStars(value: String): Int? =
        value.trim().toIntOrNull()?.takeIf { it in 1..10 }

    fun optionIds(
        value: String,
        dataType: FieldDataType,
        options: List<FieldOptionDefinition>
    ): List<Long> {
        if (value.isBlank()) return emptyList()
        val storedIds = when {
            value.startsWith(SINGLE_OPTION_PREFIX) ->
                listOfNotNull(value.removePrefix(SINGLE_OPTION_PREFIX).toLongOrNull())
            value.startsWith(MULTI_OPTION_PREFIX) ->
                value.removePrefix(MULTI_OPTION_PREFIX)
                    .split(',')
                    .mapNotNull(String::toLongOrNull)
            else -> emptyList()
        }
        if (storedIds.isNotEmpty()) return storedIds.distinct()

        val legacyNames = if (dataType == FieldDataType.MULTI_SELECT) {
            decodeFieldSelection(value)
        } else {
            listOf(value.trim())
        }
        return legacyNames.mapNotNull { name ->
            options.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
        }.distinct()
    }

    fun encodeOptionIds(ids: List<Long>, multiple: Boolean): String {
        val distinct = ids.distinct()
        if (distinct.isEmpty()) return ""
        return if (multiple) {
            MULTI_OPTION_PREFIX + distinct.joinToString(",")
        } else {
            SINGLE_OPTION_PREFIX + distinct.first()
        }
    }

    fun displaySelection(
        value: String,
        dataType: FieldDataType,
        options: List<FieldOptionDefinition>
    ): String {
        val byId = options.associateBy(FieldOptionDefinition::id)
        val names = optionIds(value, dataType, options).mapNotNull { byId[it]?.name }
        if (names.isNotEmpty()) {
            return if (dataType == FieldDataType.MULTI_SELECT) {
                encodeFieldSelection(names)
            } else {
                names.first()
            }
        }
        return value
    }
}
