package com.example.mylibrary.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDefinitionChanges
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.allowedAggregations
import com.example.mylibrary.ui.components.AppCapsule
import com.example.mylibrary.ui.components.AppConfirmDialog
import com.example.mylibrary.ui.components.AppModalBottomSheet
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.SimpleTopBar
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppDanger
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.CapsuleHeight
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import kotlinx.coroutines.launch

@Composable
fun FieldManagementScreen(
    state: FieldManagementUiState,
    onBack: () -> Unit,
    onTypeSelected: (Long) -> Unit,
    onCreate: (
        String,
        FieldDataType,
        FieldScope,
        String?,
        Set<FieldAggregation>
    ) -> Unit,
    onUpdate: (Long, FieldDefinitionChanges) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onAddOption: (Long, String) -> Unit,
    onRenameOption: (Long, String, String) -> Unit,
    onDeleteOption: (Long, String) -> Unit,
    onReorderOptions: (Long, List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var fieldActionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingFieldId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingFieldId by rememberSaveable { mutableStateOf<Long?>(null) }
    var optionSheetFieldId by rememberSaveable { mutableStateOf<Long?>(null) }

    AppScreenContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
        SimpleTopBar(title = "字段管理", onBack = onBack)
        state.errorMessage?.let { ErrorMessage(it) }
        FieldTypeCapsuleBar(
            types = state.types,
            selectedId = state.selectedTypeId,
            onSelect = {
                optionSheetFieldId = null
                onTypeSelected(it)
            }
        )
        ReorderableFieldList(
            fields = state.visibleFields,
            typeId = state.selectedTypeId,
            errorMessage = state.errorMessage,
            onOpenOptions = { optionSheetFieldId = it.id },
            onMore = { fieldActionId = it.id },
            onAdd = { showCreate = true },
            onReorder = onReorder,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        }
    }

    if (showCreate) {
        FieldCreateBottomSheet(
            existingNames = state.fields
                .filter { it.typeId == state.selectedTypeId }
                .map { it.name },
            onConfirm = onCreate,
            onDismiss = { showCreate = false }
        )
    }

    state.fields.firstOrNull { it.id == optionSheetFieldId }
        ?.takeIf(DynamicFieldDefinition::isSelectionField)
        ?.let { field ->
            FieldOptionManagementBottomSheet(
                field = field,
                errorMessage = state.errorMessage,
                onAdd = { onAddOption(field.id, it) },
                onRename = { oldName, newName ->
                    onRenameOption(field.id, oldName, newName)
                },
                onDelete = { onDeleteOption(field.id, it) },
                onReorder = { onReorderOptions(field.id, it) },
                onDismiss = { optionSheetFieldId = null }
            )
        }

    state.fields.firstOrNull { it.id == editingFieldId }?.let { field ->
        FieldCreateBottomSheet(
            initial = field,
            existingNames = state.fields
                .filter { it.typeId == field.typeId && it.id != field.id }
                .map { it.name },
            onConfirm = { name, dataType, scope, unit, aggregations ->
                onUpdate(
                    field.id,
                    FieldDefinitionChanges(
                        name = name,
                        dataType = dataType,
                        scope = scope,
                        unit = unit,
                        aggregations = aggregations
                    )
                )
            },
            onDismiss = { editingFieldId = null }
        )
    }

    state.fields.firstOrNull { it.id == fieldActionId }?.let { field ->
        TagActionDialog(
            tagName = field.name,
            renameText = "编辑字段",
            onRename = {
                fieldActionId = null
                editingFieldId = field.id
            },
            onDelete = {
                fieldActionId = null
                deletingFieldId = field.id
            },
            onDismiss = { fieldActionId = null }
        )
    }

    state.fields.firstOrNull { it.id == deletingFieldId }?.let { field ->
        AppConfirmDialog(
            title = "确认删除「${field.name}」？",
            message = "删除后，该字段及作品中已经填写的内容都会移除。\n此操作无法撤销。",
            confirmText = "删除",
            dismissText = "取消",
            destructive = true,
            onConfirm = {
                onDelete(field.id)
                if (optionSheetFieldId == field.id) optionSheetFieldId = null
                deletingFieldId = null
            },
            onDismiss = { deletingFieldId = null }
        )
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(
            start = ScreenHorizontalPadding,
            end = ScreenHorizontalPadding,
            bottom = 6.dp
        ),
        style = AppTheme.typography.metadata,
        color = AppTheme.colors.mutedText
    )
}

