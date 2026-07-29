package com.example.mylibrary.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.repository.LocalCoverImageRepository
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverImageRepositoryTest {
    @Test
    fun savesRelativeOriginalAndBoundedThumbnailThenDeletesBoth() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "cover-test-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        FileOutputStream(source).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()

        val repository = LocalCoverImageRepository(context)
        val stored = repository.save(Uri.fromFile(source).toString())
        val original = File(context.filesDir, stored.originalPath)
        val thumbnail = File(context.filesDir, stored.thumbnailPath)

        assertFalse(File(stored.originalPath).isAbsolute)
        assertFalse(File(stored.thumbnailPath).isAbsolute)
        assertTrue(original.isFile)
        assertTrue(thumbnail.isFile)
        val decoded = BitmapFactory.decodeFile(thumbnail.path)
        assertEquals(480 to 240, decoded.width to decoded.height)
        decoded.recycle()

        repository.delete(stored.originalPath, stored.thumbnailPath)
        assertFalse(original.exists())
        assertFalse(thumbnail.exists())
        source.delete()
    }

    @Test
    fun thumbnailMathPreservesAspectRatio() {
        assertEquals(
            480 to 240,
            CoverImageProcessor.calculateThumbnailSize(1200, 600, 480)
        )
        assertEquals(
            240 to 480,
            CoverImageProcessor.calculateThumbnailSize(600, 1200, 480)
        )
        assertEquals(
            320 to 200,
            CoverImageProcessor.calculateThumbnailSize(320, 200, 480)
        )
    }

    @Test
    fun importedOriginalKeepsBytesAndRegeneratesThumbnailWithNewPaths() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "backup-cover-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(300, 600, Bitmap.Config.ARGB_8888)
        FileOutputStream(source).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        val expectedBytes = source.readBytes()
        val repository = LocalCoverImageRepository(context)

        val imported = repository.importOriginal(source)
        val original = File(context.filesDir, imported.originalPath)
        val thumbnail = File(context.filesDir, imported.thumbnailPath)

        assertTrue(original.name.endsWith(".png"))
        assertTrue(original.length() <= com.example.mylibrary.backup.model.BackupArchiveLimits.MAX_COVER_BYTES)
        assertTrue(expectedBytes.contentEquals(original.readBytes()))
        assertTrue(thumbnail.isFile)
        val decoded = BitmapFactory.decodeFile(thumbnail.path)
        assertEquals(240 to 480, decoded.width to decoded.height)
        decoded.recycle()

        repository.delete(imported.originalPath, imported.thumbnailPath)
        source.delete()
    }

    @Suppress("DEPRECATION")
    @Test
    fun acceptsJpegPngAndWebpByTheirRealContent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = LocalCoverImageRepository(context)
        val formats = listOf(
            "jpg" to Bitmap.CompressFormat.JPEG,
            "png" to Bitmap.CompressFormat.PNG,
            "webp" to Bitmap.CompressFormat.WEBP
        )

        formats.forEach { (extension, format) ->
            val source = File(
                context.cacheDir,
                "cover-format-${System.nanoTime()}.$extension"
            )
            val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
            FileOutputStream(source).use { bitmap.compress(format, 90, it) }
            bitmap.recycle()

            val stored = repository.importOriginal(source)
            assertTrue(File(context.filesDir, stored.originalPath).isFile)
            repository.delete(stored.originalPath, stored.thumbnailPath)
            source.delete()
        }
    }

    @Test
    fun rejectsMismatchedExtensionAndCorruptFileWithoutStoredHalfFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = LocalCoverImageRepository(context)
        val originalDirectory = File(context.filesDir, "images/original")
        val thumbnailDirectory = File(context.filesDir, "images/thumbnail")
        val beforeOriginals = originalDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()
        val beforeThumbnails = thumbnailDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()

        val mismatched = File(context.cacheDir, "mismatch-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        FileOutputStream(mismatched).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        bitmap.recycle()
        val corrupt = File(context.cacheDir, "corrupt-${System.nanoTime()}.jpg")
        corrupt.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))

        listOf(mismatched, corrupt).forEach { source ->
            try {
                repository.importOriginal(source)
                fail("Expected invalid cover to be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }

        assertEquals(
            beforeOriginals,
            originalDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()
        )
        assertEquals(
            beforeThumbnails,
            thumbnailDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()
        )
        mismatched.delete()
        corrupt.delete()
    }
}
