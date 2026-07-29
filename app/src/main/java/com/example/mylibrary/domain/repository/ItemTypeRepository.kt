package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.ItemType
import kotlinx.coroutines.flow.Flow

interface ItemTypeRepository {
    fun observeTypes(): Flow<List<ItemType>>
    fun observeUsageCounts(): Flow<Map<Long, Int>>
    suspend fun createType(name: String): Long
    suspend fun renameType(typeId: Long, name: String)
    suspend fun deleteType(typeId: Long)
    suspend fun reorderTypes(orderedIds: List<Long>)
}
