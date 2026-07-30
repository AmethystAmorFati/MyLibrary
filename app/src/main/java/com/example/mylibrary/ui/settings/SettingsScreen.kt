package com.example.mylibrary.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mylibrary.BuildConfig
import com.example.mylibrary.backup.BackupFileNames
import com.example.mylibrary.ui.components.AppConfirmDialog
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.MainPageHeader
import com.example.mylibrary.ui.components.MainPageLayout
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.BottomContentPadding
import com.example.mylibrary.ui.theme.CardContentPadding
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.SurfaceRole
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal object SettingsPageLayoutPolicy {
    const val hasOwnStatusBarPadding = false
    const val verticallyCentersContent = false
    val contentBottomPadding = BottomContentPadding
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onLayoutSettings: () -> Unit,
    onFieldManagement: () -> Unit,
    onTagManagement: () -> Unit,
    onStatusManagement: () -> Unit,
    onThemeManagement: () -> Unit = {},
    currentThemeName: String = "MyLibrary 默认主题",
    onTrash: () -> Unit = {},
    onExportData: (Uri) -> Unit = {},
    onStartVisualExport: (SettingsExportRequest) -> Unit = {},
    onVisualExportSafRequestConsumed: () -> Unit = {},
    onVisualExportDestinationSelected: (Uri?) -> Unit = {},
    onVisualExportMessageShown: () -> Unit = {},
    onStartReportExport: (ReportExportConfig) -> Unit = {},
    onReportExportSafRequestConsumed: () -> Unit = {},
    onReportExportDestinationSelected: (Uri?) -> Unit = {},
    onReportExportMessageShown: () -> Unit = {},
    onImportFileSelected: (Uri) -> Unit = {},
    onConfirmImport: () -> Unit = {},
    onCancelImport: () -> Unit = {},
    onBackupMessageShown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeDialog by remember { mutableStateOf<SettingsDialogKind?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportFileSelected)
    }
    val backupDestination = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let(onExportData)
    }
    val visualExportDestination = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        onVisualExportDestinationSelected(uri)
    }
    val reportPngDestination = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        onReportExportDestinationSelected(uri)
    }
    val reportPdfDestination = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        onReportExportDestinationSelected(uri)
    }
    LaunchedEffect(state.backupMessage) {
        state.backupMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onBackupMessageShown()
        }
    }
    LaunchedEffect(state.visualExportMessage) {
        state.visualExportMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onVisualExportMessageShown()
        }
    }
    LaunchedEffect(state.visualExportSafRequest?.id) {
        state.visualExportSafRequest?.let { request ->
            visualExportDestination.launch(request.displayName)
            onVisualExportSafRequestConsumed()
        }
    }
    LaunchedEffect(state.reportExportMessage) {
        state.reportExportMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onReportExportMessageShown()
        }
    }
    LaunchedEffect(state.reportExportSafRequest?.id) {
        state.reportExportSafRequest?.let { request ->
            when (request.destination) {
                ReportSafDestination.DIRECTORY ->
                    reportPngDestination.launch(null)
                ReportSafDestination.PDF_DOCUMENT ->
                    reportPdfDestination.launch(request.displayName)
            }
            onReportExportSafRequestConsumed()
        }
    }

    MainPageLayout(
        modifier = modifier.testTag("screen_settings")
    ) {
        MainPageHeader(title = "设置")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsPageLayoutPolicy.contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsGroupCard(title = "外观", tag = "settings_group_appearance") {
                SettingsRow("布局", tag = "settings_layout", onClick = onLayoutSettings)
                GroupDivider()
                SettingsRow(
                    title = "主题",
                    subtitle = currentThemeName,
                    tag = "settings_theme",
                    onClick = onThemeManagement
                )
            }

            SettingsGroupCard(title = "自定义", tag = "settings_group_customization") {
                SettingsRow("状态管理", tag = "settings_statuses", onClick = onStatusManagement)
                GroupDivider()
                SettingsRow("标签管理", tag = "settings_tags", onClick = onTagManagement)
                GroupDivider()
                SettingsRow(
                    "自定义字段",
                    tag = "settings_fields",
                    onClick = onFieldManagement
                )
            }

            SettingsGroupCard(title = "导出", tag = "settings_group_export") {
                SettingsRow(
                    "导出月历页",
                    tag = "settings_export_calendar",
                    enabled = !state.isSettingsOperationBusy
                ) { activeDialog = SettingsDialogKind.EXPORT_CALENDAR_PAGE }
                GroupDivider()
                SettingsRow(
                    "导出年度海报",
                    tag = "settings_export_year_poster",
                    enabled = !state.isSettingsOperationBusy
                ) { activeDialog = SettingsDialogKind.EXPORT_YEAR_POSTER }
                GroupDivider()
                SettingsRow(
                    "导出月度报告",
                    tag = "settings_export_monthly_report",
                    enabled = !state.isSettingsOperationBusy
                ) {
                    activeDialog = SettingsDialogKind.EXPORT_MONTHLY_REPORT
                }
                GroupDivider()
                SettingsRow(
                    "导出年度报告",
                    tag = "settings_export_yearly_report",
                    enabled = !state.isSettingsOperationBusy
                ) {
                    activeDialog = SettingsDialogKind.EXPORT_YEARLY_REPORT
                }
            }

            SettingsGroupCard(title = "数据", tag = "settings_group_data") {
                SettingsRow(
                    "数据导出／备份",
                    tag = "settings_export_data",
                    enabled = !state.isSettingsOperationBusy
                ) {
                    backupDestination.launch(BackupFileNames.defaultName())
                }
                GroupDivider()
                SettingsRow(
                    "数据导入／恢复",
                    tag = "settings_import_data",
                    enabled = !state.isSettingsOperationBusy
                ) {
                    filePicker.launch(arrayOf("application/zip", "application/octet-stream"))
                }
                GroupDivider()
                SettingsRow(
                    "回收站",
                    tag = "settings_trash",
                    onClick = onTrash
                )
            }

            SettingsGroupCard(title = "关于", tag = "settings_group_about") {
                SettingsRow("关于 MyLibrary", tag = "settings_about") {
                    showAbout = true
                }
            }

            state.errorMessage?.let {
                Text(
                    text = it,
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.mutedText
                )
            }
        }
    }

    activeDialog?.let { dialog ->
        SettingsActionDialog(
            kind = dialog,
            state = state,
            onDismiss = { activeDialog = null },
            onChooseImportFile = {
                filePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
            onConfirmAction = { request ->
                when (request) {
                    is SettingsExportRequest.CalendarPage,
                    is SettingsExportRequest.YearPoster ->
                        onStartVisualExport(request)
                    is SettingsExportRequest.Report ->
                        onStartReportExport(request.config)
                    SettingsExportRequest.FullBackup -> Unit
                }
            }
        )
    }

    state.importPreview?.let { preview ->
        AppConfirmDialog(
            title = "导入数据",
            message = buildString {
                appendLine("备份时间：${formatBackupTime(preview.createdAt)}")
                appendLine("作品：${preview.itemCount}")
                appendLine("摘录：${preview.quoteCount}")
                appendLine()
                appendLine("导入将替换当前全部数据，无法撤销。")
                append("建议先导出当前数据。")
            },
            confirmText = "导入",
            dismissText = "取消",
            onConfirm = onConfirmImport,
            onDismiss = onCancelImport,
            destructive = true,
            confirmTestTag = "settings_confirm_import"
        )
    }

    state.backupOperation?.let { operation ->
        BackupLoadingDialog(operation)
    }
    state.visualExportOperation
        ?.takeIf { it != SettingsVisualExportOperation.WAITING_FOR_DESTINATION }
        ?.let { operation ->
            VisualExportLoadingDialog(operation)
        }
    state.reportExportOperation
        ?.takeIf { it != SettingsReportExportOperation.WAITING_FOR_DESTINATION }
        ?.let { operation ->
            ReportExportLoadingDialog(operation)
        }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenLink = { url ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    )
                }.onFailure {
                    Toast.makeText(
                        context,
                        "无法打开链接",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 2.dp),
            style = AppTheme.typography.cardTitle,
            color = AppTheme.colors.textSecondary
        )
        AppThemeSurface(
            role = SurfaceRole.CARD,
            modifier = Modifier.fillMaxWidth(),
            shape = LibraryShapes.medium,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    tag: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .padding(CardContentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = AppTheme.typography.body,
                color = AppTheme.colors.textPrimary
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = AppTheme.colors.mutedText
        )
    }
}

