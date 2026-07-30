package com.example.mylibrary.ui.theme.importer

import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class ThemeChecksumValidator {
    suspend fun validate(
        files: Map<String, File>,
        checksums: ThemeChecksumManifest
    ): ThemePackageResult<Unit> =
        try {
            if (checksums.algorithm != ThemePackageLimits.CHECKSUM_ALGORITHM) {
                failThemePackage(
                    ThemePackageError.ChecksumsInvalid(
                        "Only ${ThemePackageLimits.CHECKSUM_ALGORITHM} is supported"
                    )
                )
            }
            if (ThemePackageLimits.CHECKSUMS_PATH in checksums.files) {
                failThemePackage(
                    ThemePackageError.ChecksumsInvalid(
                        "checksums.json must not include itself"
                    )
                )
            }
            if (ThemePackageLimits.MANIFEST_PATH !in checksums.files) {
                failThemePackage(
                    ThemePackageError.ChecksumEntryMissing(
                        ThemePackageLimits.MANIFEST_PATH
                    )
                )
            }
            val actualPaths = files.keys - ThemePackageLimits.CHECKSUMS_PATH
            val declaredPaths = checksums.files.keys
            (actualPaths - declaredPaths).sorted().firstOrNull()?.let {
                failThemePackage(ThemePackageError.ChecksumEntryMissing(it))
            }
            (declaredPaths - actualPaths).sorted().firstOrNull()?.let {
                failThemePackage(ThemePackageError.ChecksumExtraEntry(it))
            }

            checksums.files.toSortedMap().forEach { (path, expected) ->
                coroutineContext.ensureActive()
                val file = files[path]
                    ?: failThemePackage(
                        ThemePackageError.ChecksumExtraEntry(path)
                    )
                val actual = file.inputStream().buffered().use(::sha256)
                if (actual != expected) {
                    failThemePackage(
                        ThemePackageError.ChecksumMismatch(
                            path = path,
                            expectedSha256 = expected,
                            actualSha256 = actual
                        )
                    )
                }
            }
            ThemePackageResult.Success(Unit)
        } catch (failure: ThemePackageFailureException) {
            ThemePackageResult.Failure(failure.error)
        } catch (exception: Exception) {
            ThemePackageResult.Failure(
                ThemePackageError.ChecksumsInvalid(
                    exception.message ?: exception::class.java.simpleName
                )
            )
        }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }
}

internal fun File.readUtf8TextWithLimit(
    limit: Long,
    tooLarge: (actual: Long, maximum: Long) -> ThemePackageError,
    invalidText: (reason: String) -> ThemePackageError
): ThemePackageResult<String> =
    try {
        val actual = length()
        if (actual > limit) {
            failThemePackage(tooLarge(actual, limit))
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = inputStream().buffered().use { input ->
            InputStreamReader(input, decoder).use { it.readText() }
        }
        ThemePackageResult.Success(text)
    } catch (failure: ThemePackageFailureException) {
        ThemePackageResult.Failure(failure.error)
    } catch (exception: Exception) {
        ThemePackageResult.Failure(
            invalidText(
                exception.message ?: exception::class.java.simpleName
            )
        )
    }
