package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.ItemTypeKind
import java.math.BigDecimal

data class ReportDataSnapshot(
    val config: ResolvedReportConfig,
    val summary: ReportSummarySnapshot,
    val items: List<ReportItemSnapshot>,
    val records: List<ReportRecordSnapshot>,
    val statistics: List<ReportStatisticResult>,
    val quotes: List<ReportQuoteSnapshot>
) {
    val isEmpty: Boolean
        get() = items.isEmpty() &&
            records.isEmpty() &&
            quotes.isEmpty() &&
            statistics.isEmpty() &&
            summary.activeDayCount == 0
}

data class ReportSummarySnapshot(
    val readingItemCount: Int,
    val viewingItemCount: Int,
    val recordCount: Int,
    val activeDayCount: Int,
    val quoteCount: Int,
    val statusCounts: List<ReportNamedCount>,
    val tagCounts: List<ReportNamedCount>,
    val creatorCounts: List<ReportNamedCount>,
    val topActivityDays: List<ReportDateCount>
)

data class ReportNamedCount(
    val name: String,
    val count: Int
)

data class ReportDateCount(
    val date: Long,
    val count: Int
)

data class ReportFieldValueSnapshot(
    val field: ResolvedReportField,
    val rawValue: String,
    val formattedValue: String
)

data class ReportItemSnapshot(
    val itemId: Long,
    val typeId: Long,
    val typeName: String,
    val typeKind: ItemTypeKind,
    val title: String,
    val creator: String?,
    val coverPath: String?,
    val currentStatus: String?,
    val tags: List<String>,
    val customFields: List<ReportFieldValueSnapshot>,
    val recordIds: List<Long>
)

data class ReportRecordSnapshot(
    val recordId: Long,
    val itemId: Long,
    val startDate: Long,
    val endDate: Long?,
    val ratingHalfStars: Int?,
    val review: String?,
    val customFields: List<ReportFieldValueSnapshot>
)

data class ReportStatisticResult(
    val field: ResolvedReportField,
    val aggregation: FieldAggregation,
    val rawResult: ReportStatisticValue,
    val formattedValue: String
)

sealed interface ReportStatisticValue {
    val invalidValueCount: Int

    data class Number(
        val value: BigDecimal,
        val validValueCount: Int,
        override val invalidValueCount: Int
    ) : ReportStatisticValue

    data class Distribution(
        val entries: List<ReportDistributionEntry>,
        override val invalidValueCount: Int
    ) : ReportStatisticValue
}

data class ReportDistributionEntry(
    val key: String,
    val count: Int
)

data class ReportQuoteSnapshot(
    val quoteId: Long,
    val itemId: Long,
    val itemTitle: String,
    val content: String,
    val source: String?,
    val chapter: String?,
    val page: String?,
    val createdTime: Long
)
