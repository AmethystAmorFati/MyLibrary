package com.example.mylibrary.backup.serialization

import com.example.mylibrary.backup.model.BackupActivity
import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupFieldDefinition
import com.example.mylibrary.backup.model.BackupFieldOption
import com.example.mylibrary.backup.model.BackupFieldValue
import com.example.mylibrary.backup.model.BackupFileInfo
import com.example.mylibrary.backup.model.BackupItem
import com.example.mylibrary.backup.model.BackupItemTag
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.model.BackupManifest
import com.example.mylibrary.backup.model.BackupPreferences
import com.example.mylibrary.backup.model.BackupQuote
import com.example.mylibrary.backup.model.BackupRecord
import com.example.mylibrary.backup.model.BackupRecordFieldValue
import com.example.mylibrary.backup.model.BackupStatus
import com.example.mylibrary.backup.model.BackupTag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class BackupJsonCodec(
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
) {
    fun encodeManifest(manifest: BackupManifest): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", manifest.format)
            put("backupSchemaVersion", manifest.backupSchemaVersion)
            put("createdAt", manifest.createdAt)
            put("appVersionName", manifest.appVersionName)
            put("appVersionCode", manifest.appVersionCode)
            put("databaseVersion", manifest.databaseVersion)
            put("counts", buildJsonObject {
                manifest.counts.forEach { (name, count) -> put(name, count) }
            })
            put("files", buildJsonArray {
                manifest.files.forEach { file ->
                    add(buildJsonObject {
                        put("path", file.path)
                        put("size", file.size)
                        put("sha256", file.sha256)
                    })
                }
            })
            put("missingCoverCount", manifest.missingCoverCount)
        }
    )

    fun decodeManifest(source: String): BackupManifest {
        val root = parseObject(source)
        return BackupManifest(
            format = root.requiredString("format"),
            backupSchemaVersion = root.requiredInt("backupSchemaVersion"),
            createdAt = root.requiredString("createdAt"),
            appVersionName = root.requiredString("appVersionName"),
            appVersionCode = root.requiredLong("appVersionCode"),
            databaseVersion = root.requiredInt("databaseVersion"),
            counts = root.requiredObject("counts").mapValues { (_, value) ->
                value.jsonPrimitive.longOrNull ?: invalid("Invalid count")
            },
            files = root.requiredArray("files").map { element ->
                val file = element.jsonObject
                BackupFileInfo(
                    path = file.requiredString("path"),
                    size = file.requiredLong("size"),
                    sha256 = file.requiredString("sha256")
                )
            },
            missingCoverCount = root.optionalInt("missingCoverCount") ?: 0
        )
    }

    fun encodePreferences(preferences: BackupPreferences): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("useGridLayout", preferences.useGridLayout)
            put("libraryViewMode", preferences.libraryViewMode)
            put("gridColumns", preferences.gridColumns)
            put("coverColumns", preferences.coverColumns)
            put("timelineShowCreator", preferences.timelineShowCreator)
            put("timelineShowRating", preferences.timelineShowRating)
            put("timelineShowStatus", preferences.timelineShowStatus)
            put("timelineShowDuration", preferences.timelineShowDuration)
            put("libraryShowTotalDuration", preferences.libraryShowTotalDuration)
            put("showQuoteChapter", preferences.showQuoteChapter)
            put("showQuotePage", preferences.showQuotePage)
            put("listDisplayFields", strings(preferences.listDisplayFields.sorted()))
            putNullable("currentThemeId", preferences.currentThemeId)
        }
    )

    fun decodePreferences(source: String): BackupPreferences {
        val root = parseObject(source)
        return BackupPreferences(
            useGridLayout = root.requiredBoolean("useGridLayout"),
            libraryViewMode = root.requiredString("libraryViewMode"),
            gridColumns = root.requiredInt("gridColumns"),
            coverColumns = root.requiredInt("coverColumns"),
            timelineShowCreator = root.requiredBoolean("timelineShowCreator"),
            timelineShowRating = root.requiredBoolean("timelineShowRating"),
            timelineShowStatus = root.requiredBoolean("timelineShowStatus"),
            timelineShowDuration =
                root.optionalBoolean("timelineShowDuration") ?: true,
            libraryShowTotalDuration =
                root.optionalBoolean("libraryShowTotalDuration") ?: true,
            showQuoteChapter = root.optionalBoolean("showQuoteChapter") ?: true,
            showQuotePage = root.optionalBoolean("showQuotePage") ?: true,
            listDisplayFields = root.requiredArray("listDisplayFields")
                .mapTo(linkedSetOf()) { it.jsonPrimitive.content },
            currentThemeId = root.optionalString("currentThemeId")
        )
    }

    /**
     * Checks whether the preferences JSON object explicitly contains the
     * `currentThemeId` key.  The value may be JSON null; only the key
     * presence matters.  This distinguishes v5 backups that explicitly
     * save `currentThemeId: null` (meaning "use default theme") from
     * backups where the key is absent (treated as a format error for v5).
     */
    fun preferencesContainsCurrentThemeIdKey(source: String): Boolean {
        val root = parseObject(source)
        return "currentThemeId" in root
    }

    fun encodeData(data: BackupData): String = json.encodeToString(
        JsonObject.serializer(),
        data.toJson()
    )

    fun parseDataObject(source: String): JsonObject = parseObject(source)

    fun decodeData(root: JsonObject): BackupData = BackupData(
        itemTypes = root.objects("itemTypes").map {
            BackupItemType(
                id = it.requiredLong("id"),
                name = it.requiredString("name"),
                sortOrder = it.requiredInt("sortOrder")
            )
        },
        statuses = root.objects("statuses").map {
            BackupStatus(
                id = it.requiredLong("id"),
                name = it.requiredString("name"),
                sortOrder = it.requiredInt("sortOrder"),
                enabled = it.requiredBoolean("enabled"),
                scope = it.optionalString("scope") ?: "item"
            )
        },
        fieldDefinitions = root.objects("fieldDefinitions").map {
            val optionDefinitions = it.requiredArray("options")
                .mapIndexed { index, option ->
                    if (option is JsonPrimitive) {
                        BackupFieldOption(
                            id = index + 1L,
                            name = option.content,
                            isActive = true,
                            sortOrder = index
                        )
                    } else {
                        val value = option.jsonObject
                        BackupFieldOption(
                            id = value.requiredLong("id"),
                            name = value.requiredString("name"),
                            isActive = value.requiredBoolean("isActive"),
                            sortOrder = value.requiredInt("sortOrder")
                        )
                    }
                }
            BackupFieldDefinition(
                id = it.requiredLong("id"),
                typeId = it.requiredLong("typeId"),
                name = it.requiredString("name"),
                dataType = it.requiredString("dataType"),
                enabled = it.requiredBoolean("enabled"),
                sortOrder = it.requiredInt("sortOrder"),
                isFixed = it.requiredBoolean("isFixed"),
                options = optionDefinitions
                    .asSequence()
                    .filter(BackupFieldOption::isActive)
                    .sortedWith(
                        compareBy<BackupFieldOption> { option -> option.sortOrder }
                            .thenBy { option -> option.id }
                    )
                    .map(BackupFieldOption::name)
                    .toList(),
                optionDefinitions = optionDefinitions,
                scope = it.optionalString("scope") ?: "item",
                unit = it.optionalString("unit"),
                aggregations = it.optionalArray("aggregations")
                    .mapTo(linkedSetOf()) { value -> value.jsonPrimitive.content }
            )
        },
        tags = root.objects("tags").map {
            BackupTag(
                id = it.requiredLong("id"),
                name = it.requiredString("name"),
                parentId = it.optionalLong("parentId"),
                sortOrder = it.requiredInt("sortOrder"),
                enabled = it.requiredBoolean("enabled")
            )
        },
        items = root.objects("items").map {
            BackupItem(
                id = it.requiredLong("id"),
                typeId = it.requiredLong("typeId"),
                title = it.requiredString("title"),
                coverRef = it.optionalString("coverRef"),
                currentStatusId = it.optionalLong("currentStatusId"),
                createdTime = it.requiredLong("createdTime"),
                updatedTime = it.requiredLong("updatedTime"),
                deletedAt = it.optionalLong("deletedAt")
            )
        },
        records = root.objects("records").map {
            BackupRecord(
                id = it.requiredLong("id"),
                itemId = it.requiredLong("itemId"),
                startDate = it.requiredLong("startDate"),
                endDate = it.optionalLong("endDate"),
                ratingHalfStars = it.optionalInt("ratingHalfStars"),
                review = it.optionalString("review"),
                statusSnapshot = it.optionalString("statusSnapshot"),
                durationMinutes = it.optionalLong("durationMinutes"),
                createdAt = it.requiredLong("createdAt")
            )
        },
        activities = root.objects("activities").map {
            BackupActivity(
                id = it.requiredLong("id"),
                date = it.requiredLong("date"),
                itemId = it.requiredLong("itemId"),
                recordId = it.optionalLong("recordId")
            )
        },
        itemTags = root.objects("itemTags").map {
            BackupItemTag(
                itemId = it.requiredLong("itemId"),
                tagId = it.requiredLong("tagId")
            )
        },
        fieldValues = root.objects("fieldValues").map {
            BackupFieldValue(
                id = it.requiredLong("id"),
                itemId = it.requiredLong("itemId"),
                fieldId = it.requiredLong("fieldId"),
                value = it.requiredString("value")
            )
        },
        recordFieldValues = root.optionalObjects("recordFieldValues").map {
            BackupRecordFieldValue(
                id = it.requiredLong("id"),
                recordId = it.requiredLong("recordId"),
                fieldId = it.requiredLong("fieldId"),
                value = it.requiredString("value")
            )
        },
        quotes = root.objects("quotes").map {
            BackupQuote(
                id = it.requiredLong("id"),
                itemId = it.requiredLong("itemId"),
                content = it.requiredString("content"),
                source = it.optionalString("source"),
                chapter = it.optionalString("chapter"),
                page = it.optionalString("page"),
                createdTime = it.requiredLong("createdTime")
            )
        }
    )

    private fun BackupData.toJson(): JsonObject = buildJsonObject {
        put("itemTypes", objects(itemTypes) {
            put("id", it.id)
            put("name", it.name)
            put("sortOrder", it.sortOrder)
        })
        put("statuses", objects(statuses) {
            put("id", it.id)
            put("name", it.name)
            put("sortOrder", it.sortOrder)
            put("enabled", it.enabled)
            put("scope", it.scope)
        })
        put("fieldDefinitions", objects(fieldDefinitions) {
            put("id", it.id)
            put("typeId", it.typeId)
            put("name", it.name)
            put("dataType", it.dataType)
            put("enabled", it.enabled)
            put("sortOrder", it.sortOrder)
            put("isFixed", it.isFixed)
            put("options", objects(it.optionDefinitions) { option ->
                put("id", option.id)
                put("name", option.name)
                put("isActive", option.isActive)
                put("sortOrder", option.sortOrder)
            })
            put("scope", it.scope)
            putNullable("unit", it.unit)
            put("aggregations", strings(it.aggregations))
        })
        put("tags", objects(tags) {
            put("id", it.id)
            put("name", it.name)
            putNullable("parentId", it.parentId)
            put("sortOrder", it.sortOrder)
            put("enabled", it.enabled)
        })
        put("items", objects(items) {
            put("id", it.id)
            put("typeId", it.typeId)
            put("title", it.title)
            putNullable("coverRef", it.coverRef)
            putNullable("currentStatusId", it.currentStatusId)
            put("createdTime", it.createdTime)
            put("updatedTime", it.updatedTime)
            putNullable("deletedAt", it.deletedAt)
        })
        put("records", objects(records) {
            put("id", it.id)
            put("itemId", it.itemId)
            put("startDate", it.startDate)
            putNullable("endDate", it.endDate)
            putNullable("ratingHalfStars", it.ratingHalfStars)
            putNullable("review", it.review)
            putNullable("statusSnapshot", it.statusSnapshot)
            putNullable("durationMinutes", it.durationMinutes)
            put("createdAt", it.createdAt)
        })
        put("activities", objects(activities) {
            put("id", it.id)
            put("date", it.date)
            put("itemId", it.itemId)
            putNullable("recordId", it.recordId)
        })
        put("itemTags", objects(itemTags) {
            put("itemId", it.itemId)
            put("tagId", it.tagId)
        })
        put("fieldValues", objects(fieldValues) {
            put("id", it.id)
            put("itemId", it.itemId)
            put("fieldId", it.fieldId)
            put("value", it.value)
        })
        put("recordFieldValues", objects(recordFieldValues) {
            put("id", it.id)
            put("recordId", it.recordId)
            put("fieldId", it.fieldId)
            put("value", it.value)
        })
        put("quotes", objects(quotes) {
            put("id", it.id)
            put("itemId", it.itemId)
            put("content", it.content)
            putNullable("source", it.source)
            putNullable("chapter", it.chapter)
            putNullable("page", it.page)
            put("createdTime", it.createdTime)
        })
    }

    private fun parseObject(source: String): JsonObject =
        runCatching { json.parseToJsonElement(source).jsonObject }
            .getOrElse { invalid("Invalid JSON", it) }

    private fun JsonObject.objects(name: String): List<JsonObject> =
        requiredArray(name).map { it.jsonObject }

    private fun JsonObject.optionalObjects(name: String): List<JsonObject> =
        optionalArray(name).map { it.jsonObject }

    private fun JsonObject.requiredArray(name: String): JsonArray =
        this[name]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?: invalid("Missing or invalid array: $name")

    private fun JsonObject.optionalArray(name: String): JsonArray =
        this[name]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?: JsonArray(emptyList())

    private fun JsonObject.requiredObject(name: String): JsonObject =
        this[name]?.let { runCatching { it.jsonObject }.getOrNull() }
            ?: invalid("Missing or invalid object: $name")

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
            ?: invalid("Missing or invalid string: $name")

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.requiredLong(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull ?: invalid("Missing or invalid long: $name")

    private fun JsonObject.optionalLong(name: String): Long? =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull

    private fun JsonObject.requiredInt(name: String): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: invalid("Missing or invalid int: $name")

    private fun JsonObject.optionalInt(name: String): Int? =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull
            ?: invalid("Missing or invalid boolean: $name")

    private fun JsonObject.optionalBoolean(name: String): Boolean? =
        this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull

    private fun strings(values: Collection<String>): JsonArray = buildJsonArray {
        values.forEach { add(JsonPrimitive(it)) }
    }

    private fun <T> objects(
        values: Collection<T>,
        content: kotlinx.serialization.json.JsonObjectBuilder.(T) -> Unit
    ): JsonArray = buildJsonArray {
        values.forEach { value -> add(buildJsonObject { content(value) }) }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        name: String,
        value: String?
    ) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        name: String,
        value: Long?
    ) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        name: String,
        value: Int?
    ) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun invalid(message: String, cause: Throwable? = null): Nothing =
        throw IllegalArgumentException(message, cause)
}
