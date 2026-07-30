package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.export.calendar.CalendarExportSnapshot

data class ReportDataSnapshot(
    val config: ResolvedReportConfig,
    val summary: ReportSummarySnapshot,
    val items: List<ReportItemSnapshot>,
    val quotes: List<ReportQuoteSnapshot>,
    val representativeItemId: Long?,
    val monthlySummaries: List<ReportMonthSnapshot>,
    val companionItems: List<ReportCompanionSnapshot>,
    val mediaStatistics: FixedMediaStatistics,
    val customFieldStatistics: List<ReportFieldStatisticGroup>,
    val annualCalendarSnapshots: List<CalendarExportSnapshot> = emptyList()
) {
    val isEmpty: Boolean
        get() = items.isEmpty()
}

data class ReportSummarySnapshot(
    val itemCount: Int,
    val readingItemCount: Int,
    val viewingItemCount: Int,
    val recordCount: Int,
    val activeDayCount: Int,
    val quoteCount: Int,
    val totalDurationMinutes: Long?,
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
    val resolvedCoverWidth: Int? = null,
    val resolvedCoverHeight: Int? = null,
    val currentStatusId: Long?,
    val currentStatus: String?,
    val currentStatusSortOrder: Int?,
    val tags: List<String>,
    val customFields: List<ReportFieldValueSnapshot>,
    val firstActivityDate: Long,
    val firstRecordCreatedAt: Long,
    val activityDayCount: Int,
    val periodDurationMinutes: Long? = null
) {
    val resolvedCoverAspectRatio: Double?
        get() {
            val width = resolvedCoverWidth ?: return null
            val height = resolvedCoverHeight ?: return null
            return if (width > 0 && height > 0) {
                width.toDouble() / height.toDouble()
            } else {
                null
            }
        }
}

data class ReportMonthSnapshot(
    val month: Int,
    val itemCount: Int,
    val recordCount: Int = 0,
    val totalDurationMinutes: Long? = null,
    val representativeItemId: Long?,
    val representativeCandidateItemIds: List<Long> = emptyList()
)

data class ReportCompanionSnapshot(
    val itemId: Long,
    val title: String,
    val creator: String?,
    val activityDayCount: Int
)

data class ReportFieldStatisticGroup(
    val typeId: Long,
    val typeKind: ItemTypeKind,
    val statistics: List<CustomFieldStatistic>
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
