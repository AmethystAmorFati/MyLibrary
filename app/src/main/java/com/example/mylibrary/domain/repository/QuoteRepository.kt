package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.LibraryQuote
import com.example.mylibrary.domain.model.NewQuote
import com.example.mylibrary.domain.model.QuoteChanges
import com.example.mylibrary.domain.model.QuoteListItem
import com.example.mylibrary.domain.model.QuoteStatistics
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.QuoteTagStatistic
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    fun observeRecent(): Flow<List<QuoteListItem>>
    fun observePage(query: String, limit: Int, offset: Int): Flow<List<QuoteListItem>>
    fun observeForItem(itemId: Long): Flow<List<LibraryQuote>>
    fun observeStatistics(): Flow<QuoteStatistics>
    fun observeMediaStatistics(): Flow<FixedMediaStatistics>
    fun observeTagStatistics(): Flow<List<QuoteTagStatistic>>
    suspend fun create(quote: NewQuote): Long
    suspend fun update(quoteId: Long, changes: QuoteChanges)
    suspend fun delete(quoteId: Long)
}
