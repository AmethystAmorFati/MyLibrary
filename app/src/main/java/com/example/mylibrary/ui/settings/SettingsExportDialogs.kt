package com.example.mylibrary.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.compatibleWith
import com.example.mylibrary.ui.components.AppWheelPicker
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.LibraryShapes
import com.example.mylibrary.ui.theme.SurfaceRole
import java.time.LocalDate

enum class SettingsDialogKind {
    IMPORT_DATA,
    EXPORT_DATA,
    EXPORT_CALENDAR_PAGE,
    EXPORT_YEAR_POSTER,
    EXPORT_MONTHLY_REPORT,
    EXPORT_YEARLY_REPORT
}

@Composable
fun SettingsActionDialog(
    kind: SettingsDialogKind,
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onChooseImportFile: () -> Unit,
    onConfirmAction: (SettingsExportRequest) -> Unit
) {
    val now = LocalDate.now()
    var year by remember(kind) { mutableIntStateOf(now.year) }
    var month by remember(kind) { mutableIntStateOf(now.monthValue) }
    var selectedTypeId by remember(kind) { mutableStateOf<Long?>(null) }
    var selectedStatistics by remember(kind) {
        mutableStateOf(DEFAULT_REPORT_STATISTICS)
    }
    var selectedWorkFields by remember(kind) {
        mutableStateOf(DEFAULT_REPORT_WORK_FIELDS)
    }
    var includeAllStatuses by remember(kind) { mutableStateOf(true) }
    var selectedStatusIds by remember(kind) { mutableStateOf(emptySet<Long>()) }
    var selectedWorkCustomFieldIds by remember(kind) {
        mutableStateOf(emptySet<Long>())
    }
    var selectedStatisticSelections by remember(kind) {
        mutableStateOf(emptySet<ReportStatisticSelection>())
    }
    var includeQuotes by remember(kind) { mutableStateOf(false) }
    var configMessage by remember(kind) { mutableStateOf<String?>(null) }

    val title = when (kind) {
        SettingsDialogKind.IMPORT_DATA -> "导入数据"
        SettingsDialogKind.EXPORT_DATA -> "导出数据"
        SettingsDialogKind.EXPORT_CALENDAR_PAGE -> "导出月历页"
        SettingsDialogKind.EXPORT_YEAR_POSTER -> "导出年度海报"
        SettingsDialogKind.EXPORT_MONTHLY_REPORT -> "导出月度报告"
        SettingsDialogKind.EXPORT_YEARLY_REPORT -> "导出年度报告"
    }
    val confirm = if (kind == SettingsDialogKind.IMPORT_DATA) "选择文件" else "导出"

    ConfigDialog(
        title = title,
        tag = "settings_dialog_${kind.name.lowercase()}",
        confirmText = confirm,
        onDismiss = onDismiss,
        onConfirm = confirm@{
            if (kind == SettingsDialogKind.IMPORT_DATA) {
                onChooseImportFile()
                onDismiss()
            } else {
                val request = when (kind) {
                    SettingsDialogKind.IMPORT_DATA -> null
                    SettingsDialogKind.EXPORT_DATA -> SettingsExportRequest.FullBackup
                    SettingsDialogKind.EXPORT_CALENDAR_PAGE ->
                        SettingsExportRequest.CalendarPage(year, month)
                    SettingsDialogKind.EXPORT_YEAR_POSTER ->
                        SettingsExportRequest.YearPoster(year, selectedTypeId)
                    SettingsDialogKind.EXPORT_MONTHLY_REPORT,
                    SettingsDialogKind.EXPORT_YEARLY_REPORT -> {
                        val cleaned = validReportSelections(
                            workCustomFieldIds = selectedWorkCustomFieldIds,
                            statisticSelections = selectedStatisticSelections,
                            fields = state.dynamicFields,
                            typeId = selectedTypeId
                        )
                        val reportConfig = ReportExportConfig(
                            year = year,
                            month = month.takeIf {
                                kind == SettingsDialogKind.EXPORT_MONTHLY_REPORT
                            },
                            typeId = selectedTypeId,
                            statistics = selectedStatistics,
                            workFields = selectedWorkFields,
                            includeAllStatuses = includeAllStatuses,
                            statusIds = selectedStatusIds,
                            workCustomFieldIds = cleaned.workCustomFieldIds,
                            statisticSelections = cleaned.statisticSelections,
                            includeQuotes = includeQuotes
                        )
                        if (!reportConfig.hasContent()) {
                            configMessage = EMPTY_REPORT_CONTENT_MESSAGE
                            return@confirm
                        }
                        SettingsExportRequest.Report(reportConfig)
                    }
                }
                request?.let(onConfirmAction)
                onDismiss()
            }
        }
    ) {
        when (kind) {
            SettingsDialogKind.IMPORT_DATA -> Text(
                text = "导入会覆盖当前数据，请先确认已备份。",
                style = AppTheme.typography.body,
                color = AppTheme.colors.textSecondary
            )
            SettingsDialogKind.EXPORT_DATA -> Text(
                text = "将导出完整备份包，包括数据库、封面图片和设置。",
                style = AppTheme.typography.body,
                color = AppTheme.colors.textSecondary
            )
            SettingsDialogKind.EXPORT_CALENDAR_PAGE -> {
                YearMonthOptions(
                    year = year,
                    month = month,
                    showMonth = true,
                    onYearSelected = { year = it },
                    onMonthSelected = { month = it }
                )
            }
            SettingsDialogKind.EXPORT_YEAR_POSTER -> {
                YearMonthOptions(
                    year = year,
                    month = month,
                    showMonth = false,
                    onYearSelected = { year = it },
                    onMonthSelected = { month = it }
                )
                Spacer(Modifier.height(12.dp))
                CategoryOptions(
                    types = state.types,
                    selectedTypeId = selectedTypeId,
                    onSelected = { selectedTypeId = it }
                )
            }
            SettingsDialogKind.EXPORT_MONTHLY_REPORT,
            SettingsDialogKind.EXPORT_YEARLY_REPORT -> {
                YearMonthOptions(
                    year = year,
                    month = month,
                    showMonth = kind == SettingsDialogKind.EXPORT_MONTHLY_REPORT,
                    onYearSelected = { year = it },
                    onMonthSelected = { month = it }
                )
                Spacer(Modifier.height(12.dp))
                CategoryOptions(
                    types = state.types,
                    selectedTypeId = selectedTypeId,
                    onSelected = { nextTypeId ->
                        selectedTypeId = nextTypeId
                        val cleaned = validReportSelections(
                            workCustomFieldIds = selectedWorkCustomFieldIds,
                            statisticSelections = selectedStatisticSelections,
                            fields = state.dynamicFields,
                            typeId = nextTypeId
                        )
                        selectedWorkCustomFieldIds = cleaned.workCustomFieldIds
                        selectedStatisticSelections = cleaned.statisticSelections
                        configMessage = null
                    }
                )
                ReportOptions(
                    statuses = state.statuses,
                    types = state.types,
                    fields = state.dynamicFields,
                    selectedTypeId = selectedTypeId,
                    selectedStatistics = selectedStatistics,
                    onStatisticsChanged = { selectedStatistics = it },
                    selectedWorkFields = selectedWorkFields,
                    onWorkFieldsChanged = { selectedWorkFields = it },
                    includeAllStatuses = includeAllStatuses,
                    onIncludeAllStatusesChanged = { includeAllStatuses = it },
                    selectedStatusIds = selectedStatusIds,
                    onStatusIdsChanged = { selectedStatusIds = it },
                    selectedWorkCustomFieldIds = selectedWorkCustomFieldIds,
                    onWorkCustomFieldIdsChanged = {
                        selectedWorkCustomFieldIds = it
                        configMessage = null
                    },
                    selectedStatisticSelections = selectedStatisticSelections,
                    onStatisticSelectionsChanged = {
                        selectedStatisticSelections = it
                        configMessage = null
                    },
                    includeQuotes = includeQuotes,
                    onIncludeQuotesChanged = { includeQuotes = it },
                    onMessage = { configMessage = it }
                )
                configMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.testTag("export_config_message"),
                        style = AppTheme.typography.metadata,
                        color = AppTheme.colors.mutedText
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigDialog(
    title: String,
    tag: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        AppThemeSurface(
            role = SurfaceRole.DIALOG,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 560.dp)
                .testTag(tag),
            shape = LibraryShapes.large,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 22.dp,
                    top = 22.dp,
                    end = 22.dp,
                    bottom = 16.dp
                )
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.pageTitle,
                    color = AppTheme.colors.textPrimary
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = AppTheme.colors.subtleBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogTextButton("取消", onDismiss)
                    Spacer(Modifier.size(18.dp))
                    DialogTextButton(confirmText, onConfirm, accent = true)
                }
            }
        }
    }
}

