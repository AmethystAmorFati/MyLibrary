package com.example.mylibrary.domain.model

data class LibraryQuote(
    val id: Long,
    val itemId: Long,
    val content: String,
    val page: String?,
    val createdTime: Long,
    val chapter: String? = null
)

data class QuoteListItem(
    val quote: LibraryQuote,
    val itemTitle: String,
    val creator: String
)

data class QuoteStatistics(
    val readingWorkCount: Int,
    val viewingWorkCount: Int,
    val quoteCount: Int,
    val tagCount: Int = 0
)

data class MediaCategoryStatistics(
    val itemCount: Long = 0,
    val recordCount: Long = 0,
    val quoteCount: Long = 0,
    val valuedRecordCount: Long = 0,
    val valuedItemCount: Long = 0,
    val totalDurationMinutes: Long? = null,
    val maximumSingleDurationMinutes: Long? = null,
    val longestItemId: Long? = null,
    val longestItemTitle: String? = null,
    val longestItemDurationMinutes: Long? = null
) {
    val averagePerRecordMinutes: Long?
        get() = roundedDurationAverage(totalDurationMinutes, valuedRecordCount)

    val averagePerItemMinutes: Long?
        get() = roundedDurationAverage(totalDurationMinutes, valuedItemCount)
}

data class FixedMediaStatistics(
    val reading: MediaCategoryStatistics = MediaCategoryStatistics(),
    val watching: MediaCategoryStatistics = MediaCategoryStatistics()
)

internal fun roundedDurationAverage(totalMinutes: Long?, count: Long): Long? {
    if (totalMinutes == null || count <= 0L) return null
    val quotient = totalMinutes / count
    val remainder = totalMinutes % count
    return quotient + if (remainder >= (count + 1L) / 2L) 1L else 0L
}

data class QuoteTagStatistic(
    val name: String,
    val count: Int
)

data class NewQuote(
    val itemId: Long,
    val content: String,
    val page: String?,
    val chapter: String? = null
)

data class QuoteChanges(
    val content: String,
    val page: String?,
    val chapter: String? = null
)