@Composable
private fun FieldTypeCapsuleBar(
    types: List<ItemType>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId, types) {
        val selectedIndex = types.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0 &&
            listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }
        ) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(types, key = { it.id }) { type ->
            FieldTypeCapsule(
                text = type.name,
                selected = type.id == selectedId,
                onClick = { onSelect(type.id) }
            )
        }
    }
}

@Composable
private fun FieldTypeCapsule(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Surface(
        modifier = Modifier
            .height(CapsuleHeight)
            .widthIn(max = 220.dp)
            .noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) colors.accent.copy(alpha = 0.10f) else colors.surfaces.card,
        border = BorderStroke(
            1.dp,
            if (selected) colors.accent else colors.border
        ),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.typography.capsule,
                color = if (selected) colors.accent else colors.textSecondary
            )
        }
    }
}

@Composable
private fun ReorderableFieldList(
    fields: List<DynamicFieldDefinition>,
    typeId: Long?,
    errorMessage: String?,
    onOpenOptions: (DynamicFieldDefinition) -> Unit,
    onMore: (DynamicFieldDefinition) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val localFields = remember(typeId) { mutableStateListOf<DynamicFieldDefinition>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingId by remember(typeId) { mutableStateOf<Long?>(null) }
    var dragOffset by remember(typeId) { mutableFloatStateOf(0f) }

    LaunchedEffect(typeId, fields, errorMessage) {
        if (draggingId == null) {
            localFields.clear()
            localFields.addAll(fields)
        }
    }

    fun finishDrag() {
        val reorderedIds = localFields.map { it.id }
        draggingId = null
        dragOffset = 0f
        if (reorderedIds != fields.map { it.id }) onReorder(reorderedIds)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = ScreenHorizontalPadding,
            end = ScreenHorizontalPadding,
            top = 6.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "add-field-$typeId") {
            ManagementAddRow(
                description = "添加字段",
                testTag = "add_field_row",
                onClick = onAdd
            )
        }
        items(
            items = localFields,
            key = { "field-${it.id}" }
        ) { field ->
            val isDragging = draggingId == field.id
            ManagedFieldRow(
                field = field,
                isDragging = isDragging,
                dragOffset = if (isDragging) dragOffset else 0f,
                onClick = { if (field.isSelectionField()) onOpenOptions(field) },
                onMore = { onMore(field) },
                modifier = Modifier.pointerInput(typeId, field.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingId = field.id
                            dragOffset = 0f
                        },
                        onDragCancel = ::finishDrag,
                        onDragEnd = ::finishDrag,
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val layout = listState.layoutInfo
                            val source = layout.visibleItemsInfo.firstOrNull {
                                it.key == "field-${field.id}"
                            } ?: return@detectDragGesturesAfterLongPress
                            val draggedCenter =
                                source.offset + source.size / 2f + dragOffset
                            val target = layout.visibleItemsInfo
                                .filter { it.index in 1..localFields.size }
                                .minByOrNull {
                                    kotlin.math.abs(
                                        it.offset + it.size / 2f - draggedCenter
                                    )
                                }
                            if (target != null && target.index != source.index) {
                                val sourceIndex = localFields.indexOfFirst {
                                    it.id == field.id
                                }
                                if (sourceIndex >= 0) {
                                    val targetIndex = target.index - 1
                                    val layoutDelta = target.offset - source.offset
                                    localFields.add(
                                        targetIndex,
                                        localFields.removeAt(sourceIndex)
                                    )
                                    dragOffset -= layoutDelta
                                }
                            }
                            val edge = 52f
                            when {
                                draggedCenter < layout.viewportStartOffset + edge ->
                                    scope.launch { listState.scrollBy(-20f) }
                                draggedCenter > layout.viewportEndOffset - edge ->
                                    scope.launch { listState.scrollBy(20f) }
                            }
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun ManagedFieldRow(
    field: DynamicFieldDefinition,
    isDragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier
) {
    val colors = AppTheme.colors
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("field_row_${field.id}")
            .graphicsLayer {
                translationY = dragOffset
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
                alpha = if (isDragging) 0.9f else 1f
            }
            .noRippleClickable(
                enabled = field.isSelectionField() && !isDragging,
                onClick = onClick
            ),
        border = if (isDragging) BorderStroke(1.dp, colors.accent) else null,
        shape = LibraryShapes.small,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = "长按拖动排序",
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary
                )
            }
            Text(
                text = field.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.typography.body,
                color = colors.textPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = field.dataType.displayName(),
                    style = AppTheme.typography.body,
                    color = colors.textSecondary
                )
                if (field.isSelectionField()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = colors.mutedText
                    )
                } else {
                    Spacer(Modifier.size(19.dp))
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .noRippleClickable(enabled = !isDragging, onClick = onMore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "更多操作",
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ManagementAddRow(
    description: String,
    testTag: String,
    onClick: () -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag)
            .noRippleClickable(onClick = onClick),
        shape = LibraryShapes.small,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = AppTheme.colors.accent
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = description,
                style = AppTheme.typography.button,
                color = AppTheme.colors.accent
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldCreateBottomSheet(
    existingNames: List<String>,
    initial: DynamicFieldDefinition? = null,
    onConfirm: (
        String,
        FieldDataType,
        FieldScope,
        String?,
        Set<FieldAggregation>
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable(initial?.id, stateSaver = TextFieldValue.Saver) {
        val initialName = initial?.name.orEmpty()
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(0, initialName.length)
            )
        )
    }
    var dataType by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.dataType ?: FieldDataType.TEXT)
    }
    var scope by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.scope ?: FieldScope.ITEM)
    }
    var unit by rememberSaveable(initial?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial?.unit.orEmpty()))
    }
    var aggregations by remember(initial?.id) {
        mutableStateOf(initial?.aggregations.orEmpty())
    }
    var error by rememberSaveable(initial?.id) { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun submit() {
        val normalized = value.text.trim()
        when {
            normalized.isEmpty() -> error = "字段名称不能为空"
            existingNames.any { it.equals(normalized, ignoreCase = true) } ->
                error = "字段名称已存在"
            else -> {
                onConfirm(
                    normalized,
                    dataType,
                    scope,
                    unit.text.trim().takeIf(String::isNotEmpty),
                    aggregations
                )
                onDismiss()
            }
        }
    }

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
                .padding(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ManagementSheetHeader(
                title = if (initial == null) "添加字段" else "编辑字段",
                onDismiss = onDismiss,
                onConfirm = ::submit,
                confirmEnabled = value.text.isNotBlank(),
                confirmTestTag =
                    if (initial == null) "create_field_button" else "save_field_button"
            )
            Text(
                text = "字段名称",
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.mutedText
            )
            FieldNameInput(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                placeholder = "输入字段名称",
                onSubmit = ::submit
            )
            error?.let {
                Text(
                    text = it,
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
            }
            Text(
                text = "字段类型",
                modifier = Modifier.padding(top = 4.dp),
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.mutedText
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3
            ) {
                FieldDataType.entries.forEach { option ->
                    AppCapsule(
                        text = option.displayName(),
                        selected = dataType == option,
                        onClick = {
                            dataType = option
                            if (option != FieldDataType.NUMBER) {
                                unit = TextFieldValue("")
                            }
                            aggregations = aggregations.intersect(
                                option.allowedAggregations()
                            )
                            error = null
                        },
                        modifier = Modifier.testTag("field_type_${option.name}")
                    )
                }
            }
            Text(
                text = "字段归属",
                modifier = Modifier.padding(top = 4.dp),
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.mutedText
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FieldScope.entries.forEach { option ->
                    AppCapsule(
                        text = option.displayName(),
                        selected = scope == option,
                        onClick = {
                            if (initial?.hasValues == true && option != initial.scope) {
                                error = "已有数据的字段不能修改归属"
                            } else {
                                scope = option
                                error = null
                            }
                        },
                        modifier = Modifier.testTag("field_scope_${option.name}")
                    )
                }
            }
            if (dataType == FieldDataType.NUMBER) {
                Text(
                    text = "单位",
                    modifier = Modifier.padding(top = 4.dp),
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
                CompactConfigurationInput(
                    value = unit,
                    onValueChange = {
                        unit = it
                        error = null
                    },
                        placeholder = "页 / 次 / 元 / 自定义",
                    testTag = "field_unit_input"
                )
                if (initial != null) {
                    Text(
                        text = "修改单位不会转换已有数据。",
                        style = AppTheme.typography.metadata,
                        color = AppTheme.colors.mutedText
                    )
                }
            }
            val allowedAggregations = dataType.allowedAggregations()
            if (allowedAggregations.isNotEmpty()) {
                Text(
                    text = if (aggregations.isEmpty()) "统计 · 不统计" else "统计",
                    modifier = Modifier.padding(top = 4.dp),
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    allowedAggregations.forEach { aggregation ->
                        AppCapsule(
                            text = aggregation.displayName(),
                            selected = aggregation in aggregations,
                            onClick = {
                                aggregations = if (aggregation in aggregations) {
                                    aggregations - aggregation
                                } else {
                                    aggregations + aggregation
                                }
                            },
                            modifier = Modifier.testTag(
                                "field_aggregation_${aggregation.name}"
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactConfigurationInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    testTag: String
) {
    val colors = AppTheme.colors
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            singleLine = true,
            textStyle = AppTheme.typography.input.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = AppTheme.typography.input,
                            color = colors.mutedText
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun FieldNameInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val colors = AppTheme.colors

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("field_name_input")
                .focusRequester(focusRequester)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            singleLine = true,
            textStyle = AppTheme.typography.input.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (value.text.isNotBlank()) onSubmit() }
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = AppTheme.typography.input,
                            color = colors.mutedText
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun InlineOptionInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    focusKey: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember(focusKey) { FocusRequester() }
    val colors = AppTheme.colors

    LaunchedEffect(focusKey) {
        focusRequester.requestFocus()
    }

    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            singleLine = true,
            textStyle = AppTheme.typography.input.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (value.text.isNotBlank()) onSubmit() }
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = AppTheme.typography.input,
                            color = colors.mutedText
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun InlineConfirmButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .noRippleClickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            AppTheme.colors.accent
        } else {
            AppTheme.colors.subtleCard
        },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "确认",
                modifier = Modifier.size(19.dp),
                tint = if (enabled) {
                    AppTheme.colors.onAccent
                } else {
                    AppTheme.colors.mutedText
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldOptionManagementBottomSheet(
    field: DynamicFieldDefinition,
    errorMessage: String?,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var adding by rememberSaveable(field.id) { mutableStateOf(false) }
    var addValue by rememberSaveable(
        field.id,
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(TextFieldValue(""))
    }
    var editingOption by rememberSaveable(field.id) { mutableStateOf<String?>(null) }
    var editValue by rememberSaveable(
        field.id,
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(TextFieldValue(""))
    }
    var inputError by rememberSaveable(field.id) { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun addOption() {
        val normalized = addValue.text.trim()
        when {
            normalized.isEmpty() -> inputError = "选项名称不能为空"
            field.options.any { it.equals(normalized, ignoreCase = true) } ->
                inputError = "选项名称已存在"
            else -> {
                onAdd(normalized)
                addValue = TextFieldValue("")
                inputError = null
                adding = false
            }
        }
    }

    fun startEdit(option: String) {
        adding = false
        addValue = TextFieldValue("")
        editingOption = option
        editValue = TextFieldValue(
            text = option,
            selection = TextRange(0, option.length)
        )
        inputError = null
    }

    fun renameOption() {
        val oldName = editingOption ?: return
        val normalized = editValue.text.trim()
        when {
            normalized.isEmpty() -> inputError = "选项名称不能为空"
            field.options.any {
                !it.equals(oldName, ignoreCase = true) &&
                    it.equals(normalized, ignoreCase = true)
            } -> inputError = "选项名称已存在"
            else -> {
                if (normalized != oldName) onRename(oldName, normalized)
                editingOption = null
                editValue = TextFieldValue("")
                inputError = null
            }
        }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, top = 10.dp, end = 20.dp)
        ) {
            ManagementSheetHeader(title = field.name, onDismiss = onDismiss)
            Text(
                text = "选项管理",
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.mutedText
            )
            inputError?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
            }
            ReorderableOptionList(
                fieldId = field.id,
                options = field.options,
                errorMessage = errorMessage,
                editingOption = editingOption,
                editValue = editValue,
                adding = adding,
                addValue = addValue,
                onStartEdit = ::startEdit,
                onEditValueChange = {
                    editValue = it
                    inputError = null
                },
                onConfirmEdit = ::renameOption,
                onDelete = { option ->
                    if (editingOption == option) {
                        editingOption = null
                        editValue = TextFieldValue("")
                    }
                    onDelete(option)
                    inputError = null
                },
                onStartAdd = {
                    editingOption = null
                    editValue = TextFieldValue("")
                    adding = true
                    inputError = null
                },
                onAddValueChange = {
                    addValue = it
                    inputError = null
                },
                onConfirmAdd = ::addOption,
                onReorder = onReorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun ManagementSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmTestTag: String = "management_sheet_confirm"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .noRippleClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "关闭",
                tint = AppTheme.colors.textPrimary
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = AppTheme.typography.sectionTitle,
            color = AppTheme.colors.textPrimary
        )
        if (onConfirm == null) {
            Spacer(Modifier.size(40.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .testTag(confirmTestTag)
                    .noRippleClickable(
                        enabled = confirmEnabled,
                        onClick = onConfirm
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "确认",
                    tint = if (confirmEnabled) {
                        AppTheme.colors.accent
                    } else {
                        AppTheme.colors.mutedText
                    }
                )
            }
        }
    }
}

@Composable
private fun ReorderableOptionList(
    fieldId: Long,
    options: List<String>,
    errorMessage: String?,
    editingOption: String?,
    editValue: TextFieldValue,
    adding: Boolean,
    addValue: TextFieldValue,
    onStartEdit: (String) -> Unit,
    onEditValueChange: (TextFieldValue) -> Unit,
    onConfirmEdit: () -> Unit,
    onDelete: (String) -> Unit,
    onStartAdd: () -> Unit,
    onAddValueChange: (TextFieldValue) -> Unit,
    onConfirmAdd: () -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val localOptions = remember(fieldId) { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingName by remember(fieldId) { mutableStateOf<String?>(null) }
    var dragOffset by remember(fieldId) { mutableFloatStateOf(0f) }

    LaunchedEffect(fieldId, options, errorMessage) {
        if (draggingName == null) {
            localOptions.clear()
            localOptions.addAll(options)
        }
    }

    fun finishDrag() {
        val reordered = localOptions.toList()
        draggingName = null
        dragOffset = 0f
        if (reordered != options) onReorder(reordered)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = localOptions,
            key = { "field-option-$fieldId-$it" }
        ) { option ->
            val isDragging = draggingName == option
            OptionManagementRow(
                option = option,
                isDragging = isDragging,
                dragOffset = if (isDragging) dragOffset else 0f,
                editing = editingOption == option,
                editValue = editValue,
                onEditValueChange = onEditValueChange,
                onConfirmEdit = onConfirmEdit,
                onStartEdit = { onStartEdit(option) },
                onDelete = { onDelete(option) },
                modifier = Modifier.pointerInput(fieldId, option) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingName = option
                            dragOffset = 0f
                        },
                        onDragCancel = ::finishDrag,
                        onDragEnd = ::finishDrag,
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val layout = listState.layoutInfo
                            val source = layout.visibleItemsInfo.firstOrNull {
                                it.key == "field-option-$fieldId-$option"
                            } ?: return@detectDragGesturesAfterLongPress
                            val draggedCenter =
                                source.offset + source.size / 2f + dragOffset
                            val target = layout.visibleItemsInfo
                                .filter { it.index in localOptions.indices }
                                .minByOrNull {
                                    kotlin.math.abs(
                                        it.offset + it.size / 2f - draggedCenter
                                    )
                                }
                            if (target != null && target.index != source.index) {
                                val sourceIndex = localOptions.indexOf(option)
                                if (sourceIndex >= 0) {
                                    val layoutDelta = target.offset - source.offset
                                    localOptions.add(
                                        target.index,
                                        localOptions.removeAt(sourceIndex)
                                    )
                                    dragOffset -= layoutDelta
                                }
                            }
                            val edge = 52f
                            when {
                                draggedCenter < layout.viewportStartOffset + edge ->
                                    scope.launch { listState.scrollBy(-20f) }
                                draggedCenter > layout.viewportEndOffset - edge ->
                                    scope.launch { listState.scrollBy(20f) }
                            }
                        }
                    )
                }
            )
        }
        if (localOptions.isEmpty()) {
            item(key = "empty-options") {
                Text(
                    text = "暂无选项",
                    modifier = Modifier.padding(vertical = 18.dp),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.mutedText
                )
            }
        }
        item(key = "add-field-option-$fieldId") {
            OptionAddRow(
                adding = adding,
                value = addValue,
                onValueChange = onAddValueChange,
                onConfirm = onConfirmAdd,
                onStartAdd = onStartAdd
            )
        }
    }
}

@Composable
private fun OptionManagementRow(
    option: String,
    isDragging: Boolean,
    dragOffset: Float,
    editing: Boolean,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    onConfirmEdit: () -> Unit,
    onStartEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    val colors = AppTheme.colors
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("field_option_row_$option")
            .graphicsLayer {
                translationY = dragOffset
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
                alpha = if (isDragging) 0.9f else 1f
            },
        shape = LibraryShapes.small,
        border = BorderStroke(
            1.dp,
            if (isDragging) colors.accent else colors.border
        ),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .then(modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = "长按拖动排序",
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary
                )
            }
            if (editing) {
                InlineOptionInput(
                    value = editValue,
                    onValueChange = onEditValueChange,
                    placeholder = "输入选项名称",
                    focusKey = "edit-$option",
                    onSubmit = onConfirmEdit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("field_option_edit_input")
                )
                Spacer(Modifier.size(8.dp))
                InlineConfirmButton(
                    enabled = editValue.text.isNotBlank(),
                    onClick = onConfirmEdit
                )
            } else {
                Text(
                    text = option,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("field_option_text_$option")
                        .noRippleClickable(enabled = !isDragging, onClick = onStartEdit)
                        .padding(vertical = 14.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AppTheme.typography.body,
                    color = colors.textPrimary
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .testTag("delete_field_option_$option")
                    .noRippleClickable(enabled = !isDragging, onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除选项 $option",
                    modifier = Modifier.size(20.dp),
                    tint = AppDanger
                )
            }
        }
    }
}

@Composable
private fun OptionAddRow(
    adding: Boolean,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onConfirm: () -> Unit,
    onStartAdd: () -> Unit
) {
    if (adding) {
        AppThemeSurface(
            role = SurfaceRole.CARD,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("add_field_option_input_row"),
            shape = LibraryShapes.small,
            border = BorderStroke(1.dp, AppTheme.colors.border),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.size(44.dp))
                InlineOptionInput(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = "输入选项名称",
                    focusKey = "add-option",
                    onSubmit = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("field_option_add_input")
                )
                Spacer(Modifier.size(8.dp))
                InlineConfirmButton(
                    enabled = value.text.isNotBlank(),
                    onClick = onConfirm
                )
                Spacer(Modifier.size(4.dp))
            }
        }
    } else {
        Text(
            text = "+ 添加选项",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_field_option_row")
                .noRippleClickable(onClick = onStartAdd)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            style = AppTheme.typography.button,
            color = AppTheme.colors.accent
        )
    }
}

