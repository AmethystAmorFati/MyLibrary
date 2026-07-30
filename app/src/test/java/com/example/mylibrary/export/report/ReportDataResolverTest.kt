package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.ui.settings.ReportExportConfig
import com.example.mylibrary.ui.settings.ReportStatisticOption
import com.example.mylibrary.ui.settings.ReportStatisticSelection
import com.example.mylibrary.ui.settings.ReportWorkOption
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDataResolverTest {
    private val utc = ZoneId.of("UTC")
    private val bookField = field(11, 1, "页数", FieldScope.ITEM)
    private val legacyRecordField = field(12, 1, "记录字段", FieldScope.RECORD)
    private val movieField = field(21, 2, "页数", FieldScope.ITEM)
    private val metadata = ReportSourceMetadata(
        itemTypes = listOf(ItemType(1, "书籍", 0), ItemType(2, "电影", 1)),
        fields = listOf(bookField, legacyRecordField, movieField)
    )

    @Test
    fun oneUnifiedItemSetDrivesItemOnlyFieldsTagsStatusesAndQuotes() = runTest {
        val source = FakeReportDataSource(
            metadata,
            ReportSourceData(
                records = listOf(
                    record(1, 100, 1, date(2026, 6, 3), 0, 2, "已读", 5),
                    record(2, 100, 1, date(2026, 6, 20), null, 2, "已读", 5),
                    record(3, 200, 2, date(2026, 6, 8), 60, null, null, null)
                ),
                activities = listOf(
                    ReportSourceActivity(date(2026, 6, 3), 100, 1),
                    ReportSourceActivity(date(2026, 6, 20), 100, 1),
                    ReportSourceActivity(date(2026, 6, 8), 200, 2)
                ),
                itemFieldValues = listOf(
                    ReportSourceFieldValue(100, 11, "320"),
                    ReportSourceFieldValue(200, 21, "120")
                ),
                itemTags = listOf(
                    ReportSourceItemTag(100, "经典", 9, 2),
                    ReportSourceItemTag(100, "经典", 9, 2),
                    ReportSourceItemTag(200, "经典", 9, 2)
                ),
                quotes = (1L..7L).map {
                    ReportSourceQuote(
                        quoteId = it,
                        itemId = if (it == 7L) 999 else 100,
                        itemTitle = "作品",
                        content = "摘录 $it",
                        source = null,
                        page = null,
                        createdTime = date(2026, 6, it.toInt().coerceAtMost(28))
                    )
                }
            )
        )
        val snapshot = ready(
            source,
            config(
                typeId = null,
                workFieldIds = setOf(11, 21),
                selections = setOf(
                    ReportStatisticSelection(11, FieldAggregation.SUM),
                    ReportStatisticSelection(12, FieldAggregation.SUM),
                    ReportStatisticSelection(21, FieldAggregation.AVERAGE)
                )
            )
        )

        assertEquals(listOf(100L, 200L), snapshot.items.map { it.itemId })
        assertEquals(3, snapshot.summary.recordCount)
        assertEquals(listOf("已读", "未设置"), snapshot.summary.statusCounts.map { it.name })
        assertEquals(listOf(1, 1), snapshot.summary.statusCounts.map { it.count })
        assertEquals(listOf("经典" to 2), snapshot.summary.tagCounts.map { it.name to it.count })
        assertEquals(6, snapshot.quotes.size)
        assertTrue(snapshot.quotes.all { it.itemId in setOf(100L, 200L) })
        assertTrue(snapshot.customFieldStatistics.all {
            it.typeKind != com.example.mylibrary.domain.model.ItemTypeKind.CUSTOM
        })
        assertEquals(
            listOf(11L, 21L),
            snapshot.customFieldStatistics
                .flatMap { it.statistics }
                .map { it.fieldId }
        )
        assertFalse(
            snapshot.customFieldStatistics
                .flatMap { it.statistics }
                .any { it.fieldId == legacyRecordField.id }
        )
    }

    @Test
    fun fixedStatisticsReuseFormalNullZeroAndMediaSeparationRules() = runTest {
        val snapshot = ready(
            FakeReportDataSource(
                metadata,
                ReportSourceData(
                    records = listOf(
                        record(1, 100, 1, date(2026, 6, 1), null),
                        record(2, 100, 1, date(2026, 6, 2), 0),
                        record(3, 200, 2, date(2026, 6, 3), 60)
                    ),
                    activities = emptyList(),
                    itemFieldValues = emptyList(),
                    itemTags = emptyList(),
                    quotes = emptyList()
                )
            ),
            config(typeId = null)
        )

        assertEquals(2L, snapshot.mediaStatistics.reading.recordCount)
        assertEquals(1L, snapshot.mediaStatistics.reading.valuedRecordCount)
        assertEquals(0L, snapshot.mediaStatistics.reading.totalDurationMinutes)
        assertEquals(0L, snapshot.mediaStatistics.reading.averagePerRecordMinutes)
        assertEquals(60L, snapshot.mediaStatistics.watching.totalDurationMinutes)
        assertEquals(60L, snapshot.mediaStatistics.watching.averagePerItemMinutes)
    }

    @Test
    fun bookScopeFiltersEveryModuleBeforeSnapshotConstruction() = runTest {
        val source = FakeReportDataSource(
            metadata,
            ReportSourceData(
                records = listOf(
                    record(1, 100, 1, date(2026, 6, 1), 30),
                    record(2, 200, 2, date(2026, 6, 2), 90)
                ),
                activities = listOf(
                    ReportSourceActivity(date(2026, 6, 1), 100, 1),
                    ReportSourceActivity(date(2026, 6, 2), 200, 2)
                ),
                itemFieldValues = listOf(
                    ReportSourceFieldValue(100, 11, "300"),
                    ReportSourceFieldValue(200, 21, "120")
                ),
                itemTags = listOf(
                    ReportSourceItemTag(100, "书籍标签", 1, 0),
                    ReportSourceItemTag(200, "电影标签", 2, 0)
                ),
                quotes = listOf(
                    ReportSourceQuote(1, 100, "书", "书摘", null, null, date(2026, 6, 3)),
                    ReportSourceQuote(2, 200, "电影", "影摘", null, null, date(2026, 6, 4))
                )
            )
        )
        val snapshot = ready(source, config(typeId = 1))

        assertEquals(listOf(100L), snapshot.items.map { it.itemId })
        assertEquals(listOf("书籍标签"), snapshot.summary.tagCounts.map { it.name })
        assertEquals(listOf("书摘"), snapshot.quotes.map { it.content })
        assertEquals(0L, snapshot.mediaStatistics.watching.itemCount)
        assertTrue(snapshot.customFieldStatistics.all { it.typeId == 1L })
    }

    @Test
    fun movieScopeKeepsOnlyMovieItemsFieldsAndFormalStatistics() = runTest {
        val snapshot = ready(
            FakeReportDataSource(
                metadata,
                ReportSourceData(
                    records = listOf(
                        record(1, 100, 1, date(2026, 6, 1), 30),
                        record(2, 200, 2, date(2026, 6, 2), 90)
                    ),
                    activities = listOf(
                        ReportSourceActivity(date(2026, 6, 1), 100, 1),
                        ReportSourceActivity(date(2026, 6, 2), 200, 2)
                    ),
                    itemFieldValues = listOf(
                        ReportSourceFieldValue(100, 11, "300"),
                        ReportSourceFieldValue(200, 21, "120")
                    ),
                    itemTags = emptyList(),
                    quotes = emptyList()
                )
            ),
            config(
                typeId = 2,
                workFieldIds = setOf(21),
                selections = setOf(
                    ReportStatisticSelection(21, FieldAggregation.AVERAGE)
                )
            )
        )

        assertEquals(listOf(200L), snapshot.items.map { it.itemId })
        assertEquals(listOf(21L), snapshot.items.single().customFields.map {
            it.field.fieldId
        })
        assertEquals(0L, snapshot.mediaStatistics.reading.itemCount)
        assertEquals(1L, snapshot.mediaStatistics.watching.itemCount)
        assertEquals(listOf(21L), snapshot.customFieldStatistics
            .flatMap { it.statistics }
            .map { it.fieldId })
    }

    @Test
    fun itemFieldStatisticsCountOneValuePerItemDespiteMultipleRecords() = runTest {
        val snapshot = ready(
            FakeReportDataSource(
                metadata,
                ReportSourceData(
                    records = listOf(
                        record(1, 100, 1, date(2026, 6, 1), 10),
                        record(2, 100, 1, date(2026, 6, 2), 20)
                    ),
                    activities = emptyList(),
                    itemFieldValues = listOf(ReportSourceFieldValue(100, 11, "320")),
                    itemTags = emptyList(),
                    quotes = emptyList()
                )
            ),
            config(
                typeId = 1,
                selections = setOf(
                    ReportStatisticSelection(11, FieldAggregation.SUM),
                    ReportStatisticSelection(11, FieldAggregation.AVERAGE)
                )
            )
        )
        val statistic = snapshot.customFieldStatistics.single()
            .statistics.single() as CustomFieldStatistic.Numeric

        assertEquals(listOf("320", "320"), statistic.metrics.map { it.value })
    }

    @Test
    fun periodUsesHalfOpenRecordWindowAndItemFieldsNeverRequestRecordValues() = runTest {
        val source = FakeReportDataSource(metadata, emptySourceData())
        ready(source, config(typeId = 1))

        assertEquals(date(2026, 6, 1), source.lastRange?.startInclusive)
        assertEquals(date(2026, 7, 1), source.lastRange?.endExclusive)
        assertEquals(setOf(11L), source.lastItemFieldIds)
    }

    @Test
    fun yearlySnapshotBuildsTwelveMonthStatisticsWithoutASecondCalendarModel() = runTest {
        val snapshot = ready(
            FakeReportDataSource(
                metadata,
                ReportSourceData(
                    records = listOf(
                        record(1, 100, 1, date(2026, 1, 5), 30),
                        record(2, 100, 1, date(2026, 7, 8), 60),
                        record(3, 200, 2, date(2026, 7, 8), null)
                    ),
                    activities = listOf(
                        ReportSourceActivity(date(2026, 1, 5), 100, 1),
                        ReportSourceActivity(date(2026, 7, 8), 100, 1),
                        ReportSourceActivity(date(2026, 7, 8), 200, 2)
                    ),
                    itemFieldValues = emptyList(),
                    itemTags = emptyList(),
                    quotes = emptyList()
                )
            ),
            config(typeId = null).copy(month = null)
        )

        assertEquals(12, snapshot.monthlySummaries.size)
        val july = snapshot.monthlySummaries.single { it.month == 7 }
        assertEquals(2, july.itemCount)
        assertEquals(2, july.recordCount)
        assertEquals(60L, july.totalDurationMinutes)
        assertEquals(listOf(100L, 200L), july.representativeCandidateItemIds)
    }

    private suspend fun ready(
        source: FakeReportDataSource,
        config: ReportExportConfig
    ): ReportDataSnapshot = (
        ReportDataResolver(source, zoneId = utc).resolve(config)
            as ReportPreparationResult.Ready
        ).snapshot

    private fun config(
        typeId: Long?,
        workFieldIds: Set<Long> = setOf(11),
        selections: Set<ReportStatisticSelection> = setOf(
            ReportStatisticSelection(11, FieldAggregation.SUM)
        )
    ) = ReportExportConfig(
        year = 2026,
        month = 6,
        typeId = typeId,
        statistics = setOf(
            ReportStatisticOption.ITEM_COUNT,
            ReportStatisticOption.RECORD_COUNT,
            ReportStatisticOption.QUOTE_COUNT,
            ReportStatisticOption.TAGS
        ),
        workFields = setOf(
            ReportWorkOption.COVER,
            ReportWorkOption.TITLE,
            ReportWorkOption.CREATOR
        ),
        includeAllStatuses = true,
        workCustomFieldIds = workFieldIds,
        statisticSelections = selections,
        includeQuotes = true
    )

    private fun field(
        id: Long,
        typeId: Long,
        name: String,
        scope: FieldScope
    ) = DynamicFieldDefinition(
        id = id,
        typeId = typeId,
        typeName = if (typeId == 1L) "书籍" else "电影",
        name = name,
        dataType = FieldDataType.NUMBER,
        enabled = true,
        sortOrder = id.toInt(),
        isFixed = false,
        scope = scope,
        unit = null,
        aggregations = setOf(FieldAggregation.SUM, FieldAggregation.AVERAGE)
    )

    private fun record(
        recordId: Long,
        itemId: Long,
        typeId: Long,
        startDate: Long,
        durationMinutes: Long?,
        statusId: Long? = 1,
        statusName: String? = "进行中",
        statusSortOrder: Int? = 0
    ) = ReportSourceRecord(
        recordId = recordId,
        itemId = itemId,
        startDate = startDate,
        typeId = typeId,
        typeName = if (typeId == 1L) "书籍" else "电影",
        typeSortOrder = typeId.toInt(),
        title = "作品 $itemId",
        coverPath = null,
        currentStatusId = statusId,
        currentStatusName = statusName,
        currentStatusSortOrder = statusSortOrder,
        creator = "作者",
        durationMinutes = durationMinutes,
        recordCreatedAt = startDate
    )

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(utc).toInstant().toEpochMilli()

    private fun emptySourceData() = ReportSourceData(
        records = emptyList(),
        activities = emptyList(),
        itemFieldValues = emptyList(),
        itemTags = emptyList(),
        quotes = emptyList()
    )

    private class FakeReportDataSource(
        private val metadata: ReportSourceMetadata,
        private val data: ReportSourceData
    ) : ReportDataSource {
        var lastRange: ReportEpochRange? = null
        var lastItemFieldIds: Set<Long> = emptySet()

        override suspend fun loadMetadata(): ReportSourceMetadata = metadata

        override suspend fun loadData(
            range: ReportEpochRange,
            selectedItemTypeIds: Set<Long>,
            itemFieldIds: Set<Long>,
            includeQuotes: Boolean
        ): ReportSourceData {
            lastRange = range
            lastItemFieldIds = itemFieldIds
            val records = data.records.filter {
                it.startDate in range && it.typeId in selectedItemTypeIds
            }
            val itemIds = records.mapTo(mutableSetOf()) { it.itemId }
            return ReportSourceData(
                records = records,
                activities = data.activities.filter {
                    it.date in range &&
                        it.typeId in selectedItemTypeIds &&
                        it.itemId in itemIds
                },
                itemFieldValues = data.itemFieldValues.filter {
                    it.ownerId in itemIds && it.fieldId in itemFieldIds
                },
                itemTags = data.itemTags.filter { it.itemId in itemIds },
                quotes = if (includeQuotes) {
                    data.quotes.filter { it.createdTime in range }
                } else {
                    emptyList()
                }
            )
        }
    }
}
