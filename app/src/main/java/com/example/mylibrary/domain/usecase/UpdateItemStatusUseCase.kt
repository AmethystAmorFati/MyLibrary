package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.repository.LibraryRepository

class UpdateItemStatusUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(itemId: Long, statusId: Long) {
        require(itemId > 0) { "作品编号无效" }
        require(statusId > 0) { "请选择当前状态" }
        repository.updateItemStatus(itemId, statusId)
    }
}
