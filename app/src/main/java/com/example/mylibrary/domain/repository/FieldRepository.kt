package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDefinitionChanges
import com.example.mylibrary.domain.model.NewFieldDefinition
import kotlinx.coroutines.flow.Flow

interface FieldRepository {
    fun observeDefinitions(): Flow<List<DynamicFieldDefinition>>

    suspend fun createField(field: NewFieldDefinition): Long
    suspend fun updateField(fieldId: Long, changes: FieldDefinitionChanges)
    suspend fun renameField(fieldId: Long, name: String)
    suspend fun deleteField(fieldId: Long)
    suspend fun reorderFields(typeId: Long, orderedIds: List<Long>)
    suspend fun addFieldOption(fieldId: Long, name: String)
    suspend fun renameFieldOption(fieldId: Long, oldName: String, newName: String)
    suspend fun deleteFieldOption(fieldId: Long, name: String)
    suspend fun reorderFieldOptions(fieldId: Long, orderedOptions: List<String>)
    suspend fun setFieldEnabled(fieldId: Long, enabled: Boolean)
    suspend fun moveField(fieldId: Long, direction: Int)
}
