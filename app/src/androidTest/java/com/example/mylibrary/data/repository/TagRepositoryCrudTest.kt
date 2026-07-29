package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.ItemEntity
import com.example.mylibrary.domain.model.NewTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagRepositoryCrudTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineTagRepository
    private var itemId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .addCallback(DefaultLibraryDataCallback)
            .allowMainThreadQueries()
            .build()
        repository = OfflineTagRepository(database, database.itemDao(), database.tagDao())
        itemId = database.itemDao().insert(
            ItemEntity(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "Tag Test",
                createdTime = 1,
                updatedTime = 1
            )
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun createRenameLinkReorderAndDeleteHierarchicalTags() = runBlocking {
        val literatureId = repository.createTag(NewTag("文学", null))
        val artId = repository.createTag(NewTag("艺术", null))
        val childIds = repository.createTags(literatureId, listOf("小说", "诗歌"))
        val childId = childIds.first()
        repository.renameTag(childId, "长篇小说")

        repository.setItemTag(itemId, literatureId, true)
        repository.setItemTag(itemId, childId, true)
        assertEquals(
            setOf("文学", "长篇小说"),
            repository.observeItemTags(itemId).first().mapTo(mutableSetOf()) { it.name }
        )
        assertEquals(
            1,
            repository.observeUsageCounts().first().getValue(literatureId)
        )

        repository.reorderTags(null, listOf(artId, literatureId))
        repository.reorderTags(literatureId, childIds.reversed())
        val reordered = repository.observeTags().first()
        assertEquals(
            listOf(artId, literatureId),
            reordered.filter { it.parentId == null }.map { it.id }
        )
        assertEquals(
            childIds.reversed(),
            reordered.filter { it.parentId == literatureId }.map { it.id }
        )

        repository.setItemTag(itemId, childId, false)
        assertEquals(
            listOf("文学"),
            repository.observeItemTags(itemId).first().map { it.name }
        )

        val depthResult = runCatching {
            repository.createTag(NewTag("三级标签", childId))
        }
        assertTrue(depthResult.isFailure)

        repository.setItemTag(itemId, childId, true)
        repository.deleteTag(literatureId)
        assertNull(database.tagDao().getById(literatureId))
        assertNull(database.tagDao().getById(childId))
        assertEquals(0, database.tagDao().getUsageCount(literatureId))
        assertEquals(0, database.tagDao().getUsageCount(childId))
        assertTrue(repository.observeItemTags(itemId).first().isEmpty())
    }
}
