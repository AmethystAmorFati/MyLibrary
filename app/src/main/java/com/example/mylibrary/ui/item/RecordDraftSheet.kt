package com.example.mylibrary.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.text.input.KeyboardType
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldValueParser
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.ui.record.RecordStatusRow
import com.example.mylibrary.ui.components.DatePickerBottomSheet
import com.example.mylibrary.ui.components.AppModalBottomSheet
import com.example.mylibrary.ui.components.LibraryTextField
import com.example.mylibrary.ui.components.StarRatingBar
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppDanger
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.util.formatDate
import com.example.mylibrary.util.parseDateText
import com.example.mylibrary.util.recordDurationLabel
import com.example.mylibrary.util.toTotalMinutes
import com.example.mylibrary.util.splitTotalMinutes

internal val RecordReviewEditorHeight = 152.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDraftSheet(
    initial: RecordDraftUiState,
    recordStatuses: List<LibraryStatus> = emptyList(),
    itemTypeId: Long = 0L,
    onComplete: (RecordDraftUiState) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var draft by remember(initial.key) { mutableStateOf(initial) }
    var dateTarget by remember { mutableStateOf<RecordDateTarget?>(null) }
    var activeDynamicFieldId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScreenHorizontalPadding,
                    end = ScreenHorizontalPadding,
                    top = 10.dp,
                    bottom = 18.dp
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RecordSheetHeader(
                editing = onDelete != null,
                onDelete = onDelete?.let { delete ->
                    {
                        delete()
                        onDismiss()
                    }
                },
                onDone = {
                    validateRecordDraft(draft)
                        .onSuccess {
                            val normalizedDuration = toTotalMinutes(
                                draft.durationHoursText,
                                draft.durationMinutesText
                            )?.let(::splitTotalMinutes)
                            onComplete(
                                draft.copy(
                                    durationHoursText =
                                        normalizedDuration?.hours
                                            ?.toString()
                                            .orEmpty(),
                                    durationMinutesText =
                                        normalizedDuration?.minutes
                                            ?.toString()
                                            .orEmpty()
                                )
                            )
                            onDismiss()
                        }
                        .onFailure { error = it.message }
                }
            )
            RecordDateRow(
                label = "开始日期",
                value = draft.startDate,
                onClick = { dateTarget = RecordDateTarget.START }
            )
            RecordDateRow(
                label = "结束日期",
                value = draft.endDate.ifBlank { "未设置" },
                onClick = { dateTarget = RecordDateTarget.END }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "状态",
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.textSecondary
                )
                RecordStatusRow(
                    statuses = recordStatuses,
                    selectedName = draft.statusSnapshot,
                    onSelected = {
                        draft = draft.copy(statusSnapshot = it)
                        error = null
                    },
                    modifier = Modifier.testTag("record_status_selector")
                )
            }
            RecordDurationInput(
                label = recordDurationLabel(itemTypeId),
                hoursText = draft.durationHoursText,
                minutesText = draft.durationMinutesText,
                onHoursChange = {
                    if (it.all(Char::isDigit)) {
                        draft = draft.copy(durationHoursText = it)
                        error = null
                    }
                },
                onMinutesChange = {
                    if (it.all(Char::isDigit)) {
                        draft = draft.copy(durationMinutesText = it)
                        error = null
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("record_rating_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "评分",
                    modifier = Modifier.weight(1f),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textPrimary
                )
                StarRatingBar(
                    ratingHalfStars = draft.ratingHalfStars,
                    onRatingChange = {
                        draft = draft.copy(ratingHalfStars = it)
                        error = null
                    },
                    starSize = 21.dp
                )
            }
            if (draft.dynamicFields.isNotEmpty()) {
                Text(
                    text = "记录字段",
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
                draft.dynamicFields.forEachIndexed { index, field ->
                    if (index > 0) {
                        HorizontalDivider(color = AppTheme.colors.subtleBorder)
                    }
                    DynamicFieldEditorRow(
                        field = field,
                        onValueChange = { value ->
                            draft = draft.withDynamicFieldValue(
                                field.definitionId,
                                value
                            )
                            error = null
                        },
                        onEdit = { activeDynamicFieldId = field.definitionId }
                    )
                }
            }
            LibraryTextField(
                value = draft.review,
                onValueChange = {
                    draft = draft.copy(review = it)
                    error = null
                },
                label = { Text("评价（可选）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RecordReviewEditorHeight)
                    .testTag("record_review_input"),
                singleLine = false,
                minLines = 6,
                maxLines = 6
            )
            error?.let {
                Text(
                    text = it,
                    style = AppTheme.typography.metadata,
                    color = AppDanger
                )
            }
        }
    }

    dateTarget?.let { target ->
        DatePickerBottomSheet(
            initialDateMillis = parseDateText(
                when (target) {
                    RecordDateTarget.START -> draft.startDate
                    RecordDateTarget.END -> draft.endDate
                }
            ),
            allowClear = target == RecordDateTarget.END,
            onConfirm = { millis ->
                draft = when (target) {
                    RecordDateTarget.START -> draft.copy(startDate = formatDate(millis))
                    RecordDateTarget.END -> draft.copy(endDate = formatDate(millis))
                }
                error = null
            },
            onClear = {
                draft = draft.copy(endDate = "")
                error = null
            },
            onDismiss = { dateTarget = null }
        )
    }
    draft.dynamicFields.firstOrNull {
        it.definitionId == activeDynamicFieldId
    }?.let { field ->
        val updateValue: (String) -> Unit = { value ->
            draft = draft.withDynamicFieldValue(field.definitionId, value)
            activeDynamicFieldId = null
            error = null
        }
        when (field.dataType) {
            FieldDataType.DATE -> DatePickerBottomSheet(
                initialDateMillis = parseDateText(field.value),
                allowClear = true,
                onConfirm = { updateValue(formatDate(it)) },
                onClear = { updateValue("") },
                onDismiss = { activeDynamicFieldId = null }
            )
            FieldDataType.SINGLE_SELECT -> FieldSelectionBottomSheet(
                field = field,
                multiple = false,
                onConfirm = updateValue,
                onDismiss = { activeDynamicFieldId = null }
            )
            FieldDataType.MULTI_SELECT -> FieldSelectionBottomSheet(
                field = field,
                multiple = true,
                onConfirm = updateValue,
                onDismiss = { activeDynamicFieldId = null }
            )
            FieldDataType.RATING -> FieldRatingBottomSheet(
                field = field,
                onConfirm = updateValue,
                onDismiss = { activeDynamicFieldId = null }
            )
            FieldDataType.TEXT,
            FieldDataType.NUMBER,
            FieldDataType.BOOLEAN -> Unit
        }
    }
}

private fun RecordDraftUiState.withDynamicFieldValue(
    fieldId: Long,
    value: String
): RecordDraftUiState = copy(
    dynamicFields = dynamicFields.map {
        if (it.definitionId == fieldId) it.copy(value = value) else it
    },
    modifiedDynamicFieldIds = modifiedDynamicFieldIds + fieldId
)

@Composable
private fun RecordSheetHeader(
    editing: Boolean,
    onDelete: (() -> Unit)?,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (editing) "编辑记录" else "新增记录",
            modifier = Modifier.weight(1f),
            style = AppTheme.typography.sectionTitle,
            color = AppTheme.colors.textPrimary
        )
        onDelete?.let {
            RecordSheetIconAction(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "删除记录草稿",
                tint = AppDanger,
                testTag = "record_delete_action",
                onClick = it
            )
        }
        RecordSheetIconAction(
            imageVector = Icons.Outlined.Check,
            contentDescription = "保存记录草稿",
            tint = AppTheme.colors.accent,
            testTag = "record_save_action",
            onClick = onDone
        )
    }
}

