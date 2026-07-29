package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.repository.LibraryRepository

class CreateItemUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(item: NewItem): Long {
        require(item.typeId > 0) { "请选择作品类型" }
        require(item.title.isNotBlank()) { "标题不能为空" }
        require(item.creator.isNotBlank()) { "作者或导演不能为空" }
        return repository.createItem(item)
    }
}
