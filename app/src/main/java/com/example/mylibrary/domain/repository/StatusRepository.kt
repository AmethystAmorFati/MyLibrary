package com.example.mylibrary.domain.repository

import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.StatusScope
import kotlinx.coroutines.flow.Flow

interface StatusRepository {
    fun observeStatuses(
        scope: StatusScope,
        includeDisabled: Boolean = false
    ): Flow<List<LibraryStatus>>
    fun observeUsageCounts(scope: StatusScope): Flow<Map<Long, Int>>

    suspend fun createStatus(name: String, scope: StatusScope): Long
    suspend fun renameStatus(statusId: Long, name: String)
    suspend fun setStatusEnabled(statusId: Long, enabled: Boolean)
    suspend fun deleteStatus(statusId: Long)
    suspend fun reorderStatuses(scope: StatusScope, orderedIds: List<Long>)
}
