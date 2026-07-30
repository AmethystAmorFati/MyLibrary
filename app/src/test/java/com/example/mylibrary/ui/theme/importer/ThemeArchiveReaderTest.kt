package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.navigation.ThemeIconRendering
import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.NavigationIconDefinition
import com.example.mylibrary.ui.theme.ThemeFontManifest
import com.example.mylibrary.ui.theme.ThemeNavigationManifest
import com.example.mylibrary.ui.theme.ThemeSurfaceDefinition
import com.example.mylibrary.ui.theme.ThemeSurfaceType
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeArchiveReaderTest {
    @Test
    fun packageLimitsFreezeTheV1Envelope() {
        assertEquals(
            64L * 1024L * 1024L,
            ThemePackageLimits.MAX_SOURCE_ARCHIVE_BYTES
        )
        assertEquals(
            64L * 1024L * 1024L,
            ThemePackageLimits.MAX_TOTAL_UNCOMPRESSED_BYTES
        )
        assertEquals(15, ThemePackageLimits.MAX_FILE_ENTRIES)
        assertEquals(32, ThemePackageLimits.MAX_TOTAL_ENTRIES)
        assertEquals(100.0, ThemePackageLimits.MAX_COMPRESSION_RATIO, 0.0)
        assertEquals(256L * 1024L, ThemePackageLimits.MAX_MANIFEST_BYTES)
        assertEquals(256L * 1024L, ThemePackageLimits.MAX_CHECKSUMS_BYTES)
        assertEquals(256, ThemePackageLimits.MAX_PATH_LENGTH)
    }

    @Test
    fun minimalColorPackagePassesArchiveChecksumAndConsistency() =
        withRoot { root ->
            val archive = File(root, "minimal.mylibrarytheme")
            ThemePackageTestFixtures.writePackage(archive)

            val validated = validatePackage(archive, File(root, "staging"))

            assertEquals(
                ThemePackageTestFixtures.minimalManifest(),
                validated
            )
        }

    @Test
    fun completeFifteenFileStructurePassesPackageLevelValidation() =
        withRoot { root ->
            val archive = File(root, "complete.mylibrarytheme")
            val paths = listOf(
                "surfaces/background.jpg",
                "surfaces/card.webp",
                "surfaces/dialog.webp",
                "fonts/font_a.ttf",
                "fonts/font_b.ttf",
                "icons/home.png",
                "icons/home_selected.png",
                "icons/library.png",
                "icons/library_selected.png",
                "icons/statistics.png",
                "icons/statistics_selected.png",
                "icons/settings.png",
                "icons/settings_selected.png"
            )
            val manifest = DefaultThemeManifest.copy(
                id = "package.complete",
                surfaces = DefaultThemeManifest.surfaces.copy(
                    background = image(
                        "#FF000000",
                        "surfaces/background.jpg"
                    ),
                    card = image("#FFFFFFFF", "surfaces/card.webp"),
                    dialog = image("#FFFFFFFF", "surfaces/dialog.webp")
                ),
                fonts = ThemeFontManifest(
                    "fonts/font_a.ttf",
                    "fonts/font_b.ttf"
                ),
                navigationIcons = ThemeNavigationManifest(
                    rendering = ThemeIconRendering.ORIGINAL,
                    home = definition("home"),
                    library = definition("library"),
                    statistics = definition("statistics"),
                    settings = definition("settings")
                )
            )
            ThemePackageTestFixtures.writePackage(
                archive = archive,
                manifest = manifest,
                resources = paths.associateWith { "fixture-$it".toByteArray() }
            )

            assertEquals(
                manifest,
                validatePackage(archive, File(root, "staging"))
            )
        }

    @Test
    fun legacySurfacePathImportsButNewOutputPolicyRequiresCanonicalPath() =
        withRoot { root ->
            val legacyArchive = File(root, "legacy.mylibrarytheme")
            val legacy = DefaultThemeManifest.copy(
                id = "legacy.surface",
                surfaces = DefaultThemeManifest.surfaces.copy(
                    background = image(
                        "#FF000000",
                        "surfaces/background/legacy.png"
                    )
                )
            )
            ThemePackageTestFixtures.writePackage(
                archive = legacyArchive,
                manifest = legacy,
                resources = mapOf(
                    "surfaces/background/legacy.png" to
                        "legacy-fixture".toByteArray()
                )
            )
            val canonical = legacy.copy(
                surfaces = legacy.surfaces.copy(
                    background = image(
                        "#FF000000",
                        "surfaces/background.png"
                    )
                )
            )

            assertEquals(
                legacy,
                validatePackage(
                    legacyArchive,
                    File(root, "legacy-staging")
                )
            )
            assertTrue(
                !ThemePackageAuthoringPolicy
                    .validateCanonicalOutput(legacy)
                    .isValid
            )
            assertTrue(
                ThemePackageAuthoringPolicy
                    .validateCanonicalOutput(canonical)
                    .isValid
            )
        }

    @Test
    fun missingManifestAndChecksumsAreStructuredFailures() = withRoot { root ->
        val missingManifest = File(root, "missing-manifest.zip")
        ThemePackageTestFixtures.writePackage(
            missingManifest,
            includeManifest = false
        )
        val missingChecksums = File(root, "missing-checksums.zip")
        ThemePackageTestFixtures.writePackage(
            missingChecksums,
            includeChecksums = false
        )

        assertFailure<ThemePackageError.MissingManifest>(
            extract(missingManifest, File(root, "out-1"))
        )
        assertFailure<ThemePackageError.MissingChecksums>(
            extract(missingChecksums, File(root, "out-2"))
        )
    }

    @Test
    fun checksumMismatchMissingExtraAndInvalidDigestAreRejected() =
        withRoot { root ->
            val mismatch = File(root, "mismatch.zip")
            ThemePackageTestFixtures.writePackage(
                mismatch,
                checksumTransform = {
                    it[ThemePackageLimits.MANIFEST_PATH] = "0".repeat(64)
                }
            )
            assertFailure<ThemePackageError.ChecksumMismatch>(
                validateChecksums(mismatch, File(root, "out-1"))
            )

            val missing = File(root, "missing-checksum.zip")
            ThemePackageTestFixtures.writePackage(
                missing,
                checksumTransform = {
                    it.remove(ThemePackageLimits.MANIFEST_PATH)
                }
            )
            assertFailure<ThemePackageError.ChecksumEntryMissing>(
                validateChecksums(missing, File(root, "out-2"))
            )

            val extra = File(root, "extra-checksum.zip")
            ThemePackageTestFixtures.writePackage(
                extra,
                checksumTransform = {
                    it["icons/not-present.png"] = "0".repeat(64)
                }
            )
            assertFailure<ThemePackageError.ChecksumExtraEntry>(
                validateChecksums(extra, File(root, "out-3"))
            )

            val invalid = File(root, "invalid-checksum.zip")
            ThemePackageTestFixtures.writePackage(
                invalid,
                checksumTransform = {
                    it[ThemePackageLimits.MANIFEST_PATH] = "ABC"
                }
            )
            assertFailure<ThemePackageError.ChecksumsInvalid>(
                validateChecksums(invalid, File(root, "out-4"))
            )
        }

    @Test
    fun duplicateChecksumJsonKeyIsRejectedBeforeMapConstruction() =
        withRoot { root ->
            val archive = File(root, "duplicate-checksum-key.zip")
            val manifestBytes = ThemePackageTestFixtures.codec
                .encodeManifest(ThemePackageTestFixtures.minimalManifest())
                .toByteArray()
            val digest = ThemePackageTestFixtures.sha256(manifestBytes)
            val raw = """
                {
                  "algorithm":"SHA-256",
                  "files":{
                    "manifest.json":"$digest",
                    "manifest.json":"$digest"
                  }
                }
            """.trimIndent()
            writeRawZip(
                archive,
                listOf(
                    "manifest.json" to manifestBytes,
                    "checksums.json" to raw.toByteArray()
                )
            )

            assertFailure<ThemePackageError.ChecksumsInvalid>(
                validateChecksums(archive, File(root, "out"))
            )
        }

    @Test
    fun zipSlipAbsoluteAndBackslashPathsAreRejected() = withRoot { root ->
        val unsafe = listOf(
            "../escape.png",
            "/absolute.png",
            "icons\\backslash.png"
        )
        unsafe.forEachIndexed { index, path ->
            val archive = File(root, "unsafe-$index.zip")
            writeRawZip(archive, listOf(path to byteArrayOf(1)))
            assertFailure<ThemePackageError.ZipPathInvalid>(
                extract(archive, File(root, "out-$index"))
            )
        }
        assertTrue(!File(root, "escape.png").exists())
    }

    @Test
    fun duplicateAndCaseCollisionEntriesAreRejected() = withRoot { root ->
        val duplicate = File(root, "duplicate.zip")
        writeRawZip(
            duplicate,
            listOf(
                "icons/a.png" to byteArrayOf(1),
                "icons/b.png" to byteArrayOf(2)
            )
        )
        ThemePackageTestFixtures.patchSecondCentralName(
            duplicate,
            "icons/a.png"
        )
        assertFailure<ThemePackageError.DuplicateEntry>(
            extract(duplicate, File(root, "duplicate-out"))
        )

        val collision = File(root, "collision.zip")
        writeRawZip(
            collision,
            listOf(
                "icons/a.png" to byteArrayOf(1),
                "icons/A.png" to byteArrayOf(2)
            )
        )
        assertFailure<ThemePackageError.CaseCollision>(
            extract(collision, File(root, "collision-out"))
        )
    }

    @Test
    fun scriptNestedZipAndUnexpectedMetadataAreRejected() = withRoot { root ->
        listOf(
            "icons/run.js",
            "icons/nested.zip",
            "__MACOSX/metadata"
        ).forEachIndexed { index, path ->
            val archive = File(root, "unexpected-$index.zip")
            writeRawZip(archive, listOf(path to byteArrayOf(1)))
            assertFailure<ThemePackageError.UnexpectedEntry>(
                extract(archive, File(root, "unexpected-out-$index"))
            )
        }
    }

    @Test
    fun fileCountUncompressedSizeAndCompressionRatioLimitsAreEnforced() =
        withRoot { root ->
            val tooMany = File(root, "too-many.zip")
            writeRawZip(
                tooMany,
                (0 until 16).map {
                    "icons/icon_$it.png" to byteArrayOf(it.toByte())
                }
            )
            assertFailure<ThemePackageError.TooManyFiles>(
                extract(tooMany, File(root, "many-out"))
            )

            val tooLarge = File(root, "too-large.zip")
            writeRawZip(
                tooLarge,
                listOf("icons/large.png" to byteArrayOf(1))
            )
            ThemePackageTestFixtures.patchFirstCentralEntry(
                tooLarge
            ) { bytes, offset ->
                ThemePackageTestFixtures.writeUInt32(
                    bytes,
                    offset + 24,
                    ThemePackageLimits.MAX_TOTAL_UNCOMPRESSED_BYTES + 1L
                )
            }
            assertFailure<ThemePackageError.ArchiveUncompressedSizeExceeded>(
                extract(tooLarge, File(root, "large-out"))
            )

            val ratio = File(root, "ratio.zip")
            writeRawZip(
                ratio,
                listOf("icons/ratio.png" to ByteArray(200_000))
            )
            assertFailure<ThemePackageError.CompressionRatioExceeded>(
                extract(ratio, File(root, "ratio-out"))
            )
        }

    @Test
    fun encryptedAndUnixSymlinkEntriesAreRejectedFromCentralMetadata() =
        withRoot { root ->
            val encrypted = File(root, "encrypted.zip")
            writeRawZip(
                encrypted,
                listOf("icons/encrypted.png" to byteArrayOf(1, 2, 3))
            )
            ThemePackageTestFixtures.patchFirstCentralEntry(
                encrypted
            ) { bytes, offset ->
                val flags = readUInt16(bytes, offset + 8)
                ThemePackageTestFixtures.writeUInt16(
                    bytes,
                    offset + 8,
                    flags or 1
                )
            }
            ThemePackageTestFixtures.patchFirstLocalEntry(
                encrypted
            ) { bytes, offset ->
                val flags = readUInt16(bytes, offset + 6)
                ThemePackageTestFixtures.writeUInt16(
                    bytes,
                    offset + 6,
                    flags or 1
                )
            }
            assertFailure<ThemePackageError.EncryptedZipUnsupported>(
                extract(encrypted, File(root, "encrypted-out"))
            )

            val symlink = File(root, "symlink.zip")
            writeRawZip(
                symlink,
                listOf("icons/link.png" to "target".toByteArray())
            )
            ThemePackageTestFixtures.patchFirstCentralEntry(
                symlink
            ) { bytes, offset ->
                ThemePackageTestFixtures.writeUInt16(
                    bytes,
                    offset + 4,
                    (3 shl 8) or 20
                )
                ThemePackageTestFixtures.writeUInt32(
                    bytes,
                    offset + 38,
                    0xA1FFL shl 16
                )
            }
            assertFailure<ThemePackageError.UnsupportedEntryType>(
                extract(symlink, File(root, "symlink-out"))
            )
        }

    @Test
    fun manifestMissingReferenceAndUnreferencedResourceAreRejected() =
        withRoot { root ->
            val missing = File(root, "missing-resource.zip")
            val manifest = DefaultThemeManifest.copy(
                surfaces = DefaultThemeManifest.surfaces.copy(
                    background = image(
                        "#FF000000",
                        "surfaces/background.png"
                    )
                )
            )
            ThemePackageTestFixtures.writePackage(missing, manifest)
            assertFailure<ThemePackageError.ManifestResourceMissing>(
                validateConsistency(missing, File(root, "missing-out"))
            )

            val extra = File(root, "extra-resource.zip")
            ThemePackageTestFixtures.writePackage(
                extra,
                resources = mapOf(
                    "icons/unreferenced.png" to "unused".toByteArray()
                )
            )
            assertFailure<ThemePackageError.ManifestResourceExtra>(
                validateConsistency(extra, File(root, "extra-out"))
            )
        }

    private fun validatePackage(archive: File, staging: File) =
        runBlocking {
            val extracted = extract(archive, staging).success()
            validateChecksums(extracted).success()
            val manifest = decodeManifest(extracted).success()
            ThemePackageConsistencyValidator()
                .validate(manifest, extracted.files)
                .success()
            manifest
        }

    private fun validateChecksums(
        archive: File,
        staging: File
    ): ThemePackageResult<Unit> = runBlocking {
        val extracted = when (val result = extract(archive, staging)) {
            is ThemePackageResult.Success -> result.value
            is ThemePackageResult.Failure -> return@runBlocking result
        }
        validateChecksums(extracted)
    }

    private suspend fun validateChecksums(
        extracted: ExtractedThemeArchive
    ): ThemePackageResult<Unit> {
        val decoded = decodeChecksums(extracted)
        if (decoded is ThemePackageResult.Failure) return decoded
        return ThemeChecksumValidator().validate(
            extracted.files,
            (decoded as ThemePackageResult.Success).value
        )
    }

    private fun validateConsistency(
        archive: File,
        staging: File
    ): ThemePackageResult<Unit> = runBlocking {
        val extracted = extract(archive, staging).success()
        validateChecksums(extracted).success()
        val manifest = decodeManifest(extracted).success()
        ThemePackageConsistencyValidator().validate(
            manifest,
            extracted.files
        )
    }

    private fun decodeManifest(
        extracted: ExtractedThemeArchive
    ): ThemePackageResult<com.example.mylibrary.ui.theme.ThemeManifest> {
        val text = extracted.files.getValue("manifest.json").readText()
        return ThemePackageTestFixtures.codec.decodeManifest(text)
    }

    private fun decodeChecksums(
        extracted: ExtractedThemeArchive
    ): ThemePackageResult<ThemeChecksumManifest> {
        val text = extracted.files.getValue("checksums.json").readText()
        return ThemePackageTestFixtures.codec.decodeChecksums(text)
    }

    private fun extract(
        archive: File,
        staging: File
    ): ThemePackageResult<ExtractedThemeArchive> =
        runBlocking { ThemeArchiveReader().extract(archive, staging) }

    private fun definition(role: String) = NavigationIconDefinition(
        normal = "icons/$role.png",
        selected = "icons/${role}_selected.png"
    )

    private fun image(color: String, path: String) =
        ThemeSurfaceDefinition(
            type = ThemeSurfaceType.IMAGE,
            color = color,
            file = path
        )

    private fun writeRawZip(
        archive: File,
        entries: List<Pair<String, ByteArray>>
    ) {
        ZipOutputStream(archive.outputStream()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private inline fun <reified T : ThemePackageError> assertFailure(
        result: ThemePackageResult<*>
    ) {
        assertTrue(result is ThemePackageResult.Failure)
        val error = (result as ThemePackageResult.Failure).error
        assertTrue(
            "Expected ${T::class.java.simpleName}, got $error",
            error is T
        )
    }

    private fun <T> ThemePackageResult<T>.success(): T {
        assertTrue(
            "Expected success, got ${(this as? ThemePackageResult.Failure)?.error}",
            this is ThemePackageResult.Success
        )
        return (this as ThemePackageResult.Success).value
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("theme-package-reader").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
