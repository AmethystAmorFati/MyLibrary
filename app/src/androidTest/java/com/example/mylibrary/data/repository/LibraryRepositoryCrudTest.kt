package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.domain.model.ItemChanges
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.NewFieldDefinition
import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.model.NewRecord
import com.example.mylibrary.domain.model.NewTag
import com.example.mylibrary.domain.model.RecordChanges
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.model.encodeFieldSelection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryCrudTest {
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
    fun createEditSearchRecordAndSoftDeleteItem() = runBlocking {
        val book = repository.observeItemTypes().first().first { it.name == "Book" }
        val fieldRepository = OfflineFieldRepository(database, database.dynamicFieldDao())
        val publisherFieldId = fieldRepository.createField(
            NewFieldDefinition(book.id, "出版社", FieldDataType.TEXT)
        )
        val itemId = repository.createItem(
            NewItem(
                book.id,
                "First Title",
                "First Author",
                null,
                dynamicValues = mapOf(publisherFieldId to "First Press")
            )
        )

        assertEquals(
            "First Author",
            repository.observeItems(query = "Author").first().single().creator
        )
        assertEquals(
            "First Press",
            repository.observeItemDetail(itemId).first()!!.fields
                .first { it.definitionId == publisherFieldId }
                .value
        )

        repository.updateItem(
            itemId,
            ItemChanges(
                "Edited Title",
                "Edited Author",
                "covers/original/book.jpg",
                dynamicValues = mapOf(publisherFieldId to "Edited Press")
            )
        )
        assertEquals(
            "Edited Title",
            repository.observeItemDetail(itemId).first()!!.item.title
        )
        assertEquals(
            "Edited Press",
            repository.observeItemDetail(itemId).first()!!.fields
                .first { it.definitionId == publisherFieldId }
                .value
        )

        val tagRepository = OfflineTagRepository(
            database,
            database.itemDao(),
            database.tagDao()
        )
        val parentTagId = tagRepository.createTag(NewTag("文学", null))
        val childTagId = tagRepository.createTag(NewTag("小说", parentTagId))
        tagRepository.setItemTag(itemId, childTagId, true)
        assertTrue(
            repository.observeItems(tagIds = setOf(parentTagId)).first().isEmpty()
        )
        assertEquals(
            itemId,
            repository.observeItems(tagIds = setOf(childTagId)).first().single().id
        )
        assertTrue(
            repository.observeItems(
                tagIds = setOf(parentTagId, childTagId)
            ).first().isEmpty()
        )
        tagRepository.setItemTag(itemId, parentTagId, true)
        assertEquals(
            itemId,
            repository.observeItems(
                tagIds = setOf(parentTagId, childTagId)
            ).first().single().id
        )

        val completed = repository.observeStatuses().first().first { it.name == "完成" }
        repository.addRecord(
            itemId,
            NewRecord(100, 200, 9, "Excellent")
        )
        repository.updateItemStatus(itemId, completed.id)
        assertEquals(
            itemId,
            repository.observeItems(statusId = completed.id).first().single().id
        )

        repository.deleteItem(itemId)
        assertTrue(repository.observeItems().first().isEmpty())
        assertNotNull(database.itemDao().getEntity(itemId)?.deletedAt)
    }

    @Test
    fun recordScopedFieldsAreSavedPerRecordAndCascadeOnDelete() = runBlocking {
        val book = repository.observeItemTypes().first().first { it.name == "Book" }
        val fieldRepository = OfflineFieldRepository(database, database.dynamicFieldDao())
        val pagesReadId = fieldRepository.createField(
            NewFieldDefinition(
                typeId = book.id,
                name = "本次页数",
                dataType = FieldDataType.NUMBER,
                scope = FieldScope.RECORD,
                unit = "页"
            )
        )
        val itemId = repository.createItem(
            NewItem(book.id, "重读", "作者", null)
        )

        val firstRecordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = 100L,
                endDate = null,
                ratingHalfStars = null,
                review = null,
                dynamicValues = mapOf(pagesReadId to "12")
            )
        )
        val secondRecordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = 200L,
                endDate = null,
                ratingHalfStars = null,
                review = null,
                dynamicValues = mapOf(pagesReadId to "9")
            )
        )

        val detail = repository.observeItemDetail(itemId).first()!!
        assertEquals(
            setOf("12", "9"),
            detail.records.mapNotNull { it.dynamicValues[pagesReadId] }.toSet()
        )
        assertEquals(
            "12",
            database.dynamicFieldDao()
                .getRecordValue(firstRecordId, pagesReadId)
                ?.value
        )

        repository.deleteRecord(firstRecordId)
        assertEquals(
            null,
            database.dynamicFieldDao().getRecordValue(firstRecordId, pagesReadId)
        )
        assertEquals(
            "9",
            database.dynamicFieldDao()
                .getRecordValue(secondRecordId, pagesReadId)
                ?.value
        )
    }

    @Test
    fun itemListAggregatesNullableRecordDurationsWithoutCachingOnItem() = runBlocking {
        val itemId = repository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "累计时长",
                creator = "作者",
                coverPath = null
            )
        )
        val emptyDurationRecordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = 100L,
                endDate = null,
                ratingHalfStars = null,
                review = null,
                durationMinutes = null
            )
        )
        assertEquals(
            null,
            repository.observeItems().first().single { it.id == itemId }.totalDurationMinutes
        )

        val zeroDurationRecordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = 200L,
                endDate = null,
                ratingHalfStars = null,
                review = null,
                durationMinutes = 0
            )
        )
        val positiveDurationRecordId = repository.addRecord(
            itemId,
            NewRecord(
                startDate = 300L,
                endDate = null,
                ratingHalfStars = null,
                review = null,
                durationMinutes = 90
            )
        )
        assertEquals(
            90L,
            repository.observeItems().first().single { it.id == itemId }.totalDurationMinutes
        )

        repository.updateRecord(
            positiveDurationRecordId,
            RecordChanges(
                startDate = 300L,
                endDate = null,
                ratingHalfStars = null,
                review = null,
                durationMinutes = 150
            )
        )
        assertEquals(
            150L,
            repository.observeItems().first().single { it.id == itemId }.totalDurationMinutes
        )

        repository.deleteRecord(positiveDurationRecordId)
        assertEquals(
            0L,
            repository.observeItems().first().single { it.id == itemId }.totalDurationMinutes
        )
        repository.deleteRecord(zeroDurationRecordId)
        repository.deleteRecord(emptyDurationRecordId)
        assertEquals(
            null,
            repository.observeItems().first().single { it.id == itemId }.totalDurationMinutes
        )
    }

    @Test
    fun recordScopedStatusCannotBeAssignedToAnItem() = runBlocking {
        val statusRepository = OfflineStatusRepository(database, database.statusDao())
        val recordStatusId = statusRepository.createStatus(
            "仅限记录",
            StatusScope.RECORD
        )

        assertTrue(
            runCatching {
                repository.createItem(
                    NewItem(
                        typeId = DefaultLibraryData.BOOK_TYPE_ID,
                        title = "错误状态",
                        creator = "作者",
                        coverPath = null,
                        currentStatusId = recordStatusId
                    )
                )
            }.isFailure
        )

        val itemId = repository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "正确状态",
                creator = "作者",
                coverPath = null
            )
        )
        assertTrue(
            runCatching {
                repository.updateItemStatus(itemId, recordStatusId)
            }.isFailure
        )
    }

    @Test
    fun repositoryRejectsRatingsOutsideHalfStarRange() = runBlocking {
        val book = repository.observeItemTypes().first().first { it.name == "Book" }
        val itemId = repository.createItem(
            NewItem(
                typeId = book.id,
                title = "Rating validation",
                creator = "Author",
                coverPath = null
            )
        )

        assertIllegalArgument {
            repository.addRecord(
                itemId,
                NewRecord(100, null, 0, null)
            )
        }
        val recordId = repository.addRecord(
            itemId,
            NewRecord(100, null, 10, null)
        )
        assertIllegalArgument {
            repository.updateRecord(
                recordId,
                RecordChanges(100, null, 11, null)
            )
        }
    }

    @Test
    fun deletedSelectionOptionsRemainHistoricalButCannotBeNewlySelected() = runBlocking {
        val fieldRepository = OfflineFieldRepository(
            database,
            database.dynamicFieldDao()
        )
        val fieldId = fieldRepository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "阅读方式",
                FieldDataType.MULTI_SELECT
            )
        )
        fieldRepository.addFieldOption(fieldId, "电子书")
        fieldRepository.addFieldOption(fieldId, "纸质书")
        val itemId = repository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "历史作品",
                creator = "作者",
                coverPath = null,
                dynamicValues = mapOf(
                    fieldId to encodeFieldSelection(listOf("电子书"))
                )
            )
        )

        fieldRepository.deleteFieldOption(fieldId, "电子书")

        assertEquals(
            "电子书",
            repository.observeItemDetail(itemId).first()!!.fields
                .first { it.definitionId == fieldId }
                .value
        )
        repository.updateItem(
            itemId,
            ItemChanges(
                title = "只修改标题",
                creator = "作者",
                coverPath = null,
                dynamicValues = emptyMap()
            )
        )
        assertEquals(
            "电子书",
            repository.observeItemDetail(itemId).first()!!.fields
                .first { it.definitionId == fieldId }
                .value
        )

        val newItemResult = runCatching {
            repository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "新作品",
                    creator = "新作者",
                    coverPath = null,
                    dynamicValues = mapOf(fieldId to "电子书")
                )
            )
        }
        assertTrue(newItemResult.isFailure)

        repository.updateItem(
            itemId,
            ItemChanges(
                title = "明确修改字段",
                creator = "作者",
                coverPath = null,
                dynamicValues = mapOf(fieldId to "纸质书")
            )
        )
        assertEquals(
            "纸质书",
            repository.observeItemDetail(itemId).first()!!.fields
                .first { it.definitionId == fieldId }
                .value
        )
    }

    private suspend fun assertIllegalArgument(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