@Composable
private fun BackupLoadingDialog(operation: SettingsBackupOperation) {
    Dialog(onDismissRequest = {}) {
        AppThemeSurface(
            role = SurfaceRole.DIALOG,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .testTag("settings_backup_loading"),
            shape = LibraryShapes.large,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AppTheme.colors.accent,
                    strokeWidth = 2.dp
                )
                Text(
                    text = when (operation) {
                        SettingsBackupOperation.VALIDATING -> "正在校验备份…"
                        SettingsBackupOperation.EXPORTING -> "正在导出数据…"
                        SettingsBackupOperation.IMPORTING -> "正在导入数据…"
                    },
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun VisualExportLoadingDialog(operation: SettingsVisualExportOperation) {
    Dialog(onDismissRequest = {}) {
        AppThemeSurface(
            role = SurfaceRole.DIALOG,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .testTag("settings_visual_export_loading"),
            shape = LibraryShapes.large,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AppTheme.colors.accent,
                    strokeWidth = 2.dp
                )
                Text(
                    text = when (operation) {
                        SettingsVisualExportOperation.GENERATING -> "正在生成…"
                        SettingsVisualExportOperation.SAVING -> "正在保存…"
                        SettingsVisualExportOperation.WAITING_FOR_DESTINATION ->
                            "正在等待保存位置…"
                    },
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun ReportExportLoadingDialog(operation: SettingsReportExportOperation) {
    Dialog(onDismissRequest = {}) {
        AppThemeSurface(
            role = SurfaceRole.DIALOG,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .testTag("settings_report_export_loading"),
            shape = LibraryShapes.large,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AppTheme.colors.accent,
                    strokeWidth = 2.dp
                )
                Text(
                    text = when (operation) {
                        SettingsReportExportOperation.RESOLVING_AND_RENDERING ->
                            "正在生成报告…"
                        SettingsReportExportOperation.SAVING -> "正在保存…"
                        SettingsReportExportOperation.WAITING_FOR_DESTINATION ->
                            "正在等待保存位置…"
                    },
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textPrimary
                )
            }
        }
    }
}

private fun formatBackupTime(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
}.getOrDefault(value)

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = AppTheme.colors.subtleBorder)
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        AppThemeSurface(
            role = SurfaceRole.DIALOG,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .testTag("settings_about_dialog"),
            shape = LibraryShapes.large,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MyLibrary",
                    style = AppTheme.typography.pageTitle,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = "私人文化档案库\n所有数据保存在本地设备中。",
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textSecondary
                )
                Text(
                    text = "版本：${BuildConfig.VERSION_NAME}",
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
                AboutEntry(label = "Author", value = "PeanutPersimmon")
                AboutEntry(
                    label = "GitHub",
                    value = "PeanutPersimmon",
                    tag = "settings_about_github",
                    onClick = { onOpenLink(SETTINGS_AUTHOR_URL) }
                )
                AboutEntry(
                    label = "Repository",
                    value = "MyLibrary",
                    tag = "settings_about_repository",
                    onClick = { onOpenLink(SETTINGS_REPOSITORY_URL) }
                )
                AboutEntry(label = "Development", value = "OpenAI Codex")
                Text(
                    text = "OpenAI Codex 用于代码生成、重构建议、测试与文档辅助。" +
                        "产品方向、设计决策、代码审核与最终实现责任由 " +
                        "PeanutPersimmon 负责。",
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "关闭",
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 10.dp)
                            .noRippleClickable(onClick = onDismiss),
                        style = AppTheme.typography.button,
                        color = AppTheme.colors.accent
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutEntry(
    label: String,
    value: String,
    tag: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.noRippleClickable(onClick = onClick)
                }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = AppTheme.typography.metadata,
            color = AppTheme.colors.textSecondary
        )
        Text(
            text = value,
            style = AppTheme.typography.body,
            color = if (onClick == null) {
                AppTheme.colors.textPrimary
            } else {
                AppTheme.colors.accent
            }
        )
    }
}

internal const val SETTINGS_AUTHOR_URL = "https://github.com/PeanutPersimmon"
internal const val SETTINGS_REPOSITORY_URL =
    "https://github.com/PeanutPersimmon/MyLibrary"
