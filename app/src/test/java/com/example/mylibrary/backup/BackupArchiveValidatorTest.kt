package com.example.mylibrary.backup

import com.example.mylibrary.backup.model.BACKUP_FORMAT
import com.example.mylibrary.backup.model.BACKUP_ROOT
import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupArchiveLimits
import com.example.mylibrary.backup.model.BackupFileInfo
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.model.BackupManifest
import com.example.mylibrary.backup.model.BackupPreferences
import com.example.mylibrary.backup.model.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.mylibrary.backup.serialization.BackupJsonCodec
import com.example.mylibrary.backup.validation.BackupArchiveValidator
import com.example.mylibrary.backup.validation.NewerBackupVersionException
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupArchiveValidatorTest {
    private val codec = BackupJsonCodec()

    @Test
    fun validatesManifestDigestAndLogicalData() = withTemporaryDirectory { root ->
        val archive = File(root, "valid.zip")
        val data = minimalData()
        writeArchive(archive, data)

        val validated = runBlocking {
            BackupArchiveValidator().validate(archive, File(root, "extract"))
        }

        assertEquals(1, validated.data.itemTypes.count { it.name == "Book" })
        assertEquals(1, validated.data.itemTypes.count { it.name == "Movie" })
    }

    @Test
    fun rejectsShaMismatchWithoutProducingValidatedData() = withTemporaryDirectory { root ->
        val archive = File(root, "bad-sha.zip")
        writeArchive(archive, minimalData(), declaredSha = "0".repeat(64))

        runBlocking {
            try {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
                fail("Expected digest validation failure")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("digest"))
            }
        }
    }

    @Test
    fun rejectsZipSlipEntry() = withTemporaryDirectory { root ->
        val archive = File(root, "zip-slip.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }

        runBlocking {
            try {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
                fail("Expected unsafe ZIP path failure")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
        assertTrue(!File(root, "escape.txt").exists())
    }

    @Test
    fun identifiesBackupFromNewerAppVersion() = withTemporaryDirectory { root ->
        val archive = File(root, "newer.zip")
        val manifest = BackupManifest(
            format = BACKUP_FORMAT,
            backupSchemaVersion = CURRENT_BACKUP_SCHEMA_VERSION + 1,
            createdAt = OffsetDateTime.now().toString(),
            appVersionName = "2.0",
            appVersionCode = 2,
            databaseVersion = 12,
            counts = emptyMap(),
            files = emptyList()
        )
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
            zip.write(codec.encodeManifest(manifest).toByteArray())
            zip.closeEntry()
        }

        runBlocking {
            try {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
                fail("Expected newer version failure")
            } catch (_: NewerBackupVersionException) {
                // Expected.
            }
        }
    }

    @Test
    fun rejectsCorruptZipAndMissingManifest() = withTemporaryDirectory { root ->
        val corrupt = File(root, "corrupt.zip").apply {
            writeBytes("not a ZIP archive".toByteArray())
        }
        assertTrue(
            runCatching {
                runBlocking {
                    BackupArchiveValidator().validate(corrupt, File(root, "corrupt-out"))
                }
            }.isFailure
        )

        val missingManifest = File(root, "missing-manifest.zip")
        ZipOutputStream(missingManifest.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}data.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        assertTrue(
            runCatching {
                runBlocking {
                    BackupArchiveValidator().validate(
                        missingManifest,
                        File(root, "missing-out")
                    )
                }
            }.isFailure
        )
    }

    @Test
    fun rejectsWrongFormatAndCorruptDataJson() = withTemporaryDirectory { root ->
        val wrongFormat = File(root, "wrong-format.zip")
        writeArchive(wrongFormat, minimalData(), format = "another-format")
        assertTrue(
            runCatching {
                runBlocking {
                    BackupArchiveValidator().validate(
                        wrongFormat,
                        File(root, "format-out")
                    )
                }
            }.isFailure
        )

        val corruptJson = File(root, "corrupt-json.zip")
        writeArchive(corruptJson, minimalData(), dataBytesOverride = "{broken".toByteArray())
        assertTrue(
            runCatching {
                runBlocking {
                    BackupArchiveValidator().validate(
                        corruptJson,
                        File(root, "json-out")
                    )
                }
            }.isFailure
        )
    }

    @Test
    fun rejectsEntryBeyondCentralizedSizeLimit() = withTemporaryDirectory { root ->
        val archive = File(root, "oversized.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
            zip.write(ByteArray(BackupArchiveLimits.MAX_MANIFEST_BYTES.toInt() + 1))
            zip.closeEntry()
        }

        assertTrue(
            runCatching {
                runBlocking {
                    BackupArchiveValidator().validate(archive, File(root, "oversized-out"))
                }
            }.isFailure
        )
    }

    // -------------------------------------------------------------------------
    // Theme backup validation tests (v5)
    // -------------------------------------------------------------------------

    @Test
    fun validV5ArchiveWithThemesValidatesSuccessfully() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v5-with-themes.zip")
            val themeContent = "{\"id\":\"theme.alpha\"}".toByteArray()
            val themeChecksums = "{}".toByteArray()
            val extraFiles = mapOf(
                "themes/theme.alpha/manifest.json" to themeContent,
                "themes/theme.alpha/checksums.json" to themeChecksums
            )
            writeArchive(
                archive,
                minimalData(),
                schemaVersion = 5,
                extraFiles = extraFiles,
                preferences = BackupPreferences(currentThemeId = "theme.alpha")
            )

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertEquals(5, validated.manifest.backupSchemaVersion)
            assertTrue(validated.shouldRestoreThemePreference)
            assertEquals(
                "theme.alpha",
                validated.preferences?.currentThemeId
            )
            assertEquals(1, validated.themeDirectories.size)
            assertTrue("theme.alpha" in validated.themeDirectories)
            val themeDir = validated.themeDirectories["theme.alpha"]
            assertNotNull(themeDir)
            assertTrue(File(themeDir!!, "manifest.json").isFile)
            assertTrue(File(themeDir, "checksums.json").isFile)
        }

    @Test
    fun rejectsPathTraversalInThemeDirectory() = withTemporaryDirectory { root ->
        val archive = File(root, "theme-traversal.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            val manifest = BackupManifest(
                format = BACKUP_FORMAT,
                backupSchemaVersion = 5,
                createdAt = OffsetDateTime.now().toString(),
                appVersionName = "1.0",
                appVersionCode = 1,
                databaseVersion = 10,
                counts = emptyMap(),
                files = emptyList()
            )
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
            zip.write(codec.encodeManifest(manifest).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}themes/../../escape.txt"))
            zip.write("escaped".toByteArray())
            zip.closeEntry()
        }

        assertTrue(
            runCatching {
                runBlocking {
                    BackupArchiveValidator().validate(
                        archive,
                        File(root, "extract")
                    )
                }
            }.isFailure
        )
        assertTrue(!File(root, "escape.txt").exists())
    }

    @Test
    fun rejectsOversizedThemeFile() = withTemporaryDirectory { root ->
        val archive = File(root, "oversized-theme.zip")
        val oversizedContent = ByteArray(
            BackupArchiveLimits.MAX_THEME_FILE_BYTES.toInt() + 1
        )
        val extraFiles = mapOf(
            "themes/theme.big/manifest.json" to oversizedContent
        )
        writeArchive(
            archive,
            minimalData(),
            schemaVersion = 5,
            extraFiles = extraFiles
        )

        assertTrue(
            runCatching {
                runBlocking {
                    BackupArchiveValidator().validate(
                        archive,
                        File(root, "extract")
                    )
                }
            }.isFailure
        )
    }

    @Test
    fun rejectsThemePathWithInvalidId() = withTemporaryDirectory { root ->
        val archive = File(root, "invalid-theme-id.zip")
        val content = "{}".toByteArray()
        val extraFiles = mapOf(
            "themes/Invalid ID!/manifest.json" to content
        )
        writeArchive(
            archive,
            minimalData(),
            schemaVersion = 5,
            extraFiles = extraFiles
        )

        assertTrue(
            runCatching {
                runBlocking {
                    BackupArchiveValidator().validate(
                        archive,
                        File(root, "extract")
                    )
                }
            }.isFailure
        )
    }

    @Test
    fun oldV1BackupWithoutThemesValidatesSuccessfully() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v1.zip")
            writeArchive(archive, minimalData(), schemaVersion = 1)

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertEquals(1, validated.manifest.backupSchemaVersion)
            assertTrue(validated.themeDirectories.isEmpty())
            assertFalse(validated.shouldRestoreThemePreference)
        }

    @Test
    fun oldV4BackupWithoutThemesValidatesSuccessfully() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v4.zip")
            writeArchive(archive, minimalData(), schemaVersion = 4)

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertEquals(4, validated.manifest.backupSchemaVersion)
            assertTrue(validated.themeDirectories.isEmpty())
            assertFalse(validated.shouldRestoreThemePreference)
        }

    @Test
    fun v5ArchiveWithoutThemesValidatesSuccessfully() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v5-no-themes.zip")
            writeArchive(
                archive,
                minimalData(),
                schemaVersion = 5,
                preferences = BackupPreferences(currentThemeId = null)
            )

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertEquals(5, validated.manifest.backupSchemaVersion)
            assertTrue(validated.themeDirectories.isEmpty())
            assertTrue(validated.shouldRestoreThemePreference)
        }

    @Test
    fun multipleThemeDirectoriesCollected() = withTemporaryDirectory { root ->
        val archive = File(root, "multi-themes.zip")
        val extraFiles = mapOf(
            "themes/theme.alpha/manifest.json" to "{\"id\":\"theme.alpha\"}".toByteArray(),
            "themes/theme.alpha/checksums.json" to "{}".toByteArray(),
            "themes/theme.beta/manifest.json" to "{\"id\":\"theme.beta\"}".toByteArray(),
            "themes/theme.beta/checksums.json" to "{}".toByteArray()
        )
        writeArchive(
            archive,
            minimalData(),
            schemaVersion = 5,
            extraFiles = extraFiles,
            preferences = BackupPreferences(currentThemeId = null)
        )

        val validated = runBlocking {
            BackupArchiveValidator().validate(archive, File(root, "extract"))
        }

        assertEquals(2, validated.themeDirectories.size)
        assertTrue("theme.alpha" in validated.themeDirectories)
        assertTrue("theme.beta" in validated.themeDirectories)
    }

    @Test
    fun themeDirectoryEntryWithNestedSubdirectoriesValidates() =
        withTemporaryDirectory { root ->
            val archive = File(root, "nested-theme.zip")
            val extraFiles = mapOf(
                "themes/theme.nested/manifest.json" to "{}".toByteArray(),
                "themes/theme.nested/surfaces/bg.png" to ByteArray(10),
                "themes/theme.nested/fonts/fontA.ttf" to ByteArray(10),
                "themes/theme.nested/navigation/home.svg" to ByteArray(10)
            )
            writeArchive(
                archive,
                minimalData(),
                schemaVersion = 5,
                extraFiles = extraFiles,
                preferences = BackupPreferences(currentThemeId = null)
            )

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertEquals(1, validated.themeDirectories.size)
            val themeDir = validated.themeDirectories["theme.nested"]
            assertNotNull(themeDir)
            assertTrue(File(themeDir!!, "manifest.json").isFile)
            assertTrue(File(themeDir, "surfaces/bg.png").isFile)
            assertTrue(File(themeDir, "fonts/fontA.ttf").isFile)
            assertTrue(File(themeDir, "navigation/home.svg").isFile)
        }

    // -------------------------------------------------------------------------
    // Cumulative limit tests for themes/
    // -------------------------------------------------------------------------

    /**
     * Verifies that the archive-wide [BackupArchiveLimits.MAX_TOTAL_EXTRACTED_BYTES]
     * limit naturally covers theme files: when multiple theme files are each
     * under the per-file limit ([BackupArchiveLimits.MAX_THEME_FILE_BYTES])
     * but their cumulative uncompressed size exceeds the total limit, the
     * archive is rejected during extraction.
     *
     * The per-file limit (32 MiB) and per-type limits (fonts, backgrounds,
     * navigation icons enforced by ThemeInstaller during restore) are NOT
     * replaced by this check—they remain independently enforced.  This test
     * only confirms that the existing total-size guard also catches themes/.
     */
    @Test
    fun rejectsBackupExceedingTotalExtractedBytesWithThemes() =
        withTemporaryDirectory { root ->
            val archive = File(root, "oversized-total.zip")
            // 17 files × 31 MiB = 527 MiB > 512 MiB (MAX_TOTAL_EXTRACTED_BYTES).
            // Each file is under the 32 MiB per-file limit.
            val fileCount = 17
            val fileSizeMiB = 31
            val chunk = ByteArray(1024 * 1024) // 1 MiB of zeros (highly compressible)

            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                // Minimal manifest (extraction fails before manifest validation)
                val manifest = BackupManifest(
                    format = BACKUP_FORMAT,
                    backupSchemaVersion = 5,
                    createdAt = OffsetDateTime.now().toString(),
                    appVersionName = "1.0",
                    appVersionCode = 1,
                    databaseVersion = 10,
                    counts = emptyMap(),
                    files = emptyList()
                )
                zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
                zip.write(codec.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Minimal data.json
                val dataBytes = codec.encodeData(minimalData())
                    .toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("${BACKUP_ROOT}data.json"))
                zip.write(dataBytes)
                zip.closeEntry()

                // 17 theme files of 31 MiB each (all zeros)
                for (i in 0 until fileCount) {
                    zip.putNextEntry(
                        ZipEntry("${BACKUP_ROOT}themes/theme.$i/data.bin")
                    )
                    repeat(fileSizeMiB) { zip.write(chunk) }
                    zip.closeEntry()
                }
            }

            assertTrue(
                runCatching {
                    runBlocking {
                        BackupArchiveValidator().validate(
                            archive,
                            File(root, "extract")
                        )
                    }
                }.isFailure
            )
        }

    /**
     * Verifies that the archive-wide [BackupArchiveLimits.MAX_ENTRY_COUNT]
     * limit naturally covers theme file entries.
     */
    @Test
    fun rejectsBackupExceedingEntryCountWithThemes() =
        withTemporaryDirectory { root ->
            val archive = File(root, "too-many-entries.zip")
            // MAX_ENTRY_COUNT + 1 entries: manifest.json + data.json +
            // (MAX_ENTRY_COUNT - 1) theme file entries.
            val themeEntryCount = BackupArchiveLimits.MAX_ENTRY_COUNT - 1

            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                val manifest = BackupManifest(
                    format = BACKUP_FORMAT,
                    backupSchemaVersion = 5,
                    createdAt = OffsetDateTime.now().toString(),
                    appVersionName = "1.0",
                    appVersionCode = 1,
                    databaseVersion = 10,
                    counts = emptyMap(),
                    files = emptyList()
                )
                zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
                zip.write(codec.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                val dataBytes = codec.encodeData(minimalData())
                    .toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("${BACKUP_ROOT}data.json"))
                zip.write(dataBytes)
                zip.closeEntry()

                for (i in 0 until themeEntryCount) {
                    zip.putNextEntry(
                        ZipEntry("${BACKUP_ROOT}themes/theme.bulk/f$i.bin")
                    )
                    zip.write(ByteArray(1))
                    zip.closeEntry()
                }
            }

            assertTrue(
                runCatching {
                    runBlocking {
                        BackupArchiveValidator().validate(
                            archive,
                            File(root, "extract")
                        )
                    }
                }.isFailure
            )
        }

    /**
     * Confirms that a single valid theme with a file well under the per-file
     * limit passes validation, demonstrating that the cumulative limits do not
     * over-restrict normal backups.
     */
    @Test
    fun singleValidThemeUnderLimitsPassesValidation() =
        withTemporaryDirectory { root ->
            val archive = File(root, "single-valid-theme.zip")
            val extraFiles = mapOf(
                "themes/theme.valid/manifest.json" to
                    "{\"id\":\"theme.valid\"}".toByteArray(),
                "themes/theme.valid/checksums.json" to
                    "{}".toByteArray()
            )
            writeArchive(
                archive,
                minimalData(),
                schemaVersion = 5,
                extraFiles = extraFiles,
                preferences = BackupPreferences(currentThemeId = "theme.valid")
            )

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertTrue(validated.shouldRestoreThemePreference)
            assertEquals(1, validated.themeDirectories.size)
            assertTrue("theme.valid" in validated.themeDirectories)
        }

    // -------------------------------------------------------------------------
    // v5 preferences.json / currentThemeId key presence tests
    // -------------------------------------------------------------------------

    /**
     * v5 backup without preferences.json must be rejected—not silently
     * treated as "use default theme".
     */
    @Test
    fun rejectsV5BackupWithoutPreferencesJson() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v5-no-prefs.zip")
            // preferences = null means no preferences.json is written
            writeArchive(archive, minimalData(), schemaVersion = 5)

            assertTrue(
                runCatching {
                    runBlocking {
                        BackupArchiveValidator().validate(
                            archive,
                            File(root, "extract")
                        )
                    }
                }.isFailure
            )
        }

    /**
     * v5 backup with preferences.json but without the currentThemeId key
     * must be rejected—not silently treated as "use default theme".
     */
    @Test
    fun rejectsV5BackupWithoutCurrentThemeIdKey() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v5-no-key.zip")
            // Build preferences.json manually without currentThemeId
            val rawPrefs = """
                {
                    "useGridLayout": true,
                    "libraryViewMode": "shelf",
                    "gridColumns": 4,
                    "coverColumns": 4,
                    "timelineShowCreator": false,
                    "timelineShowRating": false,
                    "timelineShowStatus": false,
                    "listDisplayFields": ["creator"]
                }
            """.trimIndent().toByteArray(Charsets.UTF_8)
            val extraFiles = mapOf<String, ByteArray>()
            writeArchiveWithRawPreferences(
                archive,
                minimalData(),
                schemaVersion = 5,
                rawPreferencesBytes = rawPrefs,
                extraFiles = extraFiles
            )

            assertTrue(
                runCatching {
                    runBlocking {
                        BackupArchiveValidator().validate(
                            archive,
                            File(root, "extract")
                        )
                    }
                }.isFailure
            )
        }

    /**
     * v5 backup with currentThemeId explicitly set to JSON null must
     * validate successfully and indicate that the default theme should
     * be applied.
     */
    @Test
    fun v5WithExplicitNullCurrentThemeIdValidatesSuccessfully() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v5-null-theme.zip")
            writeArchive(
                archive,
                minimalData(),
                schemaVersion = 5,
                preferences = BackupPreferences(currentThemeId = null)
            )

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertTrue(validated.shouldRestoreThemePreference)
            assertNull(validated.preferences?.currentThemeId)
        }

    /**
     * v5 backup with a non-null currentThemeId must validate successfully
     * and carry the theme ID through for restoration.
     */
    @Test
    fun v5WithStringCurrentThemeIdValidatesSuccessfully() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v5-string-theme.zip")
            val extraFiles = mapOf(
                "themes/theme.alpha/manifest.json" to
                    "{\"id\":\"theme.alpha\"}".toByteArray(),
                "themes/theme.alpha/checksums.json" to
                    "{}".toByteArray()
            )
            writeArchive(
                archive,
                minimalData(),
                schemaVersion = 5,
                extraFiles = extraFiles,
                preferences = BackupPreferences(currentThemeId = "theme.alpha")
            )

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertTrue(validated.shouldRestoreThemePreference)
            assertEquals("theme.alpha", validated.preferences?.currentThemeId)
        }

    /**
     * v4 backup without the currentThemeId key in preferences.json must
     * still validate successfully—v1–v4 do not carry theme semantics.
     */
    @Test
    fun v4WithoutCurrentThemeIdKeyStillValidates() =
        withTemporaryDirectory { root ->
            val archive = File(root, "v4-no-key.zip")
            // Build preferences.json manually without currentThemeId
            val rawPrefs = """
                {
                    "useGridLayout": true,
                    "libraryViewMode": "shelf",
                    "gridColumns": 4,
                    "coverColumns": 4,
                    "timelineShowCreator": false,
                    "timelineShowRating": false,
                    "timelineShowStatus": false,
                    "listDisplayFields": ["creator"]
                }
            """.trimIndent().toByteArray(Charsets.UTF_8)
            writeArchiveWithRawPreferences(
                archive,
                minimalData(),
                schemaVersion = 4,
                rawPreferencesBytes = rawPrefs,
                extraFiles = emptyMap()
            )

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertEquals(4, validated.manifest.backupSchemaVersion)
            assertFalse(validated.shouldRestoreThemePreference)
        }

    // -------------------------------------------------------------------------
    // 32 MiB theme file limit boundary tests
    // -------------------------------------------------------------------------

    /**
     * A theme file exactly at the 32 MiB limit (matching the maximum legal
     * font file size) must pass extraction validation.  This confirms the
     * backup limit does not reject any legal theme resource.
     */
    @Test
    fun themeFileAt32MiBLimitPassesExtraction() =
        withTemporaryDirectory { root ->
            val archive = File(root, "exact-32mib.zip")
            val exactLimit = BackupArchiveLimits.MAX_THEME_FILE_BYTES.toInt()
            val content = ByteArray(exactLimit) // exactly 32 MiB
            val extraFiles = mapOf(
                "themes/theme.font/manifest.json" to
                    "{\"id\":\"theme.font\"}".toByteArray(),
                "themes/theme.font/checksums.json" to
                    "{}".toByteArray(),
                "themes/theme.font/fonts/fontA.ttf" to content
            )
            writeArchive(
                archive,
                minimalData(),
                schemaVersion = 5,
                extraFiles = extraFiles,
                preferences = BackupPreferences(currentThemeId = "theme.font")
            )

            val validated = runBlocking {
                BackupArchiveValidator().validate(archive, File(root, "extract"))
            }

            assertTrue(validated.shouldRestoreThemePreference)
            assertEquals(1, validated.themeDirectories.size)
        }

    /**
     * A theme file one byte over the 32 MiB limit must be rejected by the
     * extraction guard.
     */
    @Test
    fun themeFileOver32MiBLimitRejected() =
        withTemporaryDirectory { root ->
            val archive = File(root, "over-32mib.zip")
            val overLimit = BackupArchiveLimits.MAX_THEME_FILE_BYTES.toInt() + 1
            val content = ByteArray(overLimit)
            val extraFiles = mapOf(
                "themes/theme.big/manifest.json" to
                    "{\"id\":\"theme.big\"}".toByteArray(),
                "themes/theme.big/checksums.json" to
                    "{}".toByteArray(),
                "themes/theme.big/fonts/fontA.ttf" to content
            )
            writeArchive(
                archive,
                minimalData(),
                schemaVersion = 5,
                extraFiles = extraFiles,
                preferences = BackupPreferences(currentThemeId = "theme.big")
            )

            assertTrue(
                runCatching {
                    runBlocking {
                        BackupArchiveValidator().validate(
                            archive,
                            File(root, "extract")
                        )
                    }
                }.isFailure
            )
        }

    private fun writeArchive(
        archive: File,
        data: BackupData,
        declaredSha: String? = null,
        format: String = BACKUP_FORMAT,
        dataBytesOverride: ByteArray? = null,
        schemaVersion: Int = 1,
        extraFiles: Map<String, ByteArray> = emptyMap(),
        preferences: BackupPreferences? = null
    ) {
        val dataBytes = dataBytesOverride
            ?: codec.encodeData(data).toByteArray(Charsets.UTF_8)
        val dataInfo = BackupFileInfo(
            path = "data.json",
            size = dataBytes.size.toLong(),
            sha256 = declaredSha ?: sha256(dataBytes)
        )
        val allFiles = mutableListOf(dataInfo)
        val preferencesBytes = preferences?.let {
            codec.encodePreferences(it).toByteArray(Charsets.UTF_8)
        }
        if (preferencesBytes != null) {
            allFiles += BackupFileInfo(
                path = "preferences.json",
                size = preferencesBytes.size.toLong(),
                sha256 = sha256(preferencesBytes)
            )
        }
        extraFiles.forEach { (path, content) ->
            allFiles += BackupFileInfo(
                path = path,
                size = content.size.toLong(),
                sha256 = sha256(content)
            )
        }
        val manifest = BackupManifest(
            format = format,
            backupSchemaVersion = schemaVersion,
            createdAt = OffsetDateTime.now().toString(),
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseVersion = 10,
            counts = data.counts(0),
            files = allFiles
        )
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
            zip.write(codec.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}data.json"))
            zip.write(dataBytes)
            zip.closeEntry()
            if (preferencesBytes != null) {
                zip.putNextEntry(ZipEntry("${BACKUP_ROOT}preferences.json"))
                zip.write(preferencesBytes)
                zip.closeEntry()
            }
            extraFiles.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry("${BACKUP_ROOT}$path"))
                zip.write(content)
                zip.closeEntry()
            }
        }
    }

    /**
     * Writes an archive with raw (pre-serialized) preferences.json bytes,
     * allowing tests to craft preferences JSON that omits specific keys.
     */
    private fun writeArchiveWithRawPreferences(
        archive: File,
        data: BackupData,
        schemaVersion: Int,
        rawPreferencesBytes: ByteArray,
        extraFiles: Map<String, ByteArray>
    ) {
        val dataBytes = codec.encodeData(data).toByteArray(Charsets.UTF_8)
        val allFiles = mutableListOf(
            BackupFileInfo(
                path = "data.json",
                size = dataBytes.size.toLong(),
                sha256 = sha256(dataBytes)
            ),
            BackupFileInfo(
                path = "preferences.json",
                size = rawPreferencesBytes.size.toLong(),
                sha256 = sha256(rawPreferencesBytes)
            )
        )
        extraFiles.forEach { (path, content) ->
            allFiles += BackupFileInfo(
                path = path,
                size = content.size.toLong(),
                sha256 = sha256(content)
            )
        }
        val manifest = BackupManifest(
            format = BACKUP_FORMAT,
            backupSchemaVersion = schemaVersion,
            createdAt = OffsetDateTime.now().toString(),
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseVersion = 10,
            counts = data.counts(0),
            files = allFiles
        )
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
            zip.write(codec.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}data.json"))
            zip.write(dataBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}preferences.json"))
            zip.write(rawPreferencesBytes)
            zip.closeEntry()
            extraFiles.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry("${BACKUP_ROOT}$path"))
                zip.write(content)
                zip.closeEntry()
            }
        }
    }

    private fun minimalData() = BackupData(
        itemTypes = listOf(BackupItemType(1, "Book", 0)),
        statuses = emptyList(),
        fieldDefinitions = emptyList(),
        tags = emptyList(),
        items = emptyList(),
        records = emptyList(),
        activities = emptyList(),
        itemTags = emptyList(),
        fieldValues = emptyList(),
        quotes = emptyList()
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("mylibrary-backup-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
