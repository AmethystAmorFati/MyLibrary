package com.example.mylibrary.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.TrashItem
import com.example.mylibrary.ui.components.AppConfirmDialog
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.CoverDisplayMode
import com.example.mylibrary.ui.components.CoverImage
import com.example.mylibrary.ui.components.SimpleTopBar
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppDanger
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.SecondaryHeaderHeight
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.TopBarActionSize
import com.example.mylibrary.ui.theme.TopBarHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarToContentGap

@Composable
fun TrashScreen(
    state: TrashUiState,
    onBack: () -> Unit,
    onRestore: (Long) -> Unit,
    onStartSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onPermanentlyDeleteSelected: () -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    BackHandler(enabled = state.isSelectionMode, onBack = onClearSelection)

    AppScreenContainer(modifier = modifier.testTag("screen_trash")) {
        Column(Modifier.fillMaxSize()) {
            if (state.isSelectionMode) {
                TrashSelectionTopBar(
                    selectedCount = state.pendingDeleteCount,
                    enabled = !state.isOperationRunning,
                    onClose = onClearSelection,
                    onDelete = { confirmDeleteSelection = true }
                )
            } else {
                SimpleTopBar(
                    title = "回收站",
                    onBack = onBack,
                    action = if (state.items.isNotEmpty()) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(TopBarActionSize)
                                    .testTag("trash_empty_action")
                                    .noRippleClickable(
                                        enabled = !state.isOperationRunning
                                    ) {
                                        confirmEmpty = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "清空回收站",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (state.isOperationRunning) {
                                        AppTheme.colors.mutedText
                                    } else {
                                        AppDanger
                                    }
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            }
            state.errorMessage?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(
                        horizontal = ScreenHorizontalPadding,
                        vertical = 4.dp
                    ),
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
            }
            if (!state.isLoading && state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "回收站为空",
                        modifier = Modifier.testTag("trash_empty_state"),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("trash_list"),
                    contentPadding = PaddingValues(
                        start = ScreenHorizontalPadding,
                        end = ScreenHorizontalPadding,
                        top = TopBarToContentGap,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = TrashItem::id) { item ->
                        TrashItemCard(
                            item = item,
                            isSelectionMode = state.isSelectionMode,
                            selected = item.id in state.selectedItemIds,
                            enabled = !state.isOperationRunning,
                            onRestore = { onRestore(item.id) },
                            onStartSelection = { onStartSelection(item.id) },
                            onToggleSelection = { onToggleSelection(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (confirmDeleteSelection && state.selectedItemIds.isNotEmpty()) {
        val selectedItems = state.items.filter { it.id in state.selectedItemIds }
        AppConfirmDialog(
            title = "永久删除？",
            message = if (selectedItems.size == 1) {
                "《${selectedItems.single().title}》及其记录、摘录和关联数据将永久删除，无法恢复。"
            } else {
                "将永久删除选中的 ${selectedItems.size} 个作品及其记录、摘录和关联数据，无法恢复。"
            },
            confirmText = "永久删除",
            dismissText = "取消",
            destructive = true,
            confirmTestTag = "trash_confirm_delete_selected",
            onConfirm = {
                confirmDeleteSelection = false
                onPermanentlyDeleteSelected()
            },
            onDismiss = { confirmDeleteSelection = false }
        )
    }
    if (confirmEmpty) {
        AppConfirmDialog(
            title = "清空回收站？",
            message = "将永久删除回收站中的 ${state.items.size} 个作品及其记录、摘录和关联数据，无法恢复。",
            confirmText = "清空",
            dismissText = "取消",
            destructive = true,
            confirmTestTag = "trash_confirm_empty",
            onConfirm = {
                confirmEmpty = false
                onEmptyTrash()
            },
            onDismiss = { confirmEmpty = false }
        )
    }
}

@Composable
private fun TrashSelectionTopBar(
    selectedCount: Int,
    enabled: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SecondaryHeaderHeight)
            .padding(horizontal = TopBarHorizontalPadding)
            .testTag("trash_selection_top_bar")
    ) {
        Box(
            modifier = Modifier
                .size(TopBarActionSize)
                .align(Alignment.CenterStart)
                .testTag("trash_clear_selection")
                .noRippleClickable(enabled = enabled, onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "退出多选",
                modifier = Modifier.size(24.dp),
                tint = AppTheme.colors.textPrimary
            )
        }
        Text(
            text = "已选择 $selectedCount 项",
            modifier = Modifier
                .align(Alignment.Center)
                .testTag("trash_selection_count"),
            style = AppTheme.typography.pageTitle,
            color = AppTheme.colors.textPrimary
        )
        Box(
            modifier = Modifier
                .size(TopBarActionSize)
                .align(Alignment.CenterEnd)
                .testTag("trash_delete_selected_action")
                .noRippleClickable(enabled = enabled, onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "永久删除所选作品",
                modifier = Modifier.size(24.dp),
                tint = if (enabled) AppDanger else AppTheme.colors.mutedText
            )
        }
    }
}

@Composable
private fun TrashItemCard(
    item: TrashItem,
    isSelectionMode: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onRestore: () -> Unit,
    onStartSelection: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trash_item_${item.id}")
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isSelectionMode) onToggleSelection() else onRestore()
                },
                onLongClick = {
                    if (!isSelectionMode) onStartSelection()
                }
            ),
        shape = LibraryShapes.medium,
        border = if (selected) BorderStroke(1.dp, AppTheme.colors.accent) else null,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                CoverImage(
                    thumbnailPath = item.thumbnailPath,
                    originalPath = item.coverPath,
                    title = item.title,
                    creator = item.creator,
                    typeName = item.typeName,
                    typeId = item.typeId,
                    displayMode = CoverDisplayMode.LIBRARY_LIST,
                    modifier = Modifier.size(width = 48.dp, height = 72.dp)
                )
                if (isSelectionMode && selected) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .testTag("trash_item_selected_${item.id}"),
                        shape = CircleShape,
                        color = AppTheme.colors.accent,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "已选中",
                                modifier = Modifier.size(12.dp),
                                tint = AppTheme.colors.onAccent
                            )
                        }
                    }
                }
            }
            Text(
                text = item.title,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = AppTheme.typography.itemTitle,
                color = AppTheme.colors.textPrimary
            )
        }
    }
}
