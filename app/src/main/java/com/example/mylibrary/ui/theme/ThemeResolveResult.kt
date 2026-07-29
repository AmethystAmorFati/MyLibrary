package com.example.mylibrary.ui.theme

import com.example.mylibrary.ui.navigation.NavigationIconSlot
import com.example.mylibrary.ui.navigation.NavigationIconState

sealed interface ThemeResolveError {
    data class ManifestInvalid(
        val issues: List<ThemeValidationIssue>
    ) : ThemeResolveError

    data class ResourceMissing(
        val relativePath: String
    ) : ThemeResolveError

    data class ResourceNotRegularFile(
        val relativePath: String
    ) : ThemeResolveError

    data class PathEscapesRoot(
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class ResourceAccessFailed(
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class UnsupportedFontFormat(
        val relativePath: String
    ) : ThemeResolveError

    data class FontFileTooSmall(
        val relativePath: String,
        val actualBytes: Long,
        val minimumBytes: Long
    ) : ThemeResolveError

    data class FontTooLarge(
        val relativePath: String,
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemeResolveError

    data class FontTotalTooLarge(
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemeResolveError

    data class FontHeaderInvalid(
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class FontLoadFailed(
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class ImageMissing(
        val role: SurfaceRole,
        val relativePath: String
    ) : ThemeResolveError

    data class ImageTooSmall(
        val role: SurfaceRole,
        val relativePath: String,
        val actualBytes: Long,
        val minimumBytes: Long
    ) : ThemeResolveError

    data class ImageTooLarge(
        val role: SurfaceRole,
        val relativePath: String,
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemeResolveError

    data class ImageTotalTooLarge(
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemeResolveError

    data class ImageDimensionsInvalid(
        val role: SurfaceRole,
        val relativePath: String,
        val width: Int,
        val height: Int,
        val reason: String
    ) : ThemeResolveError

    data class ImagePixelCountExceeded(
        val role: SurfaceRole,
        val relativePath: String,
        val actualPixels: Long,
        val maximumPixels: Long
    ) : ThemeResolveError

    data class ImageHeaderInvalid(
        val role: SurfaceRole,
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class ImageFormatMismatch(
        val role: SurfaceRole,
        val relativePath: String,
        val declaredExtension: String,
        val detectedFormat: ThemeImageFormat
    ) : ThemeResolveError

    data class UnsupportedImageFormat(
        val role: SurfaceRole,
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class AnimatedImageUnsupported(
        val role: SurfaceRole,
        val relativePath: String,
        val format: ThemeImageFormat
    ) : ThemeResolveError

    data class ImageDecodeFailed(
        val role: SurfaceRole,
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class NavigationIconMissing(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String
    ) : ThemeResolveError

    data class NavigationIconTooSmall(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val actualBytes: Long,
        val minimumBytes: Long
    ) : ThemeResolveError

    data class NavigationIconTooLarge(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemeResolveError

    data class NavigationIconTotalTooLarge(
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemeResolveError

    data class NavigationIconDimensionsInvalid(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val width: Int,
        val height: Int,
        val reason: String
    ) : ThemeResolveError

    data class NavigationIconPixelCountExceeded(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val actualPixels: Long,
        val maximumPixels: Long
    ) : ThemeResolveError

    data class NavigationIconAspectRatioInvalid(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val width: Int,
        val height: Int,
        val maximumRatio: Double
    ) : ThemeResolveError

    data class NavigationIconHeaderInvalid(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class NavigationIconFormatMismatch(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val declaredExtension: String,
        val detectedFormat: ThemeImageFormat
    ) : ThemeResolveError

    data class UnsupportedNavigationIconFormat(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class AnimatedNavigationIconUnsupported(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val format: ThemeImageFormat
    ) : ThemeResolveError

    data class NavigationIconDecodeFailed(
        val slot: NavigationIconSlot,
        val state: NavigationIconState,
        val relativePath: String,
        val reason: String
    ) : ThemeResolveError

    data class UnexpectedFailure(
        val reason: String
    ) : ThemeResolveError
}

sealed interface ThemeResolveResult {
    data class Success(val theme: ResolvedTheme) : ThemeResolveResult
    data class Failure(val error: ThemeResolveError) : ThemeResolveResult
}
