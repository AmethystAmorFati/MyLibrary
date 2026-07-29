package com.example.mylibrary.ui.theme

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeFontFileValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun structurallyValidTrueTypeSfntPassesFileValidation() {
        val fixture = fixture()
        fixture.writeValidSfnt("fonts/fontA.TTF")

        val result = ThemeFontFileValidator.validateDeclaredFiles(
            fonts = ThemeFontManifest("fonts/fontA.TTF", null),
            resources = fixture.provider
        )

        assertTrue(result is ThemeFontFileValidationResult.Success)
    }

    @Test
    fun ordinaryFileRenamedToTtfIsRejectedByHeader() {
        val fixture = fixture()
        fixture.writeBytes(
            "fonts/not-a-font.ttf",
            "This is an ordinary file and not an SFNT font container.".toByteArray()
        )

        val result = ThemeFontFileValidator.validateDeclaredFiles(
            fonts = ThemeFontManifest("fonts/not-a-font.ttf", null),
            resources = fixture.provider
        )

        assertFailure<ThemeResolveError.FontHeaderInvalid>(result)
    }

    @Test
    fun openTypeCffRenamedToTtfIsRejectedAsUnsupportedFormat() {
        val fixture = fixture()
        val bytes = ByteArray(80)
        "OTTO".toByteArray(Charsets.ISO_8859_1).copyInto(bytes)
        fixture.writeBytes("fonts/renamed.ttf", bytes)

        val result = ThemeFontFileValidator.validateDeclaredFiles(
            fonts = ThemeFontManifest("fonts/renamed.ttf", null),
            resources = fixture.provider
        )

        assertFailure<ThemeResolveError.UnsupportedFontFormat>(result)
    }

    @Test
    fun emptyDeclaredFontIsRejected() {
        val fixture = fixture()
        fixture.writeBytes("fonts/empty.ttf", byteArrayOf())

        val result = ThemeFontFileValidator.validateDeclaredFiles(
            fonts = ThemeFontManifest("fonts/empty.ttf", null),
            resources = fixture.provider
        )

        assertFailure<ThemeResolveError.FontFileTooSmall>(result)
    }

    @Test
    fun declaredFontMustBeARegularFile() {
        val fixture = fixture()
        fixture.file("fonts/directory.ttf").mkdirs()

        val result = ThemeFontFileValidator.validateDeclaredFiles(
            fonts = ThemeFontManifest("fonts/directory.ttf", null),
            resources = fixture.provider
        )

        assertFailure<ThemeResolveError.ResourceNotRegularFile>(result)
    }

    @Test
    fun oversizedDeclaredFontIsRejectedBeforeLoading() {
        val fixture = fixture()
        val file = fixture.file("fonts/large.ttf")
        RandomAccessFile(file, "rw").use {
            it.setLength(ThemeResourceLimits.MAX_SINGLE_FONT_FILE_BYTES + 1L)
        }

        val result = ThemeFontFileValidator.validateDeclaredFiles(
            fonts = ThemeFontManifest("fonts/large.ttf", null),
            resources = fixture.provider
        )

        assertFailure<ThemeResolveError.FontTooLarge>(result)
    }

    @Test
    fun twoFontsOverCombinedLimitAreRejectedBeforeLoading() {
        val fixture = fixture()
        listOf("fonts/a.ttf", "fonts/b.ttf").forEach { path ->
            RandomAccessFile(fixture.file(path), "rw").use {
                it.setLength(17L * 1024L * 1024L)
            }
        }

        val result = ThemeFontFileValidator.validateDeclaredFiles(
            fonts = ThemeFontManifest("fonts/a.ttf", "fonts/b.ttf"),
            resources = fixture.provider
        )

        assertFailure<ThemeResolveError.FontTotalTooLarge>(result)
    }

    @Test
    fun manifestDeclaredMissingFontFailsStrictResolution() {
        val fixture = fixture()
        val manifest = DefaultThemeManifest.copy(
            fonts = ThemeFontManifest("fonts/missing.ttf", null)
        )

        val result = ThemeResolver.resolveStrict(manifest, fixture.provider)

        assertTrue(result is ThemeResolveResult.Failure)
        assertTrue(
            (result as ThemeResolveResult.Failure).error is
                ThemeResolveError.ResourceMissing
        )
    }

    @Test
    fun noDeclaredFontsStrictlyResolveToSystemFont() {
        val fixture = fixture()

        val result = ThemeResolver.resolveStrict(
            DefaultThemeManifest,
            fixture.provider
        )

        assertTrue(result is ThemeResolveResult.Success)
        assertSame(
            SystemAppFontResolver,
            (result as ThemeResolveResult.Success).theme.fontResolver
        )
    }

    @Test
    fun damagedDeclaredBStrictlyFailsInsteadOfFallingBackToA() {
        val fixture = fixture()
        fixture.writeValidSfnt("fonts/a.ttf")
        fixture.writeBytes(
            "fonts/b.ttf",
            "Damaged B font content that is long enough for size validation.".toByteArray()
        )
        val manifest = DefaultThemeManifest.copy(
            fonts = ThemeFontManifest("fonts/a.ttf", "fonts/b.ttf")
        )

        val result = ThemeResolver.resolveStrict(manifest, fixture.provider)

        assertTrue(result is ThemeResolveResult.Failure)
        assertTrue(
            (result as ThemeResolveResult.Failure).error is
                ThemeResolveError.FontHeaderInvalid
        )
    }

    @Test
    fun platformTypefaceCreationFailureIsStructured() {
        val fixture = fixture()
        fixture.writeValidSfnt("fonts/a.ttf")
        val loader = ThemeFontLoader(
            resources = fixture.provider,
            themeId = "font.failure",
            themeVersion = "1",
            themeGeneration = 1L,
            typefaceFactory = ThemeTypefaceFactory {
                throw IllegalStateException("Platform rejected font")
            }
        )

        val result = loader.load(ThemeFontManifest("fonts/a.ttf", null))

        assertTrue(result is ThemeFontLoadResult.Failure)
        assertTrue(
            (result as ThemeFontLoadResult.Failure).error is
                ThemeResolveError.FontLoadFailed
        )
    }

    private inline fun <reified T : ThemeResolveError> assertFailure(
        result: ThemeFontFileValidationResult
    ) {
        assertTrue(result is ThemeFontFileValidationResult.Failure)
        assertTrue((result as ThemeFontFileValidationResult.Failure).error is T)
    }

    private fun fixture(): FontFixture {
        val root = temporaryFolder.newFolder("theme-${System.nanoTime()}")
        return FontFixture(
            root = root,
            provider = DirectoryThemeResourceProvider(root)
        )
    }

    private data class FontFixture(
        val root: File,
        val provider: ThemeResourceProvider
    ) {
        fun file(relativePath: String): File =
            File(root, relativePath).apply { parentFile?.mkdirs() }

        fun writeBytes(relativePath: String, bytes: ByteArray) {
            file(relativePath).writeBytes(bytes)
        }

        fun writeValidSfnt(relativePath: String) {
            val tags = listOf("head", "maxp", "cmap", "name")
            val directorySize = 12 + tags.size * 16
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data ->
                data.writeInt(0x00010000)
                data.writeShort(tags.size)
                data.writeShort(0)
                data.writeShort(0)
                data.writeShort(0)
                tags.forEachIndexed { index, tag ->
                    data.write(tag.toByteArray(Charsets.ISO_8859_1))
                    data.writeInt(0)
                    data.writeInt(directorySize + index)
                    data.writeInt(1)
                }
                repeat(tags.size) { data.writeByte(0) }
            }
            writeBytes(relativePath, output.toByteArray())
        }
    }
}
