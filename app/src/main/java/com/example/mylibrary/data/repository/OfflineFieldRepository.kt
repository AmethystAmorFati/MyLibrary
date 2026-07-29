package com.example.mylibrary.data.repository

import androidx.room.withTransaction
import com.example.mylibrary.data.dao.DynamicFieldDao
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.FieldDefinitionEntity
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldDefinitionChanges
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.NewFieldDefinition
import com.example.mylibrary.domain.model.activeFieldOptions
import com.example.mylibrary.domain.model.decodeFieldSelection
import com.example.mylibrary.domain.model.encodeFieldSelection
import com.example.mylibrary.domain.model.compatibleWith
import com.example.mylibrary.domain.repository.FieldRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineFieldRepository(
    private val database: LibraryDatabase,
    private val fieldDao: DynamicFieldDao
) : FieldRepository {
    override fun observeDefinitions(): Flow<List<DynamicFieldDefinition>> =
        fieldDao.observeAllDefinitions()
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun createField(field: NewFieldDefinition): Long =
        database.withTransaction {
            val existing = fieldDao.getDefinitions(field.typeId)
            check(existing.none { it.name.equals(field.name.trim(), ignoreCase = true) }) {
                "字段名称已存在"
            }
            fieldDao.insertDefinition(
                FieldDefinitionEntity(
                    typeId = field.typeId,
                    name = field.name.trim(),
                    dataType = field.dataType,
                    sortOrder = fieldDao.getMaxSortOrder(field.typeId) + 1,
                    scope = field.scope,
                    unit = field.unit?.trim()?.takeIf(String::isNotEmpty)
                        ?.takeIf { field.dataType == FieldDataType.NUMBER },
                    aggregations = field.aggregations.compatibleWith(field.dataType)
                )
            )
        }

    override suspend fun updateField(fieldId: Long, changes: FieldDefinitionChanges) {
        database.withTransaction {
            val field = editableField(fieldId)
            val normalizedName = changes.name.trim()
            check(
                fieldDao.getDefinitions(field.typeId).none {
                    it.id != fieldId &&
                        it.name.equals(normalizedName, ignoreCase = true)
                }
            ) { "字段名称已存在" }
            if (changes.scope != field.scope) {
                check(
                    fieldDao.getValuesForField(fieldId).isEmpty() &&
                        fieldDao.getRecordValuesForField(fieldId).isEmpty()
                ) { "已有数据的字段不能修改归属" }
            }
            fieldDao.updateDefinition(
                field.copy(
                    name = normalizedName,
                    dataType = changes.dataType,
                    scope = changes.scope,
                    unit = changes.unit?.trim()?.takeIf(String::isNotEmpty)
                        ?.takeIf { changes.dataType == FieldDataType.NUMBER },
                    aggregations = changes.aggregations.compatibleWith(changes.dataType)
                )
            )
        }
    }

    override suspend fun renameField(fieldId: Long, name: String) {
        database.withTransaction {
            val field = editableField(fieldId)
            val normalized = name.trim()
            check(
                fieldDao.getDefinitions(field.typeId).none {
                    it.id != fieldId && it.name.equals(normalized, ignoreCase = true)
                }
            ) { "字段名称已存在" }
            fieldDao.updateDefinition(field.copy(name = normalized))
        }
    }

    override suspend fun deleteField(fieldId: Long) {
        database.withTransaction {
            editableField(fieldId)
            fieldDao.deleteDefinition(fieldId)
        }
    }

    override suspend fun reorderFields(typeId: Long, orderedIds: List<Long>) {
        database.withTransaction {
            val definitions = fieldDao.getDefinitions(typeId)
            val movable = definitions.filterNot { it.isFixed }
            check(orderedIds.size == orderedIds.distinct().size) {
                "字段排序包含重复项"
            }
            check(orderedIds.toSet() == movable.mapTo(mutableSetOf()) { it.id }) {
                "字段排序内容已变化"
            }
            val firstCustomOrder =
                (definitions.filter { it.isFixed }.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val byId = movable.associateBy { it.id }
            orderedIds.forEachIndexed { index, fieldId ->
                val current = requireNotNull(byId[fieldId])
                val targetOrder = firstCustomOrder + index
                if (current.sortOrder != targetOrder) {
                    fieldDao.updateDefinition(current.copy(sortOrder = targetOrder))
                }
            }
        }
    }

    override suspend fun addFieldOption(fieldId: Long, name: String) {
        database.withTransaction {
            val field = selectionField(fieldId)
            val normalized = name.trim()
            check(normalized.isNotEmpty()) { "选项名称不能为空" }
            val existingIndex = field.optionDefinitions.indexOfFirst {
                it.name.equals(normalized, ignoreCase = true)
            }
            val nextOrder =
                (field.optionDefinitions.activeFieldOptions()
                    .maxOfOrNull { it.sortOrder } ?: -1) + 1
            val updated = if (existingIndex >= 0) {
                val existing = field.optionDefinitions[existingIndex]
                check(!existing.isActive) { "选项名称已存在" }
                field.optionDefinitions.toMutableList().apply {
                    set(
                        existingIndex,
                        existing.copy(
                            name = normalized,
                            isActive = true,
                            sortOrder = nextOrder
                        )
                    )
                }
            } else {
                field.optionDefinitions + FieldOptionDefinition(
                    id = (field.optionDefinitions.maxOfOrNull { it.id } ?: 0L) + 1L,
                    name = normalized,
                    isActive = true,
                    sortOrder = nextOrder
                )
            }
            fieldDao.updateDefinition(field.copy(optionDefinitions = updated))
        }
    }

    override suspend fun renameFieldOption(
        fieldId: Long,
        oldName: String,
        newName: String
    ) {
        database.withTransaction {
            val field = selectionField(fieldId)
            val index = field.optionDefinitions.indexOfFirst {
                it.isActive && it.name.equals(oldName, ignoreCase = true)
            }
            check(index >= 0) { "选项不存在" }
            val normalized = newName.trim()
            check(normalized.isNotEmpty()) { "选项名称不能为空" }
            check(
                field.optionDefinitions.none {
                    it.id != field.optionDefinitions[index].id &&
                        it.name.equals(normalized, ignoreCase = true)
                }
            ) { "选项名称已存在" }
            fieldDao.updateDefinition(
                field.copy(
                    optionDefinitions = field.optionDefinitions.toMutableList().apply {
                        set(index, get(index).copy(name = normalized))
                    }
                )
            )
            updateStoredOptionValues(field, oldName) { normalized }
        }
    }

    override suspend fun deleteFieldOption(fieldId: Long, name: String) {
        database.withTransaction {
            val field = selectionField(fieldId)
            val index = field.optionDefinitions.indexOfFirst {
                it.isActive && it.name.equals(name, ignoreCase = true)
            }
            check(index >= 0) { "选项不存在" }
            fieldDao.updateDefinition(
                field.copy(
                    optionDefinitions = field.optionDefinitions.toMutableList().apply {
                        set(index, get(index).copy(isActive = false))
                    }
                )
            )
        }
    }

    override suspend fun reorderFieldOptions(
        fieldId: Long,
        orderedOptions: List<String>
    ) {
        database.withTransaction {
            val field = selectionField(fieldId)
            check(
                orderedOptions.map(String::lowercase).distinct().size ==
                    orderedOptions.size
            ) {
                "选项排序包含重复项"
            }
            val activeOptions = field.optionDefinitions.activeFieldOptions()
            check(
                orderedOptions.map { it.lowercase() }.toSet() ==
                    activeOptions.map { it.name.lowercase() }.toSet()
            ) { "选项排序内容已变化" }
            val orderByName = orderedOptions
                .mapIndexed { index, option -> option.lowercase() to index }
                .toMap()
            fieldDao.updateDefinition(
                field.copy(
                    optionDefinitions = field.optionDefinitions.map { option ->
                        if (option.isActive) {
                            option.copy(
                                sortOrder = requireNotNull(
                                    orderByName[option.name.lowercase()]
                                )
                            )
                        } else {
                            option
                        }
                    }
                )
            )
        }
    }

    override suspend fun setFieldEnabled(fieldId: Long, enabled: Boolean) {
        val field = requireNotNull(fieldDao.getDefinition(fieldId)) { "字段不存在" }
        check(!field.isFixed) { "固定字段不可禁用" }
        fieldDao.updateDefinition(field.copy(enabled = enabled))
    }

    override suspend fun moveField(fieldId: Long, direction: Int) {
        database.withTransaction {
            val field = requireNotNull(fieldDao.getDefinition(fieldId)) { "字段不存在" }
            check(!field.isFixed) { "固定字段不可排序" }
            val movable = fieldDao.getDefinitions(field.typeId)
                .filterNot { it.isFixed }
            val currentIndex = movable.indexOfFirst { it.id == fieldId }
            val targetIndex = currentIndex + direction.coerceIn(-1, 1)
            if (currentIndex < 0 || targetIndex !in movable.indices) return@withTransaction
            val target = movable[targetIndex]
            fieldDao.updateDefinition(field.copy(sortOrder = target.sortOrder))
            fieldDao.updateDefinition(target.copy(sortOrder = field.sortOrder))
        }
    }

    private suspend fun editableField(fieldId: Long): FieldDefinitionEntity {
        val field = requireNotNull(fieldDao.getDefinition(fieldId)) { "字段不存在" }
        check(!field.isFixed) { "固定字段不可修改" }
        return field
    }

    private suspend fun selectionField(fieldId: Long): FieldDefinitionEntity {
        val field = editableField(fieldId)
        check(
            field.dataType == FieldDataType.SINGLE_SELECT ||
                field.dataType == FieldDataType.MULTI_SELECT
        ) { "当前字段不支持选项" }
        return field
    }

    private suspend fun updateStoredOptionValues(
        field: FieldDefinitionEntity,
        targetName: String,
        replacement: () -> String?
    ) {
        fieldDao.getValuesForField(field.id).forEach { stored ->
            val current = if (field.dataType == FieldDataType.MULTI_SELECT) {
                decodeFieldSelection(stored.value)
            } else {
                listOf(stored.value)
            }
            val updated = current.mapNotNull { value ->
                if (value.equals(targetName, ignoreCase = true)) replacement() else value
            }
            val encoded = if (field.dataType == FieldDataType.MULTI_SELECT) {
                encodeFieldSelection(updated)
            } else {
                updated.firstOrNull().orEmpty()
            }
            if (encoded.isBlank()) {
                fieldDao.deleteValue(stored.itemId, field.id)
            } else if (encoded != stored.value) {
                fieldDao.replaceValue(stored.copy(value = encoded))
            }
        }
        fieldDao.getRecordValuesForField(field.id).forEach { stored ->
            val current = if (field.dataType == FieldDataType.MULTI_SELECT) {
                decodeFieldSelection(stored.value)
            } else {
                listOf(stored.value)
            }
            val updated = current.mapNotNull { value ->
                if (value.equals(targetName, ignoreCase = true)) replacement() else value
            }
            val encoded = if (field.dataType == FieldDataType.MULTI_SELECT) {
                encodeFieldSelection(updated)
            } else {
                updated.firstOrNull().orEmpty()
            }
            if (encoded.isBlank()) {
                fieldDao.deleteRecordValue(stored.recordId, field.id)
            } else if (encoded != stored.value) {
                fieldDao.replaceRecordValue(stored.copy(value = encoded))
            }
        }
    }
}
