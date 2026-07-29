package com.example.mylibrary.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.ui.components.AppConfirmDialog
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.SimpleTopBar
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.CapsuleHeight
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import kotlinx.coroutines.launch

@Composable
fun TagManagementScreen(
    state: TagManagementUiState,
    onBack: () -> Unit,
    onSelectRoot: (Long) -> Unit,
    onCreateRoot: (String) -> Unit,
    onCreateChildren: (Long, List<String>) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onReorderRoots: (List<Long>) -> Unit,
    onReorderChildren: (Long, List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateRoot by rememberSaveable { mutableStateOf(false) }
    var addingChildrenToId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actionTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedRoot = state.selectedRoot

    AppScreenContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
        SimpleTopBar(title = "标签设置", onBack = onBack)

        state.errorMessage?.let { message ->
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

        RootTagCapsuleBar(
            tags = state.rootTags,
            selectedId = selectedRoot?.id,
            errorMessage = state.errorMessage,
            onSelect = onSelectRoot,
            onAdd = { showCreateRoot = true },
            onReorder = onReorderRoots
        )

        if (selectedRoot == null) {
            NoRootTagsState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onAdd = { showCreateRoot = true }
            )
        } else {
            val children = state.selectedChildren
            CurrentRootHeader(
                root = selectedRoot,
                childCount = children.size,
                onMore = { actionTargetId = selectedRoot.id }
            )
            ReorderableChildList(
                parentId = selectedRoot.id,
                children = children,
                errorMessage = state.errorMessage,
                onMore = { actionTargetId = it.id },
                onAdd = { addingChildrenToId = selectedRoot.id },
                onReorder = { onReorderChildren(selectedRoot.id, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        }
    }

    if (showCreateRoot) {
        TagNameInputDialog(
            title = "添加一级标签",
            initialValue = "",
            existingNames = state.rootTags.map { it.name },
            onConfirm = onCreateRoot,
            onDismiss = { showCreateRoot = false }
        )
    }

    state.tags.firstOrNull { it.id == addingChildrenToId }?.let { root ->
        TagBatchAddDialog(
            rootName = root.name,
            existingNames = state.tags
                .filter { it.enabled && it.parentId == root.id }
                .map { it.name },
            onConfirm = { names -> onCreateChildren(root.id, names) },
            onDismiss = { addingChildrenToId = null }
        )
    }

    state.tags.firstOrNull { it.id == renamingId }?.let { tag ->
        TagNameInputDialog(
            title = if (tag.parentId == null) "重命名一级标签" else "重命名二级标签",
            initialValue = tag.name,
            existingNames = state.tags
                .filter {
                    it.enabled &&
                        it.parentId == tag.parentId &&
                        it.id != tag.id
                }
                .map { it.name },
            onConfirm = { onRename(tag.id, it) },
            onDismiss = { renamingId = null }
        )
    }

    state.tags.firstOrNull { it.id == actionTargetId }?.let { tag ->
        TagActionDialog(
            tagName = tag.name,
            onRename = {
                actionTargetId = null
                renamingId = tag.id
            },
            onDelete = {
                actionTargetId = null
                deletingId = tag.id
            },
            onDismiss = { actionTargetId = null }
        )
    }

    state.tags.firstOrNull { it.id == deletingId }?.let { tag ->
        val childCount = if (tag.parentId == null) {
            state.allChildrenOf(tag.id).size
        } else {
            0
        }
        val usageCount = state.usageCount(tag.id)
        AppConfirmDialog(
            title = deleteDialogTitle(tag, usageCount, childCount),
            message = deleteDialogMessage(tag, usageCount, childCount),
            confirmText = "删除",
            dismissText = "取消",
            destructive = true,
            onConfirm = {
                onDelete(tag.id)
                deletingId = null
            },
            onDismiss = { deletingId = null }
        )
    }
}

@Composable
private fun RootTagCapsuleBar(
    tags: List<LibraryTag>,
    selectedId: Long?,
    errorMessage: String?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<Long>) -> Unit
) {
    val localTags = remember { mutableStateListOf<LibraryTag>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(tags, errorMessage) {
        if (draggingId == null) {
            localTags.clear()
            localTags.addAll(tags)
        }
    }
    LaunchedEffect(selectedId, tags) {
        val selectedIndex = tags.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0 &&
            listState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }
        ) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    fun finishDrag() {
        val reorderedIds = localTags.map { it.id }
        draggingId = null
        dragOffset = 0f
        if (reorderedIds != tags.map { it.id }) {
            onReorder(reorderedIds)
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
        items(
            items = localTags,
            key = { "root-${it.id}" }
        ) { tag ->
            val isDragging = draggingId == tag.id
            RootTagCapsule(
                tag = tag,
                selected = tag.id == selectedId,
                isDragging = isDragging,
                dragOffset = if (isDragging) dragOffset else 0f,
                onClick = { onSelect(tag.id) },
                modifier = Modifier.pointerInput(tag.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggingId = tag.id
                            dragOffset = 0f
                        },
                        onDragCancel = ::finishDrag,
                        onDragEnd = ::finishDrag,
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.x
                            val layout = listState.layoutInfo
                            val source = layout.visibleItemsInfo.firstOrNull {
                                it.key == "root-${tag.id}"
                            } ?: return@detectDragGesturesAfterLongPress
                            val draggedCenter = source.offset + source.size / 2f + dragOffset
                            val target = layout.visibleItemsInfo
                                .filter { it.index in localTags.indices }
                                .minByOrNull {
                                    kotlin.math.abs(
                                        it.offset + it.size / 2f - draggedCenter
                                    )
                                }
                            if (target != null && target.index != source.index) {
                                val sourceIndex = localTags.indexOfFirst { it.id == tag.id }
                                if (sourceIndex >= 0) {
                                    val layoutDelta = target.offset - source.offset
                                    localTags.add(
                                        target.index,
                                        localTags.removeAt(sourceIndex)
                                    )
                                    dragOffset -= layoutDelta
                                }
                            }
                            val edge = 44f
                            when {
                                draggedCenter < layout.viewportStartOffset + edge ->
                                    scope.launch { listState.scrollBy(-18f) }
                                draggedCenter > layout.viewportEndOffset - edge ->
                                    scope.launch { listState.scrollBy(18f) }
                            }
                        }
                    )
                }
            )
        }
        item(key = "add-root") {
            AddRootCapsule(onClick = onAdd)
        }
    }
}

