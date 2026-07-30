package com.example.mylibrary.export.visual

import android.graphics.Bitmap
import android.graphics.Typeface
import com.example.mylibrary.ui.poster.requireSafePosterBitmap
import java.io.File

const val VISUAL_EXPORT_WIDTH = 1_080
const val VISUAL_EXPORT_HEIGHT = 1_440

sealed interface VisualExportRequest {
    data class Calendar(
        val year: Int,
        val month: Int
    ) : VisualExportRequest {
        init {
            require(month in 1..12)
        }
    }

    data class AnnualPoster(
        val year: Int,
        val category: AnnualPosterCategory
    ) : VisualExportRequest
}

enum class AnnualPosterCategory {
    ALL,
    BOOK,
    MOVIE
}

enum class VisualExportError {
    NO_DATA,
    INVALID_CONFIGURATION,
    COVER_LOAD_FAILED,
    RENDER_FAILED,
    BITMAP_LIMIT_EXCEEDED,
    SAVE_FAILED,
    CANCELLED
}

class VisualExportException(
    val reason: VisualExportError,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

data class VisualExportThemeSnapshot(
    val backgroundColor: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val border: Int,
    val placeholderColor: Int,
    val accent: Int = textPrimary,
    val headingTypeface: Typeface?,
    val contentTypeface: Typeface?,
    /**
     * Frozen reference to the already-resolved theme asset. Renderers must not
     * recycle it because the theme runtime owns the underlying bitmap.
     */
    val backgroundBitmap: Bitmap? = null
)

data class TemporaryVisualExport(
    val request: VisualExportRequest,
    val displayName: String,
    val temporaryFile: File
)

data class SavedVisualExportLocation(
    val displayName: String,
    val displayLocation: String?
)

internal fun requireVisualExportCanvas() {
    val budget = requireSafePosterBitmap(
        width = VISUAL_EXPORT_WIDTH.toLong(),
        height = VISUAL_EXPORT_HEIGHT.toLong()
    )
    require(budget.totalPixels == 1_555_200L)
}
