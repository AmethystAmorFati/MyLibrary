package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.ui.settings.ReportStatisticOption
import com.example.mylibrary.ui.settings.ReportOutputFormat
import com.example.mylibrary.ui.settings.ReportShowcaseStyle

data class ResolvedReportField(
    val fieldId: Long,
    val itemTypeId: Long,
    val itemTypeName: String,
    val itemTypeSortOrder: Int,
    val fieldName: String,
    val fieldType: FieldDataType,
    val unit: String?,
    val aggregation: FieldAggregation?,
    val fieldSortOrder: Int,
    val optionDefinitions: List<FieldOptionDefinition>
)

data class ResolvedReportConfig(
    val period: ReportPeriod,
    val selectedItemTypeIds: Set<Long>,
    val basicStatistics: Set<ReportStatisticOption>,
    val workFields: List<ResolvedReportField>,
    val statisticFields: List<ResolvedReportField>,
    val includeCover: Boolean,
    val includeTitle: Boolean,
    val includeCreator: Boolean,
    val includeStatus: Boolean,
    val includeTags: Boolean,
    val includeQuotes: Boolean,
    val includeAllStatuses: Boolean,
    val statusIds: Set<Long>,
    val includeBasicStatistics: Boolean,
    val includeTagStatistics: Boolean,
    val includeFieldStatistics: Boolean,
    val includeItemInformation: Boolean,
    val includeItemFields: Boolean,
    val includeItemStatusStatistics: Boolean,
    val showcaseStyle: ReportShowcaseStyle,
    val outputFormat: ReportOutputFormat
) {
    val mediaScope: ReportMediaScope
        get() = when (selectedItemTypeIds) {
            setOf(ItemTypeKind.BOOK_TYPE_ID) -> ReportMediaScope.BOOK
            setOf(ItemTypeKind.MOVIE_TYPE_ID) -> ReportMediaScope.MOVIE
            else -> ReportMediaScope.ALL
        }

    fun hasContent(): Boolean =
        period is ReportPeriod.Year ||
            includeBasicStatistics ||
            includeTagStatistics ||
            includeFieldStatistics ||
            includeItemInformation ||
            includeItemFields ||
            includeItemStatusStatistics ||
            includeQuotes
}

enum class ReportMediaScope {
    ALL,
    BOOK,
    MOVIE
}
