package com.example.mylibrary.ui.item

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.decodeFieldSelection
import com.example.mylibrary.domain.model.FieldNumberFormatter
import java.math.BigDecimal
import com.example.mylibrary.ui.components.AppCapsule
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.AppInlineSwitch
import com.example.mylibrary.ui.components.CoverDisplayMode
import com.example.mylibrary.ui.components.CoverImage
import com.example.mylibrary.ui.components.DatePickerBottomSheet
import com.example.mylibrary.ui.components.FieldRow
import com.example.mylibrary.ui.components.LibraryTextField
import com.example.mylibrary.ui.components.TagSelectionSheet
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.quote.formatQuoteLocation
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CardContentPadding
import com.example.mylibrary.ui.theme.CoverAspectRatio
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.util.todayText
import com.example.mylibrary.util.formatDate
import com.example.mylibrary.util.parseDateText

@Composable
fun ItemForm(
    state: ItemEditorUiState,
    showQuoteChapter: Boolean,
    showQuotePage: Boolean,
    allowTypeSelection: Boolean,
    onTypeSelected: (Long) -> Unit,
    onTitleChange: (String) -> Unit,
    onCreatorChange: (String) -> Unit,
    onCoverSelected: (String) -> Unit,
    onRemoveCover: () -> Unit,
    onStatusSelected: (Long) -> Unit,
    onTagSelectionChanged: (Set<Long>) -> Unit,
    onCreateTag: (String, Long?) -> Unit,
    onDynamicValueChange: (Long, String) -> Unit,
    onRecordDraftCompleted: (RecordDraftUiState) -> Unit,
    onRecordDraftDeleted: (String) -> Unit,
    onCreateQuoteDraft: () -> QuoteDraftUiState,
    onQuoteDraftCompleted: (QuoteDraftUiState) -> Unit,
    onQuoteDraftDeleted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTagSheet by remember { mutableStateOf(false) }
    var activeDynamicFieldId by remember { mutableStateOf<Long?>(null) }
    var activeRecordDraft by remember { mutableStateOf<RecordDraftUiState?>(null) }
    var activeQuoteDraft by remember { mutableStateOf<QuoteDraftUiState?>(null) }
    var draftSequence by remember { mutableIntStateOf(0) }
    val colors = AppTheme.colors
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onCoverSelected(it.toString()) }
    }
    val openPicker = {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FormCard(title = "基本信息") {
            if (allowTypeSelection) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.types.forEach { type ->
                        AppCapsule(
                            text = type.name,
                            selected = state.selectedTypeId == type.id,
                            onClick = { onTypeSelected(type.id) }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                CoverEditor(
                    state = state,
                    onOpenPicker = openPicker,
                    onRemoveCover = onRemoveCover
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LibraryTextField(
                        value = state.title,
                        onValueChange = onTitleChange,
                        label = { Text("标题") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_title_input")
                    )
                    LibraryTextField(
                        value = state.creator,
                        onValueChange = onCreatorChange,
                        label = {
                            Text(state.selectedType?.creatorLabel ?: "作者 / 导演")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("item_creator_input")
                    )
                }
            }
            if (state.isProcessingCover) {
                Text(
                    text = "正在处理封面…",
                    color = colors.mutedText,
                    style = AppTheme.typography.metadata
                )
            }
        }

        FormCard(title = "状态与标签") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                state.statuses.forEach { status ->
                    AppCapsule(
                        text = status.name,
                        selected = state.selectedStatusId == status.id,
                        onClick = { onStatusSelected(status.id) }
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                state.tags
                    .filter { it.id in state.selectedTagIds }
                    .forEach { tag ->
                        AppCapsule(
                            text = tag.name,
                            selected = true,
                            onClick = { showTagSheet = true }
                        )
                    }
                AppCapsule(
                    text = "+",
                    selected = false,
                    onClick = { showTagSheet = true }
                )
            }
        }

        if (state.dynamicFields.isNotEmpty()) {
            FormCard(title = "自定义字段") {
                state.dynamicFields.forEachIndexed { index, field ->
                    key(field.definitionId) {
                        if (index > 0) {
                            HorizontalDivider(color = colors.subtleBorder)
                        }
                        DynamicFieldEditorRow(
                            field = field,
                            onValueChange = {
                                onDynamicValueChange(field.definitionId, it)
                            },
                            onEdit = { activeDynamicFieldId = field.definitionId }
                        )
                    }
                }
            }
        }

        FormCard(
            title = "记录",
            titleAction = {
                AppCapsule(
                    text = "+",
                    selected = false,
                    onClick = {
                        activeRecordDraft = RecordDraftUiState(
                            key = "draft-${System.currentTimeMillis()}-${draftSequence++}",
                            id = null,
                            startDate = todayText(),
                            endDate = "",
                            ratingHalfStars = null,
                            review = "",
                            statusSnapshot = null,
                            durationHoursText = "",
                            durationMinutesText = "",
                            createdAt = System.currentTimeMillis(),
                            dynamicFields = state.recordFieldTemplates
                        )
                    }
                )
            }
        ) {
            state.records.forEachIndexed { index, draft ->
                key(draft.key) {
                    if (index > 0) {
                        HorizontalDivider(color = colors.subtleBorder)
                    }
                    val interaction = remember(draft.key) {
                        MutableInteractionSource()
                    }
                    RecordDraftContentBlock(
                        draft = draft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { activeRecordDraft = draft },
                                onLongClick = { activeRecordDraft = draft }
                            )
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }

        run {
            FormCard(
                title = "摘录",
                titleAction = {
                    Text(
                        text = "+ 添加摘录",
                        modifier = Modifier
                            .noRippleClickable {
                                activeQuoteDraft = onCreateQuoteDraft()
                            }
                            .padding(vertical = 6.dp),
                        style = AppTheme.typography.button,
                        color = colors.textSecondary
                    )
                }
            ) {
                state.quoteDrafts.forEachIndexed { index, quote ->
                    key(quote.localKey) {
                        if (index > 0) {
                            HorizontalDivider(color = colors.subtleBorder)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    activeQuoteDraft = quote
                                }
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "• ${quote.content}",
                                style = AppTheme.typography.body,
                                color = colors.textPrimary
                            )
                            formatQuoteLocation(
                                chapter = quote.chapter,
                                page = quote.page,
                                showChapter = showQuoteChapter,
                                showPage = showQuotePage
                            )?.let { location ->
                                Text(
                                    text = location,
                                    style = AppTheme.typography.metadata,
                                    color = colors.mutedText
                                )
                            }
                        }
                    }
                }
            }
        }

        state.errorMessage?.let {
            Text(it, color = colors.textSecondary, style = AppTheme.typography.body)
        }
    }

    if (showTagSheet) {
        TagSelectionSheet(
            tags = state.tags,
            selectedIds = state.selectedTagIds,
            onDone = onTagSelectionChanged,
            onCreateTag = onCreateTag,
            onDismiss = { showTagSheet = false }
        )
    }
    state.dynamicFields.firstOrNull {
        it.definitionId == activeDynamicFieldId
    }?.let { field ->
        when (field.dataType) {
            FieldDataType.TEXT,
            FieldDataType.NUMBER -> Unit
            FieldDataType.DATE -> DatePickerBottomSheet(
                initialDateMillis = parseDateText(field.value),
                allowClear = true,
                onConfirm = {
                    onDynamicValueChange(field.definitionId, formatDate(it))
                    activeDynamicFieldId = null
                },
                onClear = {
                    onDynamicValueChange(field.definitionId, "")
                    activeDynamicFieldId = null
                },
                onDismiss = { activeDynamicFieldId = null }
            )
            FieldDataType.SINGLE_SELECT -> FieldSelectionBottomSheet(
                field = field,
                multiple = false,
                onConfirm = {
                    onDynamicValueChange(field.definitionId, it)
                    activeDynamicFieldId = null
                },
                onDismiss = { activeDynamicFieldId = null }
            )
            FieldDataType.MULTI_SELECT -> FieldSelectionBottomSheet(
                field = field,
                multiple = true,
                onConfirm = {
                    onDynamicValueChange(field.definitionId, it)
                    activeDynamicFieldId = null
                },
                onDismiss = { activeDynamicFieldId = null }
            )
            FieldDataType.RATING -> FieldRatingBottomSheet(
                field = field,
                onConfirm = {
                    onDynamicValueChange(field.definitionId, it)
                    activeDynamicFieldId = null
                },
                onDismiss = { activeDynamicFieldId = null }
            )
            FieldDataType.BOOLEAN -> Unit
        }
    }
    activeRecordDraft?.let { draft ->
        RecordDraftSheet(
            initial = draft,
            recordStatuses = state.recordStatuses,
            itemTypeId = requireNotNull(state.selectedTypeId),
            onComplete = onRecordDraftCompleted,
            onDelete = if (draft.id != null || state.records.any { it.key == draft.key }) {
                { onRecordDraftDeleted(draft.key) }
            } else {
                null
            },
            onDismiss = { activeRecordDraft = null }
        )
    }
    activeQuoteDraft?.let { draft ->
        QuoteDraftSheet(
            initial = draft,
            showChapter = showQuoteChapter,
            showPage = showQuotePage,
            onSave = onQuoteDraftCompleted,
            onDelete = if (state.quoteDrafts.any { it.localKey == draft.localKey }) {
                { onQuoteDraftDeleted(draft.localKey) }
            } else {
                null
            },
            onDismiss = { activeQuoteDraft = null }
        )
    }
}

@Composable
private fun CoverEditor(
    state: ItemEditorUiState,
    onOpenPicker: () -> Unit,
    onRemoveCover: () -> Unit
) {
    Box(modifier = Modifier.width(100.dp)) {
        CoverImage(
            thumbnailPath = state.thumbnailPath.takeIf(String::isNotBlank),
            originalPath = state.coverPath.takeIf(String::isNotBlank),
            title = state.title,
            creator = state.creator,
            typeName = state.selectedType?.name.orEmpty(),
            typeId = state.selectedType?.id,
            displayMode = CoverDisplayMode.EDITOR,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CoverAspectRatio)
                .noRippleClickable(
                    enabled = !state.isProcessingCover,
                    onClick = onOpenPicker
                )
        )
        if (state.thumbnailPath.isNotBlank()) {
            AppThemeSurface(
                role = SurfaceRole.CARD,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
                    .padding(4.dp)
                    .noRippleClickable(onClick = onRemoveCover),
                shape = CircleShape,
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "移除封面",
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FormCard(
    title: String,
    titleAction: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        forceOpaqueFallback = true
    ) {
        Column(
            modifier = Modifier.padding(CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.cardTitle,
                    color = AppTheme.colors.textSecondary
                )
                titleAction?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun DynamicFieldEditorRow(
    field: DynamicFieldInputState,
    onValueChange: (String) -> Unit,
    onEdit: () -> Unit
) {
    when (field.dataType) {
        FieldDataType.TEXT,
        FieldDataType.NUMBER -> {
            FieldRow(
                label = field.name,
                value = "",
                editable = false,
                onClick = {},
                modifier = Modifier.testTag("dynamic_field_${field.definitionId}"),
                trailingContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LibraryTextField(
                            value = field.value,
                            onValueChange = onValueChange,
                            label = { Text("未填写") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (field.dataType == FieldDataType.NUMBER) {
                                    KeyboardType.Decimal
                                } else {
                                    KeyboardType.Text
                                }
                            ),
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(min = 72.dp)
                                .testTag("dynamic_field_input_${field.definitionId}")
                        )
                        field.unit?.takeIf(String::isNotBlank)?.let { unit ->
                            Text(
                                text = unit,
                                modifier = Modifier
                                    .widthIn(min = 24.dp, max = 64.dp)
                                    .testTag(
                                        "dynamic_field_unit_${field.definitionId}"
                                    ),
                                style = AppTheme.typography.metadata,
                                color = AppTheme.colors.textSecondary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )
        }
        FieldDataType.BOOLEAN -> {
            FieldRow(
                label = field.name,
                value = "",
                editable = false,
                onClick = {},
                modifier = Modifier.testTag("dynamic_field_${field.definitionId}"),
                trailingContent = {
                    AppInlineSwitch(
                        checked = field.value == "true",
                        onCheckedChange = {
                            onValueChange(it.toString())
                        },
                        modifier = Modifier.testTag(
                            "dynamic_field_switch_${field.definitionId}"
                        )
                    )
                }
            )
        }
        FieldDataType.DATE,
        FieldDataType.SINGLE_SELECT,
        FieldDataType.MULTI_SELECT,
        FieldDataType.RATING -> {
            FieldRow(
                label = field.name,
                value = field.displayValue(),
                editable = true,
                onClick = onEdit,
                modifier = Modifier.testTag("dynamic_field_${field.definitionId}")
            )
        }
    }
}

internal fun DynamicFieldInputState.displayValue(): String = when {
    value.isBlank() -> "未填写"
    dataType == FieldDataType.MULTI_SELECT ->
        decodeFieldSelection(value).joinToString("  ")
    dataType == FieldDataType.NUMBER && !unit.isNullOrBlank() -> "$value $unit"
    dataType == FieldDataType.RATING -> runCatching {
        FieldNumberFormatter.format(
            BigDecimal(value).divide(BigDecimal(2))
        ) + " 星"
    }.getOrDefault(value)
    else -> value
}
