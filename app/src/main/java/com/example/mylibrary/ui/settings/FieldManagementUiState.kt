package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.ItemType

data class FieldManagementUiState(
    val types: List<ItemType> = emptyList(),
    val selectedTypeId: Long? = null,
    val fields: List<DynamicFieldDefinition> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val visibleFields: List<DynamicFieldDefinition>
        get() = fields
            .asSequence()
            .filter { it.typeId == selectedTypeId && !it.isFixed }
            .sortedWith(
                compareBy<DynamicFieldDefinition> { it.sortOrder }
                    .thenBy { it.id }
            )
            .toList()
}
