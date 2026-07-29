package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.ItemQuoteDraft
import com.example.mylibrary.domain.model.ItemRecordDraft
import com.example.mylibrary.domain.model.ItemSaveRequest
import com.example.mylibrary.domain.model.NewFieldDefinition
import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.model.NewRecord
import com.example.mylibrary.domain.model.NewTag
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemAggregateSaveTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineLibraryRepository
    private lateinit var fieldRepository: OfflineFieldRepository
    private lateinit var tagRepository: OfflineTagRepository

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
        fieldRepository = OfflineFieldRepository(database, database.dynamicFieldDao())
        tagRepository = OfflineTagRepository(
            database,
            database.itemDao(),
            database.tagDao()
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aggregateSaveCreatesMultipleRecordsAndPreservesCreatedAtOnEdit() = runBlocking {
        val fieldId = fieldRepository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "出版社",
                FieldDataType.TEXT
            )
        )
        val tagId = tagRepository.createTag(NewTag("文学", null))
        val itemId = repository.saveItem(
            request(
                itemId = null,
                title = "Batch Save",
                fieldId = fieldId,
                fieldValue = "First Press",
                tagIds = setOf(tagId),
                statusId = DefaultLibraryData.PAUSED_STATUS_ID,
                records = listOf(
                    draft(start = day(1), end = day(3), review = "first"),
                    draft(start = day(10), end = day(10), review = "second")
                )
            )
        )

        val created = repository.observeItemDetail(itemId).first()!!
        assertEquals(2, created.records.size)
        assertEquals(DefaultLibraryData.PAUSED_STATUS_ID, created.item.currentStatusId)
        assertEquals(setOf(tagId), created.tags.map { it.id }.toSet())
        assertEquals(
            "First Press",
            created.fields.first { it.definitionId == fieldId }.value
        )

        val retained = created.records.first { it.review == "first" }
        repository.saveItem(
            request(
                itemId = itemId,
                title = "Batch Save Edited",
                fieldId = fieldId,
                fieldValue = "Edited Press",
                tagIds = setOf(tagId),
                statusId = DefaultLibraryData.COMPLETED_STATUS_ID,
                records = listOf(
                    ItemRecordDraft(
                        id = retained.id,
                        startDate = day(2),
                        endDate = day(4),
                        ratingHalfStars = 9,
                        review = "edited",
                        createdAt = retained.createdAt + 999_999
                    )
                )
            )
        )

        val edited = repository.observeItemDetail(itemId).first()!!
        assertEquals("Batch Save Edited", edited.item.title)
        assertEquals(DefaultLibraryData.COMPLETED_STATUS_ID, edited.item.currentStatusId)
        assertEquals(1, edited.records.size)
        assertEquals(retained.id, edited.records.single().id)
        assertEquals(retained.createdAt, edited.records.single().createdAt)
        assertEquals("edited", edited.records.single().review)
    }

    @Test
    fun aggregateSaveCreatesEditsAndDeletesQuoteDraftsWithStableIds() = runBlocking {
        val fieldId = fieldRepository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "版本",
                FieldDataType.TEXT
            )
        )
        val itemId = repository.saveItem(
            request(
                itemId = null,
                title = "摘录草稿",
                fieldId = fieldId,
                fieldValue = "",
                tagIds = emptySet(),
                statusId = DefaultLibraryData.IN_PROGRESS_STATUS_ID,
                records = emptyList(),
                quotes = listOf(
                    quoteDraft("local-first", "第一条", "第一章", "12", 100),
                    quoteDraft("local-second", "第二条", null, null, 200)
                )
            )
        )
        val createdQuotes = database.quoteDao().getAllForItem(itemId)
        assertEquals(2, createdQuotes.size)
        val retained = createdQuotes.single { it.content == "第一条" }
        val deleted = createdQuotes.single { it.content == "第二条" }
        assertEquals(itemId, retained.itemId)
        assertEquals("第一章", retained.chapter)
        assertEquals(100L, retained.createdTime)

        repository.saveItem(
            request(
                itemId = itemId,
                title = "摘录草稿已编辑",
                fieldId = fieldId,
                fieldValue = "",
                tagIds = emptySet(),
                statusId = DefaultLibraryData.IN_PROGRESS_STATUS_ID,
                records = emptyList(),
                quotes = listOf(
                    ItemQuoteDraft(
                        localKey = "persisted-${retained.id}",
                        persistedId = retained.id,
                        content = "第一条已修改",
                        chapter = "  ",
                        page = "13",
                        createdTime = retained.createdTime
                    ),
                    quoteDraft("local-third", "第三条", "第二章", null, 300)
                ),
                deletedQuoteIds = setOf(deleted.id)
            )
        )

        val editedQuotes = database.quoteDao().getAllForItem(itemId)
        assertEquals(2, editedQuotes.size)
        val editedRetained = editedQuotes.single { it.id == retained.id }
        assertEquals("第一条已修改", editedRetained.content)
        assertEquals(null, editedRetained.chapter)
        assertEquals("13", editedRetained.page)
        assertEquals(retained.createdTime, editedRetained.createdTime)
        assertTrue(editedQuotes.none { it.id == deleted.id })
        assertEquals(
            itemId,
            editedQuotes.single { it.content == "第三条" }.itemId
        )
    }

    @Test
    fun aggregateSaveRollsBackItemTagsFieldsAndRecordsWhenRecordDraftIsInvalid() =
        runBlocking {
            val fieldId = fieldRepository.createField(
                NewFieldDefinition(
                    DefaultLibraryData.BOOK_TYPE_ID,
                    "页数",
                    FieldDataType.NUMBER
                )
            )
            val originalTagId = tagRepository.createTag(NewTag("原标签", null))
            val replacementTagId = tagRepository.createTag(NewTag("新标签", null))
            val requestedCreatedTime = noon(2010, 3, 4)
            val itemId = repository.saveItem(
                request(
                    itemId = null,
                    title = "Before Failure",
                    fieldId = fieldId,
                    fieldValue = "320",
                    tagIds = setOf(originalTagId),
                    statusId = DefaultLibraryData.WANT_TO_WATCH_STATUS_ID,
                    records = listOf(draft(day(5), day(6), "kept")),
                    createdTime = requestedCreatedTime
                )
            )
            val originalCreatedTime =
                repository.observeItemDetail(itemId).first()!!.item.createdTime
            val otherItemId = repository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "Other",
                    creator = "Other Author",
                    coverPath = null
                )
            )
            val foreignRecordId = repository.addRecord(
                otherItemId,
                NewRecord(day(20), null, null, null)
            )

            val result = runCatching {
                repository.saveItem(
                    request(
                        itemId = itemId,
                        title = "Must Roll Back",
                        fieldId = fieldId,
                        fieldValue = "999",
                        tagIds = setOf(replacementTagId),
                        statusId = DefaultLibraryData.COMPLETED_STATUS_ID,
                        createdTime = noon(1980, 1, 2),
                        records = listOf(
                            ItemRecordDraft(
                                id = foreignRecordId,
                                startDate = day(7),
                                endDate = null,
                                ratingHalfStars = null,
                                review = "invalid owner",
                                createdAt = null
                            )
                        )
                    )
                )
            }
            assertTrue(result.isFailure)

            val after = repository.observeItemDetail(itemId).first()!!
            assertEquals("Before Failure", after.item.title)
            assertEquals(originalCreatedTime, after.item.createdTime)
            assertEquals(
                DefaultLibraryData.WANT_TO_WATCH_STATUS_ID,
                after.item.currentStatusId
            )
            assertEquals(setOf(originalTagId), after.tags.map { it.id }.toSet())
            assertEquals("320", after.fields.first { it.definitionId == fieldId }.value)
            assertEquals(listOf("kept"), after.records.map { it.review })
        }

    @Test
    fun aggregateSaveAssignsCreationTimeAndPreservesItOnEdit() =
        runBlocking {
            val fieldId = fieldRepository.createField(
                NewFieldDefinition(
                    DefaultLibraryData.BOOK_TYPE_ID,
                    "版本",
                    FieldDataType.TEXT
                )
            )
            val ignoredRequestedTime = noon(1998, 6, 12)
            val beforeSave = System.currentTimeMillis()
            val itemId = repository.saveItem(
                request(
                    itemId = null,
                    title = "Historical",
                    fieldId = fieldId,
                    fieldValue = "",
                    tagIds = emptySet(),
                    statusId = DefaultLibraryData.WANT_TO_WATCH_STATUS_ID,
                    records = emptyList(),
                    createdTime = ignoredRequestedTime
                )
            )
            val assignedTime =
                repository.observeItemDetail(itemId).first()!!.item.createdTime
            assertTrue(assignedTime >= beforeSave)

            repository.saveItem(
                request(
                    itemId = itemId,
                    title = "Historical edited",
                    fieldId = fieldId,
                    fieldValue = "",
                    tagIds = emptySet(),
                    statusId = DefaultLibraryData.WANT_TO_WATCH_STATUS_ID,
                    records = emptyList(),
                    createdTime = noon(1980, 1, 1)
                )
            )
            assertEquals(
                assignedTime,
                repository.observeItemDetail(itemId).first()!!.item.createdTime
            )
        }

    @Test
    fun suppliedItemCreationDateCannotChangeItemRecordOrActivity() = runBlocking {
        val fieldId = fieldRepository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "版本",
                FieldDataType.TEXT
            )
        )
        val originalCreation = noon(2020, 1, 1)
        val changedCreation = noon(1990, 2, 3)
        val itemId = repository.saveItem(
            request(
                itemId = null,
                title = "Independent dates",
                fieldId = fieldId,
                fieldValue = "",
                tagIds = emptySet(),
                statusId = DefaultLibraryData.COMPLETED_STATUS_ID,
                records = listOf(draft(day(5), day(7), "kept")),
                createdTime = originalCreation
            )
        )
        val before = repository.observeItemDetail(itemId).first()!!
        val beforeRecord = before.records.single()
        val beforeActivities = repository.observeActivities(day(1), day(31)).first()

        repository.saveItem(
            request(
                itemId = itemId,
                title = before.item.title,
                fieldId = fieldId,
                fieldValue = "",
                tagIds = emptySet(),
                statusId = DefaultLibraryData.COMPLETED_STATUS_ID,
                records = listOf(
                    ItemRecordDraft(
                        id = beforeRecord.id,
                        startDate = beforeRecord.startDate,
                        endDate = beforeRecord.endDate,
                        ratingHalfStars = beforeRecord.ratingHalfStars,
                        review = beforeRecord.review,
                        createdAt = beforeRecord.createdAt
                    )
                ),
                createdTime = changedCreation
            )
        )

        val after = repository.observeItemDetail(itemId).first()!!
        assertEquals(before.item.createdTime, after.item.createdTime)
        assertEquals(beforeRecord, after.records.single())
        assertEquals(beforeActivities, repository.observeActivities(day(1), day(31)).first())
    }

    private fun request(
        itemId: Long?,
        title: String,
        fieldId: Long,
        fieldValue: String,
        tagIds: Set<Long>,
        statusId: Long,
        records: List<ItemRecordDraft>,
        createdTime: Long = noon(2026, 7, 1),
        quotes: List<ItemQuoteDraft> = emptyList(),
        deletedQuoteIds: Set<Long> = emptySet()
    ) = ItemSaveRequest(
        itemId = itemId,
        typeId = DefaultLibraryData.BOOK_TYPE_ID,
        title = title,
        creator = "Author",
        createdTime = createdTime,
        coverPath = null,
        thumbnailPath = null,
        dynamicValues = mapOf(fieldId to fieldValue),
        currentStatusId = statusId,
        tagIds = tagIds,
        records = records,
        quotes = quotes,
        deletedQuoteIds = deletedQuoteIds
    )

    private fun draft(start: Long, end: Long?, review: String) =
        ItemRecordDraft(
            id = null,
            startDate = start,
            endDate = end,
            ratingHalfStars = null,
            review = review,
            createdAt = null
        )

    private fun quoteDraft(
        localKey: String,
        content: String,
        chapter: String?,
        page: String?,
        createdTime: Long
    ) = ItemQuoteDraft(
        localKey = localKey,
        persistedId = null,
        content = content,
        chapter = chapter,
        page = page,
        createdTime = createdTime
    )

    private fun day(value: Int): Long =
        LocalDate.of(2026, 7, value).toStartOfDayMillis()

    private fun noon(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atTime(LocalTime.NOON)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
