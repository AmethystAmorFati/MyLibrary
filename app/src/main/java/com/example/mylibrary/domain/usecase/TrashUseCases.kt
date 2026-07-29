package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.TrashItem
import com.example.mylibrary.domain.repository.TrashRepository
import kotlinx.coroutines.flow.Flow

class ObserveTrashItemsUseCase(private val repository: TrashRepository) {
    operator fun invoke(): Flow<List<TrashItem>> = repository.observeItems()
}

class RestoreTrashItemUseCase(private val repository: TrashRepository) {
    suspend operator fun invoke(itemId: Long) = repository.restoreItem(itemId)
}

class PermanentlyDeleteTrashItemUseCase(private val repository: TrashRepository) {
    suspend operator fun invoke(itemId: Long) =
        repository.permanentlyDeleteItem(itemId)

    suspend operator fun invoke(itemIds: Set<Long>) =
        repository.permanentlyDeleteItems(itemIds)
}

class EmptyTrashUseCase(private val repository: TrashRepository) {
    suspend operator fun invoke() = repository.emptyTrash()
}

data class TrashUseCases(
    val observe: ObserveTrashItemsUseCase,
    val restore: RestoreTrashItemUseCase,
    val permanentlyDelete: PermanentlyDeleteTrashItemUseCase,
    val empty: EmptyTrashUseCase
)
