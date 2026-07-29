package com.example.mylibrary.data.image

import com.example.mylibrary.domain.model.CoverImageMetadata
import com.example.mylibrary.domain.model.CoverStorageLimits
import com.example.mylibrary.domain.model.coverFormatMatchesExtension
import com.example.mylibrary.domain.model.coverFormatMatchesMimeType
import com.example.mylibrary.domain.model.validateCoverMetadata
import java.io.File

object CoverInputValidator {
    fun validate(
        file: File,
        declaredMimeType: String? = null,
        declaredExtension: String? = null
    ): CoverImageMetadata {
        require(file.isFile) { "封面文件不存在" }
        require(file.length() in 1..CoverStorageLimits.MAX_SOURCE_BYTES) {
            "封面文件不能超过 32 MiB"
        }
        val actualFormat = requireNotNull(CoverFileFormat.detect(file)) {
            "封面内容不是支持的图片格式"
        }
        require(CoverFileFormat.isStructurallyComplete(file, actualFormat)) {
            "封面图片已损坏或不完整"
        }
        declaredMimeType?.takeIf(String::isNotBlank)?.let { mimeType ->
            require(coverFormatMatchesMimeType(actualFormat, mimeType)) {
                "封面 MIME 类型与真实内容不一致"
            }
        }
        declaredExtension?.takeIf(String::isNotBlank)?.let { extension ->
            require(coverFormatMatchesExtension(actualFormat, extension)) {
                "封面扩展名与真实内容不一致"
            }
        }

        val bounds = requireNotNull(CoverImageDimensions.read(file, actualFormat)) {
            "无法读取封面尺寸"
        }
        return validateCoverMetadata(
            byteSize = file.length(),
            width = bounds.width,
            height = bounds.height,
            format = actualFormat
        )
    }
}
