package com.example.mylibrary.ui.theme

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeImageFileValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pngWebpAndJpegHeadersAreRecognized() {
        val provider = provider()
        write("surfaces/card/card.png", pngHeader(320, 180))
        write("surfaces/dialog/dialog.webp", webpHeader(400, 240))
        write("surfaces/background/background.jpeg", jpegHeader(640, 360))

        val card = validateOne(
            SurfaceRole.CARD,
            "surfaces/card/card.png",
            provider
        )
        val dialog = validateOne(
            SurfaceRole.DIALOG,
            "surfaces/dialog/dialog.webp",
            provider
        )
        val background = validateOne(
            SurfaceRole.BACKGROUND,
            "surfaces/background/background.jpeg",
            provider
        )

        assertEquals(ThemeImageFormat.PNG, card.format)
        assertEquals(320, card.width)
        assertTrue(card.alphaCapable)
        assertEquals(ThemeImageFormat.WEBP, dialog.format)
        assertTrue(dialog.alphaCapable)
        assertEquals(ThemeImageFormat.JPEG, background.format)
        assertFalse(background.alphaCapable)
    }

    @Test
    fun renamedPayloadAndArbitraryBytesAreRejected() {
        val provider = provider()
        write("surfaces/card/renamed.webp", pngHeader(64, 64))
        write(
            "surfaces/card/not-image.png",
            "this is not an image payload".toByteArray()
        )

        val renamed = ThemeImageFileValidator.validateOne(
            SurfaceRole.CARD,
            "surfaces/card/renamed.webp",
            provider
        )
        val arbitrary = ThemeImageFileValidator.validateOne(
            SurfaceRole.CARD,
            "surfaces/card/not-image.png",
            provider
        )

        assertFailure<ThemeResolveError.ImageFormatMismatch>(renamed)
        assertFailure<ThemeResolveError.ImageHeaderInvalid>(arbitrary)
    }

    @Test
    fun gifAndAnimatedPngAndWebpAreRejected() {
        val provider = provider()
        write(
            "surfaces/card/renamed.png",
            "GIF89a-not-supported".toByteArray()
        )
        write(
            "surfaces/card/animated.png",
            pngHeader(64, 64, animated = true)
        )
        write(
            "surfaces/dialog/animated.webp",
            webpHeader(64, 64, animated = true)
        )

        assertFailure<ThemeResolveError.UnsupportedImageFormat>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/renamed.png",
                provider
            )
        )
        assertFailure<ThemeResolveError.AnimatedImageUnsupported>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/animated.png",
                provider
            )
        )
        assertFailure<ThemeResolveError.AnimatedImageUnsupported>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.DIALOG,
                "surfaces/dialog/animated.webp",
                provider
            )
        )
    }

    @Test
    fun svgBmpHeifAndAvifAreRejected() {
        val provider = provider()
        val unsupported = mapOf(
            "svg" to "<svg viewBox='0 0 1 1'></svg>".toByteArray(),
            "bmp" to (
                byteArrayOf('B'.code.toByte(), 'M'.code.toByte()) +
                    ByteArray(18)
                ),
            "heif" to isoBaseMediaHeader("heic"),
            "avif" to isoBaseMediaHeader("avif")
        )

        unsupported.forEach { (name, bytes) ->
            val path = "surfaces/card/$name.png"
            write(path, bytes)
            assertFailure<ThemeResolveError.UnsupportedImageFormat>(
                ThemeImageFileValidator.validateOne(
                    SurfaceRole.CARD,
                    path,
                    provider
                )
            )
        }
    }

    @Test
    fun jpegIsRejectedForCardAndDialog() {
        val provider = provider()
        write("surfaces/card/card.jpg", jpegHeader(64, 64))
        write("surfaces/dialog/dialog.jpeg", jpegHeader(64, 64))

        assertFailure<ThemeResolveError.UnsupportedImageFormat>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/card.jpg",
                provider
            )
        )
        assertFailure<ThemeResolveError.UnsupportedImageFormat>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.DIALOG,
                "surfaces/dialog/dialog.jpeg",
                provider
            )
        )
    }

    @Test
    fun missingEmptyAndOversizedFilesReturnStructuredErrors() {
        val provider = provider()
        write("surfaces/card/empty.png", ByteArray(0))
        val oversized = write(
            "surfaces/card/oversized.png",
            pngHeader(64, 64)
        )
        RandomAccessFile(oversized, "rw").use {
            it.setLength(ThemeResourceLimits.MAX_CARD_IMAGE_FILE_BYTES + 1L)
        }

        assertFailure<ThemeResolveError.ImageMissing>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/missing.png",
                provider
            )
        )
        assertFailure<ThemeResolveError.ImageTooSmall>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/empty.png",
                provider
            )
        )
        assertFailure<ThemeResolveError.ImageTooLarge>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/oversized.png",
                provider
            )
        )
    }

    @Test
    fun sideAndPixelLimitsAreRoleSpecific() {
        val provider = provider()
        write("surfaces/card/tiny.png", pngHeader(15, 64))
        write("surfaces/card/too-wide.png", pngHeader(8193, 64))
        write("surfaces/card/too-many.png", pngHeader(4000, 3000))
        write(
            "surfaces/background/allowed.png",
            pngHeader(4000, 3000)
        )

        assertFailure<ThemeResolveError.ImageDimensionsInvalid>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/tiny.png",
                provider
            )
        )
        assertFailure<ThemeResolveError.ImageDimensionsInvalid>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/too-wide.png",
                provider
            )
        )
        assertFailure<ThemeResolveError.ImagePixelCountExceeded>(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.CARD,
                "surfaces/card/too-many.png",
                provider
            )
        )
        assertTrue(
            ThemeImageFileValidator.validateOne(
                SurfaceRole.BACKGROUND,
                "surfaces/background/allowed.png",
                provider
            ) is ThemeImageFileValidationResult.Success
        )
    }

    @Test
    fun threeSurfaceFilesMustFitCombinedLimit() {
        val provider = provider()
        val background = write(
            "surfaces/background/background.png",
            pngHeader(64, 64)
        )
        val card = write("surfaces/card/card.png", pngHeader(64, 64))
        val dialog = write(
            "surfaces/dialog/dialog.png",
            pngHeader(64, 64)
        )
        RandomAccessFile(background, "rw").use {
            it.setLength(ThemeResourceLimits.MAX_BACKGROUND_IMAGE_FILE_BYTES)
        }
        RandomAccessFile(card, "rw").use {
            it.setLength(ThemeResourceLimits.MAX_CARD_IMAGE_FILE_BYTES)
        }
        RandomAccessFile(dialog, "rw").use {
            it.setLength(ThemeResourceLimits.MAX_DIALOG_IMAGE_FILE_BYTES)
        }
        val surfaces = ThemeSurfaceManifest(
            background = image("#FF000000", "surfaces/background/background.png"),
            card = image("#FF000000", "surfaces/card/card.png"),
            dialog = image("#FF000000", "surfaces/dialog/dialog.png")
        )

        val result = ThemeImageFileValidator.validateDeclaredFiles(
            surfaces,
            provider
        )

        assertFailure<ThemeResolveError.ImageTotalTooLarge>(result)
    }

    @Test
    fun samplingUsesPowerOfTwoAndNeverExceedsBucket() {
        val sample = calculateInSampleSize(
            width = 4000,
            height = 3000,
            maximumWidth = 1024,
            maximumHeight = 1024
        )

        assertEquals(4, sample)
        assertTrue(4000 / sample <= 1024)
        assertTrue(3000 / sample <= 1024)
    }

    private fun validateOne(
        role: SurfaceRole,
        relativePath: String,
        provider: ThemeResourceProvider
    ): ValidatedThemeImageFile {
        val result = ThemeImageFileValidator.validateOne(
            role,
            relativePath,
            provider
        )
        assertTrue(result is ThemeImageFileValidationResult.Success)
        return (result as ThemeImageFileValidationResult.Success)
            .images
            .getValue(role)
    }

    private inline fun <reified T : ThemeResolveError> assertFailure(
        result: ThemeImageFileValidationResult
    ) {
        assertTrue(result is ThemeImageFileValidationResult.Failure)
        val failure = result as ThemeImageFileValidationResult.Failure
        assertTrue(
            "Expected ${T::class.java.simpleName}, got " +
                failure.error,
            failure.error is T
        )
    }

    private fun provider(): ThemeResourceProvider =
        DirectoryThemeResourceProvider(temporaryFolder.root)

    private fun write(relativePath: String, bytes: ByteArray): File =
        File(temporaryFolder.root, relativePath).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }

    private fun image(
        color: String,
        file: String
    ): ThemeSurfaceDefinition = ThemeSurfaceDefinition(
        type = ThemeSurfaceType.IMAGE,
        color = color,
        file = file
    )
}

