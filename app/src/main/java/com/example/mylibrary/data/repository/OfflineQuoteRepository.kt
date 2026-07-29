package com.example.mylibrary.data.repository

import com.example.mylibrary.data.dao.ItemDao
import com.example.mylibrary.data.dao.QuoteDao
import com.example.mylibrary.data.entity.QuoteEntity
import com.example.mylibrary.data.model.QuoteListRow
import com.example.mylibrary.data.model.MediaItemStatisticsRow
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.domain.model.LibraryQuote
import com.example.mylibrary.domain.model.MediaCategoryStatistics
import com.example.mylibrary.domain.model.NewQuote
import com.example.mylibrary.domain.model.QuoteChanges
import com.example.mylibrary.domain.model.QuoteListItem
import com.example.mylibrary.domain.model.QuoteStatistics
import com.example.mylibrary.domain.model.QuoteTagStatistic
import com.example.mylibrary.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

class OfflineQuoteRepository(
    private val quoteDao: QuoteDao,
    private val itemDao: ItemDao
) : QuoteRepository {
    override fun observeRecent(): Flow<List<QuoteListItem>> =
        quoteDao.observeRecent().map { rows -> rows.map(QuoteListRow::toDomain) }

    override fun observePage(
        query: String,
        limit: Int,
        offset: Int
    ): Flow<List<QuoteListItem>> =
        quoteDao.observePage(query.trim(), limit, offset)
            .map { rows -> rows.map(QuoteListRow::toDomain) }

    override fun observeForItem(itemId: Long): Flow<List<LibraryQuote>> =
        quoteDao.observeForItem(itemId)
            .map { quotes -> quotes.map(QuoteEntity::toDomain) }

    override fun observeStatistics(): Flow<QuoteStatistics> =
        quoteDao.observeStatistics().map {
            QuoteStatistics(
                readingWorkCount = it.readingWorkCount,
                viewingWorkCount = it.viewingWorkCount,
                quoteCount = it.quoteCount,
                tagCount = it.tagCount
            )
        }

    override fun observeMediaStatistics(): Flow<FixedMediaStatistics> =
        quoteDao.observeMediaItemStatistics(
            bookTypeId = ItemTypeKind.BOOK_TYPE_ID,
            movieTypeId = ItemTypeKind.MOVIE_TYPE_ID
        ).map(::buildFixedMediaStatistics)
            .flowOn(Dispatchers.Default)

    override fun observeTagStatistics(): Flow<List<QuoteTagStatistic>> =
        quoteDao.observeTagStatistics().map { rows ->
            rows.map { QuoteTagStatistic(name = it.name, count = it.usageCount) }
        }

    override suspend fun create(quote: NewQuote): Long {
        require(quote.content.isNotBlank()) { "摘录内容不能为空" }
        requireNotNull(itemDao.getActiveEntity(quote.itemId)) { "作品不存在" }
        return quoteDao.insert(
            QuoteEntity(
                itemId = quote.itemId,
                content = quote.content.trim(),
                chapter = quote.chapter.cleaned(),
                page = quote.page.cleaned(),
                createdTime = System.currentTimeMillis()
            )
        )
    }

    override suspend fun update(quoteId: Long, changes: QuoteChanges) {
        require(changes.content.isNotBlank()) { "摘录内容不能为空" }
        val current = requireNotNull(quoteDao.getById(quoteId)) { "摘录不存在" }
        quoteDao.update(
            current.copy(
                content = changes.content.trim(),
                chapter = changes.chapter.cleaned(),
                page = changes.page.cleaned()
            )
        )
    }

    override suspend fun delete(quoteId: Long) {
        val current = requireNotNull(quoteDao.getById(quoteId)) { "摘录不存在" }
        quoteDao.delete(current)
    }
}

private fun QuoteEntity.toDomain() = LibraryQuote(
    id = id,
    itemId = itemId,
    content = content,
    page = page,
    createdTime = createdTime,
    chapter = chapter
)

private fun QuoteListRow.toDomain() = QuoteListItem(
    quote = LibraryQuote(
        id = id,
        itemId = itemId,
        content = content,
        page = page,
        createdTime = createdTime,
        chapter = chapter
    ),
    itemTitle = itemTitle,
    creator = creator
)

private fun String?.cleaned(): String? = this?.trim()?.takeIf(String::isNotEmpty)

internal fun buildFixedMediaStatistics(
    rows: List<MediaItemStatisticsRow>
): FixedMediaStatistics = FixedMediaStatistics(
    reading = rows
        .filter { ItemTypeKind.fromTypeId(it.typeId) == ItemTypeKind.BOOK }
        .toMediaCategoryStatistics(),
    watching = rows
        .filter { ItemTypeKind.fromTypeId(it.typeId) == ItemTypeKind.MOVIE }
        .toMediaCategoryStatistics()
)

private fun List<MediaItemStatisticsRow>.toMediaCategoryStatistics(): MediaCategoryStatistics {
    val valuedItems = filter { it.valuedRecordCount > 0L }
    val longestItem = valuedItems
        .sortedWith(
            compareByDescending<MediaItemStatisticsRow> {
                it.totalDurationMinutes ?: 0L
            }.thenBy {
                it.itemTitle.lowercase()
            }.thenBy {
                it.itemTitle
            }.thenBy {
                it.itemId
            }
        )
        .firstOrNull()
    val valuedRecordCount = sumOf(MediaItemStatisticsRow::valuedRecordCount)
    return MediaCategoryStatistics(
        itemCount = size.toLong(),
        recordCount = sumOf(MediaItemStatisticsRow::recordCount),
        quoteCount = sumOf(MediaItemStatisticsRow::quoteCount),
        valuedRecordCount = valuedRecordCount,
        valuedItemCount = valuedItems.size.toLong(),
        totalDurationMinutes = if (valuedRecordCount > 0L) {
            sumOf { it.totalDurationMinutes ?: 0L }
        } else {
            null
        },
        maximumSingleDurationMinutes = valuedItems
            .mapNotNull(MediaItemStatisticsRow::maximumSingleDurationMinutes)
            .maxOrNull(),
        longestItemId = longestItem?.itemId,
        longestItemTitle = longestItem?.itemTitle,
        longestItemDurationMinutes = longestItem?.totalDurationMinutes
    )
}
