package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.ItemChanges
import com.example.mylibrary.domain.model.ItemDetail
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.ItemSaveRequest
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.model.NewRecord
import com.example.mylibrary.domain.model.RecordChanges
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeItems(
        query: String = "",
        statusId: Long? = null,
        tagIds: Set<Long> = emptySet()
    ): Flow<List<LibraryItem>>

    fun observeItemTypes(): Flow<List<ItemType>>
    fun observeStatuses(scope: StatusScope = StatusScope.ITEM): Flow<List<LibraryStatus>>
    fun observeItemDetail(itemId: Long): Flow<ItemDetail?>
    fun observeActivities(startDate: Long, endDate: Long): Flow<List<LibraryActivity>>
    fun observeTimelineRecords(
        startDate: Long,
        endDate: Long
    ): Flow<List<LibraryTimelineRecord>>

    suspend fun createItem(item: NewItem): Long
    suspend fun updateItem(itemId: Long, changes: ItemChanges)
    suspend fun saveItem(request: ItemSaveRequest): Long
    suspend fun updateItemStatus(itemId: Long, statusId: Long)
    suspend fun deleteItem(itemId: Long)
    suspend fun addRecord(itemId: Long, record: NewRecord): Long
    suspend fun updateRecord(recordId: Long, changes: RecordChanges)
    suspend fun deleteRecord(recordId: Long)
}
