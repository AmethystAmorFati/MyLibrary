package com.example.mylibrary.domain.model

object CoverStorageLimits {
    // Matches Backup v4's per-cover entry limit so every newly accepted cover
    // can be exported without being silently omitted.
    const val MAX_SOURCE_BYTES = 32L * 1024L * 1024L
    const val MAX_WIDTH_PIXELS = 8_192
    const val MAX_HEIGHT_PIXELS = 8_192
    const val MAX_PIXEL_COUNT = 16_000_000L

    val SUPPORTED_FORMATS = setOf("jpg", "png", "webp", "gif")
}

data class CoverImageMetadata(
    val byteSize: Long,
    val width: Int,
    val height: Int,
    val format: String
)

fun validateCoverMetadata(
    byteSize: Long,
    width: Int,
    height: Int,
    format: String
): CoverImageMetadata {
    require(byteSize in 1..CoverStorageLimits.MAX_SOURCE_BYTES) {
        "封面文件不能超过 32 MiB"
    }
    require(format in CoverStorageLimits.SUPPORTED_FORMATS) {
        "仅支持 JPEG、PNG、WebP 或 GIF 封面"
    }
    require(width > 0 && height > 0) {
        "无法读取封面尺寸"
    }
    require(width <= CoverStorageLimits.MAX_WIDTH_PIXELS) {
        "封面宽度不能超过 ${CoverStorageLimits.MAX_WIDTH_PIXELS} 像素"
    }
    require(height <= CoverStorageLimits.MAX_HEIGHT_PIXELS) {
        "封面高度不能超过 ${CoverStorageLimits.MAX_HEIGHT_PIXELS} 像素"
    }
    val pixels = width.toLong() * height.toLong()
    require(pixels <= CoverStorageLimits.MAX_PIXEL_COUNT) {
        "封面总像素不能超过 ${CoverStorageLimits.MAX_PIXEL_COUNT}"
    }
    return CoverImageMetadata(byteSize, width, height, format)
}

fun coverFormatMatchesExtension(actual: String, declared: String): Boolean {
    val normalized = declared.lowercase().removePrefix(".")
    return actual == normalized || actual == "jpg" && normalized == "jpeg"
}

fun coverFormatMatchesMimeType(actual: String, mimeType: String): Boolean {
    if (mimeType.isBlank()) return true
    val declared = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png", "image/x-png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> return false
    }
    return actual == declared
}
