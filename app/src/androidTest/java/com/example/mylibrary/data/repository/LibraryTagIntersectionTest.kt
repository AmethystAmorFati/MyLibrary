package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.model.NewTag
import com.example.mylibrary.domain.model.StatusScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryTagIntersectionTest {
    private lateinit var database: LibraryDatabase
    private lateinit var repository: OfflineLibraryRepository
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
        tagRepository = OfflineTagRepository(
            database,
            database.itemDao(),
            database.tagDao()
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun selectedTagIdsRequireAllDirectBindingsAndCombineWithStatusAndQuery() =
        runBlocking {
            val book = repository.observeItemTypes().first().first { it.name == "Book" }
            val defaultStatus = repository.observeStatuses().first().first()
            val otherStatus = OfflineStatusRepository(
                database,
                database.statusDao()
            ).createStatus("其他测试状态", StatusScope.ITEM)
            val rootA = tagRepository.createTag(NewTag("A", null))
            val childA1 = tagRepository.createTag(NewTag("a1", rootA))
            val rootB = tagRepository.createTag(NewTag("B", null))
            val childB2 = tagRepository.createTag(NewTag("b2", rootB))

            val tripleTarget = createItem(book.id, "目标作品", defaultStatus.id)
            val pairTarget = createItem(book.id, "目标双标签", defaultStatus.id)
            val childOnly = createItem(book.id, "目标仅子标签", defaultStatus.id)
            val tripleWrongStatus = createItem(book.id, "目标错误状态", otherStatus)
            val tripleWrongQuery = createItem(book.id, "其他作品", defaultStatus.id)

            linkTags(tripleTarget, rootA, childA1, childB2)
            linkTags(pairTarget, rootA, childA1)
            linkTags(childOnly, childA1)
            linkTags(tripleWrongStatus, rootA, childA1, childB2)
            linkTags(tripleWrongQuery, rootA, childA1, childB2)

            val pairMatches = repository.observeItems(
                tagIds = setOf(rootA, childA1)
            ).first().map { it.id }.toSet()
            assertEquals(
                setOf(tripleTarget, pairTarget, tripleWrongStatus, tripleWrongQuery),
                pairMatches
            )
            assertFalse(childOnly in pairMatches)

            assertEquals(
                setOf(tripleTarget, tripleWrongStatus, tripleWrongQuery),
                repository.observeItems(
                    tagIds = setOf(rootA, childA1, childB2)
                ).first().map { it.id }.toSet()
            )

            assertEquals(
                tripleTarget,
                repository.observeItems(
                    query = "目标",
                    statusId = defaultStatus.id,
                    tagIds = setOf(rootA, childA1, childB2)
                ).first().single().id
            )
        }

    private suspend fun createItem(
        typeId: Long,
        title: String,
        statusId: Long
    ): Long = repository.createItem(
        NewItem(
            typeId = typeId,
            title = title,
            creator = "测试作者",
            coverPath = null,
            currentStatusId = statusId
        )
    )

    private suspend fun linkTags(itemId: Long, vararg tagIds: Long) {
        tagIds.forEach { tagId ->
            tagRepository.setItemTag(itemId, tagId, true)
        }
    }
}