@Composable
private fun YearMonthOptions(
    year: Int,
    month: Int,
    showMonth: Boolean,
    onYearSelected: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit
) {
    val currentYear = LocalDate.now().year
    ConfigRow("年份", tag = "export_year") {
        AppWheelPicker(
            values = ((currentYear - 20)..(currentYear + 5)).toList(),
            selectedValue = year,
            onValueSelected = onYearSelected,
            formatter = Int::toString,
            cyclic = false
        )
    }
    if (showMonth) {
        ConfigRow("月份", tag = "export_month") {
            AppWheelPicker(
                values = (1..12).toList(),
                selectedValue = month,
                onValueSelected = onMonthSelected,
                formatter = { it.toString().padStart(2, '0') },
                cyclic = true
            )
        }
    }
}

@Composable
private fun CategoryOptions(
    types: List<ItemType>,
    selectedTypeId: Long?,
    onSelected: (Long?) -> Unit
) {
    OptionGroup("类别", tag = "export_group_category") {
        SquareOptionRow(
            text = "全部",
            selected = selectedTypeId == null,
            tag = "export_category_all",
            onClick = { onSelected(null) }
        )
        types.forEach { type ->
            SquareOptionRow(
                text = type.name,
                selected = selectedTypeId == type.id,
                tag = "export_category_${type.id}",
                onClick = { onSelected(type.id) }
            )
        }
    }
}

