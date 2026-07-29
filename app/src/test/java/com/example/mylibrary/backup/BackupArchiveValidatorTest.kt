package com.example.mylibrary.backup

import com.example.mylibrary.backup.model.BACKUP_FORMAT
import com.example.mylibrary.backup.model.BACKUP_ROOT
import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupArchiveLimits
import com.example.mylibrary.backup.model.BackupFileInfo
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.model.BackupManifest
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

    private fun writeArchive(
        archive: File,
        data: BackupData,
        declaredSha: String? = null,
        format: String = BACKUP_FORMAT,
        dataBytesOverride: ByteArray? = null
    ) {
        val dataBytes = dataBytesOverride
            ?: codec.encodeData(data).toByteArray(Charsets.UTF_8)
        val dataInfo = BackupFileInfo(
            path = "data.json",
            size = dataBytes.size.toLong(),
            sha256 = declaredSha ?: sha256(dataBytes)
        )
        val manifest = BackupManifest(
            format = format,
            backupSchemaVersion = 1,
            createdAt = OffsetDateTime.now().toString(),
            appVersionName = "1.0",
            appVersionCode = 1,
            databaseVersion = 10,
            counts = data.counts(0),
            files = listOf(dataInfo)
        )
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}manifest.json"))
            zip.write(codec.encodeManifest(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${BACKUP_ROOT}data.json"))
            zip.write(dataBytes)
            zip.closeEntry()
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
