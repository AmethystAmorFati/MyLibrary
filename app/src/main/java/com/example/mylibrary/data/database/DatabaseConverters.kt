package com.example.mylibrary.data.database

import androidx.room.TypeConverter
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.model.legacyFieldOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class DatabaseConverters {
    @TypeConverter
    fun fieldDataTypeToString(value: FieldDataType): String = value.storageValue

    @TypeConverter
    fun stringToFieldDataType(value: String): FieldDataType =
        FieldDataType.fromStorageValue(value)

    @TypeConverter
    fun fieldScopeToString(value: FieldScope): String = value.storageValue

    @TypeConverter
    fun stringToFieldScope(value: String): FieldScope =
        FieldScope.fromStorageValue(value)

    @TypeConverter
    fun statusScopeToString(value: StatusScope): String = value.storageValue

    @TypeConverter
    fun stringToStatusScope(value: String): StatusScope =
        StatusScope.fromStorageValue(value)

    @TypeConverter
    fun fieldAggregationsToString(value: Set<FieldAggregation>): String =
        value.joinToString(",") { it.storageValue }

    @TypeConverter
    fun stringToFieldAggregations(value: String): Set<FieldAggregation> =
        value.split(',')
            .mapNotNull { FieldAggregation.fromStorageValue(it) }
            .toSet()

    @TypeConverter
    fun fieldOptionsToString(value: List<FieldOptionDefinition>): String {
        if (value.isEmpty()) return ""
        val encoded = buildJsonArray {
            value.forEach { option ->
                add(
                    buildJsonObject {
                        put("id", option.id)
                        put("name", option.name)
                        put("isActive", option.isActive)
                        put("sortOrder", option.sortOrder)
                    }
                )
            }
        }
        return FIELD_OPTIONS_V2_PREFIX + Json.encodeToString(
            JsonArray.serializer(),
            encoded
        )
    }

    @TypeConverter
    fun stringToFieldOptions(value: String): List<FieldOptionDefinition> {
        if (value.isEmpty()) return emptyList()
        if (!value.startsWith(FIELD_OPTIONS_V2_PREFIX)) {
            return legacyFieldOptions(
                value.split("\u001F").filter(String::isNotEmpty)
            )
        }
        return Json.parseToJsonElement(
            value.removePrefix(FIELD_OPTIONS_V2_PREFIX)
        ).jsonArray.map { element ->
            val option = element.jsonObject
            FieldOptionDefinition(
                id = option.requiredLong("id"),
                name = option.requiredString("name"),
                isActive = option.requiredBoolean("isActive"),
                sortOrder = option.requiredInt("sortOrder")
            )
        }
    }

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.jsonPrimitive?.content
            ?: error("Missing field option property: $name")

    private fun JsonObject.requiredLong(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull
            ?: error("Missing field option property: $name")

    private fun JsonObject.requiredInt(name: String): Int =
        this[name]?.jsonPrimitive?.intOrNull
            ?: error("Missing field option property: $name")

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull
            ?: error("Missing field option property: $name")

    private companion object {
        const val FIELD_OPTIONS_V2_PREFIX = "v2:"
    }
}
