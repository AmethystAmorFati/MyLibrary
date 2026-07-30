package com.example.mylibrary.ui.settings

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.compatibleWith
import com.example.mylibrary.export.visual.AnnualPosterCategory
import com.example.mylibrary.export.visual.VisualExportRequest
import java.time.LocalDate

sealed interface SettingsExportRequest {
    data object FullBackup : SettingsExportRequest

    data class CalendarPage(
        val year: Int,
        val month: Int
    ) : SettingsExportRequest

    data class YearPoster(
        val year: Int,
        val category: AnnualPosterCategory
    ) : SettingsExportRequest

    data class Report(
        val config: ReportExportConfig
    ) : SettingsExportRequest
}

data class VisualExportDialogDefaults(
    val year: Int,
    val month: Int,
    val annualCategory: AnnualPosterCategory
)

fun defaultVisualExportDialogValues(
    today: LocalDate = LocalDate.now()
): VisualExportDialogDefaults = VisualExportDialogDefaults(
    year = today.year,
    month = today.monthValue,
    annualCategory = AnnualPosterCategory.ALL
)

fun SettingsExportRequest.toVisualExportRequestOrNull(): VisualExportRequest? =
    when (this) {
        is SettingsExportRequest.CalendarPage ->
            VisualExportRequest.Calendar(year, month)
        is SettingsExportRequest.YearPoster ->
            VisualExportRequest.AnnualPoster(year, category)
        SettingsExportRequest.FullBackup,
        is SettingsExportRequest.Report -> null
    }

data class ReportStatisticSelection(
    val fieldId: Long,
    val aggregation: FieldAggregation
)

data class ReportExportConfig(
    val year: Int,
    val month: Int?,
    val typeId: Long?,
    val statistics: Set<ReportStatisticOption> = DEFAULT_REPORT_STATISTICS,
    val workFields: Set<ReportWorkOption> = DEFAULT_REPORT_WORK_FIELDS,
    val includeAllStatuses: Boolean = true,
    val statusIds: Set<Long> = emptySet(),
    val workCustomFieldIds: Set<Long> = emptySet(),
    val statisticSelections: Set<ReportStatisticSelection> = emptySet(),
    val includeQuotes: Boolean = false,
    val showcaseStyle: ReportShowcaseStyle = ReportShowcaseStyle.COLLAGE,
    val outputFormat: ReportOutputFormat = ReportOutputFormat.PDF,
    val includeBasicStatistics: Boolean = statistics.any {
        it in BASIC_REPORT_STATISTICS
    },
    val includeTagStatistics: Boolean = ReportStatisticOption.TAGS in statistics,
    val includeFieldStatistics: Boolean = statisticSelections.isNotEmpty(),
    val includeItemInformation: Boolean = workFields.isNotEmpty() ||
        workCustomFieldIds.isNotEmpty(),
    val includeItemFields: Boolean = workCustomFieldIds.isNotEmpty(),
    val includeItemStatusStatistics: Boolean =
        includeAllStatuses || statusIds.isNotEmpty()
) {
    fun hasContent(): Boolean =
        month == null ||
            includeBasicStatistics ||
            includeTagStatistics ||
            includeFieldStatistics ||
            includeItemInformation ||
            includeItemFields ||
            includeItemStatusStatistics ||
            includeQuotes

    fun normalized(): ReportExportConfig {
        val itemFieldsEnabled = includeItemFields && workCustomFieldIds.isNotEmpty()
        return copy(
            workCustomFieldIds = if (itemFieldsEnabled) {
                workCustomFieldIds
            } else {
                emptySet()
            },
            includeItemInformation = includeItemInformation && workFields.isNotEmpty(),
            includeItemFields = itemFieldsEnabled
        )
    }
}

enum class ReportShowcaseStyle {
    COLLAGE,
    GRID
}

enum class ReportOutputFormat {
    PNG,
    PDF
}

enum class ReportStatisticOption {
    ITEM_COUNT,
    RECORD_COUNT,
    ACTIVITY_DAYS,
    QUOTE_COUNT,
    TAGS,
    CREATORS,
    TOP_ACTIVITY_DAYS
}

enum class ReportWorkOption {
    COVER,
    TITLE,
    CREATOR,
    STATUS,
    TAGS
}

internal val BASIC_REPORT_STATISTICS = setOf(
    ReportStatisticOption.ITEM_COUNT,
    ReportStatisticOption.RECORD_COUNT,
    ReportStatisticOption.QUOTE_COUNT
)

internal val DEFAULT_REPORT_STATISTICS = BASIC_REPORT_STATISTICS

internal val DEFAULT_REPORT_WORK_FIELDS =
    setOf(ReportWorkOption.COVER, ReportWorkOption.TITLE, ReportWorkOption.CREATOR)

