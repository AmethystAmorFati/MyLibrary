package com.example.mylibrary.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.StatusScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    private lateinit var database: LibraryDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            LibraryDatabase::class.java
        )
            .addCallback(DefaultLibraryDataCallback)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun databaseStartsAtCurrentVersionWithDefaultTypesFieldsAndStatuses() = runBlocking {
        val types = database.itemTypeDao().getAll()
        val statuses = database.statusDao().getAll(StatusScope.ITEM)
        val bookFields = database.dynamicFieldDao()
            .getDefinitions(DefaultLibraryData.BOOK_TYPE_ID)
        val movieFields = database.dynamicFieldDao()
            .getDefinitions(DefaultLibraryData.MOVIE_TYPE_ID)

        assertEquals(12, LibraryDatabase.SCHEMA_VERSION)
        assertEquals(listOf("Book", "Movie"), types.map { it.name })
        assertEquals("author", bookFields.single().name)
        assertEquals(true, bookFields.single().isFixed)
        assertEquals("director", movieFields.single().name)
        assertEquals(true, movieFields.single().isFixed)
        assertEquals(
            listOf("想看", "进行中", "完成", "暂停"),
            statuses.map { it.name }
        )
    }

    @Test
    fun dynamicFieldTypesHaveStableStorageValues() {
        assertEquals("text", FieldDataType.TEXT.storageValue)
        assertEquals("number", FieldDataType.NUMBER.storageValue)
        assertEquals("date", FieldDataType.DATE.storageValue)
        assertEquals("boolean", FieldDataType.BOOLEAN.storageValue)
        assertEquals("single_select", FieldDataType.SINGLE_SELECT.storageValue)
        assertEquals("multi_select", FieldDataType.MULTI_SELECT.storageValue)
    }
}
