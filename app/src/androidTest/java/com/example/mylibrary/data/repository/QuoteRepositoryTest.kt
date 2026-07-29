package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.FieldValueEntity
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.data.entity.QuoteEntity
import com.example.mylibrary.data.entity.RecordEntity
import com.example.mylibrary.domain.model.NewQuote
import com.example.mylibrary.domain.model.QuoteChanges
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuoteRepositoryTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineQuoteRepository
    private var itemId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .addCallback(DefaultLibraryDataCallback)
            .allowMainThreadQueries()
            .build()
        repository = OfflineQuoteRepository(database.quoteDao(), database.itemDao())
        itemId = database.itemDao().insert(
            ItemEntity(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "百年孤独",
                currentStatusId = DefaultLibraryData.WANT_TO_WATCH_STATUS_ID,
                createdTime = 1,
                updatedTime = 1
            )
        )
        database.dynamicFieldDao().replaceValue(
            FieldValueEntity(
                itemId = itemId,
                fieldId = DefaultLibraryData.AUTHOR_FIELD_ID,
                value = "加西亚·马尔克斯"
            )
        )
        (1L..7L).forEach { createdTime ->
            database.quoteDao().insert(
                QuoteEntity(
                    itemId = itemId,
                    content = if (createdTime == 4L) {
                        "人生而自由"
                    } else {
                        "摘录 $createdTime"
                    },
                    createdTime = createdTime
                )
            )
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun recentQuotesAreLimitedToFiveAndSortedNewestFirst() = runBlocking {
        val recent = repository.observeRecent().first()

        assertEquals(5, recent.size)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), recent.map { it.quote.createdTime })
    }

    @Test
    fun searchMatchesContentTitleAndCreator() = runBlocking {
        assertEquals(
            listOf("人生而自由"),
            repository.observePage("自由", 20, 0).first().map { it.quote.content }
        )
        assertEquals(7, repository.observePage("孤独", 20, 0).first().size)
        assertEquals(7, repository.observePage("马尔克斯", 20, 0).first().size)
    }

    @Test
    fun pageQueryUsesLimitAndOffset() = runBlocking {
        val page = repository.observePage("", limit = 3, offset = 3).first()

        assertEquals(listOf(4L, 3L, 2L), page.map { it.quote.createdTime })
    }

    @Test
    fun blankPageIsStoredAsNullAndUpdateKeepsCreatedTime() = runBlocking {
        val quoteId = repository.create(
            NewQuote(itemId = itemId, content = "新增摘录", page = "   ")
        )
        val created = repository.observeForItem(itemId).first()
            .first { it.id == quoteId }
        assertNull(created.page)

        repository.update(
            quoteId,
            QuoteChanges(content = "编辑摘录", page = "23")
        )
        val updated = repository.observeForItem(itemId).first()
            .first { it.id == quoteId }
        assertEquals("编辑摘录", updated.content)
        assertEquals("23", updated.page)
        assertEquals(created.createdTime, updated.createdTime)
    }

    @Test
    fun workStatisticsRequireRecordsDeduplicateItemsAndIgnoreDisplayNames() = runBlocking {
        val initial = repository.observeStatistics().first()
        assertEquals(0, initial.readingWorkCount)
        assertEquals(0, initial.viewingWorkCount)

        database.recordDao().insert(
            RecordEntity(
                itemId = itemId,
                startDate = 100,
                createdAt = 1
            )
        )
        database.recordDao().insert(
            RecordEntity(
                itemId = itemId,
                startDate = 200,
                createdAt = 2
            )
        )
        val movieId = database.itemDao().insert(
            ItemEntity(
                typeId = DefaultLibraryData.MOVIE_TYPE_ID,
                title = "电影",
                createdTime = 1,
                updatedTime = 1
            )
        )
        database.recordDao().insert(
            RecordEntity(
                itemId = movieId,
                startDate = 300,
                createdAt = 3
            )
        )

        assertEquals(1, repository.observeStatistics().first().readingWorkCount)
        assertEquals(1, repository.observeStatistics().first().viewingWorkCount)

        val bookType = requireNotNull(
            database.itemTypeDao().getById(DefaultLibraryData.BOOK_TYPE_ID)
        )
        val movieType = requireNotNull(
            database.itemTypeDao().getById(DefaultLibraryData.MOVIE_TYPE_ID)
        )
        database.itemTypeDao().update(bookType.copy(name = "图书"))
        database.itemTypeDao().update(movieType.copy(name = "影片"))
        assertEquals(1, repository.observeStatistics().first().readingWorkCount)
        assertEquals(1, repository.observeStatistics().first().viewingWorkCount)

        database.itemDao().softDelete(itemId, deletedAt = 999)
        assertEquals(0, repository.observeStatistics().first().readingWorkCount)
        assertEquals(1, repository.observeStatistics().first().viewingWorkCount)
    }

    @Test
    fun mediaStatisticsSeparateRecordAndQuoteAggregatesAndPreserveZero() = runBlocking {
        database.recordDao().insert(
            RecordEntity(
                itemId = itemId,
                startDate = 100,
                durationMinutes = 60,
                createdAt = 1
            )
        )
        database.recordDao().insert(
            RecordEntity(
                itemId = itemId,
                startDate = 200,
                durationMinutes = 0,
                createdAt = 2
            )
        )
        database.recordDao().insert(
            RecordEntity(
                itemId = itemId,
                startDate = 300,
                durationMinutes = null,
                createdAt = 3
            )
        )

        val statistics = repository.observeMediaStatistics().first().reading

        assertEquals(1L, statistics.itemCount)
        assertEquals(3L, statistics.recordCount)
        assertEquals(7L, statistics.quoteCount)
        assertEquals(2L, statistics.valuedRecordCount)
        assertEquals(60L, statistics.totalDurationMinutes)
        assertEquals(30L, statistics.averagePerRecordMinutes)
        assertEquals(60L, statistics.maximumSingleDurationMinutes)
        assertEquals(itemId, statistics.longestItemId)

        val bookType = requireNotNull(
            database.itemTypeDao().getById(DefaultLibraryData.BOOK_TYPE_ID)
        )
        database.itemTypeDao().update(bookType.copy(name = "图书"))
        assertEquals(
            1L,
            repository.observeMediaStatistics().first().reading.itemCount
        )
    }

    @Test
    fun mediaStatisticsUseRealRoomRowsForCrossProductsAveragesAndStableTie() =
        runBlocking {
            listOf(60L, 0L, null).forEachIndexed { index, duration ->
                database.recordDao().insert(
                    RecordEntity(
                        itemId = itemId,
                        startDate = 100L + index,
                        durationMinutes = duration,
                        createdAt = 10L + index
                    )
                )
            }
            val secondBookId = database.itemDao().insert(
                ItemEntity(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "A Book",
                    createdTime = 2,
                    updatedTime = 2
                )
            )
            listOf(30L, 30L).forEachIndexed { index, duration ->
                database.recordDao().insert(
                    RecordEntity(
                        itemId = secondBookId,
                        startDate = 200L + index,
                        durationMinutes = duration,
                        createdAt = 20L + index
                    )
                )
                database.quoteDao().insert(
                    QuoteEntity(
                        itemId = secondBookId,
                        content = "Second book quote $index",
                        createdTime = 30L + index
                    )
                )
            }

            val beforeMovie = repository.observeMediaStatistics().first()
            assertEquals(0L, beforeMovie.watching.itemCount)

            val bookWithoutDurationId = database.itemDao().insert(
                ItemEntity(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "Book without duration",
                    createdTime = 3,
                    updatedTime = 3
                )
            )
            database.recordDao().insert(
                RecordEntity(
                    itemId = bookWithoutDurationId,
                    startDate = 250,
                    durationMinutes = null,
                    createdAt = 35
                )
            )

            val movieId = database.itemDao().insert(
                ItemEntity(
                    typeId = DefaultLibraryData.MOVIE_TYPE_ID,
                    title = "Movie without duration",
                    createdTime = 4,
                    updatedTime = 4
                )
            )
            database.recordDao().insert(
                RecordEntity(
                    itemId = movieId,
                    startDate = 300,
                    durationMinutes = null,
                    createdAt = 40
                )
            )
            database.quoteDao().insert(
                QuoteEntity(
                    itemId = movieId,
                    content = "Movie quote",
                    createdTime = 41
                )
            )

            val statistics = repository.observeMediaStatistics().first()
            with(statistics.reading) {
                assertEquals(3L, itemCount)
                assertEquals(6L, recordCount)
                assertEquals(9L, quoteCount)
                assertEquals(4L, valuedRecordCount)
                assertEquals(2L, valuedItemCount)
                assertEquals(120L, totalDurationMinutes)
                assertEquals(30L, averagePerRecordMinutes)
                assertEquals(60L, averagePerItemMinutes)
                assertEquals(60L, maximumSingleDurationMinutes)
                assertEquals(secondBookId, longestItemId)
                assertEquals("A Book", longestItemTitle)
                assertEquals(60L, longestItemDurationMinutes)
            }
            with(statistics.watching) {
                assertEquals(1L, itemCount)
                assertEquals(1L, recordCount)
                assertEquals(1L, quoteCount)
                assertEquals(0L, valuedRecordCount)
                assertNull(totalDurationMinutes)
                assertNull(averagePerRecordMinutes)
                assertNull(averagePerItemMinutes)
                assertNull(longestItemId)
            }
        }
}
