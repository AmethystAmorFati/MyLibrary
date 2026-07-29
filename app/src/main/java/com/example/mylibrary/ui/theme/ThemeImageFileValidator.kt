package com.example.mylibrary.ui.theme

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption

data class ValidatedThemeImageFile(
    val role: SurfaceRole,
    val relativePath: String,
    val file: File,
    val fileSize: Long,
    val lastModified: Long,
    val width: Int,
    val height: Int,
    val format: ThemeImageFormat,
    val alphaCapable: Boolean
)

sealed interface ThemeImageFileValidationResult {
    data class Success(
        val images: Map<SurfaceRole, ValidatedThemeImageFile>
    ) : ThemeImageFileValidationResult

    data class Failure(
        val error: ThemeResolveError
    ) : ThemeImageFileValidationResult
}

internal data class ParsedImageHeader(
    val width: Int,
    val height: Int,
    val format: ThemeImageFormat,
    val animated: Boolean
)

internal enum class ThemeImageHeaderFailureKind {
    INVALID_HEADER,
    UNSUPPORTED_FORMAT
}

internal sealed interface ThemeImageHeaderInspectionResult {
    data class Success(
        val header: ParsedImageHeader
    ) : ThemeImageHeaderInspectionResult

    data class Failure(
        val kind: ThemeImageHeaderFailureKind,
        val reason: String
    ) : ThemeImageHeaderInspectionResult
}

/**
 * Validates declared surface image files without using Android bitmap APIs.
 *
 * Header parsing reads only fixed metadata and skips compressed pixel payloads.
 * The Android decoder performs a second bounds check and the real sampled decode.
 */