private fun pngHeader(
    width: Int,
    height: Int,
    animated: Boolean = false
): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.write(
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
            )
        )
        output.writePngChunk("IHDR") {
            writeInt(width)
            writeInt(height)
            write(byteArrayOf(8, 6, 0, 0, 0))
        }
        if (animated) {
            output.writePngChunk("acTL") {
                writeInt(1)
                writeInt(0)
            }
        }
        output.writePngChunk("IEND") {}
    }
    bytes.toByteArray()
}

private fun DataOutputStream.writePngChunk(
    type: String,
    content: DataOutputStream.() -> Unit
) {
    val payload = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { it.content() }
        bytes.toByteArray()
    }
    writeInt(payload.size)
    write(type.toByteArray(Charsets.US_ASCII))
    write(payload)
    writeInt(0)
}

private fun webpHeader(
    width: Int,
    height: Int,
    animated: Boolean = false
): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.writeLittleEndianInt(22)
        output.write("WEBP".toByteArray(Charsets.US_ASCII))
        output.write("VP8X".toByteArray(Charsets.US_ASCII))
        output.writeLittleEndianInt(10)
        output.writeByte(if (animated) 0x02 else 0)
        output.write(byteArrayOf(0, 0, 0))
        output.writeLittleEndian24(width - 1)
        output.writeLittleEndian24(height - 1)
    }
    bytes.toByteArray()
}

