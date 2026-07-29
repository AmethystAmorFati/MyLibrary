package com.example.mylibrary.data.image

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

internal data class CoverDimensions(
    val width: Int,
    val height: Int
)

internal object CoverImageDimensions {
    fun read(file: File, format: String): CoverDimensions? = runCatching {
        when (format) {
            "jpg" -> readJpeg(file)
            "png" -> RandomAccessFile(file, "r").use { input ->
                input.seek(16)
                CoverDimensions(input.readInt(), input.readInt())
            }
            "gif" -> RandomAccessFile(file, "r").use { input ->
                input.seek(6)
                CoverDimensions(input.readLittleEndianShort(), input.readLittleEndianShort())
            }
            "webp" -> readWebp(file)
            else -> null
        }
    }.getOrNull()?.takeIf { it.width > 0 && it.height > 0 }

    private fun readJpeg(file: File): CoverDimensions? =
        BufferedInputStream(FileInputStream(file)).use { input ->
            if (input.read() != 0xFF || input.read() != 0xD8) return@use null
            while (true) {
                var prefix = input.read()
                while (prefix != -1 && prefix != 0xFF) prefix = input.read()
                if (prefix == -1) return@use null
                var marker = input.read()
                while (marker == 0xFF) marker = input.read()
                if (marker == -1 || marker == 0xD9 || marker == 0xDA) return@use null
                if (marker == 0x01 || marker in 0xD0..0xD7) continue
                val length = input.readBigEndianShort()
                if (length < 2) return@use null
                if (marker in JPEG_START_OF_FRAME_MARKERS) {
                    if (length < 7) return@use null
                    input.read() // sample precision
                    val height = input.readBigEndianShort()
                    val width = input.readBigEndianShort()
                    return@use CoverDimensions(width, height)
                }
                input.skipFully(length - 2L)
            }
            null
        }

    private fun readWebp(file: File): CoverDimensions? =
        RandomAccessFile(file, "r").use { input ->
            input.seek(12)
            val chunk = ByteArray(4).also(input::readFully)
                .toString(Charsets.US_ASCII)
            when (chunk) {
                "VP8X" -> {
                    input.seek(24)
                    val width = input.readLittleEndian24() + 1
                    val height = input.readLittleEndian24() + 1
                    CoverDimensions(width, height)
                }
                "VP8L" -> {
                    input.seek(20)
                    if (input.readUnsignedByte() != 0x2F) return@use null
                    val b0 = input.readUnsignedByte()
                    val b1 = input.readUnsignedByte()
                    val b2 = input.readUnsignedByte()
                    val b3 = input.readUnsignedByte()
                    val width = 1 + b0 + ((b1 and 0x3F) shl 8)
                    val height = 1 +
                        ((b1 and 0xC0) shr 6) +
                        (b2 shl 2) +
                        ((b3 and 0x0F) shl 10)
                    CoverDimensions(width, height)
                }
                "VP8 " -> {
                    input.seek(23)
                    if (input.readUnsignedByte() != 0x9D ||
                        input.readUnsignedByte() != 0x01 ||
                        input.readUnsignedByte() != 0x2A
                    ) {
                        return@use null
                    }
                    val width = input.readLittleEndianShort() and 0x3FFF
                    val height = input.readLittleEndianShort() and 0x3FFF
                    CoverDimensions(width, height)
                }
                else -> null
            }
        }

    private fun java.io.InputStream.readBigEndianShort(): Int {
        val high = read()
        val low = read()
        if (high < 0 || low < 0) throw EOFException()
        return (high shl 8) or low
    }

    private fun java.io.InputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining -= 1L
            } else {
                throw EOFException()
            }
        }
    }

    private fun RandomAccessFile.readLittleEndianShort(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun RandomAccessFile.readLittleEndian24(): Int =
        readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16)

    private val JPEG_START_OF_FRAME_MARKERS = setOf(
        0xC0, 0xC1, 0xC2, 0xC3,
        0xC5, 0xC6, 0xC7,
        0xC9, 0xCA, 0xCB,
        0xCD, 0xCE, 0xCF
    )
}