@Composable
private fun RecordSheetIconAction(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun RecordDateRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .noRippleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = AppTheme.typography.body,
            color = AppTheme.colors.textPrimary
        )
        Text(
            text = value,
            style = AppTheme.typography.body,
            color = AppTheme.colors.textSecondary
        )
    }
}

private fun validateRecordDraft(draft: RecordDraftUiState): Result<Unit> =
    runCatching {
        val start = requireNotNull(parseDateText(draft.startDate)) {
            "请选择开始日期"
        }
        val end = draft.endDate.takeIf(String::isNotBlank)?.let {
            requireNotNull(parseDateText(it)) { "结束日期格式无效" }
        }
        require(end == null || end >= start) { "结束日期不得早于开始日期" }
        require(draft.ratingHalfStars == null || draft.ratingHalfStars in 1..10) {
            "评分必须是 1 到 10 的半星值"
        }
        val hasDurationInput =
            draft.durationHoursText.isNotBlank() ||
                draft.durationMinutesText.isNotBlank()
        require(
            !hasDurationInput ||
                toTotalMinutes(
                    draft.durationHoursText,
                    draft.durationMinutesText
                ) != null
        ) { "时长需要填写非负整数" }
        draft.dynamicFields
            .filter {
                it.value.isNotBlank() &&
                    (draft.id == null || it.definitionId in draft.modifiedDynamicFieldIds)
            }
            .forEach { field ->
            when (field.dataType) {
                FieldDataType.NUMBER -> require(
                    FieldValueParser.parseNumber(field.value) != null
                ) { "${field.name}需要填写数字" }
                FieldDataType.DATE -> require(parseDateText(field.value) != null) {
                    "${field.name}日期格式无效"
                }
                FieldDataType.SINGLE_SELECT,
                FieldDataType.MULTI_SELECT -> Unit
                FieldDataType.RATING -> require(
                    FieldValueParser.parseRatingHalfStars(field.value) != null
                ) { "${field.name}评分无效" }
                FieldDataType.TEXT,
                FieldDataType.BOOLEAN -> Unit
            }
        }
    }

private enum class RecordDateTarget { START, END }

@Composable
private fun RecordDurationInput(
    label: String,
    hoursText: String,
    minutesText: String,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("record_duration_input"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = AppTheme.typography.metadata,
            color = AppTheme.colors.textSecondary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            DurationNumberField(
                value = hoursText,
                onValueChange = onHoursChange,
                tag = "record_duration_hours"
            )
            DurationUnitText("小时")
            DurationNumberField(
                value = minutesText,
                onValueChange = onMinutesChange,
                tag = "record_duration_minutes"
            )
            DurationUnitText("分钟")
        }
    }
}

@Composable
private fun DurationNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    tag: String
) {
    LibraryTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("0") },
        modifier = Modifier
            .width(60.dp)
            .testTag(tag),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun DurationUnitText(text: String) {
    Text(
        text = text,
        modifier = Modifier.width(32.dp),
        style = AppTheme.typography.metadata,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        softWrap = false
    )
}
