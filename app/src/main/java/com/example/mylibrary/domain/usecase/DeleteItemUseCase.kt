package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.repository.LibraryRepository

class DeleteItemUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(itemId: Long) {
        require(itemId > 0) { "作品编号无效" }
        repository.deleteItem(itemId)
    }
}
