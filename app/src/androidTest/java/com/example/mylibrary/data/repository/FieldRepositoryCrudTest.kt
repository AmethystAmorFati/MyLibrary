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
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDefinitionChanges
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.NewFieldDefinition
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
class FieldRepositoryCrudTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineFieldRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .addCallback(DefaultLibraryDataCallback)
            .allowMainThreadQueries()
            .build()
        repository = OfflineFieldRepository(database, database.dynamicFieldDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun createReorderAndDisableCustomFieldsWhileFixedFieldIsProtected() = runBlocking {
        val publisherId = repository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "出版社",
                FieldDataType.TEXT
            )
        )
        val pagesId = repository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "页数",
                FieldDataType.NUMBER
            )
        )

        repository.moveField(pagesId, -1)
        val customFields = repository.observeDefinitions().first()
            .filter { it.typeId == DefaultLibraryData.BOOK_TYPE_ID && !it.isFixed }
        assertEquals(listOf("页数", "出版社"), customFields.map { it.name })

        repository.setFieldEnabled(publisherId, false)
        val publisher = repository.observeDefinitions().first()
            .first { it.id == publisherId }
        assertFalse(publisher.enabled)

        val fixedResult = runCatching {
            repository.setFieldEnabled(DefaultLibraryData.AUTHOR_FIELD_ID, false)
        }
        assertTrue(fixedResult.isFailure)
        assertTrue(
            repository.observeDefinitions().first()
                .first { it.id == DefaultLibraryData.AUTHOR_FIELD_ID }
                .enabled
        )
    }

    @Test
    fun selectionOptionsAndFullListOrderPersist() = runBlocking {
        val publisherId = repository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "出版社",
                FieldDataType.TEXT
            )
        )
        val readingModeId = repository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "阅读方式",
                FieldDataType.MULTI_SELECT
            )
        )

        repository.addFieldOption(readingModeId, "纸质书")
        repository.addFieldOption(readingModeId, "电子书")
        repository.addFieldOption(readingModeId, "听书")
        repository.reorderFieldOptions(
            readingModeId,
            listOf("听书", "纸质书", "电子书")
        )
        repository.renameFieldOption(readingModeId, "纸质书", "实体书")
        repository.deleteFieldOption(readingModeId, "电子书")
        repository.reorderFields(
            DefaultLibraryData.BOOK_TYPE_ID,
            listOf(readingModeId, publisherId)
        )

        val customFields = repository.observeDefinitions().first()
            .filter { it.typeId == DefaultLibraryData.BOOK_TYPE_ID && !it.isFixed }
        assertEquals(listOf("阅读方式", "出版社"), customFields.map { it.name })
        assertEquals(listOf("听书", "实体书"), customFields.first().options)

        repository.deleteField(readingModeId)
        assertTrue(
            repository.observeDefinitions().first().none { it.id == readingModeId }
        )
    }

    @Test
    fun deletingAnOptionKeepsValuesAlreadyStoredOnItems() = runBlocking {
        val fieldId = repository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "阅读方式",
                FieldDataType.SINGLE_SELECT
            )
        )
        repository.addFieldOption(fieldId, "纸质书")
        val itemId = database.itemDao().insert(
            ItemEntity(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "历史作品",
                createdTime = 1L,
                updatedTime = 1L
            )
        )
        database.dynamicFieldDao().replaceValue(
            FieldValueEntity(
                itemId = itemId,
                fieldId = fieldId,
                value = "纸质书"
            )
        )

        repository.deleteFieldOption(fieldId, "纸质书")

        val field = repository.observeDefinitions().first()
            .first { it.id == fieldId }
        assertTrue(field.options.isEmpty())
        assertFalse(field.optionDefinitions.single().isActive)
        assertEquals(
            "纸质书",
            database.dynamicFieldDao().getValuesForField(fieldId).single().value
        )
    }

    @Test
    fun addingADeletedNameRestoresTheSameOptionId() = runBlocking {
        val fieldId = repository.createField(
            NewFieldDefinition(
                DefaultLibraryData.BOOK_TYPE_ID,
                "阅读方式",
                FieldDataType.SINGLE_SELECT
            )
        )
        repository.addFieldOption(fieldId, "电子书")
        val original = repository.observeDefinitions().first()
            .first { it.id == fieldId }
            .optionDefinitions
            .single()

        repository.deleteFieldOption(fieldId, "电子书")
        val deleted = repository.observeDefinitions().first()
            .first { it.id == fieldId }
        assertTrue(deleted.options.isEmpty())
        assertFalse(deleted.optionDefinitions.single().isActive)

        repository.addFieldOption(fieldId, "电子书")
        val restored = repository.observeDefinitions().first()
            .first { it.id == fieldId }
            .optionDefinitions
            .single()

        assertEquals(original.id, restored.id)
        assertTrue(restored.isActive)
        assertEquals("电子书", restored.name)
    }

    @Test
    fun fieldConfigurationIsNormalizedAndScopeLocksAfterDataExists() = runBlocking {
        val fieldId = repository.createField(
            NewFieldDefinition(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                name = "页数",
                dataType = FieldDataType.NUMBER,
                scope = FieldScope.ITEM,
                unit = "页",
                aggregations = setOf(
                    FieldAggregation.SUM,
                    FieldAggregation.AVERAGE,
                    FieldAggregation.OPTION_DISTRIBUTION
                )
            )
        )
        val created = repository.observeDefinitions().first().first { it.id == fieldId }
        assertEquals(
            setOf(FieldAggregation.SUM, FieldAggregation.AVERAGE),
            created.aggregations
        )
        assertEquals("页", created.unit)

        val itemId = database.itemDao().insert(
            ItemEntity(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "已填写",
                createdTime = 1L,
                updatedTime = 1L
            )
        )
        database.dynamicFieldDao().replaceValue(
            FieldValueEntity(itemId = itemId, fieldId = fieldId, value = "12")
        )

        val result = runCatching {
            repository.updateField(
                fieldId,
                FieldDefinitionChanges(
                    name = "页数",
                    dataType = FieldDataType.NUMBER,
                    scope = FieldScope.RECORD,
                    unit = "页",
                    aggregations = setOf(FieldAggregation.SUM)
                )
            )
        }
        assertTrue(result.isFailure)
        assertEquals(
            FieldScope.ITEM,
            repository.observeDefinitions().first().first { it.id == fieldId }.scope
        )
    }
}