@Composable
private fun RootTagCapsule(
    tag: LibraryTag,
    selected: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val colors = AppTheme.colors
    Surface(
        modifier = Modifier
            .height(CapsuleHeight)
            .widthIn(max = 220.dp)
            .graphicsLayer {
                translationX = dragOffset
                scaleX = if (isDragging) 1.04f else 1f
                scaleY = if (isDragging) 1.04f else 1f
                alpha = if (isDragging) 0.9f else 1f
            }
            .then(modifier)
            .noRippleClickable(enabled = !isDragging, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) {
            colors.accent.copy(alpha = 0.10f)
        } else {
            colors.surfaces.card
        },
        border = BorderStroke(
            1.dp,
            when {
                isDragging || selected -> colors.accent
                else -> colors.border
            }
        ),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tag.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.typography.capsule,
                color = if (selected) colors.accent else colors.textSecondary
            )
        }
    }
}

@Composable
private fun AddRootCapsule(onClick: () -> Unit) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .height(CapsuleHeight)
            .noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, AppTheme.colors.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "添加一级标签",
                modifier = Modifier.size(18.dp),
                tint = AppTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun CurrentRootHeader(
    root: LibraryTag,
    childCount: Int,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ScreenHorizontalPadding,
                top = 12.dp,
                end = ScreenHorizontalPadding,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = root.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.typography.pageTitle,
                color = AppTheme.colors.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$childCount 个二级标签",
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.mutedText
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .noRippleClickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "管理一级标签",
                tint = AppTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun ReorderableChildList(
    parentId: Long,
    children: List<LibraryTag>,
    errorMessage: String?,
    onMore: (LibraryTag) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val localChildren = remember(parentId) { mutableStateListOf<LibraryTag>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingId by remember(parentId) { mutableStateOf<Long?>(null) }
    var dragOffset by remember(parentId) { mutableFloatStateOf(0f) }

    LaunchedEffect(parentId, children, errorMessage) {
        if (draggingId == null) {
            localChildren.clear()
            localChildren.addAll(children)
        }
    }

    fun finishDrag() {
        val reorderedIds = localChildren.map { it.id }
        draggingId = null
        dragOffset = 0f
        if (reorderedIds != children.map { it.id }) {
            onReorder(reorderedIds)
        }
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
        items(
            items = localChildren,
            key = { "child-${it.id}" }
        ) { child ->
            val isDragging = draggingId == child.id
            ChildTagRow(
                tag = child,
                isDragging = isDragging,
                dragOffset = if (isDragging) dragOffset else 0f,
                onMore = { onMore(child) },
                modifier = Modifier.pointerInput(child.id) {
                    detectDragGestures(
                        onDragStart = {
                            draggingId = child.id
                            dragOffset = 0f
                        },
                        onDragCancel = ::finishDrag,
                        onDragEnd = ::finishDrag,
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val layout = listState.layoutInfo
                            val source = layout.visibleItemsInfo.firstOrNull {
                                it.key == "child-${child.id}"
                            } ?: return@detectDragGestures
                            val draggedCenter = source.offset + source.size / 2f + dragOffset
                            val target = layout.visibleItemsInfo
                                .filter { it.index in localChildren.indices }
                                .minByOrNull {
                                    kotlin.math.abs(
                                        it.offset + it.size / 2f - draggedCenter
                                    )
                                }
                            if (target != null && target.index != source.index) {
                                val sourceIndex =
                                    localChildren.indexOfFirst { it.id == child.id }
                                if (sourceIndex >= 0) {
                                    val layoutDelta = target.offset - source.offset
                                    localChildren.add(
                                        target.index,
                                        localChildren.removeAt(sourceIndex)
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
        item(key = "add-child") {
            AddChildCard(onClick = onAdd)
        }
    }
}

@Composable
private fun ChildTagRow(
    tag: LibraryTag,
    isDragging: Boolean,
    dragOffset: Float,
    onMore: () -> Unit,
    modifier: Modifier
) {
    val colors = AppTheme.colors
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("child_tag_row_${tag.id}")
            .graphicsLayer {
                translationY = dragOffset
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
                alpha = if (isDragging) 0.9f else 1f
            },
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
                    contentDescription = "拖动排序",
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary
                )
            }
            Text(
                text = tag.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.typography.body,
                color = colors.textPrimary
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .noRippleClickable(
                        enabled = !isDragging,
                        onClick = onMore
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "管理二级标签",
                    modifier = Modifier.size(20.dp),
                    tint = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun AddChildCard(onClick: () -> Unit) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("add_child_tag_row")
            .noRippleClickable(onClick = onClick),
        shape = LibraryShapes.small,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "添加二级标签",
                modifier = Modifier.size(21.dp),
                tint = AppTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun NoRootTagsState(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "暂无一级标签",
            style = AppTheme.typography.sectionTitle,
            color = AppTheme.colors.textPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "先创建一个一级标签开始整理作品。",
            style = AppTheme.typography.body,
            color = AppTheme.colors.mutedText
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .noRippleClickable(onClick = onAdd)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = AppTheme.colors.accent
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "添加一级标签",
                style = AppTheme.typography.button,
                color = AppTheme.colors.accent
            )
        }
    }
}

private fun deleteDialogTitle(
    tag: LibraryTag,
    usageCount: Int,
    childCount: Int
): String = when {
    tag.parentId == null && childCount > 0 ->
        "「${tag.name}」下还有 $childCount 个二级标签。"
    tag.parentId != null && usageCount > 0 ->
        "「${tag.name}」正在被 $usageCount 个作品使用。"
    else -> "确认删除「${tag.name}」？"
}

private fun deleteDialogMessage(
    tag: LibraryTag,
    usageCount: Int,
    childCount: Int
): String = when {
    tag.parentId == null && childCount > 0 ->
        "删除后，将同时删除这些二级标签，\n并从相关作品中移除该一级标签及这些二级标签。\n此操作无法撤销。"
    usageCount > 0 ->
        "删除后，这些作品将移除该标签，\n作品本身不会被删除。"
    else -> "此操作无法撤销。"
}
