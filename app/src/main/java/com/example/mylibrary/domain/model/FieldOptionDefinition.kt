package com.example.mylibrary.domain.model

data class FieldOptionDefinition(
    val id: Long,
    val name: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)

fun List<FieldOptionDefinition>.activeFieldOptions(): List<FieldOptionDefinition> =
    asSequence()
        .filter(FieldOptionDefinition::isActive)
        .sortedWith(
            compareBy<FieldOptionDefinition> { it.sortOrder }
                .thenBy { it.id }
        )
        .toList()

fun legacyFieldOptions(names: List<String>): List<FieldOptionDefinition> =
    names.mapIndexed { index, name ->
        FieldOptionDefinition(
            id = index + 1L,
            name = name,
            isActive = true,
            sortOrder = index
        )
    }
