package com.example.mylibrary.export.report

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.export.visual.VisualExportDataSource
import com.example.mylibrary.export.calendar.buildCalendarExportSnapshot
import com.example.mylibrary.util.toStartOfDayMillis
import com.example.mylibrary.ui.settings.ReportExportConfig
import com.example.mylibrary.ui.settings.ReportOutputFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

class ReportExportService(
    context: Context,
    private val dataResolver: ReportDataResolver,
    private val visualExportDataSource: VisualExportDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val renderDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fileStore: ReportFileStore = ReportFileStore(context)
) : ReportExportCoordinator {
    private val appContext = context.applicationContext
    private val pngRenderer = ReportPngRenderer(
        appContext,
        fileStore,
        ioDispatcher,
        renderDispatcher
    )
    private val pdfRenderer = ReportPdfRenderer(
        appContext,
        fileStore,
        ioDispatcher,
        renderDispatcher
    )

    override suspend fun prepare(
        config: ReportExportConfig,
        theme: VisualExportThemeSnapshot
    ): TemporaryReportExport {
        try {
            val prepared = withContext(ioDispatcher) {
                dataResolver.resolve(config)
            }
            val snapshot = when (prepared) {
                is ReportPreparationResult.InvalidConfig ->
                    throw ReportExportException(
                        ReportExportError.INVALID_CONFIGURATION,
                        prepared.message
                    )
                is ReportPreparationResult.Ready -> prepared.snapshot
            }
            if (snapshot.summary.recordCount == 0) {
                throw ReportExportException(
                    ReportExportError.NO_DATA,
                    noDataMessage(snapshot.config.period)
                )
            }
            val calendarResolvedSnapshot = if (snapshot.config.period is ReportPeriod.Year) {
                val period = snapshot.config.period
                val activities = withContext(ioDispatcher) {
                    visualExportDataSource.activitiesBetween(
                        LocalDate.of(period.year, 1, 1).toStartOfDayMillis(),
                        LocalDate.of(period.year, 12, 31).toStartOfDayMillis()
                    )
                }
                snapshot.copy(
                    annualCalendarSnapshots =
                        buildAnnualReportCalendarSnapshots(
                            year = period.year,
                            selectedItemTypeIds =
                                snapshot.config.selectedItemTypeIds,
                            activities = activities
                        )
                )
            } else {
                snapshot
            }
            val resolvedSnapshot = resolveReportCovers(
                appContext,
                calendarResolvedSnapshot,
                ioDispatcher
            )
            val document = ReportPageModelFactory.create(resolvedSnapshot)
            return when (config.outputFormat) {
                ReportOutputFormat.PNG -> {
                    val files = pngRenderer.renderFiles(document, theme)
                    TemporaryReportExport.PngPages(
                        config = config,
                        files = files,
                        displayNames = ReportFileNames.pngPages(
                            document.period,
                            document.pages.size
                        )
                    )
                }
                ReportOutputFormat.PDF -> TemporaryReportExport.Pdf(
                    config = config,
                    file = pdfRenderer.render(document, theme),
                    displayName = ReportFileNames.pdf(document.period)
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: ReportExportException) {
            throw known
        } catch (invalid: IllegalArgumentException) {
            Log.e(TAG, "Report rejected by layout or bitmap limits", invalid)
            throw ReportExportException(
                ReportExportError.BITMAP_LIMIT_EXCEEDED,
                "图片过大，无法安全生成",
                invalid
            )
        } catch (error: Exception) {
            Log.e(TAG, "Report preparation or rendering failed", error)
            throw ReportExportException(
                ReportExportError.RENDER_FAILED,
                "生成失败，请重试",
                error
            )
        }
    }

    override suspend fun saveDirect(
        export: TemporaryReportExport
    ): SavedExportLocation {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        return when (export) {
            is TemporaryReportExport.PngPages ->
                fileStore.savePngBatchToPictures(export.files, export.displayNames)
            is TemporaryReportExport.Pdf ->
                fileStore.savePdfToDownloads(export.file, export.displayName)
        }
    }

    override suspend fun savePngToTree(
        export: TemporaryReportExport.PngPages,
        treeUri: Uri
    ): SavedExportLocation =
        fileStore.savePngBatchToTree(export.files, export.displayNames, treeUri)

    override suspend fun savePdfToUri(
        export: TemporaryReportExport.Pdf,
        uri: Uri
    ): SavedExportLocation =
        fileStore.savePdfToUri(export.file, uri, export.displayName)

    override fun deleteTemporary(export: TemporaryReportExport?) {
        when (export) {
            null -> Unit
            is TemporaryReportExport.PngPages ->
                export.files.forEach(fileStore::deleteTemporary)
            is TemporaryReportExport.Pdf ->
                fileStore.deleteTemporary(export.file)
        }
    }

    private fun noDataMessage(period: ReportPeriod): String = when (period) {
        is ReportPeriod.Month -> "该月份没有可生成报告的记录"
        is ReportPeriod.Year -> "该年份没有可生成报告的记录"
    }

    private companion object {
        const val TAG = "ReportExportService"
    }
}

internal fun buildAnnualReportCalendarSnapshots(
    year: Int,
    selectedItemTypeIds: Set<Long>,
    activities:
        List<com.example.mylibrary.export.visual.VisualExportActivity>
) = (1..12).map { month ->
    buildCalendarExportSnapshot(
        yearMonth = YearMonth.of(year, month),
        activities = activities.filter {
            it.typeId in selectedItemTypeIds
        }
    )
}
