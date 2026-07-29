package com.example.mylibrary.ui.item

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.FieldValueParser
import com.example.mylibrary.domain.model.ItemDetail
import com.example.mylibrary.domain.model.ItemRecordDraft
import com.example.mylibrary.domain.model.ItemQuoteDraft
import com.example.mylibrary.domain.model.ItemSaveRequest
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.domain.model.LibraryQuote
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.domain.model.NewTag
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.domain.usecase.CoverImageUseCases
import com.example.mylibrary.domain.usecase.FieldUseCases
import com.example.mylibrary.domain.usecase.LibraryUseCases
import com.example.mylibrary.domain.usecase.QuoteUseCases
import com.example.mylibrary.domain.usecase.TagUseCases
import com.example.mylibrary.util.formatDate
import com.example.mylibrary.util.parseDateText
import com.example.mylibrary.util.runBestEffortCleanup
import com.example.mylibrary.util.splitTotalMinutes
import com.example.mylibrary.util.toTotalMinutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ItemEditorViewModel(
    private val useCases: LibraryUseCases,
    private val fieldUseCases: FieldUseCases,
    private val tagUseCases: TagUseCases,
    private val coverImageUseCases: CoverImageUseCases,
    private val quoteUseCases: QuoteUseCases,
    private val itemId: Long?
) : ViewModel() {
    private val mutableState = MutableStateFlow(ItemEditorUiState())
    val uiState: StateFlow<ItemEditorUiState> = mutableState.asStateFlow()

    private var definitions: List<DynamicFieldDefinition> = emptyList()
    private var initialCoverPaths: Pair<String?, String?> = null to null
    private var pendingCoverPaths: Pair<String, String>? = null
    private var initialSnapshot: EditorSnapshot? = null
    private var originalCreatedTime: Long? = null
    private val modifiedDynamicFieldIds = mutableSetOf<Long>()
    private val sharedQuotes: StateFlow<EditorQuoteEmission>? = itemId?.let { id ->
        shareEditorQuotes(
            source = quoteUseCases.observeForItem(id),
            scope = viewModelScope
        )
    }

    init {
        viewModelScope.launch {
            try {
                val prepared = loadAndPrepareEditorState(
                    itemId = itemId,
                    loadSources = ::loadInitialData
                )
                definitions = prepared.definitions
                initialCoverPaths = prepared.initialCoverPaths
                originalCreatedTime = prepared.originalCreatedTime
                initialSnapshot = prepared.initialSnapshot
                mutableState.value = prepared.uiState
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                showError(error)
            }
        }
    }

    private suspend fun loadInitialData(): EditorInitialData = coroutineScope {
        val types = async { useCases.observeTypes().first() }
        val statuses = async { useCases.observeStatuses().first() }
        val recordStatuses = async {
            useCases.observeStatuses(StatusScope.RECORD).first()
        }
        val tags = async { tagUseCases.observe().first() }
        val definitions = async { fieldUseCases.observe().first() }
        val detail = itemId?.let { id ->
            async { useCases.observeItemDetail(id).first() }
        }
        val quotes = sharedQuotes?.let { flow ->
            async { flow.awaitInitialQuotes() }
        }
        EditorInitialData(
            types = types.await(),
            statuses = statuses.await(),
            recordStatuses = recordStatuses.await(),
            tags = tags.await(),
            definitions = definitions.await(),
            detail = detail?.await(),
            quotes = quotes?.await().orEmpty(),
            loadedAtMillis = System.currentTimeMillis()
        )
    }

    fun onTypeSelected(typeId: Long) {
        if (mutableState.value.types.none { it.id == typeId }) return
        modifiedDynamicFieldIds.clear()
        update {
            copy(
                selectedTypeId = typeId,
                creator = "",
                dynamicFields = buildInputs(typeId, emptyMap(), FieldScope.ITEM),
                recordFieldTemplates = buildInputs(
                    typeId,
                    emptyMap(),
                    FieldScope.RECORD
                ),
                records = emptyList()
            )
        }
    }

    fun onTitleChange(value: String) = update { copy(title = value) }
    fun onCreatorChange(value: String) = update { copy(creator = value) }
    fun onStatusSelected(statusId: Long) = update { copy(selectedStatusId = statusId) }
    fun onTagSelectionChange(tagIds: Set<Long>) = update {
        copy(selectedTagIds = tagIds)
    }

    fun onRecordDraftCompleted(draft: RecordDraftUiState) = update {
        val existingIndex = records.indexOfFirst { it.key == draft.key }
        copy(
            records = if (existingIndex < 0) {
                (records + draft).sortedByDescending(RecordDraftUiState::startDate)
            } else {
                records.toMutableList().apply { set(existingIndex, draft) }
                    .sortedByDescending(RecordDraftUiState::startDate)
            }
        )
    }

    fun onRecordDraftDeleted(key: String) = update {
        copy(records = records.filterNot { it.key == key })
    }

    fun onQuoteDraftCompleted(draft: QuoteDraftUiState) {
        require(draft.content.isNotBlank()) { "摘录内容不能为空" }
        val normalized = draft.copy(
            content = draft.content.trim(),
            chapter = draft.chapter.trim(),
            page = draft.page.trim()
        )
        update {
            withCompletedQuoteDraft(normalized)
        }
    }

    fun onQuoteDraftDeleted(localKey: String) = update {
        val deleted = quoteDrafts.firstOrNull { it.localKey == localKey }
            ?: return@update this
        copy(
            quoteDrafts = quoteDrafts.filterNot { it.localKey == localKey },
            deletedQuoteIds = deleted.persistedId?.let { deletedQuoteIds + it }
                ?: deletedQuoteIds
        )
    }

    fun createQuoteDraft(): QuoteDraftUiState =
        newQuoteDraftUiState(
            localKey = UUID.randomUUID().toString(),
            createdTime = System.currentTimeMillis()
        )

    fun createTag(name: String, parentId: Long?) {
        viewModelScope.launch {
            runCatching {
                val (tagId, tags) = withContext(Dispatchers.IO) {
                    val createdId = tagUseCases.create(NewTag(name, parentId))
                    createdId to tagUseCases.observe().first()
                }
                mutableState.update {
                    it.copy(
                        tags = tags,
                        selectedTagIds = it.selectedTagIds + tagId,
                        errorMessage = null
                    ).withDirtyState()
                }
            }.onFailure(::handleFailure)
        }
    }

    fun onDynamicValueChange(fieldId: Long, value: String) {
        val current = mutableState.value.dynamicFields
            .firstOrNull { it.definitionId == fieldId }
            ?: return
        if (current.value == value) return
        modifiedDynamicFieldIds += fieldId
        update {
            copy(
                dynamicFields = dynamicFields.map {
                    if (it.definitionId == fieldId) it.copy(value = value) else it
                }
            )
        }
    }

    fun selectCover(uri: String) {
        if (mutableState.value.isProcessingCover) return
        viewModelScope.launch {
            mutableState.update {
                it.copy(isProcessingCover = true, errorMessage = null)
            }
            runCatching { coverImageUseCases.save(uri) }
                .onSuccess { image ->
                    pendingCoverPaths?.let { coverImageUseCases.delete(it.first, it.second) }
                    pendingCoverPaths = image.originalPath to image.thumbnailPath
                    mutableState.update {
                        it.copy(
                            coverPath = image.originalPath,
                            thumbnailPath = image.thumbnailPath,
                            isProcessingCover = false
                        ).withDirtyState()
                    }
                }
                .onFailure(::handleFailure)
        }
    }

    fun removeCover() {
        viewModelScope.launch {
            pendingCoverPaths?.let { coverImageUseCases.delete(it.first, it.second) }
            pendingCoverPaths = null
            update { copy(coverPath = "", thumbnailPath = "") }
        }
    }

    fun discardChanges(onDiscarded: () -> Unit) {
        viewModelScope.launch {
            pendingCoverPaths?.let { coverImageUseCases.delete(it.first, it.second) }
            pendingCoverPaths = null
            onDiscarded()
        }
    }

    fun save() {
        if (mutableState.value.isSaving) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val state = mutableState.value
                val dynamicValuesToSave = state.dynamicValuesForSave(
                    isNewItem = itemId == null,
                    modifiedFieldIds = modifiedDynamicFieldIds
                )
                val dynamicFieldsToSave = state.dynamicFields.filter {
                    it.definitionId in dynamicValuesToSave
                }
                validateDynamicFields(dynamicFieldsToSave)
                state.records.forEach { record ->
                    validateDynamicFields(
                        record.dynamicFields.filter {
                            record.id == null ||
                                it.definitionId in record.modifiedDynamicFieldIds
                        }
                    )
                }
                val records = state.records.map(::toDomainDraft)
                val request = ItemSaveRequest(
                    itemId = itemId,
                    typeId = requireNotNull(state.selectedTypeId),
                    title = state.title,
                    creator = state.creator,
                    createdTime = requireNotNull(originalCreatedTime),
                    coverPath = state.coverPath,
                    thumbnailPath = state.thumbnailPath,
                    dynamicValues = dynamicValuesToSave,
                    currentStatusId = requireNotNull(state.selectedStatusId),
                    tagIds = state.selectedTagIds,
                    records = records,
                    quotes = state.quoteDrafts.map { draft ->
                        ItemQuoteDraft(
                            localKey = draft.localKey,
                            persistedId = draft.persistedId,
                            content = draft.content,
                            chapter = draft.chapter,
                            page = draft.page,
                            createdTime = draft.createdTime
                        )
                    },
                    deletedQuoteIds = state.deletedQuoteIds
                )
                val currentCover =
                    state.coverPath.takeIf(String::isNotBlank) to
                        state.thumbnailPath.takeIf(String::isNotBlank)
                val obsoleteCover = obsoleteCoverPaths(
                    previous = initialCoverPaths,
                    current = currentCover
                )
                val obsoleteCoverCleanup: (suspend () -> Unit)? =
                    obsoleteCover?.let { paths ->
                        suspend {
                            coverImageUseCases.delete(paths.first, paths.second)
                        }
                    }
                commitPublishAndCleanup(
                    commit = { useCases.saveItem(request) },
                    publishSuccess = { savedId ->
                        // Room has committed. The current cover is now the source
                        // of truth and navigation may proceed immediately.
                        initialCoverPaths = currentCover
                        pendingCoverPaths = null
                        modifiedDynamicFieldIds.clear()
                        mutableState.update {
                            val saved = it.copy(
                                editingItemId = savedId,
                                isSaving = false,
                                hasUnsavedChanges = false,
                                deletedQuoteIds = emptySet(),
                                completedItemId = savedId
                            )
                            initialSnapshot = saved.toEditorSnapshot()
                            saved
                        }
                    },
                    cleanup = obsoleteCoverCleanup,
                    onCleanupFailure = { error ->
                        Log.w(
                            LOG_TAG,
                            "Item was saved, but obsolete cover cleanup failed. " +
                                "The saved item still references the current cover.",
                            error
                        )
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                handleFailure(error)
            }
        }
    }

    private fun toDomainDraft(draft: RecordDraftUiState): ItemRecordDraft {
        val startDate = requireNotNull(parseDateText(draft.startDate)) {
            "开始日期格式应为 YYYY-MM-DD"
        }
        val endDate = draft.endDate.takeIf(String::isNotBlank)?.let {
            requireNotNull(parseDateText(it)) { "结束日期格式应为 YYYY-MM-DD" }
        }
        require(endDate == null || endDate >= startDate) {
            "结束日期不得早于开始日期"
        }
        require(draft.ratingHalfStars == null || draft.ratingHalfStars in 1..10) {
            "评分必须是 1 到 10 的半星值"
        }
        val hasDurationInput =
            draft.durationHoursText.isNotBlank() ||
                draft.durationMinutesText.isNotBlank()
        val durationMinutes = toTotalMinutes(
            draft.durationHoursText,
            draft.durationMinutesText
        )
        require(!hasDurationInput || durationMinutes != null) {
            "时长需要填写非负整数"
        }
        return ItemRecordDraft(
            id = draft.id,
            startDate = startDate,
            endDate = endDate,
            ratingHalfStars = draft.ratingHalfStars,
            review = draft.review,
            statusSnapshot = draft.statusSnapshot,
            durationMinutes = durationMinutes,
            createdAt = draft.createdAt,
            dynamicValues = draft.dynamicFields
                .asSequence()
                .filter {
                    draft.id == null ||
                        it.definitionId in draft.modifiedDynamicFieldIds
                }
                .associate { it.definitionId to it.value }
        )
    }

    private fun update(transform: ItemEditorUiState.() -> ItemEditorUiState) {
        mutableState.update {
            it.transform()
                .copy(errorMessage = null)
                .withDirtyState()
        }
    }

    private fun ItemEditorUiState.withDirtyState(): ItemEditorUiState =
        copy(
            hasUnsavedChanges = initialSnapshot
                ?.let { baseline -> toEditorSnapshot() != baseline }
                ?: false
        )

    private fun buildInputs(
        typeId: Long?,
        values: Map<Long, String>,
        scope: FieldScope,
        sourceDefinitions: List<DynamicFieldDefinition> = definitions
    ): List<DynamicFieldInputState> = buildEditorInputs(
        typeId = typeId,
        values = values,
        scope = scope,
        definitions = sourceDefinitions
    )

    private fun validateDynamicFields(fields: List<DynamicFieldInputState>) {
        fields.filter { it.value.isNotBlank() }.forEach { field ->
            when (field.dataType) {
                FieldDataType.NUMBER -> require(
                    FieldValueParser.parseNumber(field.value) != null
                ) {
                    "${field.name}需要填写数字"
                }
                FieldDataType.DATE -> require(parseDateText(field.value) != null) {
                    "${field.name}需要使用 YYYY-MM-DD 格式"
                }
                FieldDataType.SINGLE_SELECT,
                FieldDataType.MULTI_SELECT -> Unit
                FieldDataType.RATING -> require(
                    FieldValueParser.parseRatingHalfStars(field.value) != null
                ) {
                    "${field.name}需要选择评分"
                }
                FieldDataType.TEXT, FieldDataType.BOOLEAN -> Unit
            }
        }
    }

    private fun showError(error: Throwable) {
        mutableState.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                isProcessingCover = false,
                errorMessage = error.message ?: "保存失败"
            )
        }
    }

    private fun handleFailure(error: Throwable) {
        if (error is CancellationException) throw error
        showError(error)
    }

    private companion object {
        const val LOG_TAG = "MyLibraryEditor"
    }
}

