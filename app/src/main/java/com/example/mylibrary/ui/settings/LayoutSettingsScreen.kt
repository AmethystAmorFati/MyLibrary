package com.example.mylibrary.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryDisplayFieldKey
import com.example.mylibrary.ui.components.AppCapsule
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.AppInlineSwitch
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.SecondaryPageHeader
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.library.ListFieldConfigSheet
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.CardContentPadding
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarToContentGap

@Composable
fun LayoutSettingsScreen(
    state: LayoutSettingsUiState,
    onBack: () -> Unit,
    onTimelineCreatorChanged: (Boolean) -> Unit,
    onTimelineRatingChanged: (Boolean) -> Unit,
    onTimelineStatusChanged: (Boolean) -> Unit = {},
    onTimelineDurationChanged: (Boolean) -> Unit = {},
    onLibraryTotalDurationChanged: (Boolean) -> Unit = {},
    onQuoteChapterChanged: (Boolean) -> Unit = {},
    onQuotePageChanged: (Boolean) -> Unit = {},
    onGridColumnsChanged: (Int) -> Unit,
    onCoverColumnsChanged: (Int) -> Unit,
    onListStatusChanged: (Boolean) -> Unit,
    onListTagsChanged: (Boolean) -> Unit,
    onListFieldsChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFieldSheet by remember { mutableStateOf(false) }
    val preferences = state.preferences
    val fields = preferences.listDisplayFields

    AppScreenContainer(modifier = modifier.testTag("screen_layout_settings")) {
        Column(Modifier.fillMaxSize()) {
            SecondaryPageHeader(title = "布局", onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ScreenHorizontalPadding,
                        end = ScreenHorizontalPadding,
                        top = TopBarToContentGap,
                        bottom = 40.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsSectionTitle("首页时间轴")
                SettingsGroup(tag = "layout_group_timeline") {
                    ToggleRow(
                        title = "显示作者 / 导演",
                        tag = "layout_timeline_creator",
                        checked = preferences.timelineShowCreator,
                        onCheckedChange = onTimelineCreatorChanged
                    )
                    GroupDivider()
                    ToggleRow(
                        title = "显示评分",
                        tag = "layout_timeline_rating",
                        checked = preferences.timelineShowRating,
                        onCheckedChange = onTimelineRatingChanged
                    )
                    GroupDivider()
                    ToggleRow(
                        title = "显示记录状态",
                        tag = "layout_timeline_status",
                        checked = preferences.timelineShowStatus,
                        onCheckedChange = onTimelineStatusChanged
                    )
                    GroupDivider()
                    ToggleRow(
                        title = "首页显示记录时长",
                        tag = "layout_timeline_duration",
                        checked = preferences.timelineShowDuration,
                        onCheckedChange = onTimelineDurationChanged
                    )
                }

                SettingsSectionTitle("资料库")
                SettingsGroup(tag = "layout_group_library") {
                    ColumnOptionsRow(
                        title = "网格列数",
                        tag = "layout_grid_columns",
                        selected = preferences.gridColumns,
                        onSelected = onGridColumnsChanged
                    )
                    GroupDivider()
                    ColumnOptionsRow(
                        title = "纯图列数",
                        tag = "layout_cover_columns",
                        selected = preferences.coverColumns,
                        onSelected = onCoverColumnsChanged
                    )
                    GroupDivider()
                    ToggleRow(
                        title = "资料库显示累计时长",
                        tag = "layout_library_total_duration",
                        checked = preferences.libraryShowTotalDuration,
                        onCheckedChange = onLibraryTotalDurationChanged
                    )
                }

                SettingsSectionTitle("摘录设置")
                SettingsGroup(tag = "layout_group_quotes") {
                    ToggleRow(
                        title = "章节",
                        tag = "layout_quote_chapter",
                        checked = preferences.showQuoteChapter,
                        onCheckedChange = onQuoteChapterChanged
                    )
                    GroupDivider()
                    ToggleRow(
                        title = "页码",
                        tag = "layout_quote_page",
                        checked = preferences.showQuotePage,
                        onCheckedChange = onQuotePageChanged
                    )
                }

                SettingsSectionTitle("列表卡片")
                SettingsGroup(tag = "layout_group_list") {
                    ToggleRow(
                        title = "状态",
                        tag = "layout_list_status",
                        checked = LibraryDisplayFieldKey.CURRENT_STATUS in fields,
                        onCheckedChange = onListStatusChanged
                    )
                    GroupDivider()
                    ToggleRow(
                        title = "标签",
                        tag = "layout_list_tags",
                        checked = LibraryDisplayFieldKey.TAGS in fields,
                        onCheckedChange = onListTagsChanged
                    )
                    GroupDivider()
                    NavigationRow(
                        title = "自定义字段",
                        tag = "layout_list_fields",
                        value = selectedDynamicCount(fields).takeIf { it > 0 }?.toString()
                            ?: ">"
                    ) {
                        showFieldSheet = true
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
    }

    if (showFieldSheet) {
        ListFieldConfigSheet(
            selectedFields = preferences.listDisplayFields,
            dynamicFields = state.dynamicFields,
            onSave = onListFieldsChanged,
            onDismiss = { showFieldSheet = false },
            dynamicFieldsOnly = true
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 2.dp),
        style = AppTheme.typography.cardTitle,
        color = AppTheme.colors.textSecondary
    )
}

@Composable
private fun SettingsGroup(
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = LibraryShapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    tag: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingRow(
        title = title,
        tag = tag,
        modifier = Modifier.noRippleClickable {
            onCheckedChange(!checked)
        }
    ) {
        AppInlineSwitch(
            checked = checked,
            modifier = Modifier.testTag("${tag}_toggle"),
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ColumnOptionsRow(
    title: String,
    tag: String,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    SettingRow(title = title, tag = tag) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (2..6).forEach { value ->
                AppCapsule(
                    text = value.toString(),
                    selected = selected == value,
                    modifier = Modifier.testTag("${tag}_$value"),
                    onClick = { onSelected(value) }
                )
            }
        }
    }
}

@Composable
private fun NavigationRow(
    title: String,
    tag: String,
    value: String,
    onClick: () -> Unit
) {
    SettingRow(
        title = title,
        tag = tag,
        modifier = Modifier.noRippleClickable(onClick = onClick)
    ) {
        Text(
            text = value,
            style = AppTheme.typography.body,
            color = AppTheme.colors.textSecondary
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    tag: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag)
            .padding(CardContentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = AppTheme.typography.body,
            color = AppTheme.colors.textPrimary
        )
        trailing()
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(color = AppTheme.colors.subtleBorder)
}

private fun selectedDynamicCount(fields: Set<String>): Int =
    fields.count { it.startsWith(LibraryDisplayFieldKey.DYNAMIC_PREFIX) }