object ThemeImageFileValidator {
    private val pngSignature = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A
    )

    /**
     * Shared signature and metadata inspection for other trusted theme image
     * categories. File-system containment must already have been established by
     * [ThemeResourceProvider].
     */
    internal fun inspectHeader(
        file: File,
        fileSize: Long
    ): ThemeImageHeaderInspectionResult =
        try {
            val header = FileInputStream(file).use { input ->
                parseHeader(
                    input = BufferedInputStream(input),
                    fileSize = fileSize,
                    role = SurfaceRole.BACKGROUND,
                    relativePath = file.name
                )
            }
            ThemeImageHeaderInspectionResult.Success(header)
        } catch (exception: ThemeImageValidationException) {
            when (val error = exception.error) {
                is ThemeResolveError.UnsupportedImageFormat ->
                    ThemeImageHeaderInspectionResult.Failure(
                        ThemeImageHeaderFailureKind.UNSUPPORTED_FORMAT,
                        error.reason
                    )

                is ThemeResolveError.ImageHeaderInvalid ->
                    ThemeImageHeaderInspectionResult.Failure(
                        ThemeImageHeaderFailureKind.INVALID_HEADER,
                        error.reason
                    )

                else -> ThemeImageHeaderInspectionResult.Failure(
                    ThemeImageHeaderFailureKind.INVALID_HEADER,
                    error.toString()
                )
            }
        } catch (exception: Exception) {
            ThemeImageHeaderInspectionResult.Failure(
                ThemeImageHeaderFailureKind.INVALID_HEADER,
                exception.message ?: exception::class.java.simpleName
            )
        }

    fun validateDeclaredFiles(
        surfaces: ThemeSurfaceManifest,
        resources: ThemeResourceProvider
    ): ThemeImageFileValidationResult {
        val validated = linkedMapOf<SurfaceRole, ValidatedThemeImageFile>()
        var totalBytes = 0L

        surfaces.entries().forEach { (role, definition) ->
            if (definition.type != ThemeSurfaceType.IMAGE) return@forEach
            val relativePath = requireNotNull(definition.file)
            val result = validateOne(role, relativePath, resources)
            if (result is ThemeImageFileValidationResult.Failure) return result
            val image = (result as ThemeImageFileValidationResult.Success)
                .images
                .getValue(role)
            totalBytes += image.fileSize
            if (totalBytes > ThemeResourceLimits.MAX_TOTAL_SURFACE_IMAGE_BYTES) {
                return ThemeImageFileValidationResult.Failure(
                    ThemeResolveError.ImageTotalTooLarge(
                        actualBytes = totalBytes,
                        maximumBytes =
                            ThemeResourceLimits.MAX_TOTAL_SURFACE_IMAGE_BYTES
                    )
                )
            }
            validated[role] = image
        }
        return ThemeImageFileValidationResult.Success(validated)
    }

    fun validateOne(
        role: SurfaceRole,
        relativePath: String,
        resources: ThemeResourceProvider
    ): ThemeImageFileValidationResult {
        val file = resources.resolveFile(relativePath)
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return failure(ThemeResolveError.ImageMissing(role, relativePath))
        }
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return failure(
                ThemeResolveError.ImageHeaderInvalid(
                    role = role,
                    relativePath = relativePath,
                    reason = "Resource is not a regular file"
                )
            )
        }

        val size = file.length()
        if (size < ThemeResourceLimits.MIN_IMAGE_FILE_BYTES) {
            return failure(
                ThemeResolveError.ImageTooSmall(
                    role = role,
                    relativePath = relativePath,
                    actualBytes = size,
                    minimumBytes = ThemeResourceLimits.MIN_IMAGE_FILE_BYTES
                )
            )
        }
        val maximumBytes = ThemeResourceLimits.maximumImageFileBytes(role)
        if (size > maximumBytes) {
            return failure(
                ThemeResolveError.ImageTooLarge(
                    role = role,
                    relativePath = relativePath,
                    actualBytes = size,
                    maximumBytes = maximumBytes
                )
            )
        }

        val declaredExtension = relativePath
            .substringAfterLast('.')
            .lowercase()
        val declaredFormat = when (declaredExtension) {
            "png" -> ThemeImageFormat.PNG
            "webp" -> ThemeImageFormat.WEBP
            "jpg", "jpeg" -> ThemeImageFormat.JPEG
            else -> {
                return failure(
                    ThemeResolveError.UnsupportedImageFormat(
                        role = role,
                        relativePath = relativePath,
                        reason = "Only PNG, WebP, JPG, and JPEG are recognized"
                    )
                )
            }
        }

        val header = try {
            FileInputStream(file).use { input ->
                parseHeader(
                    input = BufferedInputStream(input),
                    fileSize = size,
                    role = role,
                    relativePath = relativePath
                )
            }
        } catch (exception: ThemeImageValidationException) {
            return failure(exception.error)
        } catch (exception: Exception) {
            return failure(
                ThemeResolveError.ImageHeaderInvalid(
                    role = role,
                    relativePath = relativePath,
                    reason = exception.message ?: exception::class.java.simpleName
                )
            )
        }

        if (header.format != declaredFormat) {
            return failure(
                ThemeResolveError.ImageFormatMismatch(
                    role = role,
                    relativePath = relativePath,
                    declaredExtension = declaredExtension,
                    detectedFormat = header.format
                )
            )
        }
        if (
            header.format == ThemeImageFormat.JPEG &&
            role != SurfaceRole.BACKGROUND
        ) {
            return failure(
                ThemeResolveError.UnsupportedImageFormat(
                    role = role,
                    relativePath = relativePath,
                    reason = "JPEG is supported only for BACKGROUND"
                )
            )
        }
        if (header.animated) {
            return failure(
                ThemeResolveError.AnimatedImageUnsupported(
                    role = role,
                    relativePath = relativePath,
                    format = header.format
                )
            )
        }

        val dimensionsError = validateDimensions(
            role = role,
            relativePath = relativePath,
            width = header.width,
            height = header.height
        )
        if (dimensionsError != null) return failure(dimensionsError)

        return ThemeImageFileValidationResult.Success(
            mapOf(
                role to ValidatedThemeImageFile(
                    role = role,
                    relativePath = relativePath,
                    file = file,
                    fileSize = size,
                    lastModified = file.lastModified(),
                    width = header.width,
                    height = header.height,
                    format = header.format,
                    alphaCapable = header.format.alphaCapable
                )
            )
        )
    }

    private fun validateDimensions(
        role: SurfaceRole,
        relativePath: String,
        width: Int,
        height: Int
    ): ThemeResolveError? {
        if (
            width < ThemeResourceLimits.MIN_IMAGE_SIDE_PIXELS ||
            height < ThemeResourceLimits.MIN_IMAGE_SIDE_PIXELS ||
            width > ThemeResourceLimits.MAX_IMAGE_SIDE_PIXELS ||
            height > ThemeResourceLimits.MAX_IMAGE_SIDE_PIXELS
        ) {
            return ThemeResolveError.ImageDimensionsInvalid(
                role = role,
                relativePath = relativePath,
                width = width,
                height = height,
                reason = "Each side must be within " +
                    "${ThemeResourceLimits.MIN_IMAGE_SIDE_PIXELS}.." +
                    ThemeResourceLimits.MAX_IMAGE_SIDE_PIXELS
            )
        }
        val pixels = width.toLong() * height.toLong()
        val maximumPixels = ThemeResourceLimits.maximumImagePixels(role)
        if (pixels > maximumPixels) {
            return ThemeResolveError.ImagePixelCountExceeded(
                role = role,
                relativePath = relativePath,
                actualPixels = pixels,
                maximumPixels = maximumPixels
            )
        }
        return null
    }

    private fun parseHeader(
        input: InputStream,
        fileSize: Long,
        role: SurfaceRole,
        relativePath: String
    ): ParsedImageHeader {
        input.mark(32)
        val prefix = input.readExact(12)
        input.reset()
        return when {
            prefix.copyOfRange(0, 8).contentEquals(pngSignature) ->
                parsePng(input, fileSize, role, relativePath)

            prefix[0] == 0xFF.toByte() && prefix[1] == 0xD8.toByte() ->
                parseJpeg(input, role, relativePath)

            prefix.copyOfRange(0, 4).toAscii() == "RIFF" &&
                prefix.copyOfRange(8, 12).toAscii() == "WEBP" ->
                parseWebp(input, fileSize, role, relativePath)

            isKnownUnsupportedFormat(prefix) ->
                throw validationError(
                    ThemeResolveError.UnsupportedImageFormat(
                        role = role,
                        relativePath = relativePath,
                        reason = "GIF, SVG, BMP, HEIF, and AVIF are not supported"
                    )
                )

            else -> throw validationError(
                ThemeResolveError.ImageHeaderInvalid(
                    role = role,
                    relativePath = relativePath,
                    reason = "Unrecognized image signature"
                )
            )
        }
    }

    private fun parsePng(
        input: InputStream,
        fileSize: Long,
        role: SurfaceRole,
        relativePath: String
    ): ParsedImageHeader {
        if (!input.readExact(8).contentEquals(pngSignature)) {
            throw invalidHeader(role, relativePath, "Invalid PNG signature")
        }
        var consumed = 8L
        var width: Int? = null
        var height: Int? = null
        var animated = false
        while (consumed + 12L <= fileSize) {
            val length = input.readUInt32BigEndian()
            val type = input.readExact(4).toAscii()
            consumed += 8L
            if (length > fileSize - consumed - 4L) {
                throw invalidHeader(role, relativePath, "PNG chunk exceeds file bounds")
            }
            when (type) {
                "IHDR" -> {
                    if (width != null || length != 13L) {
                        throw invalidHeader(role, relativePath, "Invalid PNG IHDR")
                    }
                    width = input.readIntBigEndian()
                    height = input.readIntBigEndian()
                    input.skipExact(length - 8L)
                }

                "acTL" -> {
                    animated = true
                    input.skipExact(length)
                }

                else -> input.skipExact(length)
            }
            input.skipExact(4L)
            consumed += length + 4L
            if (type == "IEND") break
        }
        return ParsedImageHeader(
            width = width ?: throw invalidHeader(
                role,
                relativePath,
                "PNG is missing IHDR"
            ),
            height = height ?: throw invalidHeader(
                role,
                relativePath,
                "PNG is missing IHDR"
            ),
            format = ThemeImageFormat.PNG,
            animated = animated
        )
    }

    private fun parseJpeg(
        input: InputStream,
        role: SurfaceRole,
        relativePath: String
    ): ParsedImageHeader {
        if (input.readUnsignedByte() != 0xFF || input.readUnsignedByte() != 0xD8) {
            throw invalidHeader(role, relativePath, "Invalid JPEG signature")
        }
        while (true) {
            var markerPrefix = input.readUnsignedByte()
            while (markerPrefix != 0xFF) markerPrefix = input.readUnsignedByte()
            var marker = input.readUnsignedByte()
            while (marker == 0xFF) marker = input.readUnsignedByte()
            if (marker == 0xD9 || marker == 0xDA) break
            if (marker == 0x01 || marker in 0xD0..0xD7) continue
            val segmentLength = input.readUInt16BigEndian()
            if (segmentLength < 2) {
                throw invalidHeader(role, relativePath, "Invalid JPEG segment length")
            }
            if (marker in jpegStartOfFrameMarkers) {
                if (segmentLength < 7) {
                    throw invalidHeader(role, relativePath, "Invalid JPEG frame header")
                }
                input.readUnsignedByte()
                val height = input.readUInt16BigEndian()
                val width = input.readUInt16BigEndian()
                return ParsedImageHeader(
                    width = width,
                    height = height,
                    format = ThemeImageFormat.JPEG,
                    animated = false
                )
            }
            input.skipExact(segmentLength.toLong() - 2L)
        }
        throw invalidHeader(role, relativePath, "JPEG is missing a frame header")
    }

    private fun parseWebp(
        input: InputStream,
        fileSize: Long,
        role: SurfaceRole,
        relativePath: String
    ): ParsedImageHeader {
        val riff = input.readExact(4).toAscii()
        val declaredPayload = input.readUInt32LittleEndian()
        val webp = input.readExact(4).toAscii()
        if (riff != "RIFF" || webp != "WEBP") {
            throw invalidHeader(role, relativePath, "Invalid WebP RIFF header")
        }
        val declaredEnd = declaredPayload + 8L
        if (declaredEnd > fileSize || declaredEnd < 20L) {
            throw invalidHeader(role, relativePath, "WebP RIFF size is invalid")
        }
        var consumed = 12L
        var width: Int? = null
        var height: Int? = null
        var animated = false
        while (consumed + 8L <= declaredEnd) {
            val type = input.readExact(4).toAscii()
            val chunkSize = input.readUInt32LittleEndian()
            consumed += 8L
            if (chunkSize > declaredEnd - consumed) {
                throw invalidHeader(role, relativePath, "WebP chunk exceeds RIFF bounds")
            }
            when (type) {
                "VP8X" -> {
                    if (chunkSize < 10L) {
                        throw invalidHeader(role, relativePath, "Invalid VP8X header")
                    }
                    val header = input.readExact(10)
                    animated = animated || (header[0].toInt() and 0x02) != 0
                    width = 1 + header.readUInt24LittleEndian(4)
                    height = 1 + header.readUInt24LittleEndian(7)
                    input.skipExact(chunkSize - 10L)
                }

                "VP8 " -> {
                    if (chunkSize < 10L) {
                        throw invalidHeader(role, relativePath, "Invalid VP8 frame")
                    }
                    val header = input.readExact(10)
                    if (
                        header[3] != 0x9D.toByte() ||
                        header[4] != 0x01.toByte() ||
                        header[5] != 0x2A.toByte()
                    ) {
                        throw invalidHeader(role, relativePath, "Invalid VP8 start code")
                    }
                    if (width == null) {
                        width = (
                            header[6].toInt() and 0xFF or
                                ((header[7].toInt() and 0x3F) shl 8)
                            )
                        height = (
                            header[8].toInt() and 0xFF or
                                ((header[9].toInt() and 0x3F) shl 8)
                            )
                    }
                    input.skipExact(chunkSize - 10L)
                }

                "VP8L" -> {
                    if (chunkSize < 5L) {
                        throw invalidHeader(role, relativePath, "Invalid VP8L frame")
                    }
                    val header = input.readExact(5)
                    if (header[0] != 0x2F.toByte()) {
                        throw invalidHeader(role, relativePath, "Invalid VP8L signature")
                    }
                    if (width == null) {
                        width = 1 + (
                            (header[1].toInt() and 0xFF) or
                                ((header[2].toInt() and 0x3F) shl 8)
                            )
                        height = 1 + (
                            ((header[2].toInt() and 0xC0) shr 6) or
                                ((header[3].toInt() and 0xFF) shl 2) or
                                ((header[4].toInt() and 0x0F) shl 10)
                            )
                    }
                    input.skipExact(chunkSize - 5L)
                }

                "ANIM", "ANMF" -> {
                    animated = true
                    input.skipExact(chunkSize)
                }

                else -> input.skipExact(chunkSize)
            }
            consumed += chunkSize
            if ((chunkSize and 1L) != 0L) {
                if (consumed >= declaredEnd) {
                    throw invalidHeader(role, relativePath, "Missing WebP chunk padding")
                }
                input.skipExact(1L)
                consumed += 1L
            }
        }
        return ParsedImageHeader(
            width = width ?: throw invalidHeader(
                role,
                relativePath,
                "WebP is missing image dimensions"
            ),
            height = height ?: throw invalidHeader(
                role,
                relativePath,
                "WebP is missing image dimensions"
            ),
            format = ThemeImageFormat.WEBP,
            animated = animated
        )
    }

    private fun isKnownUnsupportedFormat(prefix: ByteArray): Boolean {
        val ascii = prefix.toAscii()
        return ascii.startsWith("GIF87a") ||
            ascii.startsWith("GIF89a") ||
            ascii.startsWith("BM") ||
            ascii.trimStart().startsWith("<svg", ignoreCase = true) ||
            (
                prefix.copyOfRange(4, 8).toAscii() == "ftyp" &&
                    (
                        prefix.copyOfRange(8, 12).toAscii().startsWith("hei") ||
                            prefix.copyOfRange(8, 12).toAscii().startsWith("avi")
                        )
                )
    }

    private fun failure(
        error: ThemeResolveError
    ): ThemeImageFileValidationResult.Failure =
        ThemeImageFileValidationResult.Failure(error)

    private fun invalidHeader(
        role: SurfaceRole,
        relativePath: String,
        reason: String
    ): ThemeImageValidationException = validationError(
        ThemeResolveError.ImageHeaderInvalid(role, relativePath, reason)
    )

    private fun validationError(
        error: ThemeResolveError
    ): ThemeImageValidationException = ThemeImageValidationException(error)

    private val jpegStartOfFrameMarkers = setOf(
        0xC0,
        0xC1,
        0xC2,
        0xC3,
        0xC5,
        0xC6,
        0xC7,
        0xC9,
        0xCA,
        0xCB,
        0xCD,
        0xCE,
        0xCF
    )
}

