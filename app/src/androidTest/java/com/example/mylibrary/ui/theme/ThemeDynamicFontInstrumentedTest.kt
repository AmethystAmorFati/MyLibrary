package com.example.mylibrary.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.ui.poster.CoverPosterThemeSnapshotFactory
import java.io.File
import java.io.FileInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeDynamicFontInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var root: File
    private lateinit var provider: ThemeResourceProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(
            context.cacheDir,
            "theme-font-instrumented-${System.nanoTime()}"
        ).apply { check(mkdirs()) }
        provider = DirectoryThemeResourceProvider(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun validDeviceTtfLoadsOncePerSlotAndMapsBothOutputsConsistently() {
        installDeviceTtf("fonts/a.ttf")
        installDeviceTtf("fonts/b.ttf")
        val assignments = mapOf(
            FontRole.BRAND to FontSlot.A,
            FontRole.HEADING to FontSlot.B,
            FontRole.CONTENT to FontSlot.B,
            FontRole.META to FontSlot.A
        )
        val theme = resolveTheme(
            fonts = ThemeFontManifest("fonts/a.ttf", "fonts/b.ttf"),
            assignments = assignments,
            generation = 17L
        )
        val resolver = theme.fontResolver as ThemeFontResolver

        assertRoleUsesSlot(resolver, FontRole.BRAND, FontSlot.A, 17L)
        assertRoleUsesSlot(resolver, FontRole.HEADING, FontSlot.B, 17L)
        assertRoleUsesSlot(resolver, FontRole.CONTENT, FontSlot.B, 17L)
        assertRoleUsesSlot(resolver, FontRole.META, FontSlot.A, 17L)
        assertSame(
            resolver.resolvedSlot(FontRole.BRAND),
            resolver.resolvedSlot(FontRole.META)
        )
        assertSame(
            resolver.resolvedSlot(FontRole.HEADING),
            resolver.resolvedSlot(FontRole.CONTENT)
        )

        FontRole.entries.forEach { role ->
            val slot = requireNotNull(resolver.resolvedSlot(role))
            assertEquals(slot.composeFontFamily, resolver.composeFontFamily(role))
            assertSame(slot.androidTypeface, resolver.androidTypeface(role))
            assertSame(resolver.androidTypeface(role), resolver.androidTypeface(role))
        }
    }

    @Test
    fun loaderCacheKeyKeepsEachSlotAtOneTypefaceCreationPerGeneration() {
        installDeviceTtf("fonts/a.ttf")
        installDeviceTtf("fonts/b.ttf")
        var creationCount = 0
        val loader = ThemeFontLoader(
            resources = provider,
            themeId = "cache.test",
            themeVersion = "2.0",
            themeGeneration = 99L,
            typefaceFactory = ThemeTypefaceFactory { file ->
                creationCount += 1
                PlatformThemeTypefaceFactory.create(file)
            }
        )

        val first = loader.load(ThemeFontManifest("fonts/a.ttf", "fonts/b.ttf"))
        val second = loader.load(ThemeFontManifest("fonts/a.ttf", "fonts/b.ttf"))

        assertTrue(first is ThemeFontLoadResult.Success)
        assertTrue(second is ThemeFontLoadResult.Success)
        val firstSuccess = first as ThemeFontLoadResult.Success
        val secondSuccess = second as ThemeFontLoadResult.Success
        assertEquals(2, creationCount)
        assertSame(firstSuccess.fontA, secondSuccess.fontA)
        assertSame(firstSuccess.fontB, secondSuccess.fontB)
    }

    @Test
    fun absentBFallsBackToAAndAbsentABUseSystemFont() {
        installDeviceTtf("fonts/a.ttf")
        val withOnlyA = resolveTheme(
            fonts = ThemeFontManifest("fonts/a.ttf", null),
            assignments = DefaultThemeManifest.fontAssignments,
            generation = 18L
        )
        val resolver = withOnlyA.fontResolver as ThemeFontResolver

        assertRoleUsesSlot(resolver, FontRole.CONTENT, FontSlot.A, 18L)
        assertSame(
            resolver.resolvedSlot(FontRole.BRAND),
            resolver.resolvedSlot(FontRole.CONTENT)
        )

        val withoutFiles = resolveTheme(
            fonts = ThemeFontManifest(null, null),
            assignments = DefaultThemeManifest.fontAssignments,
            generation = 19L
        )
        assertSame(SystemAppFontResolver, withoutFiles.fontResolver)
        assertEquals(
            FontFamily.Default,
            withoutFiles.fontResolver.composeFontFamily(FontRole.CONTENT)
        )
        assertSame(
            Typeface.DEFAULT,
            withoutFiles.fontResolver.androidTypeface(FontRole.CONTENT)
        )
    }

    @Test
    fun posterSnapshotRetainsCapturedHeadingAndContentTypefaces() {
        installDeviceTtf("fonts/a.ttf")
        installDeviceTtf("fonts/b.ttf")
        val assignments = mapOf(
            FontRole.BRAND to FontSlot.A,
            FontRole.HEADING to FontSlot.A,
            FontRole.CONTENT to FontSlot.B,
            FontRole.META to FontSlot.B
        )
        val firstTheme = resolveTheme(
            fonts = ThemeFontManifest("fonts/a.ttf", "fonts/b.ttf"),
            assignments = assignments,
            generation = 20L
        )
        val firstResolver = firstTheme.fontResolver
        val captured = CoverPosterThemeSnapshotFactory.create(firstTheme)

        assertSame(
            firstResolver.androidTypeface(FontRole.HEADING),
            captured.headingTypeface
        )
        assertSame(
            firstResolver.androidTypeface(FontRole.CONTENT),
            captured.contentTypeface
        )

        // A later generation creates a new immutable resolver. The palette keeps
        // the exact Typeface objects captured when the export began.
        resolveTheme(
            fonts = ThemeFontManifest("fonts/a.ttf", "fonts/b.ttf"),
            assignments = assignments,
            generation = 21L
        )
        assertSame(
            firstResolver.androidTypeface(FontRole.HEADING),
            captured.headingTypeface
        )
        assertSame(
            firstResolver.androidTypeface(FontRole.CONTENT),
            captured.contentTypeface
        )
    }

    @Test
    fun multilingualAndRareGlyphSamplesDoNotCrashPlatformFallback() {
        installDeviceTtf("fonts/a.ttf")
        val theme = resolveTheme(
            fonts = ThemeFontManifest("fonts/a.ttf", null),
            assignments = DefaultThemeManifest.fontAssignments,
            generation = 22L
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = theme.fontResolver.androidTypeface(FontRole.CONTENT)
            textSize = 42f
        }
        val samples = listOf(
            "MyLibrary Latin",
            "中文字体回退",
            "0123456789",
            "，。！？—",
            "🙂📚",
            "𠮷野家"
        )

        samples.forEach { sample ->
            paint.hasGlyph(sample)
            assertTrue(paint.measureText(sample).isFinite())
        }
    }

    @Test
    fun composeRendersMultilingualFallbackSampleWithoutRebuildingTypography() {
        installDeviceTtf("fonts/a.ttf")
        val theme = resolveTheme(
            fonts = ThemeFontManifest("fonts/a.ttf", null),
            assignments = DefaultThemeManifest.fontAssignments,
            generation = 23L
        )
        val sample = "MyLibrary 中文 123，。🙂 𠮷"

        composeRule.setContent {
            MyLibraryTheme(resolvedTheme = theme) {
                Text(
                    text = sample,
                    style = AppTheme.typography.body
                )
            }
        }

        composeRule.onNodeWithText(sample).assertExists()
    }

    private fun resolveTheme(
        fonts: ThemeFontManifest,
        assignments: Map<FontRole, FontSlot>,
        generation: Long
    ): ResolvedTheme {
        val result = ThemeResolver.resolveStrict(
            manifest = DefaultThemeManifest.copy(
                fonts = fonts,
                fontAssignments = assignments
            ),
            resources = provider,
            themeGeneration = generation
        )
        assertTrue(
            "Strict resolution failed: ${(result as? ThemeResolveResult.Failure)?.error}",
            result is ThemeResolveResult.Success
        )
        return (result as ThemeResolveResult.Success).theme
    }

    private fun assertRoleUsesSlot(
        resolver: ThemeFontResolver,
        role: FontRole,
        expectedSlot: FontSlot,
        expectedGeneration: Long
    ) {
        val source = resolver.source(role)
        assertTrue(source is FontSource.ThemeFile)
        val key = (source as FontSource.ThemeFile).cacheKey
        assertEquals(expectedSlot, key.slot)
        assertEquals(DefaultThemeManifest.id, key.themeId)
        assertEquals(DefaultThemeManifest.version, key.themeVersion)
        assertEquals(expectedGeneration, key.themeGeneration)
        assertTrue(key.relativePath.startsWith("fonts/"))
        assertTrue(key.fileSize >= ThemeResourceLimits.MIN_FONT_FILE_BYTES)
        assertTrue(key.lastModified >= 0L)
    }

    private fun installDeviceTtf(relativePath: String) {
        val destination = File(root, relativePath).apply {
            parentFile?.mkdirs()
        }
        val candidates = File("/system/fonts")
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension.equals("ttf", ignoreCase = true) }
            .filter {
                it.length() >= ThemeResourceLimits.MIN_FONT_FILE_BYTES &&
                    it.length() <= ThemeResourceLimits.MAX_TOTAL_FONT_FILE_BYTES / 2L
            }
            .filter(::hasSupportedTrueTypeSignature)
            .sortedBy { if (it.name == "Roboto-Regular.ttf") 0 else 1 }
            .toList()

        var installed = false
        for (source in candidates) {
            val copied = runCatching {
                source.copyTo(destination, overwrite = true)
                Typeface.createFromFile(destination)
            }.isSuccess
            if (!copied) continue
            val validation = ThemeFontFileValidator.validateDeclaredFiles(
                fonts = ThemeFontManifest(relativePath, null),
                resources = provider
            )
            if (validation is ThemeFontFileValidationResult.Success) {
                installed = true
                break
            }
        }
        assumeTrue(
            "No device system TTF satisfied the Phase 3A-1 validator",
            installed
        )
    }

    private fun hasSupportedTrueTypeSignature(file: File): Boolean =
        runCatching {
            FileInputStream(file).use { input ->
                val bytes = ByteArray(4)
                input.read(bytes) == 4 &&
                    (
                        bytes.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                            bytes.contentEquals(byteArrayOf(0x74, 0x72, 0x75, 0x65))
                    )
            }
        }.getOrDefault(false)
}
