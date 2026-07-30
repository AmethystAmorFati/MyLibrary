package com.example.mylibrary.export.visual

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.repository.OfflineLibraryRepository
import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.model.NewRecord
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualExportDataSourceTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineLibraryRepository
    private lateinit var dataSource: RoomVisualExportDataSource

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
        dataSource = RoomVisualExportDataSource(database.activityDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun realRoomQueryKeepsFixedTypesCoverPathsAndExcludesTrash() = runBlocking {
        val book = createItem(
            typeId = DefaultLibraryData.BOOK_TYPE_ID,
            title = "Book",
            cover = "covers/book.webp",
            thumbnail = "covers/book-thumb.webp"
        )
        val movie = createItem(
            typeId = DefaultLibraryData.MOVIE_TYPE_ID,
            title = "Movie",
            cover = null,
            thumbnail = null
        )
        val deleted = createItem(
            typeId = DefaultLibraryData.BOOK_TYPE_ID,
            title = "Deleted",
            cover = "covers/deleted.webp",
            thumbnail = "covers/deleted-thumb.webp"
        )
        addRecord(book, 3, createdAt = 300)
        addRecord(movie, 4, createdAt = 400)
        addRecord(deleted, 5, createdAt = 500)
        repository.deleteItem(deleted)

        val rows = dataSource.activitiesBetween(day(1), day(31))

        assertEquals(listOf(book, movie), rows.map { it.itemId })
        assertEquals(
            listOf(
                DefaultLibraryData.BOOK_TYPE_ID,
                DefaultLibraryData.MOVIE_TYPE_ID
            ),
            rows.map { it.typeId }
        )
        assertEquals("covers/book.webp", rows.first().coverPath)
        assertEquals("covers/book-thumb.webp", rows.first().thumbnailPath)
        assertTrue(rows.all { it.recordId != null })
    }

    private suspend fun createItem(
        typeId: Long,
        title: String,
        cover: String?,
        thumbnail: String?
    ): Long = repository.createItem(
        NewItem(
            typeId = typeId,
            title = title,
            creator = "Creator",
            coverPath = cover,
            thumbnailPath = thumbnail,
            currentStatusId = DefaultLibraryData.IN_PROGRESS_STATUS_ID
        )
    )

    private suspend fun addRecord(
        itemId: Long,
        dayOfMonth: Int,
        createdAt: Long
    ) {
        repository.addRecord(
            itemId,
            NewRecord(
                startDate = day(dayOfMonth),
                endDate = null,
                ratingHalfStars = null,
                review = null,
                createdAt = createdAt
            )
        )
    }

    private fun day(day: Int): Long =
        LocalDate.of(2026, 1, day).toStartOfDayMillis()
}