private fun jpegHeader(width: Int, height: Int): ByteArray =
    ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeByte(0xFF)
            output.writeByte(0xD8)
            output.writeByte(0xFF)
            output.writeByte(0xC0)
            output.writeShort(17)
            output.writeByte(8)
            output.writeShort(height)
            output.writeShort(width)
            output.writeByte(3)
            repeat(3) { component ->
                output.writeByte(component + 1)
                output.writeByte(0x11)
                output.writeByte(0)
            }
        }
        bytes.toByteArray()
    }

private fun DataOutputStream.writeLittleEndianInt(value: Int) {
    writeByte(value and 0xFF)
    writeByte((value ushr 8) and 0xFF)
    writeByte((value ushr 16) and 0xFF)
    writeByte((value ushr 24) and 0xFF)
}

private fun DataOutputStream.writeLittleEndian24(value: Int) {
    writeByte(value and 0xFF)
    writeByte((value ushr 8) and 0xFF)
    writeByte((value ushr 16) and 0xFF)
}

private fun isoBaseMediaHeader(brand: String): ByteArray =
    byteArrayOf(0, 0, 0, 20) +
        "ftyp".toByteArray(Charsets.US_ASCII) +
        brand.toByteArray(Charsets.US_ASCII) +
        ByteArray(8)
