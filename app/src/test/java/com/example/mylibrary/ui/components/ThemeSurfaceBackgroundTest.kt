package com.example.mylibrary.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.mylibrary.ui.theme.ResolvedSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
