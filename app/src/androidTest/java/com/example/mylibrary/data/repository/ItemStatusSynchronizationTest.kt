package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.model.NewRecord
import com.example.mylibrary.domain.model.RecordChanges
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemStatusSynchronizationTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineLibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .addCallback(DefaultLibraryDataCallback)
            .allowMainThreadQueries()
            .build()
        repository = OfflineLibraryRepository(
            database,
            database.itemDao(),
            database.itemTypeDao(),
            database.statusDao(),
            database.recordDao(),
            database.activityDao(),
            database.dynamicFieldDao(),
            database.tagDao()
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun addingEditingAndDeletingRecordsNeverChangesCurrentItemStatus() = runBlocking {
        val itemId = repository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "Independent Status",
                creator = "Author",
                coverPath = null,
                currentStatusId = DefaultLibraryData.PAUSED_STATUS_ID
            )
        )
        val recordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = 100,
                endDate = 150,
                ratingHalfStars = 8,
                review = null
            )
        )
        assertCurrentStatus(itemId, DefaultLibraryData.PAUSED_STATUS_ID)

        repository.updateRecord(
            recordId,
            RecordChanges(
                startDate = 110,
                endDate = 160,
                ratingHalfStars = 9,
                review = "updated history"
            )
        )
        assertCurrentStatus(itemId, DefaultLibraryData.PAUSED_STATUS_ID)

        repository.deleteRecord(recordId)
        assertCurrentStatus(itemId, DefaultLibraryData.PAUSED_STATUS_ID)

        repository.updateItemStatus(itemId, DefaultLibraryData.COMPLETED_STATUS_ID)
        assertEquals(
            itemId,
            repository.observeItems(
                statusId = DefaultLibraryData.COMPLETED_STATUS_ID
            ).first().single().id
        )
    }

    private suspend fun assertCurrentStatus(itemId: Long, expected: Long) {
        assertEquals(
            expected,
            repository.observeItemDetail(itemId).first()!!.item.currentStatusId
        )
    }
}