internal suspend fun <T> commitPublishAndCleanup(
    commit: suspend () -> T,
    publishSuccess: (T) -> Unit,
    cleanup: (suspend () -> Unit)?,
    onCleanupFailure: (Throwable) -> Unit
): T {
    val committed = commit()
    publishSuccess(committed)
    cleanup?.let {
        runBestEffortCleanup(
            cleanup = it,
            onFailure = onCleanupFailure
        )
    }
    return committed
}

internal fun obsoleteCoverPaths(
    previous: Pair<String?, String?>,
    current: Pair<String?, String?>
): Pair<String?, String?>? {
    val currentReferences = setOfNotNull(current.first, current.second)
    val obsoleteOriginal = previous.first?.takeUnless { it in currentReferences }
    val obsoleteThumbnail = previous.second?.takeUnless { it in currentReferences }
    return if (obsoleteOriginal == null && obsoleteThumbnail == null) {
        null
    } else {
        obsoleteOriginal to obsoleteThumbnail
    }
}

internal data class EditorInitialData(
    val types: List<ItemType>,
    val statuses: List<LibraryStatus>,
    val recordStatuses: List<LibraryStatus> = emptyList(),
    val tags: List<LibraryTag>,
    val definitions: List<DynamicFieldDefinition>,
    val detail: ItemDetail?,
    val quotes: List<LibraryQuote>,
    val loadedAtMillis: Long
)

