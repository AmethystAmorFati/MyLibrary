package com.example.mylibrary.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.mylibrary.ui.components.AppConfirmDialog
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.MainPageHeader
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppDanger
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CardContentPadding
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.TopBarToContentGap

@Composable
fun ThemeManagementScreen(
    state: ThemeManagementUiState,
    onImportSelected: (Uri) -> Unit,
    onApplyTheme: (String?) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onMessageShown: (Long) -> Unit,
    onConfirmReplace: () -> Unit,
    onCancelReplace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingDelete by remember {
        mutableStateOf<ThemeListItem?>(null)
    }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportSelected)
    }

    LaunchedEffect(state.message?.id) {
        state.message?.let { message ->
            Toast.makeText(
                context,
                message.text,
                Toast.LENGTH_SHORT
            ).show()
            onMessageShown(message.id)
        }
    }

    AppScreenContainer(
        modifier = modifier.testTag("screen_theme_management")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ScreenHorizontalPadding)
        ) {
            MainPageHeader(title = "主题")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = TopBarToContentGap,
                        bottom = 40.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ImportThemeRow(
                    isImporting = state.isImporting,
                    enabled = !state.isBusy
                ) {
                    picker.launch(THEME_PACKAGE_MIME_TYPES)
                }

                SectionTitle("默认主题")
                state.themes.firstOrNull { it.isDefault }?.let { item ->
                    ThemeRow(
                        item = item,
                        applying = state.applyingThemeId ==
                            item.operationId,
                        deleting = false,
                        operationsEnabled = !state.isBusy,
                        onApply = { onApplyTheme(null) },
                        onDelete = {}
                    )
                }

                SectionTitle("已安装")
                when {
                    state.isLoading -> LoadingRow()
                    state.themes.none { !it.isDefault } ->
                        EmptyInstalledThemes()
                    else -> state.themes
                        .filterNot { it.isDefault }
                        .forEach { item ->
                            ThemeRow(
                                item = item,
                                applying = state.applyingThemeId ==
                                    item.operationId,
                                deleting = state.deletingThemeId == item.id,
                                operationsEnabled = !state.isBusy,
                                onApply = {
                                    onApplyTheme(item.id)
                                },
                                onDelete = {
                                    pendingDelete = item
                                }
                            )
                        }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AppConfirmDialog(
            title = "删除主题",
            message = if (item.isCurrent) {
                "将先恢复默认主题，再删除“${item.name}”。"
            } else {
                "确定删除“${item.name}”吗？"
            },
            confirmText = "删除",
            dismissText = "取消",
            destructive = true,
            confirmTestTag = "theme_confirm_delete",
            onConfirm = {
                pendingDelete = null
                item.id?.let(onDeleteTheme)
            },
            onDismiss = { pendingDelete = null }
        )
    }

    state.pendingReplace?.let { pending ->
        AppConfirmDialog(
            title = "替换主题",
            message = "已存在主题“${pending.existingName ?: pending.themeId}”（版本 ${pending.existingVersion ?: "未知"}），" +
                "是否替换为新版本“${pending.importingName}”（版本 ${pending.importingVersion}）？",
            confirmText = "替换",
            dismissText = "取消",
            destructive = false,
            confirmTestTag = "theme_confirm_replace",
            onConfirm = onConfirmReplace,
            onDismiss = onCancelReplace
        )
    }
}

@Composable
private fun ImportThemeRow(
    isImporting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_import")
            .noRippleClickable(enabled = enabled, onClick = onClick),
        shape = LibraryShapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(CardContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isImporting) "正在导入" else "导入主题",
                modifier = Modifier.weight(1f),
                style = AppTheme.typography.body,
                color = AppTheme.colors.textPrimary
            )
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AppTheme.colors.accent,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(
    item: ThemeListItem,
    applying: Boolean,
    deleting: Boolean,
    operationsEnabled: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_item_${item.operationId}"),
        shape = LibraryShapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(CardContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textPrimary
                )
                item.metadataText?.let { metadata ->
                    Text(
                        text = metadata,
                        style = AppTheme.typography.metadata,
                        color = if (
                            item.status == ThemeListItemStatus.INVALID
                        ) {
                            AppDanger
                        } else {
                            AppTheme.colors.textSecondary
                        }
                    )
                }
            }
            when {
                applying || deleting -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = AppTheme.colors.accent,
                    strokeWidth = 2.dp
                )
                item.isCurrent -> Text(
                    text = "正在使用",
                    style = AppTheme.typography.button,
                    color = AppTheme.colors.accent
                )
                item.status == ThemeListItemStatus.INVALID -> Text(
                    text = "无法应用",
                    style = AppTheme.typography.metadata,
                    color = AppDanger
                )
                else -> Text(
                    text = "应用",
                    modifier = Modifier
                        .testTag("theme_apply_${item.operationId}")
                        .noRippleClickable(
                            enabled = operationsEnabled &&
                                item.canApply,
                            onClick = onApply
                        )
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    style = AppTheme.typography.button,
                    color = AppTheme.colors.accent
                )
            }
            if (item.canDelete) {
                Spacer(Modifier.size(6.dp))
                ThemeItemMenu(
                    enabled = operationsEnabled,
                    itemId = item.operationId,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun ThemeItemMenu(
    enabled: Boolean,
    itemId: String,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val verticalOffset = with(LocalDensity.current) {
        38.dp.roundToPx()
    }
    Box {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "主题操作",
            modifier = Modifier
                .size(36.dp)
                .testTag("theme_menu_$itemId")
                .noRippleClickable(enabled = enabled) {
                    expanded = true
                }
                .padding(6.dp),
            tint = AppTheme.colors.textSecondary
        )
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, verticalOffset),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                AppThemeSurface(
                    role = SurfaceRole.CARD,
                    shape = LibraryShapes.small,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text = "删除",
                        modifier = Modifier
                            .testTag("theme_delete_$itemId")
                            .noRippleClickable {
                                expanded = false
                                onDelete()
                            }
                            .padding(
                                horizontal = 22.dp,
                                vertical = 14.dp
                            ),
                        style = AppTheme.typography.body,
                        color = AppDanger
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 2.dp),
        style = AppTheme.typography.cardTitle,
        color = AppTheme.colors.textSecondary
    )
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = AppTheme.colors.accent,
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun EmptyInstalledThemes() {
    Text(
        text = "尚未安装自定义主题",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        style = AppTheme.typography.body,
        color = AppTheme.colors.textSecondary
    )
}

private val ThemeListItem.operationId: String
    get() = id ?: DEFAULT_THEME_OPERATION_ID

private val ThemeListItem.metadataText: String?
    get() = when (status) {
        ThemeListItemStatus.DEFAULT -> null
        ThemeListItemStatus.INVALID -> "主题文件不完整或已损坏"
        ThemeListItemStatus.VALID -> listOfNotNull(
            author?.takeIf { it.isNotBlank() },
            version?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { null }
    }

private const val DEFAULT_THEME_OPERATION_ID = "builtin.default"

private val THEME_PACKAGE_MIME_TYPES = arrayOf(
    "application/zip",
    "application/octet-stream",
    "*/*"
)
