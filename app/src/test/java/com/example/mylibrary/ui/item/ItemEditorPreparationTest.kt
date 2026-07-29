package com.example.mylibrary.ui.item

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.DynamicFieldValue
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.ItemDetail
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.LibraryItem
import com.example.mylibrary.domain.model.LibraryQuote
import com.example.mylibrary.domain.model.LibraryRecord
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.StatusScope
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemEditorPreparationTest {
    @Test
    fun addModeBuildsOneCompletePreparedState() {
        val prepared = prepareEditorState(addSources(), itemId = null)

        assertFalse(prepared.uiState.isLoading)
        assertEquals(1L, prepared.uiState.selectedTypeId)
        assertEquals(1L, prepared.uiState.selectedStatusId)
        assertEquals(listOf(10L), prepared.uiState.dynamicFields.map { it.definitionId })
        assertEquals(
            listOf(11L),
            prepared.uiState.recordFieldTemplates.map { it.definitionId }
        )
        assertTrue(prepared.uiState.records.isEmpty())
        assertTrue(prepared.uiState.quoteDrafts.isEmpty())
        assertEquals(prepared.uiState.toEditorSnapshot(), prepared.initialSnapshot)
    }

    @Test
    fun editorOnlyPublishesOfficialBookAndMovieTypes() {
        val prepared = prepareEditorState(
            addSources().copy(
                types = listOf(
                    ItemType(id = 1L, name = "书籍", sortOrder = 0),
                    ItemType(id = 2L, name = "电影", sortOrder = 1),
                    ItemType(id = 99L, name = "自定义", sortOrder = 2)
                )
            ),
            itemId = null
        )

        assertEquals(listOf(1L, 2L), prepared.uiState.types.map(ItemType::id))
        assertEquals(1L, prepared.uiState.selectedTypeId)
    }

    @Test
    fun editModeRejectsUnknownLegacyTypeInsteadOfSilentlyChangingItToBook() {
        val customItem = editSources().let { sources ->
            sources.copy(
                types = sources.types + ItemType(99L, "旧类型", 2),
                detail = requireNotNull(sources.detail).copy(
                    item = requireNotNull(sources.detail).item.copy(
                        typeId = 99L,
                        typeName = "旧类型"
                    )
                )
            )
        }

        val failure = runCatching {
            prepareEditorState(customItem, itemId = 42L)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("不会静默改写"))
    }

    @Test
    fun editModePreservesMultipleRecordsFieldsQuotesAndBaselines() {
        val sources = editSources()
        val prepared = prepareEditorState(sources, itemId = 42L)

        assertEquals(42L, prepared.uiState.editingItemId)
        assertEquals("银河系漫游指南", prepared.uiState.title)
        assertEquals(setOf(5L), prepared.uiState.selectedTagIds)
        assertEquals("三联书店", prepared.uiState.dynamicFields.single().value)
        assertEquals(listOf("纸书", "电子书"), prepared.uiState.recordFieldTemplates.single().options)
        assertEquals(listOf("record-101", "record-102"), prepared.uiState.records.map { it.key })
        assertEquals(listOf("已完成", "中止"), prepared.uiState.records.map { it.statusSnapshot })
        assertEquals(listOf("1" to "30", "3" to "15"), prepared.uiState.records.map {
            it.durationHoursText to it.durationMinutesText
        })
        assertEquals(
            listOf("纸书", "电子书"),
            prepared.uiState.records.map { it.dynamicFields.single().value }
        )
        assertEquals(
            sources.quotes.map(LibraryQuote::toEditorDraft),
            prepared.uiState.quoteDrafts
        )
        assertEquals("covers/original.webp", prepared.initialCoverPaths.first)
        assertEquals("covers/thumb.webp", prepared.initialCoverPaths.second)
        assertEquals(prepared.uiState.toEditorSnapshot(), prepared.initialSnapshot)
        assertNotEquals(
            prepared.initialSnapshot,
            prepared.uiState.copy(title = "用户修改").toEditorSnapshot()
        )
    }

    @Test
    fun missingEditedItemStillFailsPreparation() {
        assertThrows(IllegalStateException::class.java) {
            prepareEditorState(addSources(), itemId = 404L)
        }
    }

    @Test
    fun sourceLoadingAndPurePreparationUseTheirOwnDispatchers() = runTest {
        val io = CountingDispatcher(StandardTestDispatcher(testScheduler))
        val default = CountingDispatcher(StandardTestDispatcher(testScheduler))
        var sourceLoads = 0

        val result = async {
            loadAndPrepareEditorState(
                itemId = null,
                ioDispatcher = io,
                defaultDispatcher = default,
                loadSources = {
                    sourceLoads += 1
                    addSources()
                }
            )
        }
        advanceUntilIdle()

        assertFalse(result.await().uiState.isLoading)
        assertEquals(1, sourceLoads)
        assertTrue(io.dispatchCount > 0)
        assertTrue(default.dispatchCount > 0)
    }

    @Test
    fun cancelledSourceLoadNeverReturnsPreparedState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var returnedPreparedState = false
        val job = launch {
            loadAndPrepareEditorState(
                itemId = null,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
                loadSources = { awaitCancellation() }
            )
            returnedPreparedState = true
        }

        runCurrent()
        job.cancelAndJoin()

        assertFalse(returnedPreparedState)
    }

    @Test
    fun quoteFirstValueAndUpdatesShareOneColdUpstream() = runTest {
        val channel = Channel<List<LibraryQuote>>(Channel.UNLIMITED)
        var subscriptions = 0
        val source = flow {
            subscriptions += 1
            for (quotes in channel) {
                emit(quotes)
            }
        }
        val shared = shareEditorQuotes(
            source = source,
            scope = backgroundScope,
            upstreamDispatcher = StandardTestDispatcher(testScheduler)
        )
        val initial = listOf(quote(id = 1L, content = "初始摘录"))
        val updated = initial + quote(id = 2L, content = "后续摘录")

        runCurrent()
        channel.send(initial)
        runCurrent()
        assertEquals(initial, shared.awaitInitialQuotes())

        channel.send(updated)
        runCurrent()
        assertEquals(updated, shared.currentQuotesOr(emptyList()))
        assertEquals(1, subscriptions)

        channel.close()
    }

    @Test
    fun localQuoteDraftUpdateKeepsAllOtherUserDrafts() {
        val draft = ItemEditorUiState(
            title = "用户正在输入",
            creator = "Douglas Adams",
            dynamicFields = listOf(
                DynamicFieldInputState(
                    definitionId = 10L,
                    name = "出版社",
                    dataType = FieldDataType.TEXT,
                    value = "用户新值"
                )
            ),
            records = listOf(
                RecordDraftUiState(
                    key = "draft-local",
                    id = null,
                    startDate = "2026-07-28",
                    endDate = "",
                    ratingHalfStars = 10,
                    review = "未保存草稿",
                    createdAt = 1L
                )
            ),
            isLoading = false
        )
        val quoteDraft = quote(id = 3L, content = "用户确认的摘录").toEditorDraft()

        val updated = draft.withCompletedQuoteDraft(quoteDraft)

        assertEquals(listOf(quoteDraft), updated.quoteDrafts)
        assertEquals(draft.title, updated.title)
        assertEquals(draft.creator, updated.creator)
        assertEquals(draft.dynamicFields, updated.dynamicFields)
        assertEquals(draft.records, updated.records)
    }

    private fun addSources() = EditorInitialData(
        types = listOf(ItemType(id = 1L, name = "书籍", sortOrder = 0)),
        statuses = listOf(
            LibraryStatus(id = 1L, name = "想读", sortOrder = 0, enabled = true)
        ),
        recordStatuses = listOf(
            LibraryStatus(
                id = 2L,
                name = "已完成",
                sortOrder = 0,
                enabled = true,
                scope = StatusScope.RECORD
            )
        ),
        tags = listOf(
            LibraryTag(id = 5L, name = "科幻", parentId = null, sortOrder = 0, enabled = true)
        ),
        definitions = definitions(),
        detail = null,
        quotes = emptyList(),
        loadedAtMillis = 1234L
    )

    private fun editSources(): EditorInitialData {
        val itemField = DynamicFieldValue(
            definitionId = 10L,
            name = "出版社",
            dataType = FieldDataType.TEXT,
            value = "三联书店",
            sortOrder = 0,
            isFixed = false
        )
        val tag = LibraryTag(
            id = 5L,
            name = "科幻",
            parentId = null,
            sortOrder = 0,
            enabled = true
        )
        val item = LibraryItem(
            id = 42L,
            typeId = 1L,
            typeName = "书籍",
            title = "银河系漫游指南",
            creator = "Douglas Adams",
            coverPath = "covers/original.webp",
            thumbnailPath = "covers/thumb.webp",
            createdTime = 500L,
            updatedTime = 600L,
            currentStatusId = 1L,
            currentStatusName = "想读",
            latestRatingHalfStars = 10
        )
        val records = listOf(
            record(
                id = 101L,
                medium = "纸书",
                day = 1,
                status = "已完成",
                duration = 90L
            ),
            record(
                id = 102L,
                medium = "电子书",
                day = 2,
                status = "中止",
                duration = 195L
            )
        )
        return addSources().copy(
            detail = ItemDetail(
                item = item,
                records = records,
                fields = listOf(itemField),
                tags = listOf(tag)
            ),
            quotes = listOf(quote(id = 1L, content = "别慌")),
            loadedAtMillis = 700L
        )
    }

    private fun definitions() = listOf(
        DynamicFieldDefinition(
            id = 10L,
            typeId = 1L,
            typeName = "书籍",
            name = "出版社",
            dataType = FieldDataType.TEXT,
            enabled = true,
            sortOrder = 0,
            isFixed = false,
            scope = FieldScope.ITEM
        ),
        DynamicFieldDefinition(
            id = 11L,
            typeId = 1L,
            typeName = "书籍",
            name = "阅读介质",
            dataType = FieldDataType.SINGLE_SELECT,
            enabled = true,
            sortOrder = 0,
            isFixed = false,
            options = listOf("纸书", "电子书"),
            scope = FieldScope.RECORD
        )
    )

    private fun record(
        id: Long,
        medium: String,
        day: Int,
        status: String,
        duration: Long
    ) = LibraryRecord(
        id = id,
        itemId = 42L,
        startDate = LocalDate.of(2026, 7, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        endDate = null,
        ratingHalfStars = 10,
        review = "第 $day 次",
        createdAt = id,
        dynamicValues = mapOf(11L to medium),
        statusSnapshot = status,
        durationMinutes = duration
    )

    private fun quote(id: Long, content: String) = LibraryQuote(
        id = id,
        itemId = 42L,
        content = content,
        page = null,
        createdTime = id
    )

    private class CountingDispatcher(
        private val delegate: CoroutineDispatcher
    ) : CoroutineDispatcher() {
        var dispatchCount: Int = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount += 1
            delegate.dispatch(context, block)
        }
    }
}
