package com.example.mylibrary.ui.theme

object ThemeResourceLimits {
    const val MAX_SURFACE_IMAGES = 3
    const val MAX_FONT_FILES = 2
    const val MAX_NAVIGATION_IMAGES = 8

    const val MIN_IMAGE_FILE_BYTES = 12L
    const val MAX_BACKGROUND_IMAGE_FILE_BYTES = 12L * 1024L * 1024L
    const val MAX_CARD_IMAGE_FILE_BYTES = 8L * 1024L * 1024L
    const val MAX_DIALOG_IMAGE_FILE_BYTES = 8L * 1024L * 1024L
    const val MAX_TOTAL_SURFACE_IMAGE_BYTES = 24L * 1024L * 1024L

    const val MIN_IMAGE_SIDE_PIXELS = 16
    const val MAX_IMAGE_SIDE_PIXELS = 8192
    const val MAX_BACKGROUND_IMAGE_PIXELS = 16_000_000L
    const val MAX_CARD_IMAGE_PIXELS = 8_000_000L
    const val MAX_DIALOG_IMAGE_PIXELS = 8_000_000L

    const val BACKGROUND_DECODE_BUCKET_STEP = 512
    const val BACKGROUND_DECODE_MAX_SHORT_SIDE = 2048
    const val BACKGROUND_DECODE_MAX_LONG_SIDE = 4096
    const val CARD_DECODE_MAX_SIDE = 1024
    const val DIALOG_DECODE_MAX_SIDE = 1536
    const val MAX_THEME_IMAGE_CACHE_ENTRIES = 9
    const val MAX_THEME_IMAGE_CACHE_BYTES = 64L * 1024L * 1024L

    const val MIN_NAVIGATION_IMAGE_FILE_BYTES = 12L
    const val MAX_NAVIGATION_IMAGE_FILE_BYTES = 512L * 1024L
    const val MAX_TOTAL_NAVIGATION_IMAGE_BYTES = 2L * 1024L * 1024L
    const val MIN_NAVIGATION_IMAGE_SIDE_PIXELS = 8
    const val MAX_NAVIGATION_IMAGE_SIDE_PIXELS = 1024
    const val MAX_NAVIGATION_IMAGE_PIXELS = 262_144L
    const val MAX_NAVIGATION_IMAGE_ASPECT_RATIO = 4.0
    const val NAVIGATION_DECODE_MAX_SIDE = 128
    const val MAX_NAVIGATION_IMAGE_CACHE_ENTRIES = 16
    const val MAX_NAVIGATION_IMAGE_CACHE_BYTES = 2L * 1024L * 1024L

    const val MIN_FONT_FILE_BYTES = 28L
    const val MAX_SINGLE_FONT_FILE_BYTES = 20L * 1024L * 1024L
    const val MAX_TOTAL_FONT_FILE_BYTES = 32L * 1024L * 1024L

    const val MAX_FILE_NAME_LENGTH = 128
    const val MAX_MANIFEST_STRING_LENGTH = 256
    const val MAX_THEME_ID_LENGTH = 64

    const val BACKGROUND_SURFACE_PREFIX = "surfaces/background/"
    const val CARD_SURFACE_PREFIX = "surfaces/card/"
    const val DIALOG_SURFACE_PREFIX = "surfaces/dialog/"
    const val FONT_PREFIX = "fonts/"
    const val NAVIGATION_PREFIX = "icons/"

    val COMMON_IMAGE_EXTENSIONS = setOf("png", "webp")
    val BACKGROUND_IMAGE_EXTENSIONS = COMMON_IMAGE_EXTENSIONS + setOf("jpg", "jpeg")
    val FONT_EXTENSIONS = setOf("ttf")

    fun maximumImageFileBytes(role: SurfaceRole): Long = when (role) {
        SurfaceRole.BACKGROUND -> MAX_BACKGROUND_IMAGE_FILE_BYTES
        SurfaceRole.CARD -> MAX_CARD_IMAGE_FILE_BYTES
        SurfaceRole.DIALOG -> MAX_DIALOG_IMAGE_FILE_BYTES
    }

    fun maximumImagePixels(role: SurfaceRole): Long = when (role) {
        SurfaceRole.BACKGROUND -> MAX_BACKGROUND_IMAGE_PIXELS
        SurfaceRole.CARD -> MAX_CARD_IMAGE_PIXELS
        SurfaceRole.DIALOG -> MAX_DIALOG_IMAGE_PIXELS
    }
}
