package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.ThemeResourceLimits
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

internal enum class ThemeArchiveEntryKind {
    FILE,
    DIRECTORY
}

internal data class ThemeArchiveEntryMetadata(
    val path: String,
    val kind: ThemeArchiveEntryKind,
    val flags: Int,
    val compressionMethod: Int,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localHeaderOffset: Long
)

internal data class ThemeArchiveMetadata(
    val entries: List<ThemeArchiveEntryMetadata>
)

internal object ThemeArchiveMetadataReader {
    fun read(archive: File): ThemePackageResult<ThemeArchiveMetadata> =
        try {
            if (!archive.isFile || archive.length() <= 0L) {
                failThemePackage(
                    ThemePackageError.NotZipArchive("Archive is empty or missing")
                )
            }
            if (archive.length() > ThemePackageLimits.MAX_SOURCE_ARCHIVE_BYTES) {
                failThemePackage(
                    ThemePackageError.PackageTooLarge(
                        archive.length(),
                        ThemePackageLimits.MAX_SOURCE_ARCHIVE_BYTES
                    )
                )
            }
            RandomAccessFile(archive, "r").use { input ->
                ThemePackageResult.Success(readCentralDirectory(input))
            }
        } catch (failure: ThemePackageFailureException) {
            ThemePackageResult.Failure(failure.error)
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.NotZipArchive(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }

    private fun readCentralDirectory(
        input: RandomAccessFile
    ): ThemeArchiveMetadata {
        val eocdOffset = findEndOfCentralDirectory(input)
        input.seek(eocdOffset)
        val eocd = ByteArray(22)
        input.readFully(eocd)
        if (eocd.uint32(0) != END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
            invalidZip("End-of-central-directory signature is missing")
        }
        val diskNumber = eocd.uint16(4)
        val centralDisk = eocd.uint16(6)
        val diskEntries = eocd.uint16(8)
        val totalEntries = eocd.uint16(10)
        val centralSize = eocd.uint32(12)
        val centralOffset = eocd.uint32(16)
        val commentLength = eocd.uint16(20)
        if (
            diskNumber != 0 ||
            centralDisk != 0 ||
            diskEntries != totalEntries
        ) {
            invalidZip("Multi-disk ZIP archives are not supported")
        }
        if (
            totalEntries == 0xFFFF ||
            centralSize == UINT32_MAX ||
            centralOffset == UINT32_MAX
        ) {
            invalidZip("ZIP64 archives are not supported")
        }
        if (
            eocdOffset + 22L + commentLength.toLong() != input.length()
        ) {
            invalidZip("ZIP end record length is inconsistent")
        }
        if (
            centralOffset + centralSize > eocdOffset ||
            centralOffset < 0L
        ) {
            invalidZip("Central directory lies outside the archive")
        }
        if (totalEntries > ThemePackageLimits.MAX_TOTAL_ENTRIES) {
            failThemePackage(
                ThemePackageError.TooManyEntries(
                    totalEntries,
                    ThemePackageLimits.MAX_TOTAL_ENTRIES
                )
            )
        }

        input.seek(centralOffset)
        val entries = ArrayList<ThemeArchiveEntryMetadata>(totalEntries)
        val exactPaths = mutableSetOf<String>()
        val casePaths = mutableMapOf<String, String>()
        val logicalKinds = mutableMapOf<String, ThemeArchiveEntryKind>()
        var fileCount = 0
        var totalCompressed = 0L
        var totalUncompressed = 0L

        repeat(totalEntries) {
            val fixed = ByteArray(46)
            input.readFully(fixed)
            if (fixed.uint32(0) != CENTRAL_DIRECTORY_SIGNATURE) {
                invalidZip("Invalid central-directory entry")
            }
            val versionMadeBy = fixed.uint16(4)
            val flags = fixed.uint16(8)
            val method = fixed.uint16(10)
            val crc32 = fixed.uint32(16)
            val compressedSize = fixed.uint32(20)
            val uncompressedSize = fixed.uint32(24)
            val nameLength = fixed.uint16(28)
            val extraLength = fixed.uint16(30)
            val entryCommentLength = fixed.uint16(32)
            val diskStart = fixed.uint16(34)
            val externalAttributes = fixed.uint32(38)
            val localOffset = fixed.uint32(42)
            if (
                compressedSize == UINT32_MAX ||
                uncompressedSize == UINT32_MAX ||
                localOffset == UINT32_MAX
            ) {
                invalidZip("ZIP64 entries are not supported")
            }
            if (diskStart != 0) {
                invalidZip("Multi-disk ZIP entries are not supported")
            }
            val nameBytes = ByteArray(nameLength)
            input.readFully(nameBytes)
            val extra = ByteArray(extraLength)
            input.readFully(extra)
            if (extra.containsExtraField(ZIP64_EXTRA_FIELD)) {
                invalidZip("ZIP64 entries are not supported")
            }
            if (entryCommentLength > 0) {
                input.seek(input.filePointer + entryCommentLength)
            }
            val path = decodeEntryName(nameBytes, flags)
            val kind = entryKind(
                path = path,
                versionMadeBy = versionMadeBy,
                externalAttributes = externalAttributes
            )
            ThemeArchivePathPolicy.validate(path, kind)
            if ((flags and ENCRYPTED_FLAG) != 0 || (flags and STRONG_ENCRYPTION_FLAG) != 0 ||
                extra.containsExtraField(AES_EXTRA_FIELD)
            ) {
                failThemePackage(
                    ThemePackageError.EncryptedZipUnsupported(path)
                )
            }
            if (method != STORED_METHOD && method != DEFLATED_METHOD) {
                failThemePackage(
                    ThemePackageError.UnsupportedEntryType(
                        path,
                        "compression method $method"
                    )
                )
            }
            if (!exactPaths.add(path)) {
                failThemePackage(ThemePackageError.DuplicateEntry(path))
            }
            val caseKey = path.lowercase(Locale.ROOT)
            val priorCase = casePaths.putIfAbsent(caseKey, path)
            if (priorCase != null && priorCase != path) {
                failThemePackage(
                    ThemePackageError.CaseCollision(priorCase, path)
                )
            }
            val logicalPath = path.removeSuffix("/")
            val priorKind = logicalKinds.putIfAbsent(logicalPath, kind)
            if (priorKind != null && priorKind != kind) {
                failThemePackage(
                    ThemePackageError.DuplicateEntry(logicalPath)
                )
            }

            if (kind == ThemeArchiveEntryKind.FILE) {
                fileCount += 1
                if (fileCount > ThemePackageLimits.MAX_FILE_ENTRIES) {
                    failThemePackage(
                        ThemePackageError.TooManyFiles(
                            fileCount,
                            ThemePackageLimits.MAX_FILE_ENTRIES
                        )
                    )
                }
                validateDeclaredEntrySize(path, uncompressedSize)
                validateCompressionRatio(
                    path,
                    uncompressedSize,
                    compressedSize
                )
                totalCompressed = safeAdd(totalCompressed, compressedSize)
                totalUncompressed = safeAdd(totalUncompressed, uncompressedSize)
                if (
                    totalUncompressed >
                    ThemePackageLimits.MAX_TOTAL_UNCOMPRESSED_BYTES
                ) {
                    failThemePackage(
                        ThemePackageError.ArchiveUncompressedSizeExceeded(
                            totalUncompressed,
                            ThemePackageLimits.MAX_TOTAL_UNCOMPRESSED_BYTES
                        )
                    )
                }
            } else if (compressedSize != 0L || uncompressedSize != 0L) {
                failThemePackage(
                    ThemePackageError.UnsupportedEntryType(
                        path,
                        "directory with data"
                    )
                )
            }

            validateLocalHeader(
                input,
                localOffset,
                nameBytes,
                path,
                flags,
                method
            )
            entries += ThemeArchiveEntryMetadata(
                path = path,
                kind = kind,
                flags = flags,
                compressionMethod = method,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localOffset
            )
        }
        if (input.filePointer != centralOffset + centralSize) {
            invalidZip("Central-directory size is inconsistent")
        }
        validateCompressionRatio(null, totalUncompressed, totalCompressed)
        return ThemeArchiveMetadata(entries)
    }

    private fun validateLocalHeader(
        input: RandomAccessFile,
        localOffset: Long,
        expectedName: ByteArray,
        path: String,
        centralFlags: Int,
        centralMethod: Int
    ) {
        val returnPosition = input.filePointer
        if (localOffset < 0L || localOffset + 30L > input.length()) {
            invalidZip("Local ZIP header lies outside the archive")
        }
        input.seek(localOffset)
        val fixed = ByteArray(30)
        input.readFully(fixed)
        if (fixed.uint32(0) != LOCAL_FILE_HEADER_SIGNATURE) {
            invalidZip("Local ZIP header signature is invalid")
        }
        val localFlags = fixed.uint16(6)
        val localMethod = fixed.uint16(8)
        val nameLength = fixed.uint16(26)
        val extraLength = fixed.uint16(28)
        val localName = ByteArray(nameLength)
        input.readFully(localName)
        if (!localName.contentEquals(expectedName)) {
            invalidZip("Local and central ZIP entry names differ")
        }
        if (localFlags != centralFlags || localMethod != centralMethod) {
            invalidZip("Local and central ZIP metadata differ")
        }
        if (input.filePointer + extraLength > input.length()) {
            invalidZip("Local ZIP extra field exceeds the archive")
        }
        val extra = ByteArray(extraLength)
        input.readFully(extra)
        if (extra.containsExtraField(ZIP64_EXTRA_FIELD)) {
            invalidZip("ZIP64 entries are not supported")
        }
        if (extra.containsExtraField(AES_EXTRA_FIELD)) {
            failThemePackage(
                ThemePackageError.EncryptedZipUnsupported(path)
            )
        }
        input.seek(returnPosition)
    }

    private fun entryKind(
        path: String,
        versionMadeBy: Int,
        externalAttributes: Long
    ): ThemeArchiveEntryKind {
        val host = versionMadeBy ushr 8
        if (host == UNIX_HOST) {
            val unixMode = ((externalAttributes ushr 16) and 0xFFFF).toInt()
            val type = unixMode and UNIX_TYPE_MASK
            if (type != 0) {
                return when (type) {
                    UNIX_REGULAR_FILE -> {
                        if (path.endsWith('/')) {
                            failThemePackage(
                                ThemePackageError.UnsupportedEntryType(
                                    path,
                                    "regular file marked as directory"
                                )
                            )
                        }
                        ThemeArchiveEntryKind.FILE
                    }

                    UNIX_DIRECTORY -> {
                        if (!path.endsWith('/')) {
                            failThemePackage(
                                ThemePackageError.UnsupportedEntryType(
                                    path,
                                    "directory without trailing slash"
                                )
                            )
                        }
                        ThemeArchiveEntryKind.DIRECTORY
                    }

                    UNIX_SYMBOLIC_LINK -> failThemePackage(
                        ThemePackageError.UnsupportedEntryType(
                            path,
                            "symbolic link"
                        )
                    )

                    else -> failThemePackage(
                        ThemePackageError.UnsupportedEntryType(
                            path,
                            "Unix special file"
                        )
                    )
                }
            }
        }
        val dosDirectory = (externalAttributes and DOS_DIRECTORY_ATTRIBUTE) != 0L
        return when {
            path.endsWith('/') && !dosDirectory && externalAttributes != 0L ->
                ThemeArchiveEntryKind.DIRECTORY
            path.endsWith('/') -> ThemeArchiveEntryKind.DIRECTORY
            dosDirectory -> failThemePackage(
                ThemePackageError.UnsupportedEntryType(
                    path,
                    "directory without trailing slash"
                )
            )
            else -> ThemeArchiveEntryKind.FILE
        }
    }

    private fun validateDeclaredEntrySize(path: String, size: Long) {
        val limit = when (path) {
            ThemePackageLimits.MANIFEST_PATH ->
                ThemePackageLimits.MAX_MANIFEST_BYTES
            ThemePackageLimits.CHECKSUMS_PATH ->
                ThemePackageLimits.MAX_CHECKSUMS_BYTES
            else -> ThemePackageLimits.MAX_TOTAL_UNCOMPRESSED_BYTES
        }
        if (size > limit) {
            val error = when (path) {
                ThemePackageLimits.MANIFEST_PATH ->
                    ThemePackageError.ManifestTooLarge(size, limit)
                ThemePackageLimits.CHECKSUMS_PATH ->
                    ThemePackageError.ChecksumsTooLarge(size, limit)
                else -> ThemePackageError.ArchiveUncompressedSizeExceeded(
                    size,
                    limit
                )
            }
            failThemePackage(error)
        }
    }

    private fun validateCompressionRatio(
        path: String?,
        uncompressed: Long,
        compressed: Long
    ) {
        if (uncompressed == 0L) return
        val ratio = if (compressed == 0L) {
            Double.POSITIVE_INFINITY
        } else {
            uncompressed.toDouble() / compressed.toDouble()
        }
        if (ratio > ThemePackageLimits.MAX_COMPRESSION_RATIO) {
            failThemePackage(
                ThemePackageError.CompressionRatioExceeded(
                    path = path,
                    actualRatio = ratio,
                    maximumRatio = ThemePackageLimits.MAX_COMPRESSION_RATIO
                )
            )
        }
    }

    private fun findEndOfCentralDirectory(input: RandomAccessFile): Long {
        val searchLength = minOf(input.length(), MAX_EOCD_SEARCH_BYTES)
        if (searchLength < 22L) invalidZip("Archive is too short")
        val start = input.length() - searchLength
        input.seek(start)
        val bytes = ByteArray(searchLength.toInt())
        input.readFully(bytes)
        for (index in bytes.size - 22 downTo 0) {
            if (
                bytes.uint32(index) == END_OF_CENTRAL_DIRECTORY_SIGNATURE &&
                index + 22 + bytes.uint16(index + 20) == bytes.size
            ) {
                return start + index
            }
        }
        invalidZip("End-of-central-directory record was not found")
    }

    private fun decodeEntryName(bytes: ByteArray, flags: Int): String {
        val charset = if ((flags and UTF8_FLAG) != 0) {
            StandardCharsets.UTF_8
        } else {
            Charset.forName("Cp437")
        }
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun invalidZip(reason: String): Nothing =
        failThemePackage(ThemePackageError.NotZipArchive(reason))

    private fun safeAdd(first: Long, second: Long): Long =
        try {
            Math.addExact(first, second)
        } catch (_: ArithmeticException) {
            failThemePackage(
                ThemePackageError.ArchiveUncompressedSizeExceeded(
                    Long.MAX_VALUE,
                    ThemePackageLimits.MAX_TOTAL_UNCOMPRESSED_BYTES
                )
            )
        }

    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50L
    private const val UINT32_MAX = 0xFFFF_FFFFL
    private const val MAX_EOCD_SEARCH_BYTES = 65_557L
    private const val ENCRYPTED_FLAG = 0x0001
    private const val STRONG_ENCRYPTION_FLAG = 0x0040
    private const val UTF8_FLAG = 0x0800
    private const val STORED_METHOD = 0
    private const val DEFLATED_METHOD = 8
    private const val AES_EXTRA_FIELD = 0x9901
    private const val ZIP64_EXTRA_FIELD = 0x0001
    private const val UNIX_HOST = 3
    private const val UNIX_TYPE_MASK = 0xF000
    private const val UNIX_REGULAR_FILE = 0x8000
    private const val UNIX_DIRECTORY = 0x4000
    private const val UNIX_SYMBOLIC_LINK = 0xA000
    private const val DOS_DIRECTORY_ATTRIBUTE = 0x10L
}

internal object ThemeArchivePathPolicy {
    private val drivePath = Regex("^[A-Za-z]:.*")
    private val surfaceExtensions = setOf("png", "webp", "jpg", "jpeg")
    private val fontExtensions = setOf("ttf")
    private val iconExtensions = setOf("png", "webp")
    private val allowedDirectories = setOf(
        "surfaces/",
        "surfaces/background/",
        "surfaces/card/",
        "surfaces/dialog/",
        "fonts/",
        "icons/"
    )

    fun validate(path: String, kind: ThemeArchiveEntryKind) {
        val reason = when {
            path.isBlank() -> "Path is blank"
            path.length > ThemePackageLimits.MAX_PATH_LENGTH ->
                "Path exceeds ${ThemePackageLimits.MAX_PATH_LENGTH} characters"
            '\u0000' in path -> "Path contains NUL"
            '\\' in path -> "Backslashes are not allowed"
            path.startsWith('/') || drivePath.matches(path) ->
                "Absolute paths are not allowed"
            path.startsWith("./") || path.contains("/./") ->
                "Dot path segments are not allowed"
            path.startsWith("../") || path.contains("/../") ->
                "Parent path segments are not allowed"
            "//" in path -> "Empty path segments are not allowed"
            else -> null
        }
        if (reason != null) {
            failThemePackage(ThemePackageError.ZipPathInvalid(path, reason))
        }
        val logical = path.removeSuffix("/")
        val segments = logical.split('/')
        if (
            segments.any {
                it.isEmpty() || it == "." || it == ".." || it.startsWith('.')
            }
        ) {
            failThemePackage(
                ThemePackageError.ZipPathInvalid(
                    path,
                    "Dot, hidden, and empty path segments are not allowed"
                )
            )
        }
        if (
            segments.last().length >
            ThemeResourceLimits.MAX_FILE_NAME_LENGTH
        ) {
            failThemePackage(
                ThemePackageError.ZipPathInvalid(
                    path,
                    "File name is too long"
                )
            )
        }
        when (kind) {
            ThemeArchiveEntryKind.DIRECTORY -> {
                if (!path.endsWith('/') || path !in allowedDirectories) {
                    failThemePackage(ThemePackageError.UnexpectedEntry(path))
                }
            }

            ThemeArchiveEntryKind.FILE -> {
                if (path.endsWith('/') || !isAllowedFile(path)) {
                    failThemePackage(ThemePackageError.UnexpectedEntry(path))
                }
            }
        }
    }

    fun isAllowedFile(path: String): Boolean = when (path) {
        ThemePackageLimits.MANIFEST_PATH,
        ThemePackageLimits.CHECKSUMS_PATH -> true
        else -> {
            val extension = path.substringAfterLast('.', "")
                .lowercase(Locale.ROOT)
            when {
                path.isSingleResourceUnder("fonts/") ->
                    extension in fontExtensions
                path.isSingleResourceUnder("icons/") ->
                    extension in iconExtensions
                path.isSurfaceResource() ->
                    extension in surfaceExtensions
                else -> false
            }
        }
    }

    private fun String.isSingleResourceUnder(prefix: String): Boolean {
        if (!startsWith(prefix)) return false
        val relative = removePrefix(prefix)
        return relative.isNotBlank() && '/' !in relative
    }

    private fun String.isSurfaceResource(): Boolean {
        if (!startsWith("surfaces/")) return false
        val relative = removePrefix("surfaces/")
        if (relative.isBlank()) return false
        if ('/' !in relative) return true
        val role = relative.substringBefore('/')
        val fileName = relative.substringAfter('/')
        return role in setOf("background", "card", "dialog") &&
            fileName.isNotBlank() &&
            '/' !in fileName
    }
}

private fun ByteArray.uint16(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 2)
        .order(ByteOrder.LITTLE_ENDIAN)
        .short
        .toInt() and 0xFFFF

private fun ByteArray.uint32(offset: Int): Long =
    ByteBuffer.wrap(this, offset, 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int
        .toLong() and 0xFFFF_FFFFL

private fun ByteArray.containsExtraField(targetId: Int): Boolean {
    var offset = 0
    while (offset + 4 <= size) {
        val id = uint16(offset)
        val length = uint16(offset + 2)
        offset += 4
        if (offset + length > size) {
            failThemePackage(
                ThemePackageError.NotZipArchive(
                    "ZIP extra field exceeds its declared length"
                )
            )
        }
        if (id == targetId) return true
        offset += length
    }
    if (offset != size) {
        failThemePackage(
            ThemePackageError.NotZipArchive(
                "ZIP extra field is truncated"
            )
        )
    }
    return false
}
