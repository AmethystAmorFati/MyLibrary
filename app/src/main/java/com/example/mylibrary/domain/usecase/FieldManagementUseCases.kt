package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDefinitionChanges
import com.example.mylibrary.domain.model.NewFieldDefinition
import com.example.mylibrary.domain.model.compatibleWith
import com.example.mylibrary.domain.model.validateFieldOptionName
import com.example.mylibrary.domain.repository.FieldRepository
import kotlinx.coroutines.flow.Flow

class ObserveFieldsUseCase(private val repository: FieldRepository) {
    operator fun invoke(): Flow<List<DynamicFieldDefinition>> =
        repository.observeDefinitions()
}

class CreateFieldUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(field: NewFieldDefinition): Long {
        require(field.typeId > 0) { "请选择作品类型" }
        require(field.name.isNotBlank()) { "字段名称不能为空" }
        require(!field.name.equals("title", ignoreCase = true)) { "标题是固定字段" }
        return repository.createField(field)
    }
}

class RenameFieldUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long, name: String) {
        require(fieldId > 0) { "字段编号无效" }
        require(name.isNotBlank()) { "字段名称不能为空" }
        require(!name.equals("title", ignoreCase = true)) { "标题是固定字段" }
        repository.renameField(fieldId, name)
    }
}

class UpdateFieldUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long, changes: FieldDefinitionChanges) {
        require(fieldId > 0) { "字段编号无效" }
        require(changes.name.isNotBlank()) { "字段名称不能为空" }
        require(!changes.name.equals("title", ignoreCase = true)) { "标题是固定字段" }
        repository.updateField(
            fieldId,
            changes.copy(
                unit = changes.unit?.trim()?.takeIf(String::isNotEmpty)
                    ?.takeIf { changes.dataType == com.example.mylibrary.domain.model.FieldDataType.NUMBER },
                aggregations = changes.aggregations.compatibleWith(changes.dataType)
            )
        )
    }
}

class DeleteFieldUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long) {
        require(fieldId > 0) { "字段编号无效" }
        repository.deleteField(fieldId)
    }
}

class ReorderFieldsUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(typeId: Long, orderedIds: List<Long>) {
        require(typeId > 0) { "作品类型无效" }
        repository.reorderFields(typeId, orderedIds)
    }
}

class AddFieldOptionUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long, name: String) {
        repository.addFieldOption(fieldId, validateFieldOptionName(name))
    }
}

class RenameFieldOptionUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long, oldName: String, newName: String) {
        repository.renameFieldOption(
            fieldId,
            validateFieldOptionName(oldName),
            validateFieldOptionName(newName)
        )
    }
}

class DeleteFieldOptionUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long, name: String) {
        repository.deleteFieldOption(fieldId, validateFieldOptionName(name))
    }
}

class ReorderFieldOptionsUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long, orderedOptions: List<String>) {
        require(orderedOptions.all(String::isNotBlank)) {
            "选项排序包含空名称"
        }
        repository.reorderFieldOptions(fieldId, orderedOptions)
    }
}

class SetFieldEnabledUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long, enabled: Boolean) {
        require(fieldId > 0) { "字段编号无效" }
        repository.setFieldEnabled(fieldId, enabled)
    }
}

class MoveFieldUseCase(private val repository: FieldRepository) {
    suspend operator fun invoke(fieldId: Long, direction: Int) {
        require(direction == -1 || direction == 1) { "排序方向无效" }
        repository.moveField(fieldId, direction)
    }
}

data class FieldUseCases(
    val observe: ObserveFieldsUseCase,
    val create: CreateFieldUseCase,
    val update: UpdateFieldUseCase,
    val rename: RenameFieldUseCase,
    val delete: DeleteFieldUseCase,
    val reorder: ReorderFieldsUseCase,
    val addOption: AddFieldOptionUseCase,
    val renameOption: RenameFieldOptionUseCase,
    val deleteOption: DeleteFieldOptionUseCase,
    val reorderOptions: ReorderFieldOptionsUseCase,
    val setEnabled: SetFieldEnabledUseCase,
    val move: MoveFieldUseCase
)
