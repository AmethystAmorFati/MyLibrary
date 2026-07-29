package com.example.mylibrary.ui.theme

import com.example.mylibrary.ui.navigation.NavigationIconSlot
import com.example.mylibrary.ui.navigation.NavigationIconState
import com.example.mylibrary.ui.navigation.ThemeIconRendering
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeNavigationIconFileValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pngAndStaticWebpAreAcceptedWithFrozenRoleStateAndRendering() {
        val provider = provider()
        write("icons/home.png", png(64, 48))
        write("icons/home_selected.webp", webp(80, 80))
        val navigation = ThemeNavigationManifest(
            rendering = ThemeIconRendering.MONOCHROME,
            home = NavigationIconDefinition(
                normal = "icons/home.png",
                selected = "icons/home_selected.webp"
            )
        )

        val result = ThemeNavigationIconFileValidator.validateDeclaredFiles(
            navigation,
            provider
        )

        assertTrue(result is ThemeNavigationIconFileValidationResult.Success)
        val images =
            (result as ThemeNavigationIconFileValidationResult.Success).images
        val normal = images.getValue(
            NavigationIconVariant(
                NavigationIconSlot.HOME,
                NavigationIconState.NORMAL
            )
        )
        val selected = images.getValue(
            NavigationIconVariant(
                NavigationIconSlot.HOME,
                NavigationIconState.SELECTED
            )
        )
        assertEquals(ThemeImageFormat.PNG, normal.format)
        assertEquals(ThemeImageFormat.WEBP, selected.format)
        assertEquals(ThemeIconRendering.MONOCHROME, normal.rendering)
        assertEquals(2, images.size)
    }

    @Test
    fun renamedJpegGifSvgAndBmpAreRejectedByContent() {
        val provider = provider()
        val files = mapOf(
            "icons/jpeg.png" to jpeg(64, 64),
            "icons/gif.png" to "GIF89a-not-an-icon".toByteArray(),
            "icons/svg.png" to "<svg viewBox='0 0 1 1'></svg>".toByteArray(),
            "icons/bmp.webp" to (
                byteArrayOf('B'.code.toByte(), 'M'.code.toByte()) +
                    ByteArray(20)
                )
        )
        files.forEach { (path, bytes) -> write(path, bytes) }

        assertFailure<ThemeResolveError.NavigationIconFormatMismatch>(
            validateOne("icons/jpeg.png", provider)
        )
        listOf("icons/gif.png", "icons/svg.png", "icons/bmp.webp").forEach {
            assertFailure<ThemeResolveError.UnsupportedNavigationIconFormat>(
                validateOne(it, provider)
            )
        }
    }

    @Test
    fun animatedPngAndWebpAreRejected() {
        val provider = provider()
        write("icons/animated.png", png(64, 64, animated = true))
        write("icons/animated.webp", webp(64, 64, animated = true))

        assertFailure<ThemeResolveError.AnimatedNavigationIconUnsupported>(
            validateOne("icons/animated.png", provider)
        )
        assertFailure<ThemeResolveError.AnimatedNavigationIconUnsupported>(
            validateOne("icons/animated.webp", provider)
        )
    }

    @Test
    fun missingEmptyAndSingleOversizedFilesHaveStructuredErrors() {
        val provider = provider()
        write("icons/empty.png", ByteArray(0))
        val oversized = write("icons/oversized.png", png(64, 64))
        RandomAccessFile(oversized, "rw").use {
            it.setLength(
                ThemeResourceLimits.MAX_NAVIGATION_IMAGE_FILE_BYTES + 1L
            )
        }

        assertFailure<ThemeResolveError.NavigationIconMissing>(
            validateOne("icons/missing.png", provider)
        )
        assertFailure<ThemeResolveError.NavigationIconTooSmall>(
            validateOne("icons/empty.png", provider)
        )
        assertFailure<ThemeResolveError.NavigationIconTooLarge>(
            validateOne("icons/oversized.png", provider)
        )
    }

    @Test
    fun sidePixelAndAspectRatioLimitsAreIndependent() {
        val provider = provider()
        write("icons/too-wide.png", png(1025, 64))
        write("icons/too-many.png", png(600, 600))
        write("icons/extreme.png", png(400, 50))
        write("icons/zero.png", png(0, 64))

        assertFailure<ThemeResolveError.NavigationIconDimensionsInvalid>(
            validateOne("icons/too-wide.png", provider)
        )
        assertFailure<ThemeResolveError.NavigationIconPixelCountExceeded>(
            validateOne("icons/too-many.png", provider)
        )
        assertFailure<ThemeResolveError.NavigationIconAspectRatioInvalid>(
            validateOne("icons/extreme.png", provider)
        )
        assertFailure<ThemeResolveError.NavigationIconDimensionsInvalid>(
            validateOne("icons/zero.png", provider)
        )
    }

    @Test
    fun declaredNavigationFilesMustFitCombinedLimit() {
        val provider = provider()
        val paths = listOf(
            "icons/home.png",
            "icons/home_selected.png",
            "icons/library.png",
            "icons/library_selected.png",
            "icons/statistics.png"
        )
        paths.forEach { path ->
            val file = write(path, png(64, 64))
            RandomAccessFile(file, "rw").use {
                it.setLength(
                    ThemeResourceLimits.MAX_NAVIGATION_IMAGE_FILE_BYTES
                )
            }
        }
        val navigation = ThemeNavigationManifest(
            home = NavigationIconDefinition(paths[0], paths[1]),
            library = NavigationIconDefinition(paths[2], paths[3]),
            statistics = NavigationIconDefinition(paths[4], null)
        )

        assertFailure<ThemeResolveError.NavigationIconTotalTooLarge>(
            ThemeNavigationIconFileValidator.validateDeclaredFiles(
                navigation,
                provider
            )
        )
    }

    @Test
    fun navigationSymlinkUsesTheExistingTrustedProviderBoundary() {
        val icons = File(temporaryFolder.root, "icons").apply { mkdirs() }
        val outside = temporaryFolder.newFile("outside.png").apply {
            writeBytes(png(64, 64))
        }
        try {
            Files.createSymbolicLink(
                File(icons, "linked.png").toPath(),
                outside.toPath()
            )
        } catch (exception: Exception) {
            assumeNoException(
                "This environment cannot create symbolic links",
                exception
            )
        }

        val failure = runCatching {
            validateOne("icons/linked.png", provider())
        }.exceptionOrNull()

        assertTrue(failure is ThemeResourceAccessException)
        assertTrue(
            (failure as ThemeResourceAccessException).error is
                ThemeResolveError.PathEscapesRoot
        )
    }

    private fun validateOne(
        path: String,
        resources: ThemeResourceProvider
    ): ThemeNavigationIconFileValidationResult =
        ThemeNavigationIconFileValidator.validateOne(
            slot = NavigationIconSlot.HOME,
            state = NavigationIconState.NORMAL,
            relativePath = path,
            rendering = ThemeIconRendering.ORIGINAL,
            resources = resources
        )

    private inline fun <reified T : ThemeResolveError> assertFailure(
        result: ThemeNavigationIconFileValidationResult
    ) {
        assertTrue(result is ThemeNavigationIconFileValidationResult.Failure)
        val error =
            (result as ThemeNavigationIconFileValidationResult.Failure).error
        assertTrue(
            "Expected ${T::class.java.simpleName}, got $error",
            error is T
        )
    }

    private fun provider(): ThemeResourceProvider =
        DirectoryThemeResourceProvider(temporaryFolder.root)

    private fun write(path: String, bytes: ByteArray): File =
        File(temporaryFolder.root, path).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
}

