package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.ResolvedTheme
import com.example.mylibrary.ui.theme.ThemeResolveError
import com.example.mylibrary.ui.theme.ThemeResourceLimits
import com.example.mylibrary.ui.theme.ThemeValidationIssue
import java.io.File

object ThemePackageLimits {
    const val MAX_SOURCE_ARCHIVE_BYTES = 96L * 1024L * 1024L
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 96L * 1024L * 1024L
    const val MAX_FILE_ENTRIES = 15
    const val MAX_TOTAL_ENTRIES = 32
    const val MAX_COMPRESSION_RATIO = 100.0
    const val MAX_MANIFEST_BYTES = 256L * 1024L
    const val MAX_CHECKSUMS_BYTES = 256L * 1024L
    const val MAX_PATH_LENGTH =
        ThemeResourceLimits.MAX_MANIFEST_STRING_LENGTH

    const val MANIFEST_PATH = "manifest.json"
    const val CHECKSUMS_PATH = "checksums.json"
    const val CHECKSUM_ALGORITHM = "SHA-256"
}

/**
 * Structured import phase for diagnostics.  Each value corresponds to a
 * discrete step in [ThemePackageImporter.importLocked].  When a failure
 * occurs the current phase is logged alongside the structured error so the
 * real root cause is visible in logcat instead of being collapsed into a
 * single generic user-facing string.
 */
enum class ThemeImportPhase {
    RECOVER_INTERRUPTED,
    PREPARE_WORKSPACE,
    COPY_SOURCE,
    PREPARE_STAGING,
    OPEN_ZIP,
    EXTRACT_ARCHIVE,
    READ_CHECKSUMS,
    PARSE_CHECKSUMS,
    VERIFY_CHECKSUMS,
    READ_MANIFEST,
    PARSE_MANIFEST,
    VALIDATE_MANIFEST,
    RESOLVE_THEME,
    INSTALL_THEME,
    ATOMIC_REPLACE
}

sealed interface ThemePackageError {
    data class SourceReadFailed(val reason: String) : ThemePackageError

    data class PackageTooLarge(
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemePackageError

    data class NotZipArchive(val reason: String) : ThemePackageError

    data class EncryptedZipUnsupported(
        val path: String
    ) : ThemePackageError

    data class TooManyEntries(
        val actualEntries: Int,
        val maximumEntries: Int
    ) : ThemePackageError

    data class TooManyFiles(
        val actualFiles: Int,
        val maximumFiles: Int
    ) : ThemePackageError

    data class ArchiveUncompressedSizeExceeded(
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemePackageError

    data class CompressionRatioExceeded(
        val path: String?,
        val actualRatio: Double,
        val maximumRatio: Double
    ) : ThemePackageError

    data class DuplicateEntry(val path: String) : ThemePackageError

    data class CaseCollision(
        val firstPath: String,
        val secondPath: String
    ) : ThemePackageError

    data class ZipPathInvalid(
        val path: String,
        val reason: String
    ) : ThemePackageError

    data class ZipPathEscapesRoot(val path: String) : ThemePackageError

    data class UnsupportedEntryType(
        val path: String,
        val type: String
    ) : ThemePackageError

    data class UnexpectedEntry(val path: String) : ThemePackageError

    data object MissingManifest : ThemePackageError
    data object MissingChecksums : ThemePackageError

    data class ManifestTooLarge(
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemePackageError

    data class ChecksumsTooLarge(
        val actualBytes: Long,
        val maximumBytes: Long
    ) : ThemePackageError

    data class ChecksumsInvalid(val reason: String) : ThemePackageError
    data class ChecksumEntryMissing(val path: String) : ThemePackageError
    data class ChecksumExtraEntry(val path: String) : ThemePackageError

    data class ChecksumMismatch(
        val path: String,
        val expectedSha256: String,
        val actualSha256: String
    ) : ThemePackageError

    data class ArchiveEntrySizeMismatch(
        val path: String,
        val declaredBytes: Long,
        val actualBytes: Long
    ) : ThemePackageError

    data class ArchiveEntryCrcMismatch(
        val path: String,
        val expectedCrc32: Long,
        val actualCrc32: Long
    ) : ThemePackageError

    data class ManifestParseFailed(val reason: String) : ThemePackageError

    data class ThemeValidationFailed(
        val issues: List<ThemeValidationIssue>
    ) : ThemePackageError

    data class ManifestResourceMissing(val path: String) : ThemePackageError
    data class ManifestResourceExtra(val path: String) : ThemePackageError

    data class ThemeResolutionFailed(
        val error: ThemeResolveError
    ) : ThemePackageError

    data class InstallFailed(val reason: String) : ThemePackageError
    data class RollbackFailed(val reason: String) : ThemePackageError
    data class RecoveryFailed(val path: String, val reason: String) :
        ThemePackageError
}

sealed interface ThemePackageResult<out T> {
    data class Success<T>(val value: T) : ThemePackageResult<T>
    data class Failure(val error: ThemePackageError) :
        ThemePackageResult<Nothing>
}

sealed interface ThemePackageImportResult {
    data class Installed(val theme: InstalledTheme) : ThemePackageImportResult
    data class Failure(val error: ThemePackageError) :
        ThemePackageImportResult
}

data class InstalledTheme(
    val id: String,
    val name: String,
    val version: String,
    val author: String?,
    val directory: File,
    val resolvedTheme: ResolvedTheme
)

data class ThemeChecksumManifest(
    val algorithm: String,
    val files: Map<String, String>
)

internal class ThemePackageFailureException(
    val error: ThemePackageError
) : Exception(error.toString())

internal fun failThemePackage(error: ThemePackageError): Nothing =
    throw ThemePackageFailureException(error)
