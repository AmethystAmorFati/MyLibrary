package com.example.mylibrary.ui.theme.importer

import com.example.mylibrary.ui.theme.DefaultThemeManifest
import com.example.mylibrary.ui.theme.ThemeManifest
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ThemePackageTestFixtures {
    val codec = ThemePackageJsonCodec()

    fun minimalManifest(
        id: String = "package.test",
        version: String = "1"
    ): ThemeManifest = DefaultThemeManifest.copy(
        id = id,
        name = "Package Test",
        version = version
    )

    fun writePackage(
        archive: File,
        manifest: ThemeManifest = minimalManifest(),
        resources: Map<String, ByteArray> = emptyMap(),
        includeManifest: Boolean = true,
        includeChecksums: Boolean = true,
        checksumTransform: (MutableMap<String, String>) -> Unit = {},
        rawChecksums: String? = null,
        additionalEntries: List<Pair<String, ByteArray>> = emptyList(),
        stored: Boolean = false
    ) {
        val ordinary = linkedMapOf<String, ByteArray>()
        if (includeManifest) {
            ordinary[ThemePackageLimits.MANIFEST_PATH] =
                codec.encodeManifest(manifest).toByteArray(Charsets.UTF_8)
        }
        ordinary.putAll(resources)
        val checksums = ordinary.mapValuesTo(linkedMapOf()) {
            sha256(it.value)
        }
        checksumTransform(checksums)
        val allEntries = mutableListOf<Pair<String, ByteArray>>()
        allEntries += ordinary.map { it.key to it.value }
        if (includeChecksums) {
            val checksumText = rawChecksums ?: codec.encodeChecksums(
                ThemeChecksumManifest(
                    algorithm = ThemePackageLimits.CHECKSUM_ALGORITHM,
                    files = checksums
                )
            )
            allEntries += ThemePackageLimits.CHECKSUMS_PATH to
                checksumText.toByteArray(Charsets.UTF_8)
        }
        allEntries += additionalEntries

        archive.parentFile?.mkdirs()
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            allEntries.forEach { (path, bytes) ->
                val entry = ZipEntry(path)
                if (stored) {
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    entry.crc = crc32(bytes)
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun crc32(bytes: ByteArray): Long {
        val crc = CRC32()
        crc.update(bytes)
        return crc.value
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun patchFirstCentralEntry(
        archive: File,
        patch: (bytes: ByteArray, offset: Int) -> Unit
    ) {
        val bytes = archive.readBytes()
        val offset = findSignature(bytes, byteArrayOf(0x50, 0x4B, 0x01, 0x02))
        check(offset >= 0)
        patch(bytes, offset)
        archive.writeBytes(bytes)
    }

    fun patchFirstLocalEntry(
        archive: File,
        patch: (bytes: ByteArray, offset: Int) -> Unit
    ) {
        val bytes = archive.readBytes()
        val offset = findSignature(bytes, byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        check(offset >= 0)
        patch(bytes, offset)
        archive.writeBytes(bytes)
    }

    fun patchSecondCentralName(
        archive: File,
        replacement: String
    ) {
        val bytes = archive.readBytes()
        val signature = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
        val first = findSignature(bytes, signature)
        val second = findSignature(bytes, signature, first + 4)
        check(second >= 0)
        val nameLength = bytes.readUInt16(second + 28)
        val encoded = replacement.toByteArray(Charsets.UTF_8)
        check(encoded.size == nameLength)
        encoded.copyInto(bytes, second + 46)
        archive.writeBytes(bytes)
    }

    fun writeUInt16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    fun writeUInt32(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun findSignature(
        bytes: ByteArray,
        signature: ByteArray,
        start: Int = 0
    ): Int {
        for (index in start..bytes.size - signature.size) {
            if (
                signature.indices.all {
                    bytes[index + it] == signature[it]
                }
            ) {
                return index
            }
        }
        return -1
    }

    private fun ByteArray.readUInt16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)
}