@Composable
private fun ReportOptions(
    statuses: List<LibraryStatus>,
    types: List<ItemType>,
    fields: List<DynamicFieldDefinition>,
    selectedTypeId: Long?,
    selectedStatistics: Set<ReportStatisticOption>,
    onStatisticsChanged: (Set<ReportStatisticOption>) -> Unit,
    selectedWorkFields: Set<ReportWorkOption>,
    onWorkFieldsChanged: (Set<ReportWorkOption>) -> Unit,
    includeAllStatuses: Boolean,
    onIncludeAllStatusesChanged: (Boolean) -> Unit,
    selectedStatusIds: Set<Long>,
    onStatusIdsChanged: (Set<Long>) -> Unit,
    selectedWorkCustomFieldIds: Set<Long>,
    onWorkCustomFieldIdsChanged: (Set<Long>) -> Unit,
    selectedStatisticSelections: Set<ReportStatisticSelection>,
    onStatisticSelectionsChanged: (Set<ReportStatisticSelection>) -> Unit,
    includeQuotes: Boolean,
    onIncludeQuotesChanged: (Boolean) -> Unit,
    onMessage: (String) -> Unit
) {
    OptionGroup("基础统计", tag = "export_group_statistics") {
        MultiOption(
            text = "阅读 / 观看数量",
            value = ReportStatisticOption.ITEM_COUNT,
            selected = selectedStatistics,
            onChanged = onStatisticsChanged
        )
        MultiOption(
            text = "记录数量",
            value = ReportStatisticOption.RECORD_COUNT,
            selected = selectedStatistics,
            onChanged = onStatisticsChanged
        )
        MultiOption(
            text = "阅读 / 观看天数",
            value = ReportStatisticOption.ACTIVITY_DAYS,
            selected = selectedStatistics,
            onChanged = onStatisticsChanged
        )
        MultiOption(
            text = "标签统计",
            value = ReportStatisticOption.TAGS,
            selected = selectedStatistics,
            onChanged = onStatisticsChanged
        )
        MultiOption(
            text = "作者统计",
            value = ReportStatisticOption.CREATORS,
            selected = selectedStatistics,
            onChanged = onStatisticsChanged
        )
        MultiOption(
            text = "阅读 / 观看天数前三",
            value = ReportStatisticOption.TOP_ACTIVITY_DAYS,
            selected = selectedStatistics,
            onChanged = onStatisticsChanged
        )
        MultiOption(
            text = "摘录数量",
            value = ReportStatisticOption.QUOTE_COUNT,
            selected = selectedStatistics,
            onChanged = onStatisticsChanged
        )
    }
    OptionGroup("自定义字段统计", tag = "export_group_field_statistics") {
        val statisticFields = availableReportStatisticFields(fields, selectedTypeId)
        ReportFieldGroups(
            types = types,
            fields = statisticFields,
            showTypeTitle = selectedTypeId == null
        ) { field ->
            Text(
                text = field.reportLabel(),
                style = AppTheme.typography.body,
                color = AppTheme.colors.textPrimary
            )
            field.aggregations.compatibleWith(field.dataType)
                .sortedBy(FieldAggregation::reportOrder)
                .forEach { aggregation ->
                    val selection = ReportStatisticSelection(field.id, aggregation)
                    MultiOption(
                        text = aggregation.displayName(),
                        value = selection,
                        selected = selectedStatisticSelections,
                        tag = "export_statistic_${field.id}_${aggregation.name}",
                        onChanged = onStatisticSelectionsChanged
                    )
                }
        }
    }
    OptionGroup("作品信息", tag = "export_group_works") {
        MultiOption(
            text = "封面",
            value = ReportWorkOption.COVER,
            selected = selectedWorkFields,
            onChanged = onWorkFieldsChanged
        )
        MultiOption(
            text = "书名 / 片名",
            value = ReportWorkOption.TITLE,
            selected = selectedWorkFields,
            onChanged = onWorkFieldsChanged
        )
        MultiOption(
            text = "作者 / 导演",
            value = ReportWorkOption.CREATOR,
            selected = selectedWorkFields,
            onChanged = onWorkFieldsChanged
        )
        MultiOption(
            text = "状态",
            value = ReportWorkOption.STATUS,
            selected = selectedWorkFields,
            onChanged = onWorkFieldsChanged
        )
        MultiOption(
            text = "标签",
            value = ReportWorkOption.TAGS,
            selected = selectedWorkFields,
            onChanged = onWorkFieldsChanged
        )
    }
    OptionGroup("作品自定义字段", tag = "export_group_work_fields") {
        val workFields = availableReportWorkFields(fields, selectedTypeId)
        ReportFieldGroups(
            types = types,
            fields = workFields,
            showTypeTitle = selectedTypeId == null
        ) { field ->
            SquareOptionRow(
                text = field.reportLabel(),
                selected = field.id in selectedWorkCustomFieldIds,
                tag = "export_work_field_${field.id}",
                onClick = {
                    when (
                        val result = toggleWorkCustomField(
                            selectedWorkCustomFieldIds,
                            field.id
                        )
                    ) {
                        is WorkCustomFieldToggleResult.Updated ->
                            onWorkCustomFieldIdsChanged(result.selectedIds)
                        is WorkCustomFieldToggleResult.Rejected ->
                            onMessage(result.message)
                    }
                }
            )
        }
    }
    OptionGroup("状态", tag = "export_group_statuses") {
        SquareOptionRow(
            text = "所有状态统计",
            selected = includeAllStatuses,
            tag = "export_option_all_statuses",
            onClick = { onIncludeAllStatusesChanged(!includeAllStatuses) }
        )
        statuses.forEach { status ->
            MultiOption(
                text = status.name,
                value = status.id,
                selected = selectedStatusIds,
                tag = "export_status_${status.id}",
                onChanged = onStatusIdsChanged
            )
        }
    }
    OptionGroup("摘录", tag = "export_group_quotes") {
        SquareOptionRow(
            text = "包含摘录",
            selected = includeQuotes,
            tag = "export_option_quotes",
            onClick = { onIncludeQuotesChanged(!includeQuotes) }
        )
    }
}