private class ThemeImageValidationException(
    val error: ThemeResolveError
) : Exception(error.toString())

private fun InputStream.readExact(size: Int): ByteArray {
    val result = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val read = read(result, offset, size - offset)
        if (read < 0) throw EOFException("Unexpected end of image header")
        offset += read
    }
    return result
}

private fun InputStream.readUnsignedByte(): Int {
    val value = read()
    if (value < 0) throw EOFException("Unexpected end of image header")
    return value
}

private fun InputStream.readUInt16BigEndian(): Int =
    (readUnsignedByte() shl 8) or readUnsignedByte()

private fun InputStream.readIntBigEndian(): Int =
    (readUnsignedByte() shl 24) or
        (readUnsignedByte() shl 16) or
        (readUnsignedByte() shl 8) or
        readUnsignedByte()

private fun InputStream.readUInt32BigEndian(): Long =
    (
        (readUnsignedByte().toLong() shl 24) or
            (readUnsignedByte().toLong() shl 16) or
            (readUnsignedByte().toLong() shl 8) or
            readUnsignedByte().toLong()
        ) and 0xFFFF_FFFFL

private fun InputStream.readUInt32LittleEndian(): Long =
    (
        readUnsignedByte().toLong() or
            (readUnsignedByte().toLong() shl 8) or
            (readUnsignedByte().toLong() shl 16) or
            (readUnsignedByte().toLong() shl 24)
        ) and 0xFFFF_FFFFL

private fun InputStream.skipExact(byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped > 0L) {
            remaining -= skipped
        } else if (read() >= 0) {
            remaining -= 1L
        } else {
            throw EOFException("Unexpected end of image header")
        }
    }
}

private fun ByteArray.readUInt24LittleEndian(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16)

private fun ByteArray.toAscii(): String =
    String(this, Charsets.US_ASCII)
