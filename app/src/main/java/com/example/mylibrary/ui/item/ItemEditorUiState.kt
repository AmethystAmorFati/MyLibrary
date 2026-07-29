package com.example.mylibrary.ui.item

import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryTag

data class ItemEditorUiState(
    val types: List<ItemType> = emptyList(),
    val selectedTypeId: Long? = null,
    val title: String = "",
    val creator: String = "",
    val coverPath: String = "",
    val thumbnailPath: String = "",
    val statuses: List<LibraryStatus> = emptyList(),
    val selectedStatusId: Long? = null,
    val recordStatuses: List<LibraryStatus> = emptyList(),
    val tags: List<LibraryTag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val dynamicFields: List<DynamicFieldInputState> = emptyList(),
    val recordFieldTemplates: List<DynamicFieldInputState> = emptyList(),
    val records: List<RecordDraftUiState> = emptyList(),
    val quoteDrafts: List<QuoteDraftUiState> = emptyList(),
    val deletedQuoteIds: Set<Long> = emptySet(),
    val editingItemId: Long? = null,
    val hasUnsavedChanges: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isProcessingCover: Boolean = false,
    val errorMessage: String? = null,
    val completedItemId: Long? = null
) {
    val selectedType: ItemType?
        get() = types.firstOrNull { it.id == selectedTypeId }
}

data class DynamicFieldInputState(
    val definitionId: Long,
    val name: String,
    val dataType: FieldDataType,
    val value: String,
    val options: List<String> = emptyList(),
    val unit: String? = null
)

data class RecordDraftUiState(
    val key: String,
    val id: Long?,
    val startDate: String,
    val endDate: String,
    val ratingHalfStars: Int?,
    val review: String,
    val createdAt: Long,
    val dynamicFields: List<DynamicFieldInputState> = emptyList(),
    val modifiedDynamicFieldIds: Set<Long> = emptySet(),
    val statusSnapshot: String? = null,
    val durationHoursText: String = "",
    val durationMinutesText: String = ""
)

data class QuoteDraftUiState(
    val localKey: String,
    val persistedId: Long?,
    val content: String,
    val chapter: String,
    val page: String,
    val createdTime: Long
) {
    val isPersisted: Boolean
        get() = persistedId != null
}
