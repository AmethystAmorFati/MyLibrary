package com.example.mylibrary.ui.theme

import com.example.mylibrary.ui.navigation.NavigationIconSlot
import com.example.mylibrary.ui.navigation.NavigationIconState
import com.example.mylibrary.ui.navigation.ThemeIconRendering
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.math.max
import kotlin.math.min

data class NavigationIconVariant(
    val slot: NavigationIconSlot,
    val state: NavigationIconState
)

data class ValidatedThemeNavigationIconFile(
    val variant: NavigationIconVariant,
    val rendering: ThemeIconRendering,
    val relativePath: String,
    val canonicalPath: String,
    val file: File,
    val fileSize: Long,
    val lastModified: Long,
    val width: Int,
    val height: Int,
    val format: ThemeImageFormat
)

sealed interface ThemeNavigationIconFileValidationResult {
    data class Success(
        val images: Map<NavigationIconVariant, ValidatedThemeNavigationIconFile>
    ) : ThemeNavigationIconFileValidationResult

    data class Failure(
        val error: ThemeResolveError
    ) : ThemeNavigationIconFileValidationResult
}

/**
 * Validates only the navigation-image category. It reuses the trusted resource
 * provider and the surface parser's signature/animation inspection, while
 * enforcing much smaller icon-specific byte and geometry limits.
 */
object ThemeNavigationIconFileValidator {
    fun validateDeclaredFiles(
        navigation: ThemeNavigationManifest,
        resources: ThemeResourceProvider
    ): ThemeNavigationIconFileValidationResult {
        val validated = linkedMapOf<
            NavigationIconVariant,
            ValidatedThemeNavigationIconFile
            >()
        var totalBytes = 0L

        navigation.entries().forEach { (slot, definition) ->
            val declarations = buildList {
                add(NavigationIconState.NORMAL to definition.normal)
                definition.selected?.let {
                    add(NavigationIconState.SELECTED to it)
                }
            }
            declarations.forEach { (state, path) ->
                val result = validateOne(
                    slot = slot,
                    state = state,
                    relativePath = path,
                    rendering = navigation.rendering,
                    resources = resources
                )
                if (result is ThemeNavigationIconFileValidationResult.Failure) {
                    return result
                }
                val image = (result as ThemeNavigationIconFileValidationResult.Success)
                    .images
                    .getValue(NavigationIconVariant(slot, state))
                totalBytes += image.fileSize
                if (
                    totalBytes >
                    ThemeResourceLimits.MAX_TOTAL_NAVIGATION_IMAGE_BYTES
                ) {
                    return failure(
                        ThemeResolveError.NavigationIconTotalTooLarge(
                            actualBytes = totalBytes,
                            maximumBytes =
                                ThemeResourceLimits.MAX_TOTAL_NAVIGATION_IMAGE_BYTES
                        )
                    )
                }
                validated[image.variant] = image
            }
        }
        return ThemeNavigationIconFileValidationResult.Success(validated)
    }

