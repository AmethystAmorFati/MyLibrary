package com.example.mylibrary.export.annualposter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.example.mylibrary.data.image.CoverImageProcessor
import com.example.mylibrary.data.image.resolveStoredCoverFile
import com.example.mylibrary.export.calendar.ExportIntRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AnnualPosterRenderer {
    suspend fun render(
        context: Context,
        snapshot: AnnualPosterSnapshot,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        renderDispatcher: CoroutineDispatcher = Dispatchers.Default
    ): Bitmap {
        require(snapshot.items.isNotEmpty()) { "没有可导出的年度作品" }
        require(
            snapshot.items.all {
                !it.resolvedCoverPath.isNullOrBlank() &&
                    it.resolvedAspectRatio != null
            }
        ) {
            "年度海报只能渲染已通过校验的封面"
        }
        val layout = annualPosterLayout(snapshot.items)
        val output = withContext(renderDispatcher) {
            Bitmap.createBitmap(
                layout.width,
                layout.height,
                Bitmap.Config.ARGB_8888
            )
        }
        var completed = false
        try {
            val canvas = Canvas(output)
            layout.cells.forEach { cell ->
                val item = snapshot.items[cell.itemIndex]
                val bitmap = withContext(ioDispatcher) {
                    loadCover(context, item, cell.bounds)
                }
                try {
                    withContext(renderDispatcher) {
                        drawFullCover(
                            canvas = canvas,
                            bitmap = bitmap,
                            target = cell.bounds
                        )
                    }
                } finally {
                    bitmap?.recycle()
                }
            }
            completed = true
            return output
        } finally {
            if (!completed) output.recycle()
        }
    }

    private fun drawFullCover(
        canvas: Canvas,
        bitmap: Bitmap?,
        target: ExportIntRect
    ) {
        bitmap ?: return
        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            target.toRect(),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun loadCover(
        context: Context,
        item: AnnualPosterItem,
        target: ExportIntRect
    ): Bitmap? {
        val path = item.resolvedCoverPath ?: return null
        return try {
            resolveStoredCoverFile(context, path)?.let { file ->
                CoverImageProcessor.decodeSampledFile(
                    file,
                    maxOf(target.width, target.height)
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Unable to decode validated annual poster cover: $path", error)
            null
        }
    }

    private fun ExportIntRect.toRect(): Rect =
        Rect(left, top, right, bottom)

    private const val TAG = "AnnualPoster"
}
