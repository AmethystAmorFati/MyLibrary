package com.example.mylibrary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDefinitionChanges
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.NewFieldDefinition
import com.example.mylibrary.domain.usecase.FieldUseCases
import com.example.mylibrary.domain.usecase.LibraryUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FieldManagementViewModel(
    private val fieldUseCases: FieldUseCases,
    libraryUseCases: LibraryUseCases
) : ViewModel() {
    private val selectedTypeId = MutableStateFlow<Long?>(null)
    private val error = MutableStateFlow<String?>(null)

    val uiState = combine(
        fieldUseCases.observe(),
        libraryUseCases.observeTypes(),
        selectedTypeId,
        error
    ) { fields, types, selectedId, message ->
        FieldManagementUiState(
            types = types,
            selectedTypeId = selectedId ?: types.firstOrNull()?.id,
            fields = fields,
            isLoading = false,
            errorMessage = message
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FieldManagementUiState()
    )

    fun selectType(typeId: Long) {
        selectedTypeId.value = typeId
    }

    fun createField(
        name: String,
        dataType: FieldDataType,
        scope: FieldScope,
        unit: String?,
        aggregations: Set<FieldAggregation>
    ) {
        val typeId = uiState.value.selectedTypeId ?: return
        runOperation {
            fieldUseCases.create(
                NewFieldDefinition(
                    typeId = typeId,
                    name = name,
                    dataType = dataType,
                    scope = scope,
                    unit = unit,
                    aggregations = aggregations
                )
            )
        }
    }

    fun updateField(fieldId: Long, changes: FieldDefinitionChanges) =
        runOperation { fieldUseCases.update(fieldId, changes) }

    fun renameField(fieldId: Long, name: String) =
        runOperation { fieldUseCases.rename(fieldId, name) }

    fun deleteField(fieldId: Long) =
        runOperation { fieldUseCases.delete(fieldId) }

    fun reorderFields(orderedIds: List<Long>) {
        val typeId = uiState.value.selectedTypeId ?: return
        runOperation { fieldUseCases.reorder(typeId, orderedIds) }
    }

    fun addOption(fieldId: Long, name: String) =
        runOperation { fieldUseCases.addOption(fieldId, name) }

    fun renameOption(fieldId: Long, oldName: String, newName: String) =
        runOperation { fieldUseCases.renameOption(fieldId, oldName, newName) }

    fun deleteOption(fieldId: Long, name: String) =
        runOperation { fieldUseCases.deleteOption(fieldId, name) }

    fun reorderOptions(fieldId: Long, options: List<String>) =
        runOperation { fieldUseCases.reorderOptions(fieldId, options) }

    fun setEnabled(fieldId: Long, enabled: Boolean) =
        runOperation { fieldUseCases.setEnabled(fieldId, enabled) }

    fun move(fieldId: Long, direction: Int) =
        runOperation { fieldUseCases.move(fieldId, direction) }

    private fun runOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            error.value = null
            runCatching { block() }
                .onFailure { error.value = it.message ?: "字段操作失败" }
        }
    }
}

class FieldManagementViewModelFactory(
    private val fieldUseCases: FieldUseCases,
    private val libraryUseCases: LibraryUseCases
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FieldManagementViewModel::class.java))
        return FieldManagementViewModel(fieldUseCases, libraryUseCases) as T
    }
}