internal const val MAX_REPORT_WORK_CUSTOM_FIELDS = 3
internal const val REPORT_WORK_FIELD_LIMIT_MESSAGE = "作品自定义字段最多选择 3 个"
internal const val EMPTY_REPORT_CONTENT_MESSAGE = "请至少选择一项报告内容"

internal fun fieldsForExportCategory(
    fields: List<DynamicFieldDefinition>,
    typeId: Long?
): List<DynamicFieldDefinition> =
    typeId?.let { selected -> fields.filter { it.typeId == selected } } ?: fields

internal fun availableReportWorkFields(
    fields: List<DynamicFieldDefinition>,
    typeId: Long?
): List<DynamicFieldDefinition> =
    fieldsForExportCategory(fields, typeId)
        .filter { field ->
            field.enabled && !field.isFixed && field.scope == FieldScope.ITEM
        }
        .sortedWith(reportFieldOrder())

internal fun availableReportStatisticFields(
    fields: List<DynamicFieldDefinition>,
    typeId: Long?
): List<DynamicFieldDefinition> =
    fieldsForExportCategory(fields, typeId)
        .filter { field ->
            field.enabled &&
                !field.isFixed &&
                field.scope == FieldScope.ITEM &&
                field.aggregations.compatibleWith(field.dataType).isNotEmpty()
        }
        .sortedWith(reportFieldOrder())

internal data class ValidReportSelections(
    val workCustomFieldIds: Set<Long>,
    val statisticSelections: Set<ReportStatisticSelection>
)

internal fun validReportSelections(
    workCustomFieldIds: Set<Long>,
    statisticSelections: Set<ReportStatisticSelection>,
    fields: List<DynamicFieldDefinition>,
    typeId: Long?
): ValidReportSelections {
    val workIds = availableReportWorkFields(fields, typeId)
        .mapTo(linkedSetOf()) { it.id }
    val statisticFields = availableReportStatisticFields(fields, typeId)
        .associateBy { it.id }
    val cleanedWorkIds = fields
        .asSequence()
        .filter { it.id in workCustomFieldIds && it.id in workIds }
        .sortedWith(reportFieldOrder())
        .take(MAX_REPORT_WORK_CUSTOM_FIELDS)
        .mapTo(linkedSetOf()) { it.id }
    val cleanedStatistics = statisticSelections
        .asSequence()
        .filter { selection ->
            val field = statisticFields[selection.fieldId] ?: return@filter false
            selection.aggregation in field.aggregations.compatibleWith(field.dataType)
        }
        .sortedWith(
            compareBy<ReportStatisticSelection> { selection ->
                fields.indexOfFirst { it.id == selection.fieldId }
                    .takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { selection -> selection.aggregation.reportOrder() }
        )
        .toCollection(linkedSetOf())
    return ValidReportSelections(cleanedWorkIds, cleanedStatistics)
}

internal sealed interface WorkCustomFieldToggleResult {
    data class Updated(val selectedIds: Set<Long>) : WorkCustomFieldToggleResult
    data class Rejected(val message: String) : WorkCustomFieldToggleResult
}

internal fun toggleWorkCustomField(
    selectedIds: Set<Long>,
    fieldId: Long
): WorkCustomFieldToggleResult {
    if (fieldId in selectedIds) {
        return WorkCustomFieldToggleResult.Updated(selectedIds - fieldId)
    }
    if (selectedIds.size >= MAX_REPORT_WORK_CUSTOM_FIELDS) {
        return WorkCustomFieldToggleResult.Rejected(REPORT_WORK_FIELD_LIMIT_MESSAGE)
    }
    return WorkCustomFieldToggleResult.Updated(selectedIds + fieldId)
}

internal fun FieldAggregation.reportOrder(): Int = when (this) {
    FieldAggregation.SUM -> 0
    FieldAggregation.AVERAGE -> 1
    FieldAggregation.MAXIMUM -> 2
    FieldAggregation.MINIMUM -> 3
    FieldAggregation.OPTION_DISTRIBUTION -> 4
    FieldAggregation.RATING_AVERAGE -> 5
    FieldAggregation.RATING_DISTRIBUTION -> 6
}

private fun reportFieldOrder(): Comparator<DynamicFieldDefinition> =
    compareBy<DynamicFieldDefinition> { it.typeId }
        .thenBy { it.sortOrder }
        .thenBy { it.id }

internal enum class SquareOptionState {
    SELECTED,
    UNSELECTED
}

internal fun squareOptionState(selected: Boolean): SquareOptionState =
    if (selected) SquareOptionState.SELECTED else SquareOptionState.UNSELECTED