internal fun FieldDataType.displayName(): String = when (this) {
    FieldDataType.TEXT -> "文本"
    FieldDataType.NUMBER -> "数字"
    FieldDataType.DATE -> "日期"
    FieldDataType.BOOLEAN -> "开关"
    FieldDataType.SINGLE_SELECT -> "单选"
    FieldDataType.MULTI_SELECT -> "多选"
    FieldDataType.RATING -> "评分"
}

private fun FieldScope.displayName(): String = when (this) {
    FieldScope.ITEM -> "作品"
    FieldScope.RECORD -> "每次记录"
}

private fun FieldAggregation.displayName(): String = when (this) {
    FieldAggregation.SUM -> "总计"
    FieldAggregation.AVERAGE -> "平均"
    FieldAggregation.MAXIMUM -> "最大值"
    FieldAggregation.MINIMUM -> "最小值"
    FieldAggregation.OPTION_DISTRIBUTION -> "选项分布"
    FieldAggregation.RATING_AVERAGE -> "平均评分"
    FieldAggregation.RATING_DISTRIBUTION -> "评分分布"
}

private fun DynamicFieldDefinition.isSelectionField(): Boolean =
    dataType == FieldDataType.SINGLE_SELECT ||
        dataType == FieldDataType.MULTI_SELECT
