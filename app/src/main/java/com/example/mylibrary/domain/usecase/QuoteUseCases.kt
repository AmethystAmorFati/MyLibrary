package com.example.mylibrary.domain.usecase

import com.example.mylibrary.domain.model.LibraryQuote
import com.example.mylibrary.domain.model.NewQuote
import com.example.mylibrary.domain.model.QuoteChanges
import com.example.mylibrary.domain.model.QuoteListItem
import com.example.mylibrary.domain.model.QuoteStatistics
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.QuoteTagStatistic
import com.example.mylibrary.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow

class QuoteUseCases(
    private val repository: QuoteRepository
) {
    fun observeRecent(): Flow<List<QuoteListItem>> = repository.observeRecent()

    fun observePage(
        query: String,
        limit: Int,
        offset: Int = 0
    ): Flow<List<QuoteListItem>> = repository.observePage(query, limit, offset)

    fun observeForItem(itemId: Long): Flow<List<LibraryQuote>> =
        repository.observeForItem(itemId)

    fun observeStatistics(): Flow<QuoteStatistics> = repository.observeStatistics()

    fun observeMediaStatistics(): Flow<FixedMediaStatistics> =
        repository.observeMediaStatistics()

    fun observeTagStatistics(): Flow<List<QuoteTagStatistic>> =
        repository.observeTagStatistics()

    suspend fun create(quote: NewQuote): Long = repository.create(quote)

    suspend fun update(quoteId: Long, changes: QuoteChanges) =
        repository.update(quoteId, changes)

    suspend fun delete(quoteId: Long) = repository.delete(quoteId)
}