@Composable
private fun ColumnScope.ReportFieldGroups(
    types: List<ItemType>,
    fields: List<DynamicFieldDefinition>,
    showTypeTitle: Boolean,
    content: @Composable ColumnScope.(DynamicFieldDefinition) -> Unit
) {
    if (fields.isEmpty()) {
        Text(
            text = "暂无可选字段",
            style = AppTheme.typography.metadata,
            color = AppTheme.colors.mutedText
        )
        return
    }
    types.sortedWith(compareBy<ItemType> { it.sortOrder }.thenBy { it.id })
        .forEach { type ->
            val typeFields = fields.filter { it.typeId == type.id }
            if (typeFields.isEmpty()) return@forEach
            if (showTypeTitle) {
                Text(
                    text = type.name,
                    modifier = Modifier.padding(top = 6.dp),
                    style = AppTheme.typography.cardTitle,
                    color = AppTheme.colors.textSecondary
                )
            }
            typeFields.forEach { field -> content(field) }
        }
}

private fun DynamicFieldDefinition.reportLabel(): String =
    unit?.takeIf(String::isNotBlank)?.let { "$name（$it）" } ?: name

private fun FieldAggregation.displayName(): String = when (this) {
    FieldAggregation.SUM -> "总和"
    FieldAggregation.AVERAGE -> "平均值"
    FieldAggregation.MAXIMUM -> "最大值"
    FieldAggregation.MINIMUM -> "最小值"
    FieldAggregation.OPTION_DISTRIBUTION -> "选项分布"
    FieldAggregation.RATING_AVERAGE -> "平均评分"
    FieldAggregation.RATING_DISTRIBUTION -> "评分分布"
}

