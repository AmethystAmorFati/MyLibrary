package com.example.mylibrary.export.visual

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.mylibrary.export.annualposter.AnnualPosterRenderer
import com.example.mylibrary.export.annualposter.AnnualPosterTooLargeException
import com.example.mylibrary.export.annualposter.buildAnnualPosterSnapshot
import com.example.mylibrary.export.annualposter.resolveAnnualPosterCovers
import com.example.mylibrary.export.calendar.CalendarExportRenderer
import com.example.mylibrary.export.calendar.buildCalendarExportSnapshot
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface VisualExportCoordinator {
    suspend fun prepare(
        request: VisualExportRequest,
        theme: VisualExportThemeSnapshot
    ): TemporaryVisualExport

    suspend fun saveToPictures(
        export: TemporaryVisualExport
    ): SavedVisualExportLocation
    suspend fun saveToUri(
        export: TemporaryVisualExport,
        destination: Uri
    ): SavedVisualExportLocation
    fun deleteTemporary(export: TemporaryVisualExport?)
}

class VisualExportService(
    context: Context,
    private val dataSource: VisualExportDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val renderDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fileStore: ExportFileStore = ExportFileStore(context)
) : VisualExportCoordinator {
    private val appContext = context.applicationContext

    override suspend fun prepare(
        request: VisualExportRequest,
        theme: VisualExportThemeSnapshot
    ): TemporaryVisualExport {
        val activities = withContext(ioDispatcher) {
            when (request) {
                is VisualExportRequest.Calendar -> {
                    val month = YearMonth.of(request.year, request.month)
                    dataSource.activitiesBetween(
                        month.atDay(1).toStartOfDayMillis(),
                        month.atEndOfMonth().toStartOfDayMillis()
                    )
                }
                is VisualExportRequest.AnnualPoster -> dataSource.activitiesBetween(
                    LocalDate.of(request.year, 1, 1).toStartOfDayMillis(),
                    LocalDate.of(request.year, 12, 31).toStartOfDayMillis()
                )
            }
        }
        val displayName = when (request) {
            is VisualExportRequest.Calendar ->
                ExportFileNames.calendar(request.year, request.month)
            is VisualExportRequest.AnnualPoster ->
                ExportFileNames.annual(request.year, request.category)
        }
        val bitmap = try {
            withContext(renderDispatcher) {
                when (request) {
                    is VisualExportRequest.Calendar -> {
                        val snapshot = buildCalendarExportSnapshot(
                            yearMonth = YearMonth.of(request.year, request.month),
                            activities = activities
                        )
                        if (snapshot.sourceActivityCount == 0) {
                            throw VisualExportException(
                                VisualExportError.NO_DATA,
                                "该月份没有可导出的记录"
                            )
                        }
                        CalendarExportRenderer.render(
                            context = appContext,
                            snapshot = snapshot,
                            theme = theme,
                            ioDispatcher = ioDispatcher,
                            renderDispatcher = renderDispatcher
                        )
                    }
                    is VisualExportRequest.AnnualPoster -> {
                        val snapshot = resolveAnnualPosterCovers(
                            context = appContext,
                            snapshot = buildAnnualPosterSnapshot(
                                year = request.year,
                                category = request.category,
                                activities = activities
                            ),
                            ioDispatcher = ioDispatcher
                        )
                        if (snapshot.items.isEmpty()) {
                            throw VisualExportException(
                                VisualExportError.NO_DATA,
                                annualPosterNoDataMessage(request.category)
                            )
                        }
                        AnnualPosterRenderer.render(
                            context = appContext,
                            snapshot = snapshot,
                            ioDispatcher = ioDispatcher,
                            renderDispatcher = renderDispatcher
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: VisualExportException) {
            throw known
        } catch (tooLarge: AnnualPosterTooLargeException) {
            Log.e(TAG, "Annual poster exceeds safe bitmap limits", tooLarge)
            throw VisualExportException(
                VisualExportError.BITMAP_LIMIT_EXCEEDED,
                "年度封面过多，无法安全生成",
                tooLarge
            )
        } catch (invalid: IllegalArgumentException) {
            Log.e(TAG, "Visual export rejected by bitmap/layout limits", invalid)
            throw VisualExportException(
                VisualExportError.BITMAP_LIMIT_EXCEEDED,
                "图片过大，无法安全生成",
                invalid
            )
        } catch (error: Exception) {
            Log.e(TAG, "Visual export rendering failed", error)
            throw VisualExportException(
                VisualExportError.RENDER_FAILED,
                "生成失败，请重试",
                error
            )
        }
        try {
            val temporaryFile = fileStore.writeTemporaryPng(displayName, bitmap)
            return TemporaryVisualExport(request, displayName, temporaryFile)
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun saveToPictures(
        export: TemporaryVisualExport
    ): SavedVisualExportLocation {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return fileStore.saveToPictures(export.temporaryFile, export.displayName)
        } else {
            throw VisualExportException(
                VisualExportError.SAVE_FAILED,
                "保存失败"
            )
        }
    }

    override suspend fun saveToUri(
        export: TemporaryVisualExport,
        destination: Uri
    ): SavedVisualExportLocation =
        fileStore.saveToUri(
            export.temporaryFile,
            destination,
            export.displayName
        )

    override fun deleteTemporary(export: TemporaryVisualExport?) {
        fileStore.deleteTemporary(export?.temporaryFile)
    }

    private companion object {
        const val TAG = "VisualExportService"
    }
}

internal fun annualPosterNoDataMessage(
    category: AnnualPosterCategory
): String = when (category) {
    AnnualPosterCategory.ALL -> "该年份没有有效封面"
    AnnualPosterCategory.BOOK -> "该年份没有有效书籍封面"
    AnnualPosterCategory.MOVIE -> "该年份没有有效电影封面"
}
