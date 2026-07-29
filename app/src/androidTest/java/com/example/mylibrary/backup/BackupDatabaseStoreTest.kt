package com.example.mylibrary.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mylibrary.backup.model.BackupActivity
import com.example.mylibrary.backup.model.BackupData
import com.example.mylibrary.backup.model.BackupFieldDefinition
import com.example.mylibrary.backup.model.BackupFieldOption
import com.example.mylibrary.backup.model.BackupFieldValue
import com.example.mylibrary.backup.model.BackupItem
import com.example.mylibrary.backup.model.BackupItemTag
import com.example.mylibrary.backup.model.BackupItemType
import com.example.mylibrary.backup.model.BackupQuote
import com.example.mylibrary.backup.model.BackupRecord
import com.example.mylibrary.backup.model.BackupRecordFieldValue
import com.example.mylibrary.backup.model.BackupStatus
import com.example.mylibrary.backup.model.BackupTag
import com.example.mylibrary.data.database.LibraryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDatabaseStoreTest {
    private lateinit var database: LibraryDatabase
    private lateinit var store: BackupDatabaseStore

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = BackupDatabaseStore(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun completeRoundTripIncludesEveryBusinessTable() = runBlocking {
        val expected = richData("完整数据")

        store.replace(expected, emptyMap())

        assertEquals(expected, store.readSnapshot())
    }

    @Test
    fun replacementDoesNotMergeOldData() = runBlocking {
        store.replace(richData("A"), emptyMap())
        val replacement = richData("B").copy(
            items = richData("B").items.map { it.copy(id = it.id + 100) },
            records = emptyList(),
            activities = emptyList(),
            itemTags = emptyList(),
            fieldValues = emptyList(),
            quotes = emptyList()
        )

        store.replace(replacement, emptyMap())

        val actual = store.readSnapshot()
        assertEquals(replacement, actual)
        assertTrue(actual.items.none { it.title.startsWith("A") })
    }

    @Test
    fun failedInsertionRollsBackDeletionOfOriginalData() = runBlocking {
        val original = richData("原数据")
        store.replace(original, emptyMap())
        val invalid = original.copy(
            items = original.items.map { it.copy(typeId = 999) }
        )

        try {
            store.replace(invalid, emptyMap())
        } catch (_: Throwable) {
            // Expected foreign-key failure inside the replacement transaction.
        }

        assertEquals(original, store.readSnapshot())
    }

    private fun richData(prefix: String) = BackupData(
        itemTypes = listOf(
            BackupItemType(1, "Book", 0),
            BackupItemType(2, "Movie", 1),
            BackupItemType(3, "Custom", 2)
        ),
        statuses = listOf(
            BackupStatus(1, "想看", 0, true),
            BackupStatus(2, "完成", 1, true),
            BackupStatus(3, "完成", 0, true, scope = "record")
        ),
        fieldDefinitions = listOf(
            BackupFieldDefinition(1, 1, "author", "text", true, 0, true, emptyList()),
            BackupFieldDefinition(
                2,
                1,
                "format",
                "multi_select",
                true,
                1,
                false,
                options = listOf("纸书"),
                optionDefinitions = listOf(
                    BackupFieldOption(21, "纸书", true, 0),
                    BackupFieldOption(22, "电子", false, 1)
                )
            ),
            BackupFieldDefinition(
                id = 3,
                typeId = 1,
                name = "focus_count",
                dataType = "number",
                enabled = true,
                sortOrder = 2,
                isFixed = false,
                options = emptyList(),
                scope = "record",
                unit = "次",
                aggregations = setOf("sum", "average")
            )
        ),
        tags = listOf(
            BackupTag(1, "一级", null, 0, true),
            BackupTag(2, "二级", 1, 0, true)
        ),
        items = listOf(
            BackupItem(1, 1, "$prefix 作品", null, 2, 10, 20, null),
            BackupItem(2, 2, "$prefix 电影", null, null, 11, 21, null)
        ),
        records = listOf(
            BackupRecord(
                id = 1,
                itemId = 1,
                startDate = 100,
                endDate = 101,
                ratingHalfStars = 8,
                review = "多行\n评价",
                createdAt = 50,
                statusSnapshot = "完成",
                durationMinutes = 90
            ),
            BackupRecord(
                id = 2,
                itemId = 1,
                startDate = 102,
                endDate = null,
                ratingHalfStars = null,
                review = null,
                createdAt = 51,
                statusSnapshot = null,
                durationMinutes = 0
            )
        ),
        activities = listOf(
            BackupActivity(1, 100, 1, 1),
            BackupActivity(2, 102, 1, 2)
        ),
        itemTags = listOf(BackupItemTag(1, 1), BackupItemTag(1, 2)),
        fieldValues = listOf(
            BackupFieldValue(1, 1, 1, "作者"),
            BackupFieldValue(2, 1, 2, "纸书\u001F电子")
        ),
        quotes = listOf(
            BackupQuote(
                id = 1,
                itemId = 1,
                content = "摘录 📚",
                source = "纸书",
                page = "42",
                createdTime = 60,
                chapter = "第一章"
            ),
            BackupQuote(2, 1, "", null, null, 61)
        ),
        recordFieldValues = listOf(
            BackupRecordFieldValue(1, 1, 3, "12"),
            BackupRecordFieldValue(2, 2, 3, "9")
        )
    )
}
