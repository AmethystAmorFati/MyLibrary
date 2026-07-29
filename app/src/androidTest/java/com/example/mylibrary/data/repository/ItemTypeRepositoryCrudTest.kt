package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.domain.model.ItemTypeKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemTypeRepositoryCrudTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineItemTypeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .addCallback(DefaultLibraryDataCallback)
            .allowMainThreadQueries()
            .build()
        repository = OfflineItemTypeRepository(
            database,
            database.itemTypeDao(),
            database.dynamicFieldDao()
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun customTypeSupportsCreateRenameReorderAndDelete() = runBlocking {
        val customId = repository.createType("播客")
        val creatorField = database.dynamicFieldDao().getDefinitions(customId).single()
        assertEquals("author", creatorField.name)
        assertTrue(creatorField.isFixed)

        repository.renameType(customId, "音频节目")
        assertEquals("音频节目", database.itemTypeDao().getById(customId)?.name)

        val reversedIds = database.itemTypeDao().getAll().map { it.id }.reversed()
        repository.reorderTypes(reversedIds)
        assertEquals(reversedIds, database.itemTypeDao().getAll().map { it.id })

        repository.deleteType(customId)
        assertTrue(database.itemTypeDao().getById(customId) == null)
    }

    @Test
    fun builtInNamesCanChangeButKindsAndDeletionProtectionRemainStable() = runBlocking {
        repository.renameType(DefaultLibraryData.BOOK_TYPE_ID, "图书")
        repository.renameType(DefaultLibraryData.MOVIE_TYPE_ID, "影片")
        val builtIns = repository.observeTypes().first()
        assertEquals(
            ItemTypeKind.BOOK,
            builtIns.first { it.id == DefaultLibraryData.BOOK_TYPE_ID }.kind
        )
        assertEquals(
            ItemTypeKind.MOVIE,
            builtIns.first { it.id == DefaultLibraryData.MOVIE_TYPE_ID }.kind
        )
        assertTrue(
            runCatching {
                repository.deleteType(DefaultLibraryData.BOOK_TYPE_ID)
            }.isFailure
        )

        val customId = repository.createType("课程")
        val itemId = database.itemDao().insert(
            ItemEntity(
                typeId = customId,
                title = "Used type",
                createdTime = 1,
                updatedTime = 1
            )
        )
        val blocked = runCatching { repository.deleteType(customId) }

        assertTrue(blocked.isFailure)
        assertTrue(blocked.exceptionOrNull()?.message.orEmpty().contains("1"))
        assertEquals(customId, database.itemDao().getEntity(itemId)?.typeId)
        assertEquals(1, repository.observeUsageCounts().first()[customId])

        database.itemDao().softDelete(itemId, deletedAt = 99)
        assertEquals(1, repository.observeUsageCounts().first()[customId])
        assertTrue(runCatching { repository.deleteType(customId) }.isFailure)
    }
}
