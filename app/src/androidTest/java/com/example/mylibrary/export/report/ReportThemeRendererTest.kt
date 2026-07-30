package com.example.mylibrary.export.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.export.visual.VisualExportThemeSnapshotFactory
import com.example.mylibrary.ui.settings.ReportShowcaseStyle
import com.example.mylibrary.ui.theme.AppFontResolver
import com.example.mylibrary.ui.theme.DefaultResolvedTheme
import com.example.mylibrary.ui.theme.FontRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReportThemeRendererTest {
    @Test
    fun pngAndPdfPageSpecsConsumeTheSameFrozenBackgroundSnapshot() = runBlocking {
        val background = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888)
        background.eraseColor(Color.GREEN)
        val theme = VisualExportThemeSnapshot(
            backgroundColor = Color.BLUE,
            textPrimary = Color.BLACK,
            textSecondary = Color.DKGRAY,
            border = Color.GRAY,
            placeholderColor = Color.LTGRAY,
            accent = Color.RED,
            headingTypeface = Typeface.DEFAULT_BOLD,
            contentTypeface = Typeface.DEFAULT,
            backgroundBitmap = background
        )
        try {
            listOf(ReportPageSpec.PNG, ReportPageSpec.PDF_A4).forEach { spec ->
                val output = Bitmap.createBitmap(
                    spec.width,
                    spec.height,
                    Bitmap.Config.ARGB_8888
                )
                try {
                    ReportLayoutEngine.draw(
                        canvas = Canvas(output),
                        page = emptyOpeningPage(),
                        spec = spec,
                        theme = theme,
                        coverLoader = ReportCoverLoader { _, _ -> null }
                    )
                    assertEquals(
                        Color.GREEN,
                        output.getPixel(spec.width - 1, spec.height - 1)
                    )
                } finally {
                    output.recycle()
                }
            }
        } finally {
            background.recycle()
        }
    }

    @Test
    fun unavailableBackgroundKeepsFallbackColor() = runBlocking {
        val recycledBackground = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        recycledBackground.recycle()
        val output = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        try {
            ReportLayoutEngine.draw(
                canvas = Canvas(output),
                page = emptyOpeningPage(),
                spec = ReportPageSpec(
                    width = 64,
                    height = 64,
                    marginLeft = 4f,
                    marginTop = 4f,
                    marginRight = 4f,
                    marginBottom = 4f,
                    coverDecodeScale = 1f
                ),
                theme = VisualExportThemeSnapshot(
                    backgroundColor = Color.BLUE,
                    textPrimary = Color.BLACK,
                    textSecondary = Color.DKGRAY,
                    border = Color.GRAY,
                    placeholderColor = Color.LTGRAY,
                    accent = Color.RED,
                    headingTypeface = Typeface.DEFAULT_BOLD,
                    contentTypeface = Typeface.DEFAULT,
                    backgroundBitmap = recycledBackground
                ),
                coverLoader = ReportCoverLoader { _, _ -> null }
            )
            assertEquals(Color.BLUE, output.getPixel(63, 63))
        } finally {
            output.recycle()
        }
    }

    @Test
    fun snapshotFactoryCapturesSemanticColorsAndFallsBackFromBrokenFonts() {
        val brokenTheme = DefaultResolvedTheme.copy(
            fontResolver = object : AppFontResolver {
                override fun composeFontFamily(role: FontRole): FontFamily =
                    FontFamily.Default

                override fun androidTypeface(role: FontRole): Typeface =
                    error("broken test font")
            }
        )

        val snapshot = VisualExportThemeSnapshotFactory.create(brokenTheme)

        assertEquals(brokenTheme.colors.textPrimary.toArgb(), snapshot.textPrimary)
        assertEquals(brokenTheme.colors.textSecondary.toArgb(), snapshot.textSecondary)
        assertEquals(brokenTheme.colors.border.toArgb(), snapshot.border)
        assertEquals(brokenTheme.colors.accent.toArgb(), snapshot.accent)
        assertSame(Typeface.DEFAULT, snapshot.headingTypeface)
        assertSame(Typeface.DEFAULT, snapshot.contentTypeface)
    }

    @Test
    fun pdfDirectCanvasPageKeepsMarginsOpaqueAndNonAccent() = runBlocking {
        val context =
            ApplicationProvider.getApplicationContext<android.content.Context>()
        val translucentBackground = Bitmap.createBitmap(
            8,
            8,
            Bitmap.Config.ARGB_8888
        ).apply {
            eraseColor(Color.argb(80, 20, 30, 40))
        }
        val accent = Color.rgb(103, 58, 183)
        val cover = File(context.filesDir, "pdf-tests/full-report-cover.png")
        cover.parentFile?.mkdirs()
        Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).also { bitmap ->
            try {
                bitmap.eraseColor(Color.argb(72, 120, 80, 40))
                cover.outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally {
                bitmap.recycle()
            }
        }
        val theme = VisualExportThemeSnapshot(
            backgroundColor = Color.argb(90, 245, 244, 240),
            textPrimary = Color.BLACK,
            textSecondary = Color.DKGRAY,
            border = Color.GRAY,
            placeholderColor = Color.LTGRAY,
            accent = accent,
            headingTypeface = Typeface.DEFAULT_BOLD,
            contentTypeface = Typeface.DEFAULT,
            backgroundBitmap = translucentBackground
        )
        val store = ReportFileStore(context)
        val file = ReportPdfRenderer(context, store).render(
            ReportDocumentModel(
                period = ReportPeriod.Month(2026, 7),
                pages = listOf(
                    emptyOpeningPage(),
                    ReportPageModel.WorkShowcase(
                        pageNumber = 2,
                        heading = "作品展示",
                        style = ReportShowcaseStyle.GRID,
                        items = listOf(
                            reportItemWithCover("pdf-tests/full-report-cover.png")
                        )
                    )
                )
            ),
            theme
        )
        try {
            assertFalse(
                file.readBytes().toString(Charsets.ISO_8859_1).contains("/SMask")
            )
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
            ).use { descriptor ->
                val renderer = AndroidPdfRenderer(descriptor)
                try {
                    val page = renderer.openPage(0)
                    try {
                        val output = Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888
                        )
                        try {
                            page.render(
                                output,
                                null,
                                null,
                                AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            listOf(
                                output.getPixel(0, 0),
                                output.getPixel(output.width - 1, 0),
                                output.getPixel(0, output.height - 1),
                                output.getPixel(
                                    output.width - 1,
                                    output.height - 1
                                ),
                                output.getPixel(output.width / 2, output.height / 2)
                            ).forEach { pixel ->
                                assertEquals(255, Color.alpha(pixel))
                                assertNotEquals(accent, pixel)
                            }
                        } finally {
                            output.recycle()
                        }
                    } finally {
                        page.close()
                    }
                } finally {
                    renderer.close()
                }
            }
        } finally {
            store.deleteTemporary(file)
            translucentBackground.recycle()
            cover.delete()
            cover.parentFile?.delete()
        }
    }

    @Test
    fun pdfDirectCanvasUsesOpaqueFallbackWhenThereIsNoBackgroundImage() =
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<android.content.Context>()
            val fallback = Color.rgb(245, 244, 240)
            val theme = VisualExportThemeSnapshot(
                backgroundColor = Color.argb(70, 245, 244, 240),
                textPrimary = Color.BLACK,
                textSecondary = Color.DKGRAY,
                border = Color.GRAY,
                placeholderColor = Color.LTGRAY,
                accent = Color.rgb(103, 58, 183),
                headingTypeface = Typeface.DEFAULT_BOLD,
                contentTypeface = Typeface.DEFAULT,
                backgroundBitmap = null
            )
            val store = ReportFileStore(context)
            val file = ReportPdfRenderer(context, store).render(
                ReportDocumentModel(
                    period = ReportPeriod.Month(2026, 7),
                    pages = listOf(emptyOpeningPage())
                ),
                theme
            )
            try {
                assertFalse(
                    file.readBytes().toString(Charsets.ISO_8859_1)
                        .contains("/SMask")
                )
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
                ).use { descriptor ->
                    val renderer = AndroidPdfRenderer(descriptor)
                    try {
                        val page = renderer.openPage(0)
                        try {
                            val output = Bitmap.createBitmap(
                                page.width,
                                page.height,
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                page.render(
                                    output,
                                    null,
                                    null,
                                    AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )
                                listOf(
                                    output.getPixel(0, 0),
                                    output.getPixel(output.width - 1, 0),
                                    output.getPixel(0, output.height - 1),
                                    output.getPixel(
                                        output.width - 1,
                                        output.height - 1
                                    )
                                ).forEach { pixel ->
                                    assertEquals(255, Color.alpha(pixel))
                                    assertEquals(fallback, pixel)
                                }
                            } finally {
                                output.recycle()
                            }
                        } finally {
                            page.close()
                        }
                    } finally {
                        renderer.close()
                    }
                }
            } finally {
                store.deleteTemporary(file)
            }
        }

    @Test
    fun pdfCoverBitmapIsSoftwareSrgbAndOpaqueBeforeEmbedding() {
        val source = Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.argb(90, 30, 80, 120))
        val flattened = source.toOpaquePdfBitmap(Color.WHITE)
        try {
            assertEquals(Bitmap.Config.ARGB_8888, flattened.config)
            assertFalse(flattened.hasAlpha())
            assertEquals(255, Color.alpha(flattened.getPixel(12, 18)))
            assertEquals(true, isOpaqueSoftwareSrgbPdfBitmap(flattened))
        } finally {
            flattened.recycle()
            source.recycle()
        }
    }

    @Test
    fun pdfWithCoverContainsNoSoftMaskAndRendersOpaque() = runBlocking {
        val context =
            ApplicationProvider.getApplicationContext<android.content.Context>()
        val cover = File(context.filesDir, "pdf-tests/translucent-cover.png")
        cover.parentFile?.mkdirs()
        val source = Bitmap.createBitmap(60, 90, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.argb(96, 50, 90, 130))
            cover.outputStream().use {
                source.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            source.recycle()
        }
        val theme = VisualExportThemeSnapshot(
            backgroundColor = Color.WHITE,
            textPrimary = Color.BLACK,
            textSecondary = Color.DKGRAY,
            border = Color.GRAY,
            placeholderColor = Color.WHITE,
            accent = Color.BLACK,
            headingTypeface = Typeface.DEFAULT_BOLD,
            contentTypeface = Typeface.DEFAULT,
            backgroundBitmap = null
        )
        val store = ReportFileStore(context)
        val file = ReportPdfRenderer(context, store).render(
            ReportDocumentModel(
                period = ReportPeriod.Month(2026, 7),
                pages = listOf(
                    ReportPageModel.WorkShowcase(
                        pageNumber = 1,
                        heading = "作品展示",
                        style = ReportShowcaseStyle.GRID,
                        items = listOf(reportItemWithCover("pdf-tests/translucent-cover.png"))
                    )
                )
            ),
            theme
        )
        try {
            assertFalse(
                file.readBytes().toString(Charsets.ISO_8859_1).contains("/SMask")
            )
            assertPdfPagePixelsAreOpaque(file)
        } finally {
            store.deleteTemporary(file)
            cover.delete()
            cover.parentFile?.delete()
        }
    }

    private fun emptyOpeningPage() = ReportPageModel.TimeAndStatus(
        pageNumber = 1,
        heading = "2026.07",
        subtitle = "时间与状态",
        itemCountLine = "这个月，你读了 1 本书。",
        narrativeSections = listOf(
            ReportNarrativeSection(
                heading = "阅读",
                paragraphs = listOf("这些阅读一共陪伴了你 2 小时。")
            )
        ),
        statusSentence = null,
        closingLine = null
    )

    private fun reportItemWithCover(path: String) = ReportItemSnapshot(
        itemId = 1L,
        typeId = 1L,
        typeName = "书籍",
        typeKind = ItemTypeKind.BOOK,
        title = "测试作品",
        creator = "作者",
        coverPath = path,
        resolvedCoverWidth = 60,
        resolvedCoverHeight = 90,
        currentStatusId = null,
        currentStatus = null,
        currentStatusSortOrder = null,
        tags = emptyList(),
        customFields = emptyList(),
        firstActivityDate = 1L,
        firstRecordCreatedAt = 1L,
        activityDayCount = 1,
        periodDurationMinutes = null
    )

    private fun assertPdfPagePixelsAreOpaque(file: File) {
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        ).use { descriptor ->
            val renderer = AndroidPdfRenderer(descriptor)
            try {
                val page = renderer.openPage(0)
                try {
                    val output = Bitmap.createBitmap(
                        page.width,
                        page.height,
                        Bitmap.Config.ARGB_8888
                    )
                    try {
                        page.render(
                            output,
                            null,
                            null,
                            AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        listOf(
                            output.getPixel(0, 0),
                            output.getPixel(output.width - 1, 0),
                            output.getPixel(0, output.height - 1),
                            output.getPixel(output.width - 1, output.height - 1),
                            output.getPixel(output.width / 2, output.height / 2)
                        ).forEach { pixel ->
                            assertEquals(255, Color.alpha(pixel))
                        }
                    } finally {
                        output.recycle()
                    }
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
            }
        }
    }
}