    fun validateOne(
        slot: NavigationIconSlot,
        state: NavigationIconState,
        relativePath: String,
        rendering: ThemeIconRendering,
        resources: ThemeResourceProvider
    ): ThemeNavigationIconFileValidationResult {
        val variant = NavigationIconVariant(slot, state)
        val file = resources.resolveFile(relativePath)
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return failure(
                ThemeResolveError.NavigationIconMissing(
                    slot,
                    state,
                    relativePath
                )
            )
        }
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return failure(
                ThemeResolveError.NavigationIconHeaderInvalid(
                    slot,
                    state,
                    relativePath,
                    "Resource is not a regular file"
                )
            )
        }

        val size = file.length()
        if (size < ThemeResourceLimits.MIN_NAVIGATION_IMAGE_FILE_BYTES) {
            return failure(
                ThemeResolveError.NavigationIconTooSmall(
                    slot = slot,
                    state = state,
                    relativePath = relativePath,
                    actualBytes = size,
                    minimumBytes =
                        ThemeResourceLimits.MIN_NAVIGATION_IMAGE_FILE_BYTES
                )
            )
        }
        if (size > ThemeResourceLimits.MAX_NAVIGATION_IMAGE_FILE_BYTES) {
            return failure(
                ThemeResolveError.NavigationIconTooLarge(
                    slot = slot,
                    state = state,
                    relativePath = relativePath,
                    actualBytes = size,
                    maximumBytes =
                        ThemeResourceLimits.MAX_NAVIGATION_IMAGE_FILE_BYTES
                )
            )
        }

        val extension = relativePath.substringAfterLast('.').lowercase()
        val declaredFormat = when (extension) {
            "png" -> ThemeImageFormat.PNG
            "webp" -> ThemeImageFormat.WEBP
            else -> {
                return failure(
                    ThemeResolveError.UnsupportedNavigationIconFormat(
                        slot,
                        state,
                        relativePath,
                        "Only PNG and static WebP are supported"
                    )
                )
            }
        }

        val header = when (
            val inspection = ThemeImageFileValidator.inspectHeader(file, size)
        ) {
            is ThemeImageHeaderInspectionResult.Success -> inspection.header
            is ThemeImageHeaderInspectionResult.Failure -> {
                val error = when (inspection.kind) {
                    ThemeImageHeaderFailureKind.INVALID_HEADER ->
                        ThemeResolveError.NavigationIconHeaderInvalid(
                            slot,
                            state,
                            relativePath,
                            inspection.reason
                        )

                    ThemeImageHeaderFailureKind.UNSUPPORTED_FORMAT ->
                        ThemeResolveError.UnsupportedNavigationIconFormat(
                            slot,
                            state,
                            relativePath,
                            inspection.reason
                        )
                }
                return failure(error)
            }
        }

        if (header.format != declaredFormat) {
            return failure(
                ThemeResolveError.NavigationIconFormatMismatch(
                    slot = slot,
                    state = state,
                    relativePath = relativePath,
                    declaredExtension = extension,
                    detectedFormat = header.format
                )
            )
        }
        if (header.format !in supportedFormats) {
            return failure(
                ThemeResolveError.UnsupportedNavigationIconFormat(
                    slot,
                    state,
                    relativePath,
                    "Navigation icons support PNG and static WebP only"
                )
            )
        }
        if (header.animated) {
            return failure(
                ThemeResolveError.AnimatedNavigationIconUnsupported(
                    slot,
                    state,
                    relativePath,
                    header.format
                )
            )
        }

        validateDimensions(
            slot = slot,
            state = state,
            relativePath = relativePath,
            width = header.width,
            height = header.height
        )?.let { return failure(it) }

        return ThemeNavigationIconFileValidationResult.Success(
            mapOf(
                variant to ValidatedThemeNavigationIconFile(
                    variant = variant,
                    rendering = rendering,
                    relativePath = relativePath,
                    canonicalPath = file.canonicalPath,
                    file = file,
                    fileSize = size,
                    lastModified = file.lastModified(),
                    width = header.width,
                    height = header.height,
                    format = header.format
                )
            )
        )
    }

    private fun validateDimensions(
        slot: NavigationIconSlot,
        state: NavigationIconState,
        relativePath: String,
        width: Int,
        height: Int
    ): ThemeResolveError? {
        if (
            width < ThemeResourceLimits.MIN_NAVIGATION_IMAGE_SIDE_PIXELS ||
            height < ThemeResourceLimits.MIN_NAVIGATION_IMAGE_SIDE_PIXELS ||
            width > ThemeResourceLimits.MAX_NAVIGATION_IMAGE_SIDE_PIXELS ||
            height > ThemeResourceLimits.MAX_NAVIGATION_IMAGE_SIDE_PIXELS
        ) {
            return ThemeResolveError.NavigationIconDimensionsInvalid(
                slot = slot,
                state = state,
                relativePath = relativePath,
                width = width,
                height = height,
                reason = "Each side must be within " +
                    "${ThemeResourceLimits.MIN_NAVIGATION_IMAGE_SIDE_PIXELS}.." +
                    ThemeResourceLimits.MAX_NAVIGATION_IMAGE_SIDE_PIXELS
            )
        }
        val pixels = width.toLong() * height.toLong()
        if (pixels > ThemeResourceLimits.MAX_NAVIGATION_IMAGE_PIXELS) {
            return ThemeResolveError.NavigationIconPixelCountExceeded(
                slot = slot,
                state = state,
                relativePath = relativePath,
                actualPixels = pixels,
                maximumPixels = ThemeResourceLimits.MAX_NAVIGATION_IMAGE_PIXELS
            )
        }
        val ratio = max(width, height).toDouble() / min(width, height).toDouble()
        if (ratio > ThemeResourceLimits.MAX_NAVIGATION_IMAGE_ASPECT_RATIO) {
            return ThemeResolveError.NavigationIconAspectRatioInvalid(
                slot = slot,
                state = state,
                relativePath = relativePath,
                width = width,
                height = height,
                maximumRatio =
                    ThemeResourceLimits.MAX_NAVIGATION_IMAGE_ASPECT_RATIO
            )
        }
        return null
    }

    private fun failure(
        error: ThemeResolveError
    ): ThemeNavigationIconFileValidationResult.Failure =
        ThemeNavigationIconFileValidationResult.Failure(error)

    private val supportedFormats = setOf(
        ThemeImageFormat.PNG,
        ThemeImageFormat.WEBP
    )
}
