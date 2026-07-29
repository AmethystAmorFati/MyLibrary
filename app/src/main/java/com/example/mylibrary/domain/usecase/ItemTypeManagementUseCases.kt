package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.repository.ItemTypeRepository
import kotlinx.coroutines.flow.Flow

class ObserveManagedItemTypesUseCase(
    private val repository: ItemTypeRepository
) {
    operator fun invoke(): Flow<List<ItemType>> = repository.observeTypes()
    fun usageCounts(): Flow<Map<Long, Int>> = repository.observeUsageCounts()
}

class CreateItemTypeUseCase(private val repository: ItemTypeRepository) {
    suspend operator fun invoke(name: String): Long = repository.createType(name)
}

class RenameItemTypeUseCase(private val repository: ItemTypeRepository) {
    suspend operator fun invoke(typeId: Long, name: String) =
        repository.renameType(typeId, name)
}

class DeleteItemTypeUseCase(private val repository: ItemTypeRepository) {
    suspend operator fun invoke(typeId: Long) = repository.deleteType(typeId)
}

class ReorderItemTypesUseCase(private val repository: ItemTypeRepository) {
    suspend operator fun invoke(orderedIds: List<Long>) =
        repository.reorderTypes(orderedIds)
}

data class ItemTypeUseCases(
    val observe: ObserveManagedItemTypesUseCase,
    val create: CreateItemTypeUseCase,
    val rename: RenameItemTypeUseCase,
    val delete: DeleteItemTypeUseCase,
    val reorder: ReorderItemTypesUseCase
)
