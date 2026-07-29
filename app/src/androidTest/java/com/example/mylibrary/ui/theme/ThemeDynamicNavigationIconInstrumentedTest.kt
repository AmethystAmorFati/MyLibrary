package com.example.mylibrary.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.ui.navigation.AppNavigationIcon
import com.example.mylibrary.ui.navigation.NavigationIconAsset
import com.example.mylibrary.ui.navigation.NavigationIconResource
import com.example.mylibrary.ui.navigation.NavigationIconSlot
import com.example.mylibrary.ui.navigation.NavigationIconState
import com.example.mylibrary.ui.navigation.ResolvedNavigationIconResolver
import com.example.mylibrary.ui.navigation.ThemeIconRendering
import com.example.mylibrary.ui.navigation.ThemeNavigationIconCacheKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeDynamicNavigationIconInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var root: File
    private lateinit var provider: ThemeResourceProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(
            context.cacheDir,
            "theme-navigation-instrumented-${System.nanoTime()}"
        ).apply { check(mkdirs()) }
        provider = DirectoryThemeResourceProvider(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun transparentPngStaticWebpAndBoundsSamplingDecodeSuccessfully() {
        writeIcon(
            "icons/home.png",
            width = 512,
            height = 384,
            centerColor = AndroidColor.RED,
            format = Bitmap.CompressFormat.PNG
        )
        writeIcon(
            "icons/home_selected.webp",
            width = 96,
            height = 64,
            centerColor = AndroidColor.BLUE,
            format = webpFormat()
        )

        val resolver = resolve(
            ThemeNavigationManifest(
                rendering = ThemeIconRendering.ORIGINAL,
                home = NavigationIconDefinition(
                    normal = "icons/home.png",
                    selected = "icons/home_selected.webp"
                )
            ),
            generation = 11L
        )
        val home = resolver.resolve(NavigationIconSlot.HOME)
        val normal = home.normal as NavigationIconAsset.Bitmap
        val selected = home.selected as NavigationIconAsset.Bitmap

        assertTrue(requireNotNull(normal.imageBitmap).width <= 128)
        assertTrue(requireNotNull(normal.imageBitmap).height <= 128)
        assertEquals(ThemeIconRendering.ORIGINAL, normal.rendering)
        assertEquals(NavigationIconState.NORMAL, normal.cacheKey.state)
        assertEquals(NavigationIconState.SELECTED, selected.cacheKey.state)
        assertEquals(11L, normal.cacheKey.themeGeneration)
        assertTrue(
            requireNotNull(normal.imageBitmap).toPixelMap()[0, 0].alpha < 0.1f
        )
        val selectedPixels = requireNotNull(selected.imageBitmap).toPixelMap()
        val center = selectedPixels[
            selectedPixels.width / 2,
            selectedPixels.height / 2
        ]
        assertTrue(center.blue > 0.8f)
    }

    @Test
    fun originalKeepsRgbWhileMonochromeUsesAlphaMaskTint() {
        writeIcon(
            "icons/home.png",
            width = 64,
            height = 64,
            centerColor = AndroidColor.RED,
            format = Bitmap.CompressFormat.PNG
        )
        val original = resolve(
            ThemeNavigationManifest(
                rendering = ThemeIconRendering.ORIGINAL,
                home = NavigationIconDefinition("icons/home.png", null)
            ),
            generation = 21L
        )
        val monochrome = resolve(
            ThemeNavigationManifest(
                rendering = ThemeIconRendering.MONOCHROME,
                home = NavigationIconDefinition("icons/home.png", null)
            ),
            generation = 22L
        )

        composeRule.setContent {
            AppNavigationIcon(
                slot = NavigationIconSlot.HOME,
                selected = false,
                iconResolver = original,
                contentDescription = "首页",
                tint = Color.Green,
                modifier = Modifier
                    .testTag("original")
                    .size(48.dp)
            )
            AppNavigationIcon(
                slot = NavigationIconSlot.HOME,
                selected = false,
                iconResolver = monochrome,
                contentDescription = "资料库",
                tint = Color.Green,
                modifier = Modifier
                    .testTag("monochrome")
                    .size(48.dp)
            )
        }

        val originalPixels = composeRule.onNodeWithTag("original")
            .captureToImage()
            .toPixelMap()
        val monoPixels = composeRule.onNodeWithTag("monochrome")
            .captureToImage()
            .toPixelMap()
        val originalCenter = originalPixels[
            originalPixels.width / 2,
            originalPixels.height / 2
        ]
        val monoCenter = monoPixels[
            monoPixels.width / 2,
            monoPixels.height / 2
        ]
        assertTrue(originalCenter.red > 0.8f && originalCenter.green < 0.2f)
        assertTrue(monoCenter.green > 0.8f && monoCenter.red < 0.2f)
    }

    @Test
    fun selectedFallbackRoleMappingAndBuiltInMixAreFrozen() {
        writeIcon(
            "icons/library.png",
            64,
            64,
            AndroidColor.MAGENTA,
            Bitmap.CompressFormat.PNG
        )
        val resolver = resolve(
            ThemeNavigationManifest(
                library = NavigationIconDefinition(
                    normal = "icons/library.png",
                    selected = null
                )
            ),
            generation = 31L
        )
        val library = resolver.resolve(NavigationIconSlot.LIBRARY)

        assertTrue(library.normal is NavigationIconAsset.Bitmap)
        assertSame(
            library.forSelection(isSelected = false),
            library.forSelection(isSelected = true)
        )
        assertTrue(
            resolver.resolve(NavigationIconSlot.HOME).normal is
                NavigationIconAsset.Vector
        )
        assertTrue(
            resolver.resolve(NavigationIconSlot.STATISTICS).normal is
                NavigationIconAsset.Vector
        )
        assertTrue(
            resolver.resolve(NavigationIconSlot.SETTINGS).normal is
                NavigationIconAsset.Vector
        )
    }

    @Test
    fun concurrentLoadsMergeAndCacheIsLruGenerationAwareAndReferenceSafe() {
        writeIcon(
            "icons/home.png",
            256,
            256,
            AndroidColor.CYAN,
            Bitmap.CompressFormat.PNG
        )
        val navigation = ThemeNavigationManifest(
            home = NavigationIconDefinition("icons/home.png", null)
        )
        val cache = ThemeNavigationIconCache(maximumEntries = 2)
        val decodeCount = AtomicInteger()
        val decoder = ThemeNavigationBitmapDecoder { image, bucket, key ->
            decodeCount.incrementAndGet()
            Thread.sleep(100L)
            AndroidThemeNavigationBitmapDecoder.decode(image, bucket, key)
        }
        val firstLoader = loader(
            navigation = navigation,
            generation = 1L,
            cache = cache,
            decoder = decoder
        )
        val executor = Executors.newFixedThreadPool(2)
        val first: NavigationIconAsset.Bitmap
        try {
            val one = executor.submit<ThemeNavigationIconLoadResult> {
                firstLoader.load(navigation)
            }
            val two = executor.submit<ThemeNavigationIconLoadResult> {
                firstLoader.load(navigation)
            }
            first = one.get(5, TimeUnit.SECONDS).homeBitmap()
            val repeated = two.get(5, TimeUnit.SECONDS).homeBitmap()
            assertSame(first, repeated)
            assertEquals(1, decodeCount.get())
        } finally {
            executor.shutdownNow()
        }

        val second = loader(
            navigation,
            2L,
            cache,
            decoder
        ).load(navigation).homeBitmap()
        val third = loader(
            navigation,
            3L,
            cache,
            decoder
        ).load(navigation).homeBitmap()

        assertNotSame(first, second)
        assertNotSame(second, third)
        assertEquals(3, decodeCount.get())
        assertEquals(2, cache.entryCount())
        assertTrue(
            cache.byteCount() <=
                ThemeResourceLimits.MAX_NAVIGATION_IMAGE_CACHE_BYTES
        )
        cache.clear()
        assertEquals(0, cache.entryCount())
        assertTrue(requireNotNull(first.imageBitmap).width > 0)
    }

    @Test
    fun runtimeUnavailableAssetFallsBackAndKeepsAppContentDescription() {
        val unavailable = NavigationIconAsset.Bitmap(
            imageBitmap = null,
            rendering = ThemeIconRendering.ORIGINAL,
            cacheKey = ThemeNavigationIconCacheKey(
                themeId = "runtime.test",
                themeVersion = "1",
                themeGeneration = 1L,
                slot = NavigationIconSlot.HOME,
                state = NavigationIconState.NORMAL,
                relativePath = "icons/home.png",
                canonicalPath = File(root, "icons/home.png").absolutePath,
                fileSize = 1L,
                lastModified = 1L,
                decodeBucket = "test"
            ),
            unavailableReason = "File disappeared after resolution"
        )
        val resolver = ResolvedNavigationIconResolver(
            mapOf(
                NavigationIconSlot.HOME to NavigationIconResource(
                    normal = unavailable
                )
            )
        )

        composeRule.setContent {
            AppNavigationIcon(
                slot = NavigationIconSlot.HOME,
                selected = false,
                iconResolver = resolver,
                contentDescription = "首页",
                tint = Color.Black,
                modifier = Modifier
                    .testTag("runtime-fallback")
                    .size(48.dp)
            )
        }

        composeRule.onNodeWithTag("runtime-fallback")
            .assertContentDescriptionEquals("首页")
            .captureToImage()
    }

    private fun resolve(
        navigation: ThemeNavigationManifest,
        generation: Long
    ): ResolvedNavigationIconResolver {
        val result = ThemeResolver.resolveStrict(
            manifest = DefaultThemeManifest.copy(
                navigationIcons = navigation
            ),
            resources = provider,
            themeGeneration = generation
        )
        assertTrue(
            "Strict resolution failed: " +
                (result as? ThemeResolveResult.Failure)?.error,
            result is ThemeResolveResult.Success
        )
        val resolver =
            (result as ThemeResolveResult.Success).theme.navigationIconResolver
        assertTrue(resolver is ResolvedNavigationIconResolver)
        return resolver as ResolvedNavigationIconResolver
    }

    private fun loader(
        navigation: ThemeNavigationManifest,
        generation: Long,
        cache: ThemeNavigationIconCache,
        decoder: ThemeNavigationBitmapDecoder
    ): ThemeNavigationIconLoader {
        check(navigation.entries().isNotEmpty())
        return ThemeNavigationIconLoader(
            resources = provider,
            themeId = "navigation.cache",
            themeVersion = "1",
            themeGeneration = generation,
            cache = cache,
            decoder = decoder
        )
    }

    private fun writeIcon(
        path: String,
        width: Int,
        height: Int,
        centerColor: Int,
        format: Bitmap.CompressFormat
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.TRANSPARENT)
        val left = width / 4
        val top = height / 4
        for (y in top until height - top) {
            for (x in left until width - left) {
                bitmap.setPixel(x, y, centerColor)
            }
        }
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(format, 100, output))
            output.toByteArray()
        }
        File(root, path).apply {
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
}

private fun ThemeNavigationIconLoadResult.homeBitmap(): NavigationIconAsset.Bitmap {
    assertTrue(
        "Icon load failed: ${(this as? ThemeNavigationIconLoadResult.Failure)?.error}",
        this is ThemeNavigationIconLoadResult.Success
    )
    return (
        (this as ThemeNavigationIconLoadResult.Success)
            .resources
            .getValue(NavigationIconSlot.HOME)
            .normal as NavigationIconAsset.Bitmap
        )
}
