package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.ItemChanges
import com.example.mylibrary.domain.repository.LibraryRepository

class UpdateItemUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(itemId: Long, changes: ItemChanges) {
        require(itemId > 0) { "作品编号无效" }
        require(changes.title.isNotBlank()) { "标题不能为空" }
        require(changes.creator.isNotBlank()) { "作者或导演不能为空" }
        repository.updateItem(itemId, changes)
    }
}
