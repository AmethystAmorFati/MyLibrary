package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.repository.LibraryRepository

class DeleteRecordUseCase(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(recordId: Long) {
        require(recordId > 0) { "记录编号无效" }
        repository.deleteRecord(recordId)
    }
}

