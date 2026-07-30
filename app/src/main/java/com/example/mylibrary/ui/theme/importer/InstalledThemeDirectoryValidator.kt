package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.ThemeManifest
import java.io.File

internal data class ValidatedInstalledThemeDirectory(
    val directory: File,
    val manifest: ThemeManifest,
    val files: Map<String, File>
)

internal class InstalledThemeDirectoryValidator(
    private val codec: ThemePackageJsonCodec = ThemePackageJsonCodec(),
    private val checksumValidator: ThemeChecksumValidator =
        ThemeChecksumValidator(),
    private val consistencyValidator: ThemePackageConsistencyValidator =
        ThemePackageConsistencyValidator()
) {
    suspend fun validate(
        installedDirectory: File
    ): ThemePackageResult<ValidatedInstalledThemeDirectory> {
        val files = when (
            val scan = ThemePackageDirectoryScanner.scan(installedDirectory)
        ) {
            is ThemePackageResult.Success -> scan.value
            is ThemePackageResult.Failure -> return scan
        }
        val checksumsText = when (
            val read = requireNotNull(
                files[ThemePackageLimits.CHECKSUMS_PATH]
            ).readUtf8TextWithLimit(
                limit = ThemePackageLimits.MAX_CHECKSUMS_BYTES,
                tooLarge = { actual, maximum ->
                    ThemePackageError.ChecksumsTooLarge(actual, maximum)
                },
                invalidText = { ThemePackageError.ChecksumsInvalid(it) }
            )
        ) {
            is ThemePackageResult.Success -> read.value
            is ThemePackageResult.Failure -> return read
        }
        val checksums = when (
            val decoded = codec.decodeChecksums(checksumsText)
        ) {
            is ThemePackageResult.Success -> decoded.value
            is ThemePackageResult.Failure -> return decoded
        }
        when (
            val validation = checksumValidator.validate(files, checksums)
        ) {
            is ThemePackageResult.Success -> Unit
            is ThemePackageResult.Failure -> return validation
        }

        val manifestText = when (
            val read = requireNotNull(
                files[ThemePackageLimits.MANIFEST_PATH]
            ).readUtf8TextWithLimit(
                limit = ThemePackageLimits.MAX_MANIFEST_BYTES,
                tooLarge = { actual, maximum ->
                    ThemePackageError.ManifestTooLarge(actual, maximum)
                },
                invalidText = { ThemePackageError.ManifestParseFailed(it) }
            )
        ) {
            is ThemePackageResult.Success -> read.value
            is ThemePackageResult.Failure -> return read
        }
        val manifest = when (
            val decoded = codec.decodeManifest(manifestText)
        ) {
            is ThemePackageResult.Success -> decoded.value
            is ThemePackageResult.Failure -> return decoded
        }
        when (
            val validation = consistencyValidator.validate(manifest, files)
        ) {
            is ThemePackageResult.Success -> Unit
            is ThemePackageResult.Failure -> return validation
        }
        return ThemePackageResult.Success(
            ValidatedInstalledThemeDirectory(
                directory = installedDirectory.canonicalFile,
                manifest = manifest,
                files = files
            )
        )
    }
}
