package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

interface TrashRepository {
    fun observeItems(): Flow<List<TrashItem>>
    suspend fun restoreItem(itemId: Long)
    suspend fun permanentlyDeleteItems(itemIds: Set<Long>)
    suspend fun permanentlyDeleteItem(itemId: Long) =
        permanentlyDeleteItems(setOf(itemId))
    suspend fun emptyTrash()
}
