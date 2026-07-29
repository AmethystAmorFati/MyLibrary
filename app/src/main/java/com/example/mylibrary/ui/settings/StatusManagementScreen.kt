package com.example.mylibrary.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.StatusScope
import com.example.mylibrary.ui.components.AppCapsule
import com.example.mylibrary.ui.components.AppConfirmDialog
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding

@Composable
fun StatusManagementScreen(
    state: StatusManagementUiState,
    onBack: () -> Unit,
    onSelectScope: (StatusScope) -> Unit = {},
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
        title = "状态管理",
        items = state.statuses.map { FlatManagementItem(it.id, it.name) },
        errorMessage = state.errorMessage,
        testTagPrefix = "status",
        onBack = onBack,
        onMore = { actionTargetId = it },
        onAdd = { creating = true },
        onReorder = onReorder,
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp)
                    .testTag("status_scope_switcher"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppCapsule(
                    text = "作品状态",
                    selected = state.selectedScope == StatusScope.ITEM,
                    onClick = { onSelectScope(StatusScope.ITEM) },
                    modifier = Modifier.testTag("status_scope_item")
                )
                AppCapsule(
                    text = "记录状态",
                    selected = state.selectedScope == StatusScope.RECORD,
                    onClick = { onSelectScope(StatusScope.RECORD) },
                    modifier = Modifier.testTag("status_scope_record")
                )
            }
        },
        modifier = modifier
    )

    if (creating) {
        TagNameInputDialog(
            title = "新增状态",
            initialValue = "",
            existingNames = state.statuses.map { it.name },
            placeholder = "输入状态名称",
            emptyMessage = "状态名称不能为空",
            duplicateMessage = "状态名称已存在",
            onConfirm = onCreate,
            onDismiss = { creating = false }
        )
    }
    state.statuses.firstOrNull { it.id == renamingId }?.let { status ->
        TagNameInputDialog(
            title = "编辑状态",
            initialValue = status.name,
            existingNames = state.statuses.filterNot { it.id == status.id }.map { it.name },
            placeholder = "输入状态名称",
            emptyMessage = "状态名称不能为空",
            duplicateMessage = "状态名称已存在",
            onConfirm = { onRename(status.id, it) },
            onDismiss = { renamingId = null }
        )
    }
    state.statuses.firstOrNull { it.id == actionTargetId }?.let { status ->
        TagActionDialog(
            tagName = status.name,
            renameText = "编辑",
            onRename = {
                actionTargetId = null
                renamingId = status.id
            },
            onDelete = {
                actionTargetId = null
                deletingId = status.id
            },
            onDismiss = { actionTargetId = null }
        )
    }
    state.statuses.firstOrNull { it.id == deletingId }?.let { status ->
        val usageCount = if (state.selectedScope == StatusScope.ITEM) {
            state.usageCount(status.id)
        } else {
            0
        }
        AppConfirmDialog(
            title = if (usageCount == 0) "删除状态" else "无法删除状态",
            message = if (usageCount == 0) {
                "确定删除“${status.name}”吗？"
            } else {
                "仍有 $usageCount 部作品使用“${status.name}”，请先调整这些作品的状态。"
            },
            confirmText = if (usageCount == 0) "删除" else "知道了",
            dismissText = "取消",
            destructive = usageCount == 0,
            onConfirm = {
                if (usageCount == 0) onDelete(status.id)
                deletingId = null
            },
            onDismiss = { deletingId = null }
        )
    }
}