internal data class PreparedEditorState(
    val uiState: ItemEditorUiState,
    val definitions: List<DynamicFieldDefinition>,
    val initialCoverPaths: Pair<String?, String?>,
    val originalCreatedTime: Long,
    val initialSnapshot: EditorSnapshot
)

internal suspend fun loadAndPrepareEditorState(
    itemId: Long?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    loadSources: suspend () -> EditorInitialData
): PreparedEditorState {
    val sources = withContext(ioDispatcher) {
        loadSources()
    }
    return withContext(defaultDispatcher) {
        prepareEditorState(sources, itemId)
    }
}

internal fun prepareEditorState(
    sources: EditorInitialData,
    itemId: Long?
): PreparedEditorState {
    val detail = sources.detail
    check(itemId == null || detail != null) { "作品不存在" }
    val officialTypes = sources.types.filter(ItemType::isOfficialType)
    val existingTypeId = detail?.item?.typeId
    check(
        existingTypeId == null ||
            officialTypes.any { it.id == existingTypeId }
    ) {
        "该作品使用旧版自定义类型，当前编辑器不会静默改写为书籍或电影"
    }
    val typeId = existingTypeId ?: officialTypes.firstOrNull()?.id
    val selectedTags = detail?.tags
        ?.mapTo(mutableSetOf()) { it.id }
        .orEmpty()
    val values = detail?.fields
        ?.associate { it.definitionId to it.value }
        .orEmpty()
    val loadedState = ItemEditorUiState(
        types = officialTypes,
        selectedTypeId = typeId,
        title = detail?.item?.title.orEmpty(),
        creator = detail?.item?.creator.orEmpty(),
        coverPath = detail?.item?.coverPath.orEmpty(),
        thumbnailPath = detail?.item?.thumbnailPath.orEmpty(),
        statuses = sources.statuses,
        recordStatuses = sources.recordStatuses,
        selectedStatusId = detail?.item?.currentStatusId
            ?: sources.statuses.firstOrNull()?.id,
        tags = sources.tags,
        selectedTagIds = selectedTags,
        dynamicFields = buildEditorInputs(
            typeId = typeId,
            values = values,
            scope = FieldScope.ITEM,
            definitions = sources.definitions
        ),
        recordFieldTemplates = buildEditorInputs(
            typeId = typeId,
            values = emptyMap(),
            scope = FieldScope.RECORD,
            definitions = sources.definitions
        ),
        records = detail?.records.orEmpty().map { record ->
            val durationParts = record.durationMinutes?.let(::splitTotalMinutes)
            RecordDraftUiState(
                key = "record-${record.id}",
                id = record.id,
                startDate = formatDate(record.startDate),
                endDate = record.endDate?.let(::formatDate).orEmpty(),
                ratingHalfStars = record.ratingHalfStars,
                review = record.review.orEmpty(),
                statusSnapshot = record.statusSnapshot,
                durationHoursText = durationParts?.hours?.toString().orEmpty(),
                durationMinutesText = durationParts?.minutes?.toString().orEmpty(),
                createdAt = record.createdAt,
                dynamicFields = buildEditorInputs(
                    typeId = typeId,
                    values = record.dynamicValues,
                    scope = FieldScope.RECORD,
                    definitions = sources.definitions
                )
            )
        },
        quoteDrafts = sources.quotes.map(LibraryQuote::toEditorDraft),
        editingItemId = itemId,
        isLoading = false
    )
    return PreparedEditorState(
        uiState = loadedState,
        definitions = sources.definitions,
        initialCoverPaths =
            detail?.item?.coverPath to detail?.item?.thumbnailPath,
        originalCreatedTime = detail?.item?.createdTime ?: sources.loadedAtMillis,
        initialSnapshot = loadedState.toEditorSnapshot()
    )
}

