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
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivitySynchronizationTest {
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
    fun addEditOverlapAndDeleteRebuildActivitiesWithoutDuplicates() = runBlocking {
        val itemId = repository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "Calendar Book",
                creator = "Author",
                coverPath = null,
                currentStatusId = DefaultLibraryData.IN_PROGRESS_STATUS_ID
            )
        )
        repository.addRecord(
            itemId,
            record(start = day(1), end = day(3))
        )
        assertEquals(
            listOf(day(1), day(2), day(3)),
            activities().map { it.date }.sorted()
        )

        val overlappingId = repository.addRecord(
            itemId,
            record(start = day(2), end = day(4))
        )
        val overlapping = activities()
        assertEquals(listOf(day(1), day(2), day(3), day(4)), overlapping.map { it.date }.sorted())
        assertEquals(overlapping.size, overlapping.distinctBy { it.itemId to it.date }.size)

        repository.updateRecord(
            overlappingId,
            RecordChanges(
                startDate = day(4),
                endDate = day(5),
                ratingHalfStars = null,
                review = null
            )
        )
        assertEquals(
            listOf(day(1), day(2), day(3), day(4), day(5)),
            activities().map { it.date }.sorted()
        )

        repository.deleteRecord(overlappingId)
        assertEquals(
            listOf(day(1), day(2), day(3)),
            activities().map { it.date }.sorted()
        )
    }

    private suspend fun activities() =
        repository.observeActivities(day(1), day(31)).first()

    private fun record(start: Long, end: Long?) = NewRecord(
        startDate = start,
        endDate = end,
        ratingHalfStars = null,
        review = null
    )

    private fun day(value: Int): Long =
        LocalDate.of(2026, 7, value).toStartOfDayMillis()
}
