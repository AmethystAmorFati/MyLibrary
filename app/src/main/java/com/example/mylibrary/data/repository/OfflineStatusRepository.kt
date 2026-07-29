package com.example.mylibrary.data.repository

import androidx.room.withTransaction
import com.example.mylibrary.data.dao.StatusDao
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.StatusEntity
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.repository.StatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OfflineStatusRepository(
    private val database: LibraryDatabase,
    private val statusDao: StatusDao
) : StatusRepository {
    override fun observeStatuses(
        scope: StatusScope,
        includeDisabled: Boolean
    ): Flow<List<LibraryStatus>> {
        val source = if (includeDisabled) {
            statusDao.observeAll(scope)
        } else {
            statusDao.observeEnabled(scope)
        }
        return source.map { statuses -> statuses.map { it.toDomain() } }
    }

    override fun observeUsageCounts(scope: StatusScope): Flow<Map<Long, Int>> =
        statusDao.observeUsageCounts(scope).map { rows ->
            rows.associate { it.statusId to it.usageCount }
        }

    override suspend fun createStatus(name: String, scope: StatusScope): Long {
        ensureUniqueName(name, scope)
        return statusDao.insert(
            StatusEntity(
                name = name.trim(),
                sortOrder = statusDao.getMaxSortOrder(scope) + 1,
                scope = scope
            )
        )
    }

    override suspend fun renameStatus(statusId: Long, name: String) {
        val status = requireNotNull(statusDao.getById(statusId)) { "状态不存在" }
        ensureUniqueName(name, status.scope, statusId)
        statusDao.update(status.copy(name = name.trim()))
    }

    override suspend fun setStatusEnabled(statusId: Long, enabled: Boolean) {
        val status = requireNotNull(statusDao.getById(statusId)) { "状态不存在" }
        statusDao.update(status.copy(enabled = enabled))
    }

    override suspend fun deleteStatus(statusId: Long) {
        database.withTransaction {
            val status = requireNotNull(statusDao.getById(statusId)) {
                "状态不存在"
            }
            if (status.scope == StatusScope.ITEM) {
                val usageCount = statusDao.observeUsageCounts(StatusScope.ITEM)
                    .map { rows -> rows.firstOrNull { it.statusId == status.id }?.usageCount ?: 0 }
                    .first()
                check(usageCount == 0) {
                    "仍有 $usageCount 部作品使用“${status.name}”，请先调整作品状态"
                }
            }
            statusDao.deleteById(statusId)
        }
    }

    override suspend fun reorderStatuses(scope: StatusScope, orderedIds: List<Long>) {
        database.withTransaction {
            val statuses = statusDao.getAll(scope)
            check(
                orderedIds.size == statuses.size &&
                    orderedIds.toSet() == statuses.mapTo(mutableSetOf()) { it.id }
            ) { "状态排序数据已变化，请重试" }
            orderedIds.forEachIndexed { index, id ->
                statusDao.updateSortOrder(id, index)
            }
        }
    }

    private suspend fun ensureUniqueName(
        name: String,
        scope: StatusScope,
        excludingId: Long? = null
    ) {
        val normalized = name.trim()
        check(
            statusDao.getAll(scope).none {
                it.id != excludingId && it.name.equals(normalized, ignoreCase = true)
            }
        ) { "状态名称已存在" }
    }
}
