package com.example.mylibrary.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.mylibrary.data.database.DefaultLibraryData
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.ui.components.AppConfirmDialog

@Composable
fun ItemTypeManagementScreen(
    state: ItemTypeManagementUiState,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    var creating by remember { mutableStateOf(false) }
    var actionTargetId by remember { mutableStateOf<Long?>(null) }
    var renamingId by remember { mutableStateOf<Long?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    FlatManagementScreen(
        title = "作品类型",
        items = state.types.map {
            FlatManagementItem(it.id, it.managementDisplayName())
        },
        errorMessage = state.errorMessage,
        testTagPrefix = "item_type",
        onBack = onBack,
        onMore = { actionTargetId = it },
        onAdd = { creating = true },
        onReorder = onReorder,
        modifier = modifier
    )

    if (creating) {
        TagNameInputDialog(
            title = "新增作品类型",
            initialValue = "",
            existingNames = state.types.map { it.name },
            placeholder = "输入作品类型名称",
            emptyMessage = "作品类型名称不能为空",
            duplicateMessage = "作品类型名称已存在",
            onConfirm = onCreate,
            onDismiss = { creating = false }
        )
    }
    state.types.firstOrNull { it.id == renamingId }?.let { type ->
        TagNameInputDialog(
            title = "重命名作品类型",
            initialValue = type.managementDisplayName(),
            existingNames = state.types
                .filterNot { it.id == type.id }
                .map { it.managementDisplayName() },
            placeholder = "输入作品类型名称",
            emptyMessage = "作品类型名称不能为空",
            duplicateMessage = "作品类型名称已存在",
            onConfirm = { onRename(type.id, it) },
            onDismiss = { renamingId = null }
        )
    }
    state.types.firstOrNull { it.id == actionTargetId }?.let { type ->
        TagActionDialog(
            tagName = type.managementDisplayName(),
            renameText = "编辑",
            onRename = {
                actionTargetId = null
                renamingId = type.id
            },
            onDelete = {
                actionTargetId = null
                deletingId = type.id
            },
            onDismiss = { actionTargetId = null }
        )
    }
    state.types.firstOrNull { it.id == deletingId }?.let { type ->
        val builtIn = type.id == DefaultLibraryData.BOOK_TYPE_ID ||
            type.id == DefaultLibraryData.MOVIE_TYPE_ID
        val usageCount = state.usageCount(type.id)
        val canDelete = !builtIn && usageCount == 0
        AppConfirmDialog(
            title = if (canDelete) "删除作品类型" else "无法删除作品类型",
            message = when {
                builtIn -> "“${type.managementDisplayName()}”是内置作品类型，不能删除。"
                usageCount > 0 ->
                    "仍有 $usageCount 部作品属于“${type.name}”，请先迁移这些作品。"
                else -> "确定删除“${type.name}”吗？"
            },
            confirmText = if (canDelete) "删除" else "知道了",
            dismissText = "取消",
            destructive = canDelete,
            onConfirm = {
                if (canDelete) onDelete(type.id)
                deletingId = null
            },
            onDismiss = { deletingId = null }
        )
    }
}

private fun ItemType.managementDisplayName(): String = when (id) {
    DefaultLibraryData.BOOK_TYPE_ID -> if (name == "Book") "书" else name
    DefaultLibraryData.MOVIE_TYPE_ID -> if (name == "Movie") "电影" else name
    else -> name
}
