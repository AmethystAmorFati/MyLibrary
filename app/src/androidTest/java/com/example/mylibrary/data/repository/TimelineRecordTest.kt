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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineRecordTest {
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
    fun timelineUsesEachRecordSnapshotAndDurationWhileCreatedAtRemainsInternal() = runBlocking {
        val itemId = repository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "Timeline Book",
                creator = "Author",
                coverPath = null,
                currentStatusId = DefaultLibraryData.IN_PROGRESS_STATUS_ID
            )
        )
        val firstStart = LocalDate.of(2023, 4, 12).toStartOfDayMillis()
        val secondStart = LocalDate.of(2026, 7, 1).toStartOfDayMillis()
        val firstRecordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = firstStart,
                endDate = null,
                ratingHalfStars = 8,
                review = "first",
                statusSnapshot = "已完成",
                durationMinutes = 90
            )
        )
        val secondRecordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = secondStart,
                endDate = null,
                ratingHalfStars = 9,
                review = "second",
                statusSnapshot = "重读中",
                durationMinutes = 45
            )
        )
        val beforeEdit = repository.observeItemDetail(itemId).first()!!
        val firstCreatedAt = beforeEdit.records.first { it.id == firstRecordId }.createdAt
        val secondCreatedAt = beforeEdit.records.first { it.id == secondRecordId }.createdAt

        val timeline = repository.observeTimelineRecords(
            startDate = firstStart,
            endDate = secondStart
        ).first()
        assertEquals(listOf(firstRecordId, secondRecordId), timeline.map { it.recordId })
        assertEquals(listOf(firstStart, secondStart), timeline.map { it.recordStartDate })
        assertEquals(listOf(8, 9), timeline.map { it.ratingHalfStars })
        assertEquals(listOf("已完成", "重读中"), timeline.map { it.statusSnapshot })
        assertEquals(listOf(90L, 45L), timeline.map { it.durationMinutes })

        repository.updateItemStatus(
            itemId = itemId,
            statusId = DefaultLibraryData.COMPLETED_STATUS_ID
        )
        assertEquals(
            listOf("已完成", "重读中"),
            repository.observeTimelineRecords(firstStart, secondStart)
                .first()
                .map { it.statusSnapshot }
        )

        repository.updateRecord(
            secondRecordId,
            RecordChanges(
                startDate = secondStart,
                endDate = secondStart,
                ratingHalfStars = 7,
                review = "second edited",
                statusSnapshot = "中止",
                durationMinutes = 75
            )
        )
        val afterEdit = repository.observeItemDetail(itemId).first()!!
        assertEquals(firstCreatedAt, afterEdit.records.first { it.id == firstRecordId }.createdAt)
        assertEquals(secondCreatedAt, afterEdit.records.first { it.id == secondRecordId }.createdAt)
        assertEquals(
            listOf(8, 7),
            repository.observeTimelineRecords(firstStart, secondStart)
                .first()
                .map { it.ratingHalfStars }
        )
        assertEquals(
            listOf("已完成", "中止"),
            repository.observeTimelineRecords(firstStart, secondStart)
                .first()
                .map { it.statusSnapshot }
        )
        assertEquals(
            listOf(90L, 75L),
            repository.observeTimelineRecords(firstStart, secondStart)
                .first()
                .map { it.durationMinutes }
        )

        repository.deleteRecord(secondRecordId)
        assertEquals(
            listOf(firstRecordId),
            repository.observeTimelineRecords(firstStart, secondStart)
                .first()
                .map { it.recordId }
        )
    }

    @Test
    fun tenDayRecordProjectsTenActivitiesAcrossMonthsButOneTimelineRow() = runBlocking {
        val itemId = repository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "Cross-month Book",
                creator = "Author",
                coverPath = null,
                currentStatusId = DefaultLibraryData.IN_PROGRESS_STATUS_ID
            )
        )
        val start = LocalDate.of(2026, 7, 25)
        val end = LocalDate.of(2026, 8, 3)
        val recordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = start.toStartOfDayMillis(),
                endDate = end.toStartOfDayMillis(),
                ratingHalfStars = null,
                review = null
            )
        )
        val timeline = repository.observeTimelineRecords(0, Long.MAX_VALUE).first()
        val julyActivities = repository.observeActivities(
            start.toStartOfDayMillis(),
            LocalDate.of(2026, 7, 31).toStartOfDayMillis()
        ).first()
        val augustActivities = repository.observeActivities(
            LocalDate.of(2026, 8, 1).toStartOfDayMillis(),
            end.toStartOfDayMillis()
        ).first()

        assertEquals(listOf(recordId), timeline.map { it.recordId })
        assertEquals(10, timeline.single().activityDates.size)
        assertEquals(7, julyActivities.size)
        assertEquals(3, augustActivities.size)
        assertTrue(
            (julyActivities + augustActivities).all { it.recordId == recordId }
        )
    }
}
