package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDataType
import org.junit.Assert.assertEquals
import org.junit.Test

class FieldManagementUiStateTest {
    @Test
    fun visibleFieldsOnlyContainsSortedUserFieldsForSelectedType() {
        val state = FieldManagementUiState(
            selectedTypeId = 1,
            fields = listOf(
                field(id = 1, typeId = 1, name = "author", order = 0, fixed = true),
                field(id = 2, typeId = 1, name = "页数", order = 2),
                field(id = 3, typeId = 2, name = "片长", order = 1),
                field(id = 4, typeId = 1, name = "出版社", order = 1)
            ),
            isLoading = false
        )

        assertEquals(listOf("出版社", "页数"), state.visibleFields.map { it.name })
    }

    private fun field(
        id: Long,
        typeId: Long,
        name: String,
        order: Int,
        fixed: Boolean = false
    ) = DynamicFieldDefinition(
        id = id,
        typeId = typeId,
        typeName = if (typeId == 1L) "Book" else "Movie",
        name = name,
        dataType = FieldDataType.TEXT,
        enabled = true,
        sortOrder = order,
        isFixed = fixed
    )
}
