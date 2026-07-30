package com.example.mylibrary.export.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.export.annualposter.AnnualPosterItem
import com.example.mylibrary.export.annualposter.AnnualPosterRenderer
import com.example.mylibrary.export.annualposter.AnnualPosterSnapshot
import com.example.mylibrary.export.annualposter.ANNUAL_POSTER_WIDTH
import com.example.mylibrary.export.calendar.CalendarExportRenderer
import com.example.mylibrary.export.calendar.buildCalendarExportSnapshot
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualExportRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun calendarDefaultThemeRendersExactArgbCanvas() = runBlocking {
        val bitmap = CalendarExportRenderer.render(
            context = context,
            snapshot = buildCalendarExportSnapshot(
                YearMonth.of(2026, 7),
                emptyList()
            ),
            theme = theme()
        )
        try {
            assertEquals(VISUAL_EXPORT_WIDTH, bitmap.width)
            assertEquals(VISUAL_EXPORT_HEIGHT, bitmap.height)
            assertEquals(android.graphics.Bitmap.Config.ARGB_8888, bitmap.config)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun resolvedAnnualCoverFillsDynamicCanvasWithoutCroppingOrBackground() = runBlocking {
        val relativePath = "images/annual-renderer-${System.nanoTime()}.png"
        val file = File(context.filesDir, relativePath)
        writeSolidPng(file, 200, 300, Color.RED)
        try {
            val bitmap = AnnualPosterRenderer.render(
                context = context,
                snapshot = AnnualPosterSnapshot(
                    year = 2026,
                    category = AnnualPosterCategory.BOOK,
                    items = listOf(
                        AnnualPosterItem(
                            itemId = 1,
                            typeId = ItemTypeKind.BOOK_TYPE_ID,
                            title = "Valid cover",
                            coverPath = relativePath,
                            thumbnailPath = null,
                            firstActivityDate = 1,
                            firstRecordCreatedAt = 1,
                            resolvedCoverPath = relativePath,
                            resolvedCoverWidth = 200,
                            resolvedCoverHeight = 300
                        )
                    )
                )
            )
            try {
                assertEquals(ANNUAL_POSTER_WIDTH, bitmap.width)
                assertEquals(1_620, bitmap.height)
                assertEquals(Color.RED, bitmap.getPixel(0, 0))
                assertEquals(
                    Color.RED,
                    bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                )
            } finally {
                bitmap.recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun adjacentAnnualCoversFillTheRowWithoutAVisiblePixelSeam() = runBlocking {
        val suffix = System.nanoTime()
        val firstPath = "images/annual-seam-first-$suffix.png"
        val secondPath = "images/annual-seam-second-$suffix.png"
        val firstFile = File(context.filesDir, firstPath)
        val secondFile = File(context.filesDir, secondPath)
        writeSolidPng(firstFile, 200, 300, Color.RED)
        writeSolidPng(secondFile, 200, 300, Color.BLUE)
        try {
            val bitmap = AnnualPosterRenderer.render(
                context = context,
                snapshot = AnnualPosterSnapshot(
                    year = 2026,
                    category = AnnualPosterCategory.ALL,
                    items = listOf(
                        resolvedAnnualItem(1, firstPath, 200, 300),
                        resolvedAnnualItem(2, secondPath, 200, 300)
                    )
                )
            )
            try {
                assertEquals(ANNUAL_POSTER_WIDTH, bitmap.width)
                assertEquals(810, bitmap.height)
                val middleY = bitmap.height / 2
                assertTrue(
                    (0 until bitmap.width).all { x ->
                        Color.alpha(bitmap.getPixel(x, middleY)) == 255
                    }
                )
                assertEquals(Color.RED, bitmap.getPixel(539, middleY))
                assertEquals(Color.BLUE, bitmap.getPixel(540, middleY))
            } finally {
                bitmap.recycle()
            }
        } finally {
            firstFile.delete()
            secondFile.delete()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun annualRendererRejectsUnresolvedItemsInsteadOfDrawingPlaceholders() =
        runBlocking {
            AnnualPosterRenderer.render(
                context = context,
                snapshot = AnnualPosterSnapshot(
                    year = 2026,
                    category = AnnualPosterCategory.BOOK,
                    items = listOf(
                        AnnualPosterItem(
                            itemId = 1,
                            typeId = ItemTypeKind.BOOK_TYPE_ID,
                            title = "Missing cover",
                            coverPath = null,
                            thumbnailPath = null,
                            firstActivityDate = 1,
                            firstRecordCreatedAt = 1
                        )
                    )
                )
            )
        }

    @Test
    fun dynamicAnnualPngCanBeEncodedAndDecodedAtItsContentHeight() = runBlocking {
        val relativePath = "images/annual-png-${System.nanoTime()}.png"
        val sourceFile = File(context.filesDir, relativePath)
        writeSolidPng(sourceFile, 200, 300, Color.GREEN)
        val store = ExportFileStore(context)
        var outputFile: File? = null
        try {
            val rendered = AnnualPosterRenderer.render(
                context = context,
                snapshot = AnnualPosterSnapshot(
                    year = 2026,
                    category = AnnualPosterCategory.BOOK,
                    items = listOf(
                        AnnualPosterItem(
                            itemId = 1,
                            typeId = ItemTypeKind.BOOK_TYPE_ID,
                            title = "PNG cover",
                            coverPath = relativePath,
                            thumbnailPath = null,
                            firstActivityDate = 1,
                            firstRecordCreatedAt = 1,
                            resolvedCoverPath = relativePath,
                            resolvedCoverWidth = 200,
                            resolvedCoverHeight = 300
                        )
                    )
                )
            )
            outputFile = try {
                store.writeTemporaryPng("annual-dynamic.png", rendered)
            } finally {
                rendered.recycle()
            }
            val decoded = BitmapFactory.decodeFile(
                requireNotNull(outputFile).absolutePath
            )
            assertNotNull(decoded)
            try {
                assertEquals(ANNUAL_POSTER_WIDTH, decoded.width)
                assertEquals(1_620, decoded.height)
            } finally {
                decoded.recycle()
            }
        } finally {
            store.deleteTemporary(outputFile)
            sourceFile.delete()
        }
    }

    @Test
    fun pngEncodingProducesReadableExactSizeFile() = runBlocking {
        val bitmap = CalendarExportRenderer.render(
            context = context,
            snapshot = buildCalendarExportSnapshot(
                YearMonth.of(2024, 2),
                emptyList()
            ),
            theme = theme()
        )
        val store = ExportFileStore(context)
        val file = try {
            store.writeTemporaryPng("renderer-test.png", bitmap)
        } finally {
            bitmap.recycle()
        }
        try {
            assertTrue(file.isFile)
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull(decoded)
            try {
                assertEquals(VISUAL_EXPORT_WIDTH, decoded.width)
                assertEquals(VISUAL_EXPORT_HEIGHT, decoded.height)
            } finally {
                decoded.recycle()
            }
        } finally {
            store.deleteTemporary(file)
        }
    }

    private fun theme() = VisualExportThemeSnapshot(
        backgroundColor = Color.WHITE,
        textPrimary = Color.BLACK,
        textSecondary = Color.DKGRAY,
        border = Color.GRAY,
        placeholderColor = PLACEHOLDER,
        headingTypeface = Typeface.DEFAULT_BOLD,
        contentTypeface = Typeface.DEFAULT,
        backgroundBitmap = null
    )

    private fun resolvedAnnualItem(
        id: Long,
        path: String,
        width: Int,
        height: Int
    ) = AnnualPosterItem(
        itemId = id,
        typeId = ItemTypeKind.BOOK_TYPE_ID,
        title = "Item $id",
        coverPath = path,
        thumbnailPath = null,
        firstActivityDate = id,
        firstRecordCreatedAt = id,
        resolvedCoverPath = path,
        resolvedCoverWidth = width,
        resolvedCoverHeight = height
    )

    private fun writeSolidPng(
        file: File,
        width: Int,
        height: Int,
        color: Int
    ) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            FileOutputStream(file).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val PLACEHOLDER = 0xFF607D8B.toInt()
    }
}
