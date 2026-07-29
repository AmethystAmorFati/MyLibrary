package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.StoredCoverImage
import com.example.mylibrary.domain.repository.CoverImageRepository

class SaveCoverImageUseCase(
    private val repository: CoverImageRepository
) {
    suspend operator fun invoke(uri: String): StoredCoverImage {
        require(uri.isNotBlank()) { "图片地址无效" }
        return repository.save(uri)
    }
}

class DeleteCoverImageUseCase(
    private val repository: CoverImageRepository
) {
    suspend operator fun invoke(originalPath: String?, thumbnailPath: String?) =
        repository.delete(originalPath, thumbnailPath)
}

data class CoverImageUseCases(
    val save: SaveCoverImageUseCase,
    val delete: DeleteCoverImageUseCase
)
