package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow

class ObserveLibraryItemsUseCase(
    private val repository: LibraryRepository
) {
    operator fun invoke(
        query: String = "",
        statusId: Long? = null,
        tagIds: Set<Long> = emptySet()
    ): Flow<List<LibraryItem>> = repository.observeItems(query, statusId, tagIds)
}
