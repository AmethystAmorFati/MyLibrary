package com.example.mylibrary.export.report

import android.net.Uri
import com.example.mylibrary.export.visual.VisualExportThemeSnapshot
import com.example.mylibrary.ui.settings.ReportExportConfig
import com.example.mylibrary.ui.settings.ReportOutputFormat
import java.io.File

enum class ReportExportError {
    NO_DATA,
    INVALID_CONFIGURATION,
    DATA_RESOLVE_FAILED,
    RENDER_FAILED,
    BITMAP_LIMIT_EXCEEDED,
    PDF_WRITE_FAILED,
    SAVE_FAILED
}

class ReportExportException(
    val reason: ReportExportError,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

sealed interface TemporaryReportExport {
    val config: ReportExportConfig
    val format: ReportOutputFormat

    data class PngPages(
        override val config: ReportExportConfig,
        val files: List<File>,
        val displayNames: List<String>
    ) : TemporaryReportExport {
        override val format: ReportOutputFormat = ReportOutputFormat.PNG
    }

    data class Pdf(
        override val config: ReportExportConfig,
        val file: File,
        val displayName: String
    ) : TemporaryReportExport {
        override val format: ReportOutputFormat = ReportOutputFormat.PDF
    }
}

enum class ExportDestination {
    PICTURES,
    DOWNLOADS,
    USER_SELECTED
}

data class SavedExportLocation(
    val displayName: String,
    val destination: ExportDestination,
    val displayLocation: String?,
    val fileCount: Int = 1
)

interface ReportExportCoordinator {
    suspend fun prepare(
        config: ReportExportConfig,
        theme: VisualExportThemeSnapshot
    ): TemporaryReportExport

    suspend fun saveDirect(export: TemporaryReportExport): SavedExportLocation
    suspend fun savePngToTree(
        export: TemporaryReportExport.PngPages,
        treeUri: Uri
    ): SavedExportLocation
    suspend fun savePdfToUri(
        export: TemporaryReportExport.Pdf,
        uri: Uri
    ): SavedExportLocation
    fun deleteTemporary(export: TemporaryReportExport?)
}

object ReportFileNames {
    fun pngPages(period: ReportPeriod, pageCount: Int): List<String> {
        require(pageCount > 0)
        val stem = when (period) {
            is ReportPeriod.Month ->
                "MyLibrary_Monthly_${period.year}_${period.month.toString().padStart(2, '0')}"
            is ReportPeriod.Year -> "MyLibrary_Yearly_${period.year}"
        }
        return (1..pageCount).map { page ->
            "${stem}_P${page.toString().padStart(2, '0')}.png"
        }
    }

    fun pdf(period: ReportPeriod): String = when (period) {
        is ReportPeriod.Month ->
            "MyLibrary_Monthly_${period.year}_${period.month.toString().padStart(2, '0')}.pdf"
        is ReportPeriod.Year -> "MyLibrary_Yearly_${period.year}.pdf"
    }
}