internal fun ItemType.isOfficialType(): Boolean =
    kind == ItemTypeKind.BOOK || kind == ItemTypeKind.MOVIE

internal fun buildEditorInputs(
    typeId: Long?,
    values: Map<Long, String>,
    scope: FieldScope,
    definitions: List<DynamicFieldDefinition>
): List<DynamicFieldInputState> =
    definitions
        .filter {
            it.typeId == typeId &&
                it.enabled &&
                !it.isFixed &&
                it.scope == scope
        }
        .map {
            DynamicFieldInputState(
                definitionId = it.id,
                name = it.name,
                dataType = it.dataType,
                value = values[it.id].orEmpty(),
                options = it.options,
                unit = it.unit
            )
        }

internal sealed interface EditorQuoteEmission {
    data object Pending : EditorQuoteEmission

    data class Value(
        val quotes: List<LibraryQuote>
    ) : EditorQuoteEmission

    data class Failure(
        val error: Throwable
    ) : EditorQuoteEmission
}

internal fun shareEditorQuotes(
    source: Flow<List<LibraryQuote>>,
    scope: CoroutineScope,
    upstreamDispatcher: CoroutineDispatcher = Dispatchers.IO
): StateFlow<EditorQuoteEmission> =
    source
        .map<List<LibraryQuote>, EditorQuoteEmission> {
            EditorQuoteEmission.Value(it)
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(EditorQuoteEmission.Failure(error))
        }
        .flowOn(upstreamDispatcher)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = EditorQuoteEmission.Pending
        )

