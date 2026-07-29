package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.ItemSaveRequest
import com.example.mylibrary.domain.repository.LibraryRepository

class SaveItemUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(request: ItemSaveRequest): Long {
        require(request.title.isNotBlank()) { "标题不能为空" }
        require(request.creator.isNotBlank()) { "作者或导演不能为空" }
        return repository.saveItem(request)
    }
}
