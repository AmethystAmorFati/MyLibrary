package com.example.mylibrary.backup.validation

import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.FieldValueParser
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.model.allowedAggregations
import java.time.LocalDate
import java.util.Locale

class BackupDataValidator {
    fun validate(
        data: BackupData,
        availableCoverPaths: Set<String>,
        declaredCounts: Map<String, Long>? = null
    ) {
        val referencedCoverPaths = data.items.mapNotNullTo(linkedSetOf()) { it.coverRef }
        require(referencedCoverPaths == availableCoverPaths) {
            "Backup contains missing or unreferenced covers"
        }
        val typeIds = validUniqueIds("item type", data.itemTypes.map { it.id })
        validUniqueIds("status", data.statuses.map { it.id })
        val itemStatusIds = data.statuses
            .filter { it.scope == StatusScope.ITEM.storageValue }
            .mapTo(mutableSetOf()) { it.id }
        val fieldIds = validUniqueIds("field definition", data.fieldDefinitions.map { it.id })
        val tagIds = validUniqueIds("tag", data.tags.map { it.id })
        val itemIds = validUniqueIds("item", data.items.map { it.id })
        val recordIds = validUniqueIds("record", data.records.map { it.id })
        validUniqueIds("activity", data.activities.map { it.id })
        validUniqueIds("field value", data.fieldValues.map { it.id })
        validUniqueIds("record field value", data.recordFieldValues.map { it.id })
        validUniqueIds("quote", data.quotes.map { it.id })

        require(data.itemTypes.all { it.name.isNotBlank() }) { "Item type name is blank" }
        require(data.statuses.all { it.name.isNotBlank() }) { "Status name is blank" }
        requireUnique(
            "item type name",
            data.itemTypes.map { it.name.lowercase(Locale.ROOT) }
        )
        requireUnique(
            "status scope/name",
            data.statuses.map { it.scope to it.name.lowercase(Locale.ROOT) }
        )
        require(data.statuses.all { status ->
            StatusScope.entries.any { it.storageValue == status.scope }
        }) { "Unsupported status scope" }
        requireUnique(
            "field definition type/name",
            data.fieldDefinitions.map { it.typeId to it.name.lowercase(Locale.ROOT) }
        )
        requireUnique("item/tag link", data.itemTags.map { it.itemId to it.tagId })
        requireUnique(
            "field value item/field",
            data.fieldValues.map { it.itemId to it.fieldId }
        )
        requireUnique(
            "record field value record/field",
            data.recordFieldValues.map { it.recordId to it.fieldId }
        )
        requireUnique(
            "activity item/date",
            data.activities.map { it.itemId to it.date }
        )

        data.fieldDefinitions.forEach { field ->
            require(field.typeId in typeIds) { "Field references a missing item type" }
            require(FieldDataType.entries.any { it.storageValue == field.dataType }) {
                "Unsupported field data type"
            }
            require(FieldScope.entries.any { it.storageValue == field.scope }) {
                "Unsupported field scope"
            }
            val dataType = FieldDataType.fromStorageValue(field.dataType)
            val aggregations = field.aggregations.mapNotNullTo(linkedSetOf()) {
                FieldAggregation.fromStorageValue(it)
            }
            require(aggregations.size == field.aggregations.size) {
                "Unsupported field aggregation"
            }
            require(aggregations.all { it in dataType.allowedAggregations() }) {
                "Field aggregation is incompatible with its data type"
            }
            require(
                field.optionDefinitions.all { it.id > 0L } &&
                    field.optionDefinitions.map { it.id }.distinct().size ==
                    field.optionDefinitions.size
            ) {
                "Field option IDs are invalid or duplicated"
            }
            require(
                field.optionDefinitions
                    .map { it.name.lowercase(Locale.ROOT) }
                    .distinct()
                    .size == field.optionDefinitions.size
            ) {
                "Field options contain duplicates"
            }
            require(
                field.optionDefinitions.all {
                    it.name.isNotBlank() && '\u001F' !in it.name
                }
            ) {
                "Field contains an invalid option"
            }
        }
        val tagsById = data.tags.associateBy { it.id }
        data.tags.forEach { tag ->
            val parentId = tag.parentId ?: return@forEach
            val parent = tagsById[parentId] ?: error("Tag references a missing parent")
            require(parent.id != tag.id && parent.parentId == null) {
                "Tags must contain at most two levels"
            }
        }
        data.items.forEach { item ->
            require(item.typeId in typeIds) { "Item references a missing item type" }
            require(item.currentStatusId == null || item.currentStatusId in itemStatusIds) {
                "Item references a missing item status"
            }
            item.coverRef?.let { coverRef ->
                require(isSafeCoverReference(coverRef)) { "Item has an unsafe cover reference" }
                require(coverRef in availableCoverPaths) {
                    "Item references a missing cover"
                }
            }
        }
        require(data.records.all { it.durationMinutes == null || it.durationMinutes >= 0L }) {
            "Record duration is invalid"
        }
        data.records.forEach { record ->
            require(record.itemId in itemIds) { "Record references a missing item" }
            require(record.ratingHalfStars == null || record.ratingHalfStars in 1..10) {
                "Record rating is out of range"
            }
        }
        data.activities.forEach { activity ->
            require(activity.itemId in itemIds) { "Activity references a missing item" }
            activity.recordId?.let { recordId ->
                val record = data.records.firstOrNull { it.id == recordId }
                    ?: error("Activity references a missing record")
                require(record.itemId == activity.itemId) {
                    "Activity and record refer to different items"
                }
            }
        }
        data.itemTags.forEach { link ->
            require(link.itemId in itemIds && link.tagId in tagIds) {
                "Item/tag link references missing data"
            }
        }
        val fieldsById = data.fieldDefinitions.associateBy { it.id }
        val itemsById = data.items.associateBy { it.id }
        data.fieldValues.forEach { value ->
            val item = itemsById[value.itemId] ?: error("Field value references a missing item")
            val field = fieldsById[value.fieldId]
                ?: error("Field value references a missing field")
            require(field.typeId == item.typeId) {
                "Field value does not belong to the item's type"
            }
            require(field.scope == FieldScope.ITEM.storageValue) {
                "Record field value is stored on an item"
            }
            if (value.value.isNotBlank() &&
                field.dataType == FieldDataType.DATE.storageValue
            ) {
                require(runCatching { LocalDate.parse(value.value) }.isSuccess) {
                    "Date field contains an invalid value"
                }
            }
            validateSelectionValue(field, value.value)
        }
        val recordsById = data.records.associateBy { it.id }
        data.recordFieldValues.forEach { value ->
            val record = recordsById[value.recordId]
                ?: error("Record field value references a missing record")
            val item = itemsById[record.itemId]
                ?: error("Record field value references a missing item")
            val field = fieldsById[value.fieldId]
                ?: error("Record field value references a missing field")
            require(field.typeId == item.typeId) {
                "Record field value does not belong to the item's type"
            }
            require(field.scope == FieldScope.RECORD.storageValue) {
                "Item field value is stored on a record"
            }
            validateSelectionValue(field, value.value)
        }
        data.quotes.forEach { quote ->
            require(quote.itemId in itemIds) { "Quote references a missing item" }
        }

        declaredCounts?.let { counts ->
            data.counts(availableCoverPaths.size).forEach { (name, actual) ->
                val declared = counts[name]
                require(declared == actual || declared == null && actual == 0L) {
                    "Manifest count does not match data: $name"
                }
            }
        }
    }

