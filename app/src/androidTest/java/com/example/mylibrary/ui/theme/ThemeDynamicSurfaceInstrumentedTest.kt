package com.example.mylibrary.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.mylibrary.ui.components.AppAlertDialog
import com.example.mylibrary.ui.components.AppModalBottomSheet
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.AppThemeSurface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeDynamicSurfaceInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var root: File
    private lateinit var provider: ThemeResourceProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(
            context.cacheDir,
            "theme-surface-instrumented-${System.nanoTime()}"
        ).apply { check(mkdirs()) }
        provider = DirectoryThemeResourceProvider(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun actualPngWebpAndJpegDecodeIntoTheirFrozenSurfaceRoles() {
        writeBitmap(
            "surfaces/background/background.jpg",
            width = 180,
            height = 120,
            color = AndroidColor.RED,
            format = Bitmap.CompressFormat.JPEG
        )
        writeBitmap(
            "surfaces/card/card.png",
            width = 96,
            height = 64,
            color = AndroidColor.GREEN,
            format = Bitmap.CompressFormat.PNG
        )
        writeBitmap(
            "surfaces/dialog/dialog.webp",
            width = 80,
            height = 120,
            color = 0x800000FF.toInt(),
            format = webpFormat()
        )

        val theme = resolveTheme(
            surfaces = ThemeSurfaceManifest(
                background = image(
                    "#FF010203",
                    "surfaces/background/background.jpg"
                ),
                card = image("#FF040506", "surfaces/card/card.png"),
                dialog = image(
                    "#FF070809",
                    "surfaces/dialog/dialog.webp"
                )
            ),
            generation = 31L
        )

        assertImageSurface(
            theme.surfaces.background,
            SurfaceRole.BACKGROUND,
            ThemeImageFormat.JPEG,
            180,
            120
        )
        assertImageSurface(
            theme.surfaces.card,
            SurfaceRole.CARD,
            ThemeImageFormat.PNG,
            96,
            64
        )
        val dialog = assertImageSurface(
            theme.surfaces.dialog,
            SurfaceRole.DIALOG,
            ThemeImageFormat.WEBP,
            80,
            120
        )
        assertTrue(dialog.alphaCapable)
        val webpPixel = requireNotNull(dialog.imageBitmap).toPixelMap()[0, 0]
        assertTrue(webpPixel.alpha in 0.35f..0.65f)
    }

    @Test
    fun colorAndImageSurfaceMixKeepsEachRoleIndependent() {
        writeBitmap(
            "surfaces/card/card.png",
            width = 64,
            height = 64,
            color = AndroidColor.BLUE,
            format = Bitmap.CompressFormat.PNG
        )

        val theme = resolveTheme(
            surfaces = DefaultThemeManifest.surfaces.copy(
                card = image("#FF112233", "surfaces/card/card.png")
            ),
            generation = 33L
        )

        assertTrue(theme.surfaces.background is ResolvedSurface.ColorSurface)
        assertTrue(theme.surfaces.card is ResolvedSurface.ImageSurface)
        assertTrue(theme.surfaces.dialog is ResolvedSurface.ColorSurface)
        assertEquals(Color(0xFF112233), theme.surfaces.card.fallbackColor)
    }

    @Test
    fun animatedPngAndWebpAreRejectedBeforeBitmapDecode() {
        val staticPng = encodeBitmap(
            width = 32,
            height = 32,
            color = AndroidColor.RED,
            format = Bitmap.CompressFormat.PNG
        )
        writeBytes(
            "surfaces/card/animated.png",
            insertPngAnimationChunk(staticPng)
        )
        writeBytes(
            "surfaces/dialog/animated.webp",
            animatedWebpHeader(width = 32, height = 32)
        )

        val png = ThemeImageFileValidator.validateOne(
            SurfaceRole.CARD,
            "surfaces/card/animated.png",
            provider
        )
        val webp = ThemeImageFileValidator.validateOne(
            SurfaceRole.DIALOG,
            "surfaces/dialog/animated.webp",
            provider
        )

        assertTrue(
            (png as ThemeImageFileValidationResult.Failure).error is
                ThemeResolveError.AnimatedImageUnsupported
        )
        assertTrue(
            (webp as ThemeImageFileValidationResult.Failure).error is
                ThemeResolveError.AnimatedImageUnsupported
        )
    }

    @Test
    fun headerValidButUndecodablePayloadFailsStrictResolution() {
        val validPng = encodeBitmap(
            width = 32,
            height = 32,
            color = AndroidColor.RED,
            format = Bitmap.CompressFormat.PNG
        )
        writeBytes(
            "surfaces/card/truncated.png",
            validPng.copyOfRange(0, 33)
        )
        val manifest = DefaultThemeManifest.copy(
            surfaces = DefaultThemeManifest.surfaces.copy(
                card = image(
                    "#FFFFFFFF",
                    "surfaces/card/truncated.png"
                )
            )
        )

        val result = ThemeResolver.resolveStrict(
            manifest = manifest,
            resources = provider,
            themeGeneration = 32L,
            imageDecodeProfile = testDecodeProfile(128)
        )

        assertTrue(result is ThemeResolveResult.Failure)
        assertTrue(
            (result as ThemeResolveResult.Failure).error is
                ThemeResolveError.ImageDecodeFailed
        )
    }

    @Test
    fun sampledDecodeAndCacheAreBoundedGenerationAwareAndReferenceSafe() {
        writeBitmap(
            "surfaces/card/card.png",
            width = 512,
            height = 384,
            color = AndroidColor.BLUE,
            format = Bitmap.CompressFormat.PNG
        )
        val surfaces = DefaultThemeManifest.surfaces.copy(
            card = image("#FFFF00FF", "surfaces/card/card.png")
        )
        val profile = testDecodeProfile(maximumSide = 128)
        val cache = ThemeSurfaceImageCache(maximumEntries = 2)
        val decodeCount = AtomicInteger()
        val countingDecoder = ThemeBitmapDecoder { image, bucket, key ->
            decodeCount.incrementAndGet()
            AndroidThemeBitmapDecoder.decode(image, bucket, key)
        }
        val firstLoader = ThemeImageLoader(
            resources = provider,
            themeId = "surface.test",
            themeVersion = "1",
            themeGeneration = 1L,
            decodeProfile = profile,
            cache = cache,
            decoder = countingDecoder
        )

        val first = firstLoader.load(surfaces).successAsset(SurfaceRole.CARD)
        val repeated = firstLoader.load(surfaces).successAsset(SurfaceRole.CARD)
        val secondGeneration = ThemeImageLoader(
            resources = provider,
            themeId = "surface.test",
            themeVersion = "1",
            themeGeneration = 2L,
            decodeProfile = profile,
            cache = cache,
            decoder = countingDecoder
        ).load(surfaces).successAsset(SurfaceRole.CARD)
        val thirdGeneration = ThemeImageLoader(
            resources = provider,
            themeId = "surface.test",
            themeVersion = "1",
            themeGeneration = 3L,
            decodeProfile = profile,
            cache = cache,
            decoder = countingDecoder
        ).load(surfaces).successAsset(SurfaceRole.CARD)

        assertEquals(3, decodeCount.get())
        assertSame(first, repeated)
        assertNotSame(first, secondGeneration)
        assertNotSame(secondGeneration, thirdGeneration)
        assertEquals(1L, first.cacheKey.themeGeneration)
        assertEquals(2L, secondGeneration.cacheKey.themeGeneration)
        assertEquals(3L, thirdGeneration.cacheKey.themeGeneration)
        assertEquals("surface.test", first.cacheKey.themeId)
        assertEquals("card-test-128", first.cacheKey.decodeBucket)
        assertTrue(first.decodedWidth <= 128)
        assertTrue(first.decodedHeight <= 128)
        assertEquals(2, cache.entryCount())
        assertTrue(
            cache.byteCount() <= ThemeResourceLimits.MAX_THEME_IMAGE_CACHE_BYTES
        )

        cache.clear()
        assertEquals(0, cache.entryCount())
        assertEquals(0L, cache.byteCount())
        assertTrue(requireNotNull(first.imageBitmap).width > 0)
    }

    @Test
    fun concurrentIdenticalLoadsShareOneDecode() {
        writeBitmap(
            "surfaces/card/card.png",
            width = 128,
            height = 128,
            color = AndroidColor.CYAN,
            format = Bitmap.CompressFormat.PNG
        )
        val surfaces = DefaultThemeManifest.surfaces.copy(
            card = image("#FF000000", "surfaces/card/card.png")
        )
        val cache = ThemeSurfaceImageCache(maximumEntries = 2)
        val decodeCount = AtomicInteger()
        val decoder = ThemeBitmapDecoder { image, bucket, key ->
            decodeCount.incrementAndGet()
            Thread.sleep(120L)
            AndroidThemeBitmapDecoder.decode(image, bucket, key)
        }
        val loader = ThemeImageLoader(
            resources = provider,
            themeId = "surface.concurrent",
            themeVersion = "1",
            themeGeneration = 5L,
            decodeProfile = testDecodeProfile(128),
            cache = cache,
            decoder = decoder
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = executor.submit<ThemeImageLoadResult> {
                loader.load(surfaces)
            }
            val second = executor.submit<ThemeImageLoadResult> {
                loader.load(surfaces)
            }
            val firstAsset = first.get(5, TimeUnit.SECONDS)
                .successAsset(SurfaceRole.CARD)
            val secondAsset = second.get(5, TimeUnit.SECONDS)
                .successAsset(SurfaceRole.CARD)

            assertEquals(1, decodeCount.get())
            assertSame(firstAsset, secondAsset)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun cardAndBackgroundDrawCropClipAndRuntimeFallbackWithoutCrashing() {
        val cardBitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        cardBitmap.eraseColor(AndroidColor.BLUE)
        val backgroundBitmap = Bitmap.createBitmap(
            120,
            60,
            Bitmap.Config.ARGB_8888
        )
        backgroundBitmap.eraseColor(AndroidColor.GREEN)
        val cardAsset = testAsset(
            bitmap = cardBitmap,
            role = SurfaceRole.CARD,
            keySuffix = "card"
        )
        val backgroundAsset = testAsset(
            bitmap = backgroundBitmap,
            role = SurfaceRole.BACKGROUND,
            keySuffix = "background"
        )
        val unavailableDialogAsset = testAsset(
            bitmap = cardBitmap,
            role = SurfaceRole.DIALOG,
            keySuffix = "fallback"
        ).copy(
            imageBitmap = null,
            unavailableReason = "instrumented runtime fallback"
        )
        val theme = DefaultResolvedTheme.copy(
            surfaces = DefaultResolvedTheme.surfaces.copy(
                background = ResolvedSurface.ImageSurface(
                    fallbackColor = Color.Red,
                    image = backgroundAsset,
                    role = SurfaceRole.BACKGROUND
                ),
                card = ResolvedSurface.ImageSurface(
                    fallbackColor = Color.Red,
                    image = cardAsset,
                    role = SurfaceRole.CARD
                ),
                dialog = ResolvedSurface.ImageSurface(
                    fallbackColor = Color.Magenta,
                    image = unavailableDialogAsset,
                    role = SurfaceRole.DIALOG
                )
            )
        )

        composeRule.setContent {
            MyLibraryTheme(resolvedTheme = theme) {
                AppScreenContainer(
                    modifier = Modifier
                        .size(180.dp)
                        .testTag("background")
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .testTag("screen-content")
                    )
                    AppThemeSurface(
                        role = SurfaceRole.CARD,
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.BottomStart)
                            .testTag("card"),
                        shape = RoundedCornerShape(24.dp)
                    ) {}
                    AppThemeSurface(
                        role = SurfaceRole.DIALOG,
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.BottomEnd)
                            .testTag("fallback"),
                        shape = RoundedCornerShape(12.dp)
                    ) {}
                }
            }
        }

        val background = composeRule.onNodeWithTag("background")
            .captureToImage()
            .toPixelMap()
        val card = composeRule.onNodeWithTag("card")
            .captureToImage()
            .toPixelMap()
        val fallback = composeRule.onNodeWithTag("fallback")
            .captureToImage()
            .toPixelMap()

        assertColorNear(Color.Green, background[2, 2])
        assertColorNear(Color.Blue, card[card.width / 2, card.height / 2])
        assertTrue(card[0, 0].blue < 0.8f)
        assertColorNear(
            Color.Magenta,
            fallback[fallback.width / 2, fallback.height / 2]
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun alertDialogUsesTheResolvedDialogImageBehindItsVisibleContent() {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.CYAN)
        val asset = testAsset(
            bitmap = bitmap,
            role = SurfaceRole.DIALOG,
            keySuffix = "dialog"
        )
        val theme = DefaultResolvedTheme.copy(
            surfaces = DefaultResolvedTheme.surfaces.copy(
                dialog = ResolvedSurface.ImageSurface(
                    fallbackColor = Color.Magenta,
                    image = asset,
                    role = SurfaceRole.DIALOG
                )
            )
        )

        composeRule.setContent {
            MyLibraryTheme(resolvedTheme = theme) {
                AppAlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Text("确认") },
                    modifier = Modifier.testTag("image-dialog"),
                    title = { Text("动态表面") },
                    text = { Text("Dialog 图片应位于内容后方") }
                )
            }
        }
        composeRule.waitForIdle()

        val pixels = composeRule.onNodeWithTag("image-dialog")
            .captureToImage()
            .toPixelMap()
        assertTrue(pixels.containsColor(Color.Cyan))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun modalBottomSheetUsesTheResolvedDialogImage() {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.YELLOW)
        val theme = DefaultResolvedTheme.copy(
            surfaces = DefaultResolvedTheme.surfaces.copy(
                dialog = ResolvedSurface.ImageSurface(
                    fallbackColor = Color.Magenta,
                    image = testAsset(
                        bitmap = bitmap,
                        role = SurfaceRole.DIALOG,
                        keySuffix = "sheet"
                    ),
                    role = SurfaceRole.DIALOG
                )
            )
        )

        composeRule.setContent {
            MyLibraryTheme(resolvedTheme = theme) {
                AppModalBottomSheet(
                    onDismissRequest = {},
                    modifier = Modifier.testTag("image-sheet")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val pixels = composeRule.onNodeWithTag("image-sheet")
            .captureToImage()
            .toPixelMap()
        // Image is present inside the sheet (bottom half of the screen).
        assertTrue(
            "DIALOG image should be visible inside the sheet",
            pixels.containsColorInRegion(
                Color.Yellow,
                startY = pixels.height / 2,
                endY = pixels.height
            )
        )
        // Image must NOT appear above the sheet (top quarter of the screen).
        assertFalse(
            "DIALOG image must not appear above the sheet at the screen top",
            pixels.containsColorInRegion(
                Color.Yellow,
                startY = 0,
                endY = pixels.height / 4
            )
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun modalBottomSheetColorModeShowsFallbackColor() {
        val dialogColor = Color(0xFF00BCD4)
        val theme = DefaultResolvedTheme.copy(
            surfaces = DefaultResolvedTheme.surfaces.copy(
                dialog = ResolvedSurface.ColorSurface(dialogColor)
            )
        )

        composeRule.setContent {
            MyLibraryTheme(resolvedTheme = theme) {
                AppModalBottomSheet(
                    onDismissRequest = {},
                    modifier = Modifier.testTag("color-sheet")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val pixels = composeRule.onNodeWithTag("color-sheet")
            .captureToImage()
            .toPixelMap()
        assertTrue(
            "DIALOG color should fill the sheet area",
            pixels.containsColorInRegion(
                dialogColor,
                startY = pixels.height / 2,
                endY = pixels.height
            )
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun modalBottomSheetImageFallbackShowsDialogColor() {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.YELLOW)
        val unavailableAsset = testAsset(
            bitmap = bitmap,
            role = SurfaceRole.DIALOG,
            keySuffix = "fallback"
        ).copy(
            imageBitmap = null,
            unavailableReason = "instrumented runtime fallback"
        )
        val theme = DefaultResolvedTheme.copy(
            surfaces = DefaultResolvedTheme.surfaces.copy(
                dialog = ResolvedSurface.ImageSurface(
                    fallbackColor = Color.Magenta,
                    image = unavailableAsset,
                    role = SurfaceRole.DIALOG
                )
            )
        )

        composeRule.setContent {
            MyLibraryTheme(resolvedTheme = theme) {
                AppModalBottomSheet(
                    onDismissRequest = {},
                    modifier = Modifier.testTag("fallback-sheet")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val pixels = composeRule.onNodeWithTag("fallback-sheet")
            .captureToImage()
            .toPixelMap()
        assertTrue(
            "DIALOG fallback color should show when image is unavailable",
            pixels.containsColorInRegion(
                Color.Magenta,
                startY = pixels.height / 2,
                endY = pixels.height
            )
        )
    }

    private fun resolveTheme(
        surfaces: ThemeSurfaceManifest,
        generation: Long
    ): ResolvedTheme {
        val result = ThemeResolver.resolveStrict(
            manifest = DefaultThemeManifest.copy(surfaces = surfaces),
            resources = provider,
            themeGeneration = generation,
            imageDecodeProfile = testDecodeProfile(256)
        )
        assertTrue(
            "Strict resolution failed: ${(result as? ThemeResolveResult.Failure)?.error}",
            result is ThemeResolveResult.Success
        )
        return (result as ThemeResolveResult.Success).theme
    }

    private fun assertImageSurface(
        surface: ResolvedSurface,
        role: SurfaceRole,
        format: ThemeImageFormat,
        width: Int,
        height: Int
    ): ThemeImageAsset {
        assertTrue(surface is ResolvedSurface.ImageSurface)
        surface as ResolvedSurface.ImageSurface
        assertEquals(role, surface.role)
        assertEquals(format, surface.image.format)
        assertEquals(width, surface.image.originalWidth)
        assertEquals(height, surface.image.originalHeight)
        assertTrue(surface.image.imageBitmap != null)
        return surface.image
    }

    private fun testAsset(
        bitmap: Bitmap,
        role: SurfaceRole,
        keySuffix: String
    ): ThemeImageAsset = ThemeImageAsset(
        imageBitmap = bitmap.asImageBitmap(),
        originalWidth = bitmap.width,
        originalHeight = bitmap.height,
        decodedWidth = bitmap.width,
        decodedHeight = bitmap.height,
        format = ThemeImageFormat.PNG,
        alphaCapable = true,
        cacheKey = ThemeImageCacheKey(
            themeId = "instrumented",
            themeVersion = "1",
            themeGeneration = 1L,
            role = role,
            relativePath = "surfaces/${role.name.lowercase()}/$keySuffix.png",
            fileSize = 1L,
            lastModified = 1L,
            decodeBucket = "test"
        )
    )

    private fun testDecodeProfile(maximumSide: Int): ThemeImageDecodeProfile =
        ThemeImageDecodeProfile(
            background = ThemeImageDecodeBucket(
                maximumSide,
                maximumSide,
                "background-test-$maximumSide"
            ),
            card = ThemeImageDecodeBucket(
                maximumSide,
                maximumSide,
                "card-test-$maximumSide"
            ),
            dialog = ThemeImageDecodeBucket(
                maximumSide,
                maximumSide,
                "dialog-test-$maximumSide"
            )
        )

    private fun writeBitmap(
        relativePath: String,
        width: Int,
        height: Int,
        color: Int,
        format: Bitmap.CompressFormat
    ) {
        writeBytes(
            relativePath,
            encodeBitmap(width, height, color, format)
        )
    }

    private fun encodeBitmap(
        width: Int,
        height: Int,
        color: Int,
        format: Bitmap.CompressFormat
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(format, 100, output))
            output.toByteArray()
        }
    }

    private fun writeBytes(relativePath: String, bytes: ByteArray) {
        File(root, relativePath).apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun image(
        fallbackColor: String,
        relativePath: String
    ): ThemeSurfaceDefinition = ThemeSurfaceDefinition(
        type = ThemeSurfaceType.IMAGE,
        color = fallbackColor,
        file = relativePath
    )
}

private fun ThemeImageLoadResult.successAsset(
    role: SurfaceRole
): ThemeImageAsset {
    assertTrue(
        "Image load failed: ${(this as? ThemeImageLoadResult.Failure)?.error}",
        this is ThemeImageLoadResult.Success
    )
    return (this as ThemeImageLoadResult.Success).images.getValue(role)
}

private fun insertPngAnimationChunk(png: ByteArray): ByteArray {
    val insertionOffset = 8 + 4 + 4 + 13 + 4
    val animationChunk = byteArrayOf(
        0, 0, 0, 8,
        'a'.code.toByte(),
        'c'.code.toByte(),
        'T'.code.toByte(),
        'L'.code.toByte(),
        0, 0, 0, 1,
        0, 0, 0, 0,
        0, 0, 0, 0
    )
    return png.copyOfRange(0, insertionOffset) +
        animationChunk +
        png.copyOfRange(insertionOffset, png.size)
}

private fun animatedWebpHeader(width: Int, height: Int): ByteArray {
    val widthMinusOne = width - 1
    val heightMinusOne = height - 1
    return byteArrayOf(
        'R'.code.toByte(),
        'I'.code.toByte(),
        'F'.code.toByte(),
        'F'.code.toByte(),
        22, 0, 0, 0,
        'W'.code.toByte(),
        'E'.code.toByte(),
        'B'.code.toByte(),
        'P'.code.toByte(),
        'V'.code.toByte(),
        'P'.code.toByte(),
        '8'.code.toByte(),
        'X'.code.toByte(),
        10, 0, 0, 0,
        0x02, 0, 0, 0,
        (widthMinusOne and 0xFF).toByte(),
        ((widthMinusOne ushr 8) and 0xFF).toByte(),
        ((widthMinusOne ushr 16) and 0xFF).toByte(),
        (heightMinusOne and 0xFF).toByte(),
        ((heightMinusOne ushr 8) and 0xFF).toByte(),
        ((heightMinusOne ushr 16) and 0xFF).toByte()
    )
}

private fun assertColorNear(expected: Color, actual: Color) {
    assertEquals(expected.red, actual.red, 0.08f)
    assertEquals(expected.green, actual.green, 0.08f)
    assertEquals(expected.blue, actual.blue, 0.08f)
}

private fun androidx.compose.ui.graphics.PixelMap.containsColor(
    expected: Color
): Boolean {
    for (y in 0 until height) {
        for (x in 0 until width) {
            val color = this[x, y]
            if (
                kotlin.math.abs(color.red - expected.red) < 0.08f &&
                kotlin.math.abs(color.green - expected.green) < 0.08f &&
                kotlin.math.abs(color.blue - expected.blue) < 0.08f
            ) {
                return true
            }
        }
    }
    return false
}

private fun androidx.compose.ui.graphics.PixelMap.containsColorInRegion(
    expected: Color,
    startY: Int,
    endY: Int
): Boolean {
    val clampedEnd = endY.coerceAtMost(height)
    for (y in startY.coerceAtLeast(0) until clampedEnd) {
        for (x in 0 until width) {
            val color = this[x, y]
            if (
                kotlin.math.abs(color.red - expected.red) < 0.08f &&
                kotlin.math.abs(color.green - expected.green) < 0.08f &&
                kotlin.math.abs(color.blue - expected.blue) < 0.08f
            ) {
                return true
            }
        }
    }
    return false
}
