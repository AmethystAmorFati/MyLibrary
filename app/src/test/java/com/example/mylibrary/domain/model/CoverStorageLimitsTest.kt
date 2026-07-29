package com.example.mylibrary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverStorageLimitsTest {
    @Test
    fun acceptedBoundaryMatchesBackupV4CoverLimit() {
        val metadata = validateCoverMetadata(
            byteSize = CoverStorageLimits.MAX_SOURCE_BYTES,
            width = 4_000,
            height = 4_000,
            format = "webp"
        )

        assertEquals(32L * 1024L * 1024L, metadata.byteSize)
        assertEquals(16_000_000L, metadata.width.toLong() * metadata.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun byteLimitIsEnforcedBeforeStorage() {
        validateCoverMetadata(
            CoverStorageLimits.MAX_SOURCE_BYTES + 1L,
            100,
            100,
            "jpg"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun widthLimitIsIndependentFromTotalPixels() {
        validateCoverMetadata(100, 8_193, 1, "png")
    }

    @Test(expected = IllegalArgumentException::class)
    fun heightLimitIsIndependentFromTotalPixels() {
        validateCoverMetadata(100, 1, 8_193, "png")
    }

    @Test(expected = IllegalArgumentException::class)
    fun totalPixelLimitUsesLongArithmetic() {
        validateCoverMetadata(100, 8_192, 8_192, "webp")
    }

    @Test
    fun declaredExtensionAndMimeMustMatchRealFormat() {
        assertTrue(coverFormatMatchesExtension("jpg", "jpeg"))
        assertTrue(coverFormatMatchesMimeType("webp", "image/webp"))
        assertFalse(coverFormatMatchesExtension("jpg", "png"))
        assertFalse(coverFormatMatchesMimeType("png", "image/jpeg"))
        assertFalse(coverFormatMatchesMimeType("jpg", "image/heic"))
    }
}
