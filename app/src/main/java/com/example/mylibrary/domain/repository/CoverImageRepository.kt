package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.StoredCoverImage
import java.io.File

interface CoverImageRepository {
    suspend fun save(uri: String): StoredCoverImage
    fun resolveOriginal(relativePath: String?): File?
    suspend fun importOriginal(source: File): StoredCoverImage
    suspend fun delete(originalPath: String?, thumbnailPath: String?)
}