private fun png(
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
        output.writeNavPngChunk("IHDR") {
            writeInt(width)
            writeInt(height)
            write(byteArrayOf(8, 6, 0, 0, 0))
        }
        if (animated) {
            output.writeNavPngChunk("acTL") {
                writeInt(1)
                writeInt(0)
            }
        }
        output.writeNavPngChunk("IEND") {}
    }
    bytes.toByteArray()
}

private fun DataOutputStream.writeNavPngChunk(
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

private fun webp(
    width: Int,
    height: Int,
    animated: Boolean = false
): ByteArray = ByteArrayOutputStream().use { bytes ->
    DataOutputStream(bytes).use { output ->
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.writeNavLittleEndianInt(22)
        output.write("WEBP".toByteArray(Charsets.US_ASCII))
        output.write("VP8X".toByteArray(Charsets.US_ASCII))
        output.writeNavLittleEndianInt(10)
        output.writeByte(if (animated) 0x02 else 0)
        output.write(byteArrayOf(0, 0, 0))
        output.writeNavLittleEndian24(width - 1)
        output.writeNavLittleEndian24(height - 1)
    }
    bytes.toByteArray()
}

private fun jpeg(width: Int, height: Int): ByteArray =
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

private fun DataOutputStream.writeNavLittleEndianInt(value: Int) {
    writeByte(value and 0xFF)
    writeByte((value ushr 8) and 0xFF)
    writeByte((value ushr 16) and 0xFF)
    writeByte((value ushr 24) and 0xFF)
}

private fun DataOutputStream.writeNavLittleEndian24(value: Int) {
    writeByte(value and 0xFF)
    writeByte((value ushr 8) and 0xFF)
    writeByte((value ushr 16) and 0xFF)
}
