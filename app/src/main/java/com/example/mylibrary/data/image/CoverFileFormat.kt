package com.example.mylibrary.data.image

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

object CoverFileFormat {
    fun detect(file: File): String? = file.inputStream().buffered().use(::detect)

    fun detect(input: InputStream): String? {
        val header = ByteArray(12)
        val size = input.read(header)
        if (size >= 3 &&
            header[0].toInt() and 0xFF == 0xFF &&
            header[1].toInt() and 0xFF == 0xD8 &&
            header[2].toInt() and 0xFF == 0xFF
        ) {
            return "jpg"
        }
        if (size >= 8 && header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) {
            return "png"
        }
        if (size >= 12 &&
            header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        ) {
            return "webp"
        }
        if (size >= 6) {
            val signature = header.copyOfRange(0, 6).toString(Charsets.US_ASCII)
            if (signature == "GIF87a" || signature == "GIF89a") return "gif"
        }
        return null
    }

    fun isStructurallyComplete(file: File, detectedFormat: String): Boolean =
        runCatching {
            RandomAccessFile(file, "r").use { input ->
                when (detectedFormat) {
                    "jpg" -> {
                        if (input.length() < 4) return@use false
                        input.seek(input.length() - 2)
                        input.readUnsignedByte() == 0xFF && input.readUnsignedByte() == 0xD9
                    }
                    "png" -> {
                        if (input.length() < 20) return@use false
                        input.seek(input.length() - 8)
                        input.readInt() == PNG_IEND && input.readInt().let { true }
                    }
                    "webp" -> {
                        if (input.length() < 20) return@use false
                        input.seek(4)
                        val declaredLength = Integer.reverseBytes(input.readInt()).toLong() + 8L
                        input.seek(12)
                        val chunk = ByteArray(4).also(input::readFully)
                            .toString(Charsets.US_ASCII)
                        declaredLength == input.length() && chunk in WEBP_CHUNKS
                    }
                    "gif" -> {
                        if (input.length() < 14) return@use false
                        input.seek(input.length() - 1)
                        input.readUnsignedByte() == 0x3B
                    }
                    else -> false
                }
            }
        }.getOrDefault(false)

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A
    )
    private const val PNG_IEND = 0x49454E44
    private val WEBP_CHUNKS = setOf("VP8 ", "VP8L", "VP8X")
}
