package com.example.mylibrary.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.data.database.DefaultLibraryDataCallback
import com.example.mylibrary.data.database.LibraryDatabase
import com.example.mylibrary.data.entity.QuoteEntity
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.NewFieldDefinition
import com.example.mylibrary.domain.model.NewItem
import com.example.mylibrary.domain.model.NewRecord
import com.example.mylibrary.domain.model.NewTag
import com.example.mylibrary.domain.model.StoredCoverImage
import com.example.mylibrary.domain.repository.CoverImageRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrashRepositoryTest {
    private lateinit var database: LibraryDatabase
    private lateinit var libraryRepository: OfflineLibraryRepository
    private lateinit var trashRepository: OfflineTrashRepository
    private lateinit var covers: RecordingCoverRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .addCallback(DefaultLibraryDataCallback)
            .allowMainThreadQueries()
            .build()
        libraryRepository = OfflineLibraryRepository(
            database,
            database.itemDao(),
            database.itemTypeDao(),
            database.statusDao(),
            database.recordDao(),
            database.activityDao(),
            database.dynamicFieldDao(),
            database.tagDao()
        )
        covers = RecordingCoverRepository()
        trashRepository = OfflineTrashRepository(
            database,
            database.itemDao(),
            covers
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun restoreKeepsIdentityAndRelationsThenPermanentDeleteCascadesOnlyTarget() =
        runBlocking {
            val fieldRepository = OfflineFieldRepository(
                database,
                database.dynamicFieldDao()
            )
            val tagRepository = OfflineTagRepository(
                database,
                database.itemDao(),
                database.tagDao()
            )
            val fieldId = fieldRepository.createField(
                NewFieldDefinition(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    name = "出版社",
                    dataType = FieldDataType.TEXT
                )
            )
            val tagId = tagRepository.createTag(NewTag("文学", null))
            val itemId = libraryRepository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "可恢复作品",
                    creator = "作者",
                    coverPath = "images/original/target.jpg",
                    thumbnailPath = "images/thumbnail/target.jpg",
                    dynamicValues = mapOf(fieldId to "出版社")
                )
            )
            tagRepository.setItemTag(itemId, tagId, true)
            val startDate = 1_700_000_000_000L
            val recordId = libraryRepository.addRecord(
                itemId,
                NewRecord(startDate, startDate, 9, "保留评价")
            )
            val recordCreatedAt =
                database.recordDao().getById(recordId)!!.createdAt
            database.quoteDao().insert(
                QuoteEntity(
                    itemId = itemId,
                    content = "保留摘录",
                    createdTime = 10
                )
            )
            val originalCreatedTime = database.itemDao().getEntity(itemId)!!.createdTime

            val otherId = libraryRepository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.MOVIE_TYPE_ID,
                    title = "其他作品",
                    creator = "导演",
                    coverPath = null
                )
            )
            database.quoteDao().insert(
                QuoteEntity(
                    itemId = otherId,
                    content = "其他摘录",
                    createdTime = 11
                )
            )

            libraryRepository.deleteItem(itemId)
            assertTrue(libraryRepository.observeItems().first().none { it.id == itemId })
            assertEquals(itemId, trashRepository.observeItems().first().single().id)
            assertEquals(1, database.backupDao().getRecords().count { it.itemId == itemId })
            assertEquals(1, database.backupDao().getActivities().count { it.itemId == itemId })
            assertEquals(1, database.backupDao().getQuotes().count { it.itemId == itemId })
            assertEquals(1, database.backupDao().getItemTags().count { it.itemId == itemId })
            assertTrue(database.backupDao().getFieldValues().any { it.itemId == itemId })

            trashRepository.restoreItem(itemId)
            assertTrue(trashRepository.observeItems().first().isEmpty())
            assertTrue(libraryRepository.observeItems().first().any { it.id == itemId })
            assertTrue(
                libraryRepository.observeTimelineRecords(
                    startDate,
                    startDate
                ).first().any { it.recordId == recordId }
            )
            assertTrue(
                libraryRepository.observeActivities(
                    startDate - 86_400_000,
                    startDate + 86_400_000
                ).first().any { it.itemId == itemId }
            )
            assertEquals(originalCreatedTime, database.itemDao().getEntity(itemId)?.createdTime)
            assertEquals(recordCreatedAt, database.recordDao().getById(recordId)?.createdAt)

            libraryRepository.deleteItem(itemId)
            trashRepository.permanentlyDeleteItem(itemId)
            assertTrue(database.itemDao().getEntity(itemId) == null)
            assertTrue(database.backupDao().getRecords().none { it.itemId == itemId })
            assertTrue(database.backupDao().getActivities().none { it.itemId == itemId })
            assertTrue(database.backupDao().getQuotes().none { it.itemId == itemId })
            assertTrue(database.backupDao().getItemTags().none { it.itemId == itemId })
            assertTrue(database.backupDao().getFieldValues().none { it.itemId == itemId })
            assertTrue(database.itemDao().getEntity(otherId) != null)
            assertEquals(1, database.backupDao().getQuotes().count { it.itemId == otherId })
            assertEquals(
                listOf(
                    "images/original/target.jpg" to
                        "images/thumbnail/target.jpg"
                ),
                covers.deleted
            )
        }

    @Test
    fun emptyTrashDeletesAllRowsInOneStateChangeButKeepsSharedCover() = runBlocking {
        val firstId = libraryRepository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "一",
                creator = "作者",
                coverPath = "images/original/shared.jpg",
                thumbnailPath = "images/thumbnail/one.jpg"
            )
        )
        val secondId = libraryRepository.createItem(
            NewItem(
                typeId = DefaultLibraryData.MOVIE_TYPE_ID,
                title = "二",
                creator = "导演",
                coverPath = "images/original/two.jpg",
                thumbnailPath = "images/thumbnail/two.jpg"
            )
        )
        libraryRepository.createItem(
            NewItem(
                typeId = DefaultLibraryData.BOOK_TYPE_ID,
                title = "仍在资料库",
                creator = "作者",
                coverPath = "images/original/shared.jpg"
            )
        )
        libraryRepository.deleteItem(firstId)
        libraryRepository.deleteItem(secondId)

        trashRepository.emptyTrash()

        assertTrue(trashRepository.observeItems().first().isEmpty())
        assertTrue(database.itemDao().getEntity(firstId) == null)
        assertTrue(database.itemDao().getEntity(secondId) == null)
        assertTrue(
            covers.deleted.none { (original, _) ->
                original == "images/original/shared.jpg"
            }
        )
        assertTrue(
            covers.deleted.any { (_, thumbnail) ->
                thumbnail == "images/thumbnail/one.jpg"
            }
        )
    }

    @Test
    fun batchDeleteIsAtomicDeletesOnlySelectionAndCleansCoversAfterCommit() =
        runBlocking {
            val firstId = libraryRepository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "选中一",
                    creator = "作者",
                    coverPath = "images/original/shared-selection.jpg",
                    thumbnailPath = "images/thumbnail/selected-one.jpg"
                )
            )
            val secondId = libraryRepository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.MOVIE_TYPE_ID,
                    title = "选中二",
                    creator = "导演",
                    coverPath = "images/original/selected-two.jpg",
                    thumbnailPath = "images/thumbnail/selected-two.jpg"
                )
            )
            val unselectedId = libraryRepository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "未选中",
                    creator = "作者",
                    coverPath = "images/original/shared-selection.jpg"
                )
            )
            val recordId = libraryRepository.addRecord(
                firstId,
                NewRecord(1_700_000_000_000L, null, 8, "批量关联")
            )
            database.quoteDao().insert(
                QuoteEntity(
                    itemId = firstId,
                    content = "批量摘录",
                    createdTime = 12
                )
            )
            libraryRepository.deleteItem(firstId)
            libraryRepository.deleteItem(secondId)
            libraryRepository.deleteItem(unselectedId)

            val failure = runCatching {
                trashRepository.permanentlyDeleteItems(
                    setOf(firstId, Long.MAX_VALUE)
                )
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(database.itemDao().getEntity(firstId) != null)
            assertTrue(covers.deleted.isEmpty())

            covers.beforeDelete = { _, _ ->
                assertTrue(database.itemDao().getEntity(firstId) == null)
                assertTrue(database.itemDao().getEntity(secondId) == null)
            }
            trashRepository.permanentlyDeleteItems(setOf(firstId, secondId))

            assertTrue(database.itemDao().getEntity(firstId) == null)
            assertTrue(database.itemDao().getEntity(secondId) == null)
            assertTrue(database.itemDao().getEntity(unselectedId) != null)
            assertTrue(database.recordDao().getById(recordId) == null)
            assertTrue(database.backupDao().getQuotes().none { it.itemId == firstId })
            assertTrue(
                covers.deleted.none { (original, _) ->
                    original == "images/original/shared-selection.jpg"
                }
            )
            assertTrue(
                covers.deleted.any { (_, thumbnail) ->
                    thumbnail == "images/thumbnail/selected-one.jpg"
                }
            )
        }

    @Test
    fun coverCleanupFailureDoesNotTurnCommittedPermanentDeleteIntoFailure() =
        runBlocking {
            val itemId = libraryRepository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "清理失败仍删除",
                    creator = "作者",
                    coverPath = "images/original/locked.jpg",
                    thumbnailPath = "images/thumbnail/locked.jpg"
                )
            )
            libraryRepository.deleteItem(itemId)
            covers.beforeDelete = { _, _ -> error("cover locked") }

            trashRepository.permanentlyDeleteItem(itemId)

            assertTrue(database.itemDao().getEntity(itemId) == null)
            assertTrue(trashRepository.observeItems().first().isEmpty())
        }

    @Test
    fun cleanupCancellationPropagatesWithoutPretendingDatabaseDeleteRolledBack() =
        runBlocking {
            val itemId = libraryRepository.createItem(
                NewItem(
                    typeId = DefaultLibraryData.BOOK_TYPE_ID,
                    title = "取消清理",
                    creator = "作者",
                    coverPath = "images/original/cancel.jpg",
                    thumbnailPath = "images/thumbnail/cancel.jpg"
                )
            )
            libraryRepository.deleteItem(itemId)
            covers.beforeDelete = { _, _ ->
                throw CancellationException("cancel cleanup")
            }

            val failure = runCatching {
                trashRepository.permanentlyDeleteItem(itemId)
            }.exceptionOrNull()

            assertTrue(failure is CancellationException)
            assertTrue(database.itemDao().getEntity(itemId) == null)
        }
}

private class RecordingCoverRepository : CoverImageRepository {
    val deleted = mutableListOf<Pair<String?, String?>>()
    var beforeDelete: suspend (String?, String?) -> Unit = { _, _ -> }

    override suspend fun save(uri: String): StoredCoverImage =
        error("Not used in trash tests")

    override fun resolveOriginal(relativePath: String?): File? = null

    override suspend fun importOriginal(source: File): StoredCoverImage =
        error("Not used in trash tests")

    override suspend fun delete(originalPath: String?, thumbnailPath: String?) {
        beforeDelete(originalPath, thumbnailPath)
        deleted += originalPath to thumbnailPath
    }
}
