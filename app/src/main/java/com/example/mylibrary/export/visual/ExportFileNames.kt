package com.example.mylibrary.export.visual

import java.util.Locale

object ExportFileNames {
    fun calendar(year: Int, month: Int): String =
        "MyLibrary_Calendar_${year}_${month.toString().padStart(2, '0')}.png"

    fun annual(year: Int, category: AnnualPosterCategory): String {
        val categoryName = when (category) {
            AnnualPosterCategory.ALL -> "All"
            AnnualPosterCategory.BOOK -> "Books"
            AnnualPosterCategory.MOVIE -> "Movies"
        }
        return "MyLibrary_Annual_${categoryName}_${year}.png"
    }

    fun withSequence(fileName: String, sequence: Int): String {
        require(sequence >= 1)
        if (sequence == 1) return fileName
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        val stem = fileName.removeSuffix(
            extension.takeIf(String::isNotEmpty)?.let { ".$it" }.orEmpty()
        )
        return if (extension.isEmpty()) {
            "${stem}_$sequence"
        } else {
            "${stem}_${sequence}.${extension.lowercase(Locale.ROOT)}"
        }
    }
}
