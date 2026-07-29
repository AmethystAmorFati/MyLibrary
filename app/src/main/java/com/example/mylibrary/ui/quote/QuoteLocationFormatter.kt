package com.example.mylibrary.ui.quote

internal fun formatQuoteLocation(
    chapter: String?,
    page: String?,
    showChapter: Boolean,
    showPage: Boolean
): String? {
    val chapterText = chapter
        ?.trim()
        ?.takeIf { showChapter && it.isNotEmpty() }
    val pageText = page
        ?.trim()
        ?.takeIf { showPage && it.isNotEmpty() }
        ?.let(::formatQuotePage)
    return listOfNotNull(chapterText, pageText)
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString(" · ")
}

internal fun formatQuotePage(page: String): String {
    val value = page.trim()
    return if ('第' in value || '页' in value) value else "第 $value 页"
}
