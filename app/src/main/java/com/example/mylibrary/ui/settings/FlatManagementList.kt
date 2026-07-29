package com.example.mylibrary.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.components.SimpleTopBar
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarToContentGap
import kotlinx.coroutines.launch

internal data class FlatManagementItem(
    val id: Long,
    val name: String
)

@Composable
internal fun FlatManagementScreen(
    title: String,
    items: List<FlatManagementItem>,
    errorMessage: String?,
    testTagPrefix: String,
    onBack: () -> Unit,
    onMore: (Long) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    header: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AppScreenContainer(
        modifier = modifier.testTag("${testTagPrefix}_management_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        SimpleTopBar(title = title, onBack = onBack)
        header?.invoke()
        errorMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(
                    start = ScreenHorizontalPadding,
                    end = ScreenHorizontalPadding,
                    bottom = 6.dp
                ),
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.mutedText
            )
        }
            ReorderableFlatList(
            items = items,
            errorMessage = errorMessage,
            testTagPrefix = testTagPrefix,
            onMore = onMore,
            onAdd = onAdd,
            onReorder = onReorder,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            )
        }
    }
}

@Composable
private fun ReorderableFlatList(
    items: List<FlatManagementItem>,
    errorMessage: String?,
    testTagPrefix: String,
    onMore: (Long) -> Unit,
    onAdd: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier
) {
    val localItems = remember { mutableStateListOf<FlatManagementItem>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(items, errorMessage) {
        if (draggingId == null) {
            localItems.clear()
            localItems.addAll(items)
        }
    }

    fun finishDrag() {
        val orderedIds = localItems.map { it.id }
        draggingId = null
        dragOffset = 0f
        if (orderedIds != items.map { it.id }) onReorder(orderedIds)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = ScreenHorizontalPadding,
            end = ScreenHorizontalPadding,
            top = TopBarToContentGap,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = localItems,
            key = { "$testTagPrefix-${it.id}" }
        ) { item ->
            val isDragging = draggingId == item.id
            FlatManagementRow(
                item = item,
                isDragging = isDragging,
                dragOffset = if (isDragging) dragOffset else 0f,
                testTagPrefix = testTagPrefix,
                onMore = { onMore(item.id) },
                dragModifier = Modifier.pointerInput(item.id) {
                    detectDragGestures(
                        onDragStart = {
                            draggingId = item.id
                            dragOffset = 0f
                        },
                        onDragCancel = ::finishDrag,
                        onDragEnd = ::finishDrag,
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val layout = listState.layoutInfo
                            val source = layout.visibleItemsInfo.firstOrNull {
                                it.key == "$testTagPrefix-${item.id}"
                            } ?: return@detectDragGestures
                            val center = source.offset + source.size / 2f + dragOffset
                            val target = layout.visibleItemsInfo
                                .filter { it.index in localItems.indices }
                                .minByOrNull {
                                    kotlin.math.abs(
                                        it.offset + it.size / 2f - center
                                    )
                                }
                            if (target != null && target.index != source.index) {
                                val sourceIndex = localItems.indexOfFirst {
                                    it.id == item.id
                                }
                                if (sourceIndex >= 0) {
                                    val layoutDelta = target.offset - source.offset
                                    localItems.add(
                                        target.index,
                                        localItems.removeAt(sourceIndex)
                                    )
                                    dragOffset -= layoutDelta
                                }
                            }
                            val edge = 52f
                            when {
                                center < layout.viewportStartOffset + edge ->
                                    scope.launch { listState.scrollBy(-20f) }
                                center > layout.viewportEndOffset - edge ->
                                    scope.launch { listState.scrollBy(20f) }
                            }
                        }
                    )
                }
            )
        }
        item(key = "$testTagPrefix-add") {
            AppThemeSurface(
                role = SurfaceRole.CARD,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("${testTagPrefix}_add_row")
                    .noRippleClickable(onClick = onAdd),
                shape = LibraryShapes.small,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "新增",
                        modifier = Modifier.size(21.dp),
                        tint = AppTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun FlatManagementRow(
    item: FlatManagementItem,
    isDragging: Boolean,
    dragOffset: Float,
    testTagPrefix: String,
    onMore: () -> Unit,
    dragModifier: Modifier
) {
    val colors = AppTheme.colors
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("${testTagPrefix}_row_${item.id}")
            .graphicsLayer {
                translationY = dragOffset
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
                alpha = if (isDragging) 0.9f else 1f
            },
        shape = LibraryShapes.small,
        border = if (isDragging) BorderStroke(1.dp, colors.accent) else null,
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
                    .then(dragModifier),
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
                text = item.name,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.typography.body,
                color = colors.textPrimary
            )
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
