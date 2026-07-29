package com.example.mylibrary.data.image

import com.example.mylibrary.domain.model.CoverStorageLimits
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CoverInputValidatorTest {
    @Test
    fun readsBoundsWithoutDecodingJpegPngWebpAndGif() = withTempDirectory { root ->
        val fixtures = listOf(
            File(root, "cover.jpg").apply { writeBytes(jpeg(320, 480)) },
            File(root, "cover.png").apply { writeBytes(png(640, 960)) },
            File(root, "cover.webp").apply { writeBytes(webpVp8x(800, 1_200)) },
            File(root, "cover.gif").apply { writeBytes(gif(120, 180)) }
        )

        assertEquals(
            listOf(
                Triple("jpg", 320, 480),
                Triple("png", 640, 960),
                Triple("webp", 800, 1_200),
                Triple("gif", 120, 180)
            ),
            fixtures.map {
                val metadata = CoverInputValidator.validate(
                    it,
                    declaredExtension = it.extension
                )
                Triple(metadata.format, metadata.width, metadata.height)
            }
        )
    }

    @Test
    fun byteLimitIsCheckedBeforeImageParsing() = withTempDirectory { root ->
        val oversized = File(root, "oversized.jpg")
        RandomAccessFile(oversized, "rw").use {
            it.setLength(CoverStorageLimits.MAX_SOURCE_BYTES + 1L)
        }

        expectInvalid { CoverInputValidator.validate(oversized) }
    }

    @Test
    fun rejectsSingleSideTotalPixelsExtensionAndCorruption() = withTempDirectory { root ->
        val tooWide = File(root, "wide.png").apply {
            writeBytes(png(8_193, 1))
        }
        val tooManyPixels = File(root, "pixels.png").apply {
            writeBytes(png(8_192, 8_192))
        }
        val mismatched = File(root, "wrong.png").apply {
            writeBytes(jpeg(100, 100))
        }
        val corrupt = File(root, "broken.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        }

        expectInvalid { CoverInputValidator.validate(tooWide) }
        expectInvalid { CoverInputValidator.validate(tooManyPixels) }
        expectInvalid {
            CoverInputValidator.validate(
                mismatched,
                declaredExtension = mismatched.extension
            )
        }
        expectInvalid { CoverInputValidator.validate(corrupt) }
    }

    private fun jpeg(width: Int, height: Int): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xC0.toByte(),
        0x00, 0x0B,
        0x08,
        (height ushr 8).toByte(), height.toByte(),
        (width ushr 8).toByte(), width.toByte(),
        0x01, 0x01, 0x11, 0x00,
        0xFF.toByte(), 0xD9.toByte()
    )

    private fun png(width: Int, height: Int): ByteArray =
        ByteArray(32).apply {
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
            ).copyInto(this)
            putBigEndianInt(16, width)
            putBigEndianInt(20, height)
            "IEND".toByteArray().copyInto(this, 24)
        }

    private fun webpVp8x(width: Int, height: Int): ByteArray =
        ByteArray(30).apply {
            "RIFF".toByteArray().copyInto(this, 0)
            putLittleEndianInt(4, size - 8)
            "WEBP".toByteArray().copyInto(this, 8)
            "VP8X".toByteArray().copyInto(this, 12)
            putLittleEndian24(24, width - 1)
            putLittleEndian24(27, height - 1)
        }

    private fun gif(width: Int, height: Int): ByteArray =
        ByteArray(14).apply {
            "GIF89a".toByteArray().copyInto(this, 0)
            this[6] = width.toByte()
            this[7] = (width ushr 8).toByte()
            this[8] = height.toByte()
            this[9] = (height ushr 8).toByte()
            this[lastIndex] = 0x3B
        }

    private fun ByteArray.putBigEndianInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private fun ByteArray.putLittleEndianInt(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
        this[offset + 2] = (value ushr 16).toByte()
        this[offset + 3] = (value ushr 24).toByte()
    }

    private fun ByteArray.putLittleEndian24(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
        this[offset + 2] = (value ushr 16).toByte()
    }

    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid cover")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("cover-validator-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