    private fun validateSelectionValue(
        field: com.example.mylibrary.backup.model.BackupFieldDefinition,
        value: String
    ) {
        val dataType = FieldDataType.fromStorageValue(field.dataType)
        if (dataType != FieldDataType.SINGLE_SELECT &&
            dataType != FieldDataType.MULTI_SELECT
        ) {
            return
        }
        val options = field.optionDefinitions.map {
            FieldOptionDefinition(it.id, it.name, it.isActive, it.sortOrder)
        }
        val selections = FieldValueParser.optionIds(value, dataType, options)
        if (dataType == FieldDataType.SINGLE_SELECT) {
            require(selections.size <= 1) {
                "Single-select field contains multiple values"
            }
        }
        require(value.isBlank() || selections.isNotEmpty()) {
            "Field value references a missing option"
        }
        require(selections.all { selected -> options.any { it.id == selected } }) {
            "Field value references a missing option"
        }
    }

    private fun validUniqueIds(label: String, ids: List<Long>): Set<Long> {
        require(ids.all { it > 0L }) { "$label ID must be positive" }
        require(ids.distinct().size == ids.size) { "Duplicate $label ID" }
        return ids.toSet()
    }

    private fun <T> requireUnique(label: String, values: List<T>) {
        require(values.distinct().size == values.size) { "Duplicate $label" }
    }

    private fun isSafeCoverReference(path: String): Boolean {
        if (!path.startsWith("covers/original/")) return false
        if ('\\' in path || path.startsWith('/') || ':' in path) return false
        val parts = path.split('/')
        return parts.none { it.isBlank() || it == "." || it == ".." } &&
            parts.size == 3
    }
}
