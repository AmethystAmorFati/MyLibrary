package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.ItemDetail
import com.example.mylibrary.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow

class ObserveItemDetailUseCase(
    private val repository: LibraryRepository
) {
    operator fun invoke(itemId: Long): Flow<ItemDetail?> =
        repository.observeItemDetail(itemId)
}
