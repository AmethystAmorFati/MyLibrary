package com.example.mylibrary.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.mylibrary.data.dao.ItemDao
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.domain.model.TrashItem
import com.example.mylibrary.domain.repository.CoverImageRepository
import com.example.mylibrary.domain.repository.TrashRepository
import com.example.mylibrary.util.runBestEffortCleanup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineTrashRepository(
    private val database: LibraryDatabase,
    private val itemDao: ItemDao,
    private val coverImageRepository: CoverImageRepository
) : TrashRepository {
    override fun observeItems(): Flow<List<TrashItem>> =
        itemDao.observeTrashRows().map { rows ->
            rows.map {
                TrashItem(
                    id = it.id,
                    typeId = it.typeId,
                    typeName = it.typeName,
                    title = it.title,
                    creator = it.creator.orEmpty(),
                    coverPath = it.coverPath,
                    thumbnailPath = it.thumbnailPath,
                    deletedAt = it.deletedAt
                )
            }
        }

    override suspend fun restoreItem(itemId: Long) {
        database.withTransaction {
            requireNotNull(itemDao.getDeletedEntity(itemId)) {
                "回收站中不存在该作品"
            }
            check(itemDao.restore(itemId, System.currentTimeMillis()) == 1) {
                "恢复作品失败"
            }
        }
    }

    override suspend fun permanentlyDeleteItems(itemIds: Set<Long>) {
        if (itemIds.isEmpty()) return
        val orderedIds = itemIds.sorted()
        val deletedItems = database.withTransaction {
            val items = itemDao.getDeletedEntities(orderedIds)
            require(items.size == orderedIds.size) {
                "选中的作品已不在回收站"
            }
            check(itemDao.permanentlyDeleteItems(orderedIds) == items.size) {
                "永久删除作品失败"
            }
            items
        }
        cleanupOrphanedCovers(deletedItems)
    }

    override suspend fun emptyTrash() {
        val deletedItems = database.withTransaction {
            val items = itemDao.getAllDeletedEntities()
            if (items.isNotEmpty()) {
                check(itemDao.permanentlyDeleteAllTrash() == items.size) {
                    "清空回收站失败"
                }
            }
            items
        }
        cleanupOrphanedCovers(deletedItems)
    }

    private suspend fun cleanupOrphanedCovers(items: List<ItemEntity>) {
        items.forEach { item ->
            val original = item.coverPath
                ?.takeIf(String::isNotBlank)
                ?.takeIf { itemDao.countCoverPathReferences(it) == 0 }
            val thumbnail = item.thumbnailPath
                ?.takeIf(String::isNotBlank)
                ?.takeIf { itemDao.countCoverPathReferences(it) == 0 }
            if (original == null && thumbnail == null) return@forEach
            runBestEffortCleanup(
                cleanup = {
                    coverImageRepository.delete(original, thumbnail)
                },
                onFailure = { error ->
                    Log.w(
                        LOG_TAG,
                        "Database deletion committed, but orphan cover cleanup failed " +
                            "for item ${item.id}. A later maintenance pass may retry it.",
                        error
                    )
                }
            )
        }
    }

    private companion object {
        const val LOG_TAG = "MyLibraryTrash"
    }
}
