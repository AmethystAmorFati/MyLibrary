package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldOptionDefinition
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.ui.settings.ReportStatisticOption
import com.example.mylibrary.ui.settings.ReportWorkOption

data class ResolvedReportField(
    val fieldId: Long,
    val itemTypeId: Long,
    val itemTypeName: String,
    val itemTypeSortOrder: Int,
    val fieldName: String,
    val fieldType: FieldDataType,
    val scope: FieldScope,
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
    val statusIds: Set<Long>
) {
    fun hasContent(): Boolean =
        basicStatistics.isNotEmpty() ||
            statisticFields.isNotEmpty() ||
            includeCover ||
            includeTitle ||
            includeCreator ||
            includeStatus ||
            includeTags ||
            includeQuotes ||
            includeAllStatuses ||
            statusIds.isNotEmpty()
}