@Composable
private fun <T> MultiOption(
    text: String,
    value: T,
    selected: Set<T>,
    tag: String? = null,
    onChanged: (Set<T>) -> Unit
) {
    SquareOptionRow(
        text = text,
        selected = value in selected,
        tag = tag,
        onClick = {
            onChanged(if (value in selected) selected - value else selected + value)
        }
    )
}

@Composable
private fun OptionGroup(
    title: String,
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = AppTheme.typography.cardTitle,
            color = AppTheme.colors.textSecondary
        )
        Column(content = content)
    }
}

@Composable
private fun ConfigRow(
    title: String,
    tag: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
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
private fun SquareOptionRow(
    text: String,
    selected: Boolean,
    tag: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .clip(RoundedCornerShape(6.dp))
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val colors = AppTheme.colors
        Box(
            modifier = Modifier
                .size(15.dp)
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(
                            BorderStroke(1.dp, colors.border),
                            RoundedCornerShape(3.dp)
                        )
                    }
                )
        ) {
            if (selected) {
                Surface(
                    modifier = Modifier.size(15.dp),
                    shape = RoundedCornerShape(3.dp),
                    color = colors.accent
                ) {}
            }
        }
        Text(
            text = text,
            modifier = Modifier.padding(start = 10.dp),
            style = AppTheme.typography.body,
            color = if (enabled) colors.textPrimary else colors.mutedText
        )
    }
}

@Composable
private fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    accent: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .noRippleClickable(onClick = onClick),
        style = AppTheme.typography.button,
        color = if (accent) AppTheme.colors.accent else AppTheme.colors.textSecondary
    )
}