internal suspend fun StateFlow<EditorQuoteEmission>.awaitInitialQuotes():
    List<LibraryQuote> =
    when (val emission = first { it !is EditorQuoteEmission.Pending }) {
        is EditorQuoteEmission.Value -> emission.quotes
        is EditorQuoteEmission.Failure -> throw emission.error
        EditorQuoteEmission.Pending -> error("Quote stream did not emit")
    }

internal fun StateFlow<EditorQuoteEmission>.currentQuotesOr(
    fallback: List<LibraryQuote>
): List<LibraryQuote> =
    (value as? EditorQuoteEmission.Value)?.quotes ?: fallback

internal fun ItemEditorUiState.toEditorSnapshot() = EditorSnapshot(
    selectedTypeId = selectedTypeId,
    title = title,
    creator = creator,
    coverPath = coverPath,
    thumbnailPath = thumbnailPath,
    selectedStatusId = selectedStatusId,
    selectedTagIds = selectedTagIds,
    dynamicValues = dynamicFields.associate { it.definitionId to it.value },
    records = records,
    quoteDrafts = quoteDrafts
)

internal fun ItemEditorUiState.withCompletedQuoteDraft(
    draft: QuoteDraftUiState
): ItemEditorUiState {
    val existingIndex = quoteDrafts.indexOfFirst {
        it.localKey == draft.localKey
    }
    return copy(
        quoteDrafts = if (existingIndex < 0) {
            quoteDrafts + draft
        } else {
            quoteDrafts.toMutableList().apply {
                set(existingIndex, draft)
            }
        },
        deletedQuoteIds = draft.persistedId?.let {
            deletedQuoteIds - it
        } ?: deletedQuoteIds
    )
}

