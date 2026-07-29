package com.example.mylibrary.domain.model

private const val FIELD_SELECTION_SEPARATOR = "\u001F"

fun encodeFieldSelection(values: Collection<String>): String =
    values
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(FIELD_SELECTION_SEPARATOR)

fun decodeFieldSelection(value: String): List<String> =
    value
        .split(FIELD_SELECTION_SEPARATOR)
        .map(String::trim)
        .filter(String::isNotEmpty)

fun validateFieldOptionName(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty()) { "选项名称不能为空" }
    require(FIELD_SELECTION_SEPARATOR !in normalized) { "选项名称包含不支持的字符" }
    return normalized
}
