package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow

class ObserveItemTypesUseCase(
    private val repository: LibraryRepository
) {
    operator fun invoke(): Flow<List<ItemType>> = repository.observeItemTypes()
}