internal fun ItemEditorUiState.dynamicValuesForSave(
    isNewItem: Boolean,
    modifiedFieldIds: Set<Long>
): Map<Long, String> =
    dynamicFields
        .asSequence()
        .filter { isNewItem || it.definitionId in modifiedFieldIds }
        .associate { it.definitionId to it.value }

internal data class EditorSnapshot(
    val selectedTypeId: Long?,
    val title: String,
    val creator: String,
    val coverPath: String,
    val thumbnailPath: String,
    val selectedStatusId: Long?,
    val selectedTagIds: Set<Long>,
    val dynamicValues: Map<Long, String>,
    val records: List<RecordDraftUiState>,
    val quoteDrafts: List<QuoteDraftUiState>
)

internal fun LibraryQuote.toEditorDraft() = QuoteDraftUiState(
    localKey = "quote-$id",
    persistedId = id,
    content = content,
    chapter = chapter.orEmpty(),
    page = page.orEmpty(),
    createdTime = createdTime
)

internal fun newQuoteDraftUiState(
    localKey: String,
    createdTime: Long
) = QuoteDraftUiState(
    localKey = localKey,
    persistedId = null,
    content = "",
    chapter = "",
    page = "",
    createdTime = createdTime
)

class ItemEditorViewModelFactory(
    private val useCases: LibraryUseCases,
    private val fieldUseCases: FieldUseCases,
    private val tagUseCases: TagUseCases,
    private val coverImageUseCases: CoverImageUseCases,
    private val quoteUseCases: QuoteUseCases,
    private val itemId: Long?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ItemEditorViewModel::class.java))
        return ItemEditorViewModel(
            useCases,
            fieldUseCases,
            tagUseCases,
            coverImageUseCases,
            quoteUseCases,
            itemId
        ) as T
    }
}
