package com.example.mylibrary.export.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.util.Log
import com.example.mylibrary.data.image.CoverImageProcessor
import com.example.mylibrary.data.image.resolveStoredCoverFile
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.ui.poster.requireSafePosterBitmap
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ReportPngRenderer(
    context: Context,
    private val fileStore: ReportFileStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val renderDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val appContext = context.applicationContext

    suspend fun renderFiles(
        document: ReportDocumentModel,
        theme: VisualExportThemeSnapshot
    ): List<File> {
        val names = ReportFileNames.pngPages(document.period, document.pages.size)
        require(document.pages.size == names.size)
        val files = mutableListOf<File>()
        try {
            document.pages.forEachIndexed { index, page ->
                coroutineContext.ensureActive()
                requireSafePosterBitmap(
                    ReportPageSpec.PNG.width.toLong(),
                    ReportPageSpec.PNG.height.toLong()
                )
                val bitmap = withContext(renderDispatcher) {
                    Bitmap.createBitmap(
                        ReportPageSpec.PNG.width,
                        ReportPageSpec.PNG.height,
                        Bitmap.Config.ARGB_8888
                    )
                }
                try {
                    withContext(renderDispatcher) {
                        ReportLayoutEngine.draw(
                            canvas = Canvas(bitmap),
                            page = page,
                            spec = ReportPageSpec.PNG,
                            theme = theme,
                            coverLoader = coverLoader()
                        )
                    }
                    files += fileStore.writeTemporaryPng(names[index], bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
            return files
        } catch (cancelled: CancellationException) {
            files.forEach(fileStore::deleteTemporary)
            throw cancelled
        } catch (error: Exception) {
            files.forEach(fileStore::deleteTemporary)
            throw error
        }
    }

    private fun coverLoader() = ReportCoverLoader { path, maxDimension ->
        withContext(ioDispatcher) {
            try {
                resolveStoredCoverFile(appContext, path)?.let {
                    CoverImageProcessor.decodeSampledFile(it, maxDimension)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Unable to decode report cover: $path", error)
                null
            }
        }
    }

    private companion object {
        const val TAG = "ReportPngRenderer"
    }
}

class ReportPdfRenderer(
    context: Context,
    private val fileStore: ReportFileStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val renderDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val appContext = context.applicationContext

    suspend fun render(
        document: ReportDocumentModel,
        theme: VisualExportThemeSnapshot
    ): File {
        val output = fileStore.newTemporaryPdf(ReportFileNames.pdf(document.period))
        val staging = File(output.parentFile, "${output.name}.tmp")
        val pdf = PdfDocument()
        val pageTransform = calculateReportPdfPageTransform(
            pageWidth = ReportPageSpec.PDF_A4.width,
            pageHeight = ReportPageSpec.PDF_A4.height
        )
        val pdfTheme = theme.toOpaquePdfTheme()
        var pdfBackground: Bitmap? = null
        var committed = false
        try {
            pdfBackground = withContext(renderDispatcher) {
                theme.backgroundBitmap
                    ?.takeUnless(Bitmap::isRecycled)
                    ?.let { source ->
                        try {
                            createOpaquePdfPageBackgroundBitmap(
                                source = source,
                                pageWidth = ReportPageSpec.PDF_A4.width,
                                pageHeight = ReportPageSpec.PDF_A4.height,
                                backgroundColor = pdfTheme.backgroundColor
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Log.w(
                                TAG,
                                "Unable to flatten PDF theme background; using fallback",
                                error
                            )
                            null
                        }
                    }
            }
            document.pages.forEach { pageModel ->
                coroutineContext.ensureActive()
                val page = withContext(renderDispatcher) {
                    pdf.startPage(
                        PdfDocument.PageInfo.Builder(
                            ReportPageSpec.PDF_A4.width,
                            ReportPageSpec.PDF_A4.height,
                            pageModel.pageNumber
                        ).create()
                    )
                }
                try {
                    withContext(renderDispatcher) {
                        drawOpaquePdfPageBackground(
                            canvas = page.canvas,
                            pageWidth = ReportPageSpec.PDF_A4.width,
                            pageHeight = ReportPageSpec.PDF_A4.height,
                            color = pdfTheme.backgroundColor
                        )
                        pdfBackground?.let { background ->
                            page.canvas.drawBitmap(
                                background,
                                null,
                                RectF(
                                    0f,
                                    0f,
                                    ReportPageSpec.PDF_A4.width.toFloat(),
                                    ReportPageSpec.PDF_A4.height.toFloat()
                                ),
                                opaquePdfBitmapPaint()
                            )
                        }
                        val saveCount = page.canvas.save()
                        try {
                            page.canvas.translate(
                                pageTransform.offsetX,
                                pageTransform.offsetY
                            )
                            page.canvas.scale(
                                pageTransform.scale,
                                pageTransform.scale
                            )
                            ReportLayoutEngine.draw(
                                canvas = page.canvas,
                                page = pageModel,
                                spec = ReportPageSpec.PNG.copy(
                                    coverDecodeScale = pageTransform.scale
                                ),
                                theme = pdfTheme,
                                coverLoader = coverLoader(
                                    backgroundColor = pdfTheme.placeholderColor
                                ),
                                drawBackground = false,
                                allowTransparency = false
                            )
                        } finally {
                            page.canvas.restoreToCount(saveCount)
                        }
                    }
                } finally {
                    withContext(renderDispatcher) { pdf.finishPage(page) }
                }
            }
            withContext(ioDispatcher) {
                FileOutputStream(staging).use(pdf::writeTo)
                check(staging.isFile && staging.length() > 0L) {
                    "PDF 未生成有效文件"
                }
                check(staging.renameTo(output)) { "PDF 临时文件提交失败" }
            }
            committed = true
            return output
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Report PDF rendering failed", error)
            throw ReportExportException(
                ReportExportError.PDF_WRITE_FAILED,
                "生成失败，请重试",
                error
            )
        } finally {
            pdfBackground?.recycle()
            pdf.close()
            if (!committed) {
                staging.delete()
                output.delete()
            }
        }
    }

    private fun coverLoader(
        backgroundColor: Int
    ) = ReportCoverLoader { path, maxDimension ->
        withContext(ioDispatcher) {
            try {
                val decoded = resolveStoredCoverFile(appContext, path)?.let {
                    CoverImageProcessor.decodeSampledFile(it, maxDimension)
                }
                decoded?.let { source ->
                    try {
                        source.toOpaquePdfBitmap(backgroundColor)
                    } finally {
                        source.recycle()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Unable to decode report PDF cover: $path", error)
                null
            }
        }
    }

    private companion object {
        const val TAG = "ReportPdfRenderer"
    }
}

internal fun opaqueReportColor(color: Int): Int =
    Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))

internal data class ReportPdfPageTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val contentWidth: Float,
    val contentHeight: Float
)

internal fun calculateReportPdfPageTransform(
    pageWidth: Int,
    pageHeight: Int,
    designWidth: Int = ReportPageSpec.PNG.width,
    designHeight: Int = ReportPageSpec.PNG.height
): ReportPdfPageTransform {
    require(pageWidth > 0 && pageHeight > 0)
    require(designWidth > 0 && designHeight > 0)
    val scale = minOf(
        pageWidth.toFloat() / designWidth,
        pageHeight.toFloat() / designHeight
    )
    val contentWidth = designWidth * scale
    val contentHeight = designHeight * scale
    return ReportPdfPageTransform(
        scale = scale,
        offsetX = (pageWidth - contentWidth) / 2f,
        offsetY = (pageHeight - contentHeight) / 2f,
        contentWidth = contentWidth,
        contentHeight = contentHeight
    )
}

internal fun Bitmap.toOpaquePdfBitmap(backgroundColor: Int): Bitmap {
    require(!isRecycled)
    val output = createSoftwareSrgbBitmap(width, height)
    var completed = false
    try {
        val canvas = Canvas(output)
        canvas.drawColor(opaqueReportColor(backgroundColor))
        val drawable = softwareDrawableBitmap()
        try {
            canvas.drawBitmap(drawable.bitmap, 0f, 0f, opaquePdfBitmapPaint())
        } finally {
            if (drawable.owned) drawable.bitmap.recycle()
        }
        output.setHasAlpha(false)
        completed = true
        return output
    } finally {
        if (!completed) output.recycle()
    }
}

internal fun isOpaqueSoftwareSrgbPdfBitmap(bitmap: Bitmap): Boolean =
    !bitmap.isRecycled &&
        bitmap.config == Bitmap.Config.ARGB_8888 &&
        !bitmap.hasAlpha() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                bitmap.colorSpace?.isSrgb == true
            )

private fun VisualExportThemeSnapshot.toOpaquePdfTheme() = copy(
    backgroundColor = opaqueReportColor(backgroundColor),
    textPrimary = opaqueReportColor(textPrimary),
    textSecondary = opaqueReportColor(textSecondary),
    border = opaqueReportColor(border),
    placeholderColor = opaqueReportColor(placeholderColor),
    accent = opaqueReportColor(accent),
    backgroundBitmap = null
)

private fun drawOpaquePdfPageBackground(
    canvas: Canvas,
    pageWidth: Int,
    pageHeight: Int,
    color: Int
) {
    canvas.drawRect(
        0f,
        0f,
        pageWidth.toFloat(),
        pageHeight.toFloat(),
        Paint().apply {
            style = Paint.Style.FILL
            this.color = opaqueReportColor(color)
            alpha = 255
            isAntiAlias = false
            xfermode = null
            colorFilter = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = null
            }
        }
    )
}

private fun createOpaquePdfPageBackgroundBitmap(
    source: Bitmap,
    pageWidth: Int,
    pageHeight: Int,
    backgroundColor: Int
): Bitmap {
    val flattenedSource = source.toOpaquePdfBitmap(backgroundColor)
    val output = createSoftwareSrgbBitmap(pageWidth, pageHeight)
    var completed = false
    try {
        val canvas = Canvas(output)
        canvas.drawColor(opaqueReportColor(backgroundColor))
        val sourceRect = centerCropRect(
            sourceWidth = flattenedSource.width,
            sourceHeight = flattenedSource.height,
            targetWidth = pageWidth,
            targetHeight = pageHeight
        )
        canvas.drawBitmap(
            flattenedSource,
            sourceRect,
            Rect(0, 0, pageWidth, pageHeight),
            opaquePdfBitmapPaint()
        )
        output.setHasAlpha(false)
        completed = true
        return output
    } finally {
        flattenedSource.recycle()
        if (!completed) output.recycle()
    }
}

private fun centerCropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Rect {
    val sourceRatio = sourceWidth.toFloat() / sourceHeight
    val targetRatio = targetWidth.toFloat() / targetHeight
    return if (sourceRatio > targetRatio) {
        val width = sourceHeight * targetRatio
        val left = (sourceWidth - width) / 2f
        Rect(left.toInt(), 0, (left + width).toInt(), sourceHeight)
    } else {
        val height = sourceWidth / targetRatio
        val top = (sourceHeight - height) / 2f
        Rect(0, top.toInt(), sourceWidth, (top + height).toInt())
    }
}

private data class DrawableBitmap(
    val bitmap: Bitmap,
    val owned: Boolean
)

private fun Bitmap.softwareDrawableBitmap(): DrawableBitmap =
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        config == Bitmap.Config.HARDWARE
    ) {
        DrawableBitmap(
            bitmap = requireNotNull(copy(Bitmap.Config.ARGB_8888, false)),
            owned = true
        )
    } else {
        DrawableBitmap(this, owned = false)
    }

private fun createSoftwareSrgbBitmap(width: Int, height: Int): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888,
            true,
            ColorSpace.get(ColorSpace.Named.SRGB)
        )
    } else {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

private fun opaquePdfBitmapPaint() =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = 255
        xfermode = null
        colorFilter = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            blendMode = null
        }
    }
