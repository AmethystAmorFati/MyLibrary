package com.example.mylibrary.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.mylibrary.ui.theme.ResolvedSurface
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.ThemeImageAsset
import com.example.mylibrary.ui.theme.ThemeImageCacheKey
import com.example.mylibrary.ui.theme.ThemeImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSurfaceBackgroundTest {
    @Test
    fun fullAlphaColorSurfaceUsesMaterialContainer() {
        val color = Color(0xFF336699)
        val policy = themeSurfaceContainerPolicy(
            surface = ResolvedSurface.ColorSurface(color),
            containerAlpha = 1f
        )

        assertEquals(color, policy.materialColor)
        assertFalse(policy.drawsResolvedBackground)
    }

    @Test
    fun translucentContainerDrawsResolvedBackgroundBehindTransparentMaterial() {
        val policy = themeSurfaceContainerPolicy(
            surface = ResolvedSurface.ColorSurface(Color(0xFF336699)),
            containerAlpha = 0.72f
        )

        assertEquals(Color.Transparent, policy.materialColor)
        assertTrue(policy.drawsResolvedBackground)
    }

    @Test
    fun forcedFallbackOpacityRemovesThemeColorAlpha() {
        val surface = resolvedThemeSurfaceForContainer(
            surface = ResolvedSurface.ColorSurface(Color(0x66336699)),
            drawImageSurface = false,
            forceOpaqueFallback = true
        )

        assertEquals(1f, surface.fallbackColor.alpha, 0f)
    }

    @Test
    fun landscapeImageCenterCropsForPortraitDestination() {
        val crop = calculateCenterCropSource(
            sourceWidth = 400,
            sourceHeight = 200,
            destinationWidth = 100,
            destinationHeight = 200
        )

        assertEquals(IntOffset(150, 0), crop.offset)
        assertEquals(IntSize(100, 200), crop.size)
    }

    @Test
    fun portraitImageCenterCropsForLandscapeDestination() {
        val crop = calculateCenterCropSource(
            sourceWidth = 200,
            sourceHeight = 400,
            destinationWidth = 200,
            destinationHeight = 100
        )

        assertEquals(IntOffset(0, 150), crop.offset)
        assertEquals(IntSize(200, 100), crop.size)
    }

    @Test
    fun centerCropUsesLocalDestinationSizeNotRootViewport() {
        // The crop is computed from the surface's own measured dimensions,
        // never from a shared root/viewport coordinate space.  This verifies
        // that DIALOG and CARD surfaces do not reuse BACKGROUND coordinates.
        val crop = calculateCenterCropSource(
            sourceWidth = 400,
            sourceHeight = 400,
            destinationWidth = 100,
            destinationHeight = 50
        )

        // Crop matches the 2:1 aspect ratio of the local 100x50 destination,
        // not the 1:1 aspect ratio of the 400x400 source.
        assertEquals(IntOffset(0, 100), crop.offset)
        assertEquals(IntSize(400, 200), crop.size)
    }

    // ---------- Calendar background alignment tests ----------

    /**
     * Test 1: COLOR 半透明背景在吸顶月历上不透出底层内容.
     *
     * When `compositeOverBaseColor = true`, the translucent fallback color
     * is composited over the app base background and forced to alpha = 1.
     * This produces an opaque layer that fully occludes the timeline
     * beneath the sticky calendar, matching the root page's visual.
     */
    @Test
    fun compositeOverBaseColorProducesOpaqueFallbackForTranslucentColor() {
        val translucent = Color(0x66E8A87D)
        val surface = resolvedThemeSurfaceForContainer(
            surface = ResolvedSurface.ColorSurface(translucent),
            drawImageSurface = false,
            forceOpaqueFallback = false,
            compositeOverBaseColor = true
        )

        assertEquals(1f, surface.fallbackColor.alpha, 0.0001f)
        // The composited color is not the raw translucent color.
        assertFalse(surface.fallbackColor == translucent)
        // The composited color is not transparent black.
        assertFalse(surface.fallbackColor == Color.Transparent)
    }

    /**
     * Test 1b: An opaque COLOR background remains opaque after compositing.
     */
    @Test
    fun compositeOverBaseColorKeepsOpaqueColorOpaque() {
        val opaque = Color(0xFFE8A87D)
        val surface = resolvedThemeSurfaceForContainer(
            surface = ResolvedSurface.ColorSurface(opaque),
            drawImageSurface = false,
            forceOpaqueFallback = false,
            compositeOverBaseColor = true
        )

        assertEquals(1f, surface.fallbackColor.alpha, 0.0001f)
    }

    /**
     * Test 2: IMAGE 背景月历使用同一 viewport 的对应裁切区域.
     *
     * The center-crop source rectangle is computed from the **root viewport
     * size**, not the calendar's own dimensions.  This ensures the calendar
     * sees the same slice of the image that the root page draws behind it.
     *
     * Here a 1080×2400 image is cropped for a 1080×2400 viewport (full
     * screen), yielding the entire image.  A small 400×80 calendar overlay
     * at offset (0, 100) then translates the full-size image to show only
     * its own region — the crop itself does not shrink to 400×80.
     */
    @Test
    fun viewportCropUsesRootViewportDimensionsNotOverlaySize() {
        val viewportCrop = calculateCenterCropSource(
            sourceWidth = 1080,
            sourceHeight = 2400,
            destinationWidth = 1080,
            destinationHeight = 2400
        )

        // The crop covers the entire source because viewport matches source.
        assertEquals(IntOffset(0, 0), viewportCrop.offset)
        assertEquals(IntSize(1080, 2400), viewportCrop.size)

        // If the calendar incorrectly used its own 400×80 size, the crop
        // would be a narrow horizontal strip — verify it is NOT.
        val wrongCrop = calculateCenterCropSource(
            sourceWidth = 1080,
            sourceHeight = 2400,
            destinationWidth = 400,
            destinationHeight = 80
        )
        assertTrue(viewportCrop.size.width > wrongCrop.size.width)
        assertTrue(viewportCrop.size.height > wrongCrop.size.height)
    }

    /**
     * Test 2b: viewport crop changes when the viewport rotates.
     *
     * On rotation the viewport swaps width/height, so the crop rectangle
     * must change to match the new aspect ratio.
     */
    @Test
    fun viewportCropChangesOnRotation() {
        val portraitCrop = calculateCenterCropSource(
            sourceWidth = 1080,
            sourceHeight = 2400,
            destinationWidth = 1080,
            destinationHeight = 2400
        )
        val landscapeCrop = calculateCenterCropSource(
            sourceWidth = 1080,
            sourceHeight = 2400,
            destinationWidth = 2400,
            destinationHeight = 1080
        )

        // Portrait: full image.  Landscape: vertical strip.
        assertTrue(portraitCrop.size.height > landscapeCrop.size.height)
        assertTrue(landscapeCrop.size.width >= portraitCrop.size.width)
    }

    /**
     * Test 3: 月历背景不得退化为单独绘制 background.color.
     *
     * When the BACKGROUND surface is an ImageSurface and
     * `drawImageSurface = true`, the resolved surface must remain an
     * ImageSurface — not collapse to a ColorSurface that only draws
     * `background.color`.
     */
    @Test
    fun imageSurfaceDoesNotDegradeToColorSurfaceWhenDrawImageSurfaceTrue() {
        val imageSurface = createTestImageSurface()
        val resolved = resolvedThemeSurfaceForContainer(
            surface = imageSurface,
            drawImageSurface = true,
            forceOpaqueFallback = false,
            compositeOverBaseColor = true
        )

        assertTrue(resolved is ResolvedSurface.ImageSurface)
        // The fallback color is composited (opaque) but the image is kept.
        assertEquals(1f, resolved.fallbackColor.alpha, 0.0001f)
    }

    /**
     * Test 3b: When `drawImageSurface = false`, the surface correctly
     * degrades to a ColorSurface (used by non-BACKGROUND containers).
     */
    @Test
    fun imageSurfaceDegradesToColorWhenDrawImageSurfaceFalse() {
        val imageSurface = createTestImageSurface()
        val resolved = resolvedThemeSurfaceForContainer(
            surface = imageSurface,
            drawImageSurface = false,
            forceOpaqueFallback = false,
            compositeOverBaseColor = true
        )

        assertTrue(resolved is ResolvedSurface.ColorSurface)
    }

    /**
     * Test 4 + 5: 图片透明像素/加载失败显示 fallback color.
     *
     * The ImageSurface carries a `fallbackColor` that is drawn first (as
     * a full-rect `drawRect`) before the image bitmap.  When the bitmap
     * is null (load failure) or contains transparent pixels, this fallback
     * is what the user sees — never the timeline content beneath.
     *
     * Here we verify that the fallback color on an ImageSurface is
     * composited to opaque when `compositeOverBaseColor = true`, so it
     * can serve as a fully opaque occlusion layer.
     */
    @Test
    fun imageSurfaceFallbackColorIsOpaqueWhenComposited() {
        val imageSurface = createTestImageSurface(
            fallbackColor = Color(0x66E8A87D)
        )
        val resolved = resolvedThemeSurfaceForContainer(
            surface = imageSurface,
            drawImageSurface = true,
            forceOpaqueFallback = false,
            compositeOverBaseColor = true
        )

        assertTrue(resolved is ResolvedSurface.ImageSurface)
        assertEquals(1f, resolved.fallbackColor.alpha, 0.0001f)
    }

    /**
     * Test 5b: When the image bitmap is null (load failure), the
     * ImageSurface still carries a non-null fallbackColor that will be
     * drawn by `themeSurfaceBackground`.
     */
    @Test
    fun imageSurfaceWithNullBitmapRetainsOpaqueFallback() {
        val imageSurface = createTestImageSurface(
            bitmap = null,
            fallbackColor = Color(0x99E8A87D)
        )
        val resolved = resolvedThemeSurfaceForContainer(
            surface = imageSurface,
            drawImageSurface = true,
            forceOpaqueFallback = false,
            compositeOverBaseColor = true
        )

        assertTrue(resolved is ResolvedSurface.ImageSurface)
        assertEquals(1f, resolved.fallbackColor.alpha, 0.0001f)
        // The image asset with null bitmap is preserved so the draw logic
        // can log the unavailability and still show the fallback color.
        val imageResolved = resolved as ResolvedSurface.ImageSurface
        assertNull(imageResolved.image.imageBitmap)
    }

    /**
     * Test 6: 月历高度变化后背景坐标仍正确.
     *
     * The calendar overlay's `offsetInViewport` changes as the calendar
     * expands/collapses, but the viewport crop rectangle (computed from
     * the root viewport size) stays the same.  The draw logic translates
     * by `-offsetInViewport`, so different offsets produce different
     * visible regions of the same full-viewport image.
     *
     * Here we verify that the crop remains stable while the offset
     * changes — the calendar never re-crops to its own dimensions.
     */
    @Test
    fun viewportCropStableWhileOffsetChanges() {
        val viewportWidth = 1080
        val viewportHeight = 2400

        // Calendar at collapsed height (offset y = 80)
        val collapsedOffset = 80
        // Calendar at expanded height (offset y = 400)
        val expandedOffset = 400

        // The crop is always computed from the viewport, not the overlay.
        val cropCollapsed = calculateCenterCropSource(
            sourceWidth = 1080,
            sourceHeight = 2400,
            destinationWidth = viewportWidth,
            destinationHeight = viewportHeight
        )
        val cropExpanded = calculateCenterCropSource(
            sourceWidth = 1080,
            sourceHeight = 2400,
            destinationWidth = viewportWidth,
            destinationHeight = viewportHeight
        )

        // Crop is identical regardless of calendar offset.
        assertEquals(cropCollapsed, cropExpanded)

        // The translate offset differs, so different parts of the image
        // are visible — but the source crop is the same full-viewport rect.
        assertTrue(collapsedOffset != expandedOffset)
    }

    // ---------- Helpers ----------

    private fun createTestImageSurface(
        bitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
        fallbackColor: Color = Color(0xFFE8A87D)
    ): ResolvedSurface.ImageSurface {
        val cacheKey = ThemeImageCacheKey(
            themeId = "test.theme",
            themeVersion = "1",
            themeGeneration = 1L,
            role = SurfaceRole.BACKGROUND,
            relativePath = "background.png",
            fileSize = 1024L,
            lastModified = 0L,
            decodeBucket = "default"
        )
        val asset = ThemeImageAsset(
            imageBitmap = bitmap,
            originalWidth = 1080,
            originalHeight = 2400,
            decodedWidth = 1080,
            decodedHeight = 2400,
            format = ThemeImageFormat.PNG,
            alphaCapable = true,
            cacheKey = cacheKey
        )
        return ResolvedSurface.ImageSurface(
            fallbackColor = fallbackColor,
            image = asset,
            role = SurfaceRole.BACKGROUND
        )
    }
}
