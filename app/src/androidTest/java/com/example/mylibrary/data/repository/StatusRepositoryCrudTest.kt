package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.data.entity.RecordEntity
import com.example.mylibrary.domain.model.StatusScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusRepositoryCrudTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineStatusRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .addCallback(DefaultLibraryDataCallback)
            .allowMainThreadQueries()
            .build()
        repository = OfflineStatusRepository(database, database.statusDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun createRenameAndDisableStatusWithoutBreakingItemCurrentStatusReference() = runBlocking {
        val statusId = repository.createStatus("搁置", StatusScope.ITEM)
        repository.renameStatus(statusId, "以后再看")

        val itemId = database.itemDao().insert(
            ItemEntity(
                typeId = DefaultLibraryData.MOVIE_TYPE_ID,
                title = "Status Test",
                currentStatusId = statusId,
                createdTime = 1,
                updatedTime = 1
            )
        )

        repository.setStatusEnabled(statusId, false)
        val managed = repository.observeStatuses(
            StatusScope.ITEM,
            includeDisabled = true
        ).first()
            .first { it.id == statusId }
        assertEquals("以后再看", managed.name)
        assertFalse(managed.enabled)
        assertTrue(
            repository.observeStatuses(StatusScope.ITEM).first()
                .none { it.id == statusId }
        )

        assertEquals(statusId, database.itemDao().getEntity(itemId)?.currentStatusId)
    }

    @Test
    fun usedStatusCannotBeDeletedAndUnusedStatusCanBeReorderedAndDeleted() = runBlocking {
        val usedId = repository.createStatus("使用中", StatusScope.ITEM)
        val unusedId = repository.createStatus("可删除", StatusScope.ITEM)
        database.itemDao().insert(
            ItemEntity(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "Status owner",
                currentStatusId = usedId,
                createdTime = 1,
                updatedTime = 1
            )
        )

        database.itemDao().softDelete(
            database.backupDao().getItems().single { it.currentStatusId == usedId }.id,
            deletedAt = 99
        )
        val blocked = runCatching { repository.deleteStatus(usedId) }
        assertTrue(blocked.isFailure)
        assertTrue(blocked.exceptionOrNull()?.message.orEmpty().contains("1"))
        assertEquals(
            1,
            repository.observeUsageCounts(StatusScope.ITEM).first()[usedId]
        )

        val reversedIds = database.statusDao()
            .getAll(StatusScope.ITEM)
            .map { it.id }
            .reversed()
        repository.reorderStatuses(StatusScope.ITEM, reversedIds)
        assertEquals(
            reversedIds,
            database.statusDao().getAll(StatusScope.ITEM).map { it.id }
        )

        repository.deleteStatus(unusedId)
        assertTrue(database.statusDao().getById(unusedId) == null)
    }

    @Test
    fun recordStatusCanShareNameAndDeletionKeepsHistoricalSnapshot() = runBlocking {
        val itemStatus = repository.createStatus("已完成", StatusScope.ITEM)
        val recordStatus = repository.createStatus("已完成", StatusScope.RECORD)
        assertTrue(
            runCatching {
                repository.createStatus("已完成", StatusScope.RECORD)
            }.isFailure
        )
        val itemId = database.itemDao().insert(
            ItemEntity(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "History owner",
                currentStatusId = itemStatus,
                createdTime = 1,
                updatedTime = 1
            )
        )
        val recordId = database.recordDao().insert(
            RecordEntity(
                itemId = itemId,
                startDate = 1,
                statusSnapshot = "已完成",
                durationMinutes = 90,
                createdAt = 1
            )
        )

        repository.renameStatus(recordStatus, "重读完成")
        assertEquals(
            "已完成",
            database.recordDao().getById(recordId)?.statusSnapshot
        )
        repository.deleteStatus(recordStatus)

        assertEquals(
            "已完成",
            database.recordDao().getById(recordId)?.statusSnapshot
        )
        assertTrue(
            repository.observeStatuses(StatusScope.ITEM).first()
                .any { it.id == itemStatus }
        )
        assertTrue(repository.observeStatuses(StatusScope.RECORD).first().isEmpty())
    }

    @Test
    fun itemAndRecordStatusQueriesAndOrderingRemainIndependent() = runBlocking {
        val originalItemOrder = database.statusDao()
            .getAll(StatusScope.ITEM)
            .map { it.id }
        val firstRecordId = repository.createStatus("进行中", StatusScope.RECORD)
        val secondRecordId = repository.createStatus("中止", StatusScope.RECORD)

        assertEquals(
            listOf(firstRecordId, secondRecordId),
            repository.observeStatuses(StatusScope.RECORD).first().map { it.id }
        )
        assertTrue(
            repository.observeStatuses(StatusScope.ITEM).first()
                .none { it.id == firstRecordId || it.id == secondRecordId }
        )

        val reorderedRecords = listOf(secondRecordId, firstRecordId)
        repository.reorderStatuses(StatusScope.RECORD, reorderedRecords)

        assertEquals(
            reorderedRecords,
            database.statusDao().getAll(StatusScope.RECORD).map { it.id }
        )
        assertEquals(
            originalItemOrder,
            database.statusDao().getAll(StatusScope.ITEM).map { it.id }
        )
    }
}
