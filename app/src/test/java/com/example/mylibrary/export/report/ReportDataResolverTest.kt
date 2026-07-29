package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.DynamicFieldDefinition
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FieldScope
import com.example.mylibrary.domain.model.ItemType
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.ui.settings.ReportExportConfig
import com.example.mylibrary.ui.settings.ReportStatisticOption
import com.example.mylibrary.ui.settings.ReportStatisticSelection
import com.example.mylibrary.ui.settings.ReportWorkOption
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDataResolverTest {
    private val utc = ZoneId.of("UTC")
    private val itemField = field(
        id = 11,
        name = "页数",
        scope = FieldScope.ITEM,
        unit = "页"
    )
    private val recordField = field(
        id = 12,
        name = "本次页数",
        scope = FieldScope.RECORD,
        unit = "页"
    )
    private val metadata = ReportSourceMetadata(
        itemTypes = listOf(ItemType(1, "阅读", 0), ItemType(2, "观影", 1)),
        fields = listOf(itemField, recordField)
    )

    @Test
    fun itemStatisticsDeduplicateItemsWhileRecordStatisticsUseEveryRecord() = runTest {
        val source = FakeReportDataSource(
            metadata = metadata,
            data = ReportSourceData(
                records = listOf(
                    record(1, 100, date(2026, 6, 3)),
                    record(2, 100, date(2026, 6, 20))
                ),
                activities = listOf(
                    ReportSourceActivity(date(2026, 6, 3), 100, 1),
                    ReportSourceActivity(date(2026, 6, 20), 100, 1)
                ),
                itemFieldValues = listOf(
                    ReportSourceFieldValue(100, 11, "328")
                ),
                recordFieldValues = listOf(
                    ReportSourceFieldValue(1, 12, "120"),
                    ReportSourceFieldValue(2, 12, "90")
                ),
                itemTags = listOf(ReportSourceItemTag(100, "文学")),
                quotes = listOf(
                    ReportSourceQuote(
                        quoteId = 1,
                        itemId = 100,
                        itemTitle = "百年孤独",
                        content = "摘录",
                        source = null,
                        page = null,
                        createdTime = date(2026, 6, 10),
                        chapter = "第一章"
                    )
                )
            )
        )
        val result = ReportDataResolver(source, zoneId = utc).resolve(fullConfig())
            as ReportPreparationResult.Ready
        val snapshot = result.snapshot

        assertEquals(1, snapshot.items.size)
        assertEquals(listOf(1L, 2L), snapshot.items.single().recordIds)
        assertEquals(ItemTypeKind.BOOK, snapshot.items.single().typeKind)
        assertEquals("328 页", snapshot.items.single().customFields.single().formattedValue)
        assertEquals(2, snapshot.records.size)
        assertEquals(2, snapshot.summary.recordCount)
        assertEquals(2, snapshot.summary.activeDayCount)
        assertEquals(1, snapshot.summary.quoteCount)
        assertEquals("第一章", snapshot.quotes.single().chapter)
        assertEquals(
            "328 页",
            statistic(snapshot, 11, FieldAggregation.SUM).formattedValue
        )
        assertEquals(
            "328 页",
            statistic(snapshot, 11, FieldAggregation.AVERAGE).formattedValue
        )
        assertEquals(
            "210 页",
            statistic(snapshot, 12, FieldAggregation.SUM).formattedValue
        )
        assertEquals(
            "105 页",
            statistic(snapshot, 12, FieldAggregation.AVERAGE).formattedValue
        )
    }

    @Test
    fun invalidNumbersAreCountedWithoutFailingTheSnapshot() = runTest {
        val source = FakeReportDataSource(
            metadata,
            ReportSourceData(
                records = listOf(
                    record(1, 100, date(2026, 6, 3)),
                    record(2, 100, date(2026, 6, 4))
                ),
                activities = emptyList(),
                itemFieldValues = emptyList(),
                recordFieldValues = listOf(
                    ReportSourceFieldValue(1, 12, "120"),
                    ReportSourceFieldValue(2, 12, "not-a-number")
                ),
                itemTags = emptyList(),
                quotes = emptyList()
            )
        )
        val config = fullConfig().copy(
            workCustomFieldIds = emptySet(),
            statisticSelections = setOf(
                ReportStatisticSelection(12, FieldAggregation.SUM)
            )
        )
        val snapshot = (
            ReportDataResolver(source, zoneId = utc).resolve(config)
                as ReportPreparationResult.Ready
            ).snapshot
        val statistic = statistic(snapshot, 12, FieldAggregation.SUM)
        val raw = statistic.rawResult as ReportStatisticValue.Number

        assertEquals("120 页", statistic.formattedValue)
        assertEquals(1, raw.validValueCount)
        assertEquals(1, raw.invalidValueCount)
    }

    @Test
    fun recordStartDateControlsItemsButActivityDateControlsActiveDays() = runTest {
        val spanningRecord = record(1, 100, date(2026, 6, 28))
        val source = FakeReportDataSource(
            metadata,
            ReportSourceData(
                records = listOf(spanningRecord),
                activities = listOf(
                    ReportSourceActivity(date(2026, 6, 28), 100, 1),
                    ReportSourceActivity(date(2026, 7, 5), 100, 1)
                ),
                itemFieldValues = emptyList(),
                recordFieldValues = emptyList(),
                itemTags = emptyList(),
                quotes = emptyList()
            )
        )
        val resolver = ReportDataResolver(source, zoneId = utc)
        val june = (resolver.resolve(minimalConfig(month = 6))
            as ReportPreparationResult.Ready).snapshot
        val july = (resolver.resolve(minimalConfig(month = 7))
            as ReportPreparationResult.Ready).snapshot

        assertEquals(1, june.records.size)
        assertEquals(1, june.items.size)
        assertEquals(1, june.summary.activeDayCount)
        assertTrue(july.records.isEmpty())
        assertTrue(july.items.isEmpty())
        assertEquals(1, july.summary.activeDayCount)
    }

    @Test
    fun annualRangeAndQuoteDateArePassedAsHalfOpenCalendarWindow() = runTest {
        val source = FakeReportDataSource(
            metadata,
            ReportSourceData(
                records = emptyList(),
                activities = emptyList(),
                itemFieldValues = emptyList(),
                recordFieldValues = emptyList(),
                itemTags = emptyList(),
                quotes = listOf(
                    ReportSourceQuote(
                        1,
                        100,
                        "作品",
                        "年内",
                        null,
                        null,
                        date(2026, 12, 31)
                    ),
                    ReportSourceQuote(
                        2,
                        100,
                        "作品",
                        "次年",
                        null,
                        null,
                        date(2027, 1, 1)
                    )
                )
            )
        )
        val config = minimalConfig(month = null).copy(
            statistics = setOf(ReportStatisticOption.QUOTE_COUNT),
            includeQuotes = true
        )
        val snapshot = (
            ReportDataResolver(source, zoneId = utc).resolve(config)
                as ReportPreparationResult.Ready
            ).snapshot

        assertEquals(date(2026, 1, 1), source.lastRange?.startInclusive)
        assertEquals(date(2027, 1, 1), source.lastRange?.endExclusive)
        assertEquals(listOf("年内"), snapshot.quotes.map { it.content })
        assertEquals(1, snapshot.summary.quoteCount)
    }

    @Test
    fun validConfigWithNoRangeDataReturnsStructuredEmptySnapshot() = runTest {
        val source = FakeReportDataSource(metadata, emptySourceData())
        val result = ReportDataResolver(source, zoneId = utc).resolve(minimalConfig(6))
            as ReportPreparationResult.Ready

        assertTrue(result.snapshot.isEmpty)
        assertTrue(result.snapshot.items.isEmpty())
        assertTrue(result.snapshot.records.isEmpty())
        assertTrue(result.snapshot.statistics.isEmpty())
    }

    private fun fullConfig() = ReportExportConfig(
        year = 2026,
        month = 6,
        typeId = 1,
        statistics = setOf(
            ReportStatisticOption.ITEM_COUNT,
            ReportStatisticOption.RECORD_COUNT,
            ReportStatisticOption.ACTIVITY_DAYS,
            ReportStatisticOption.QUOTE_COUNT
        ),
        workFields = setOf(
            ReportWorkOption.TITLE,
            ReportWorkOption.STATUS,
            ReportWorkOption.TAGS
        ),
        includeAllStatuses = true,
        workCustomFieldIds = setOf(11),
        statisticSelections = setOf(
            ReportStatisticSelection(11, FieldAggregation.SUM),
            ReportStatisticSelection(11, FieldAggregation.AVERAGE),
            ReportStatisticSelection(12, FieldAggregation.SUM),
            ReportStatisticSelection(12, FieldAggregation.AVERAGE)
        ),
        includeQuotes = true
    )

    private fun minimalConfig(month: Int?) = ReportExportConfig(
        year = 2026,
        month = month,
        typeId = 1,
        statistics = setOf(ReportStatisticOption.ACTIVITY_DAYS),
        workFields = emptySet(),
        includeAllStatuses = false
    )

    private fun field(
        id: Long,
        name: String,
        scope: FieldScope,
        unit: String
    ) = DynamicFieldDefinition(
        id = id,
        typeId = 1,
        typeName = "阅读",
        name = name,
        dataType = FieldDataType.NUMBER,
        enabled = true,
        sortOrder = id.toInt(),
        isFixed = false,
        scope = scope,
        unit = unit,
        aggregations = setOf(FieldAggregation.SUM, FieldAggregation.AVERAGE)
    )

    private fun record(
        recordId: Long,
        itemId: Long,
        startDate: Long
    ) = ReportSourceRecord(
        recordId = recordId,
        itemId = itemId,
        startDate = startDate,
        endDate = null,
        ratingHalfStars = null,
        review = null,
        typeId = 1,
        typeName = "阅读",
        typeSortOrder = 0,
        title = "百年孤独",
        coverPath = "covers/book.webp",
        currentStatusId = 1,
        currentStatusName = "已读",
        creator = "马尔克斯"
    )

    private fun statistic(
        snapshot: ReportDataSnapshot,
        fieldId: Long,
        aggregation: FieldAggregation
    ) = snapshot.statistics.single {
        it.field.fieldId == fieldId && it.aggregation == aggregation
    }

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(utc).toInstant().toEpochMilli()

    private fun emptySourceData() = ReportSourceData(
        records = emptyList(),
        activities = emptyList(),
        itemFieldValues = emptyList(),
        recordFieldValues = emptyList(),
        itemTags = emptyList(),
        quotes = emptyList()
    )

    private class FakeReportDataSource(
        private val metadata: ReportSourceMetadata,
        private val data: ReportSourceData
    ) : ReportDataSource {
        var lastRange: ReportEpochRange? = null

        override suspend fun loadMetadata(): ReportSourceMetadata = metadata

        override suspend fun loadData(
            range: ReportEpochRange,
            selectedItemTypeIds: Set<Long>,
            itemFieldIds: Set<Long>,
            recordFieldIds: Set<Long>,
            includeQuotes: Boolean
        ): ReportSourceData {
            lastRange = range
            val records = data.records.filter {
                it.startDate in range && it.typeId in selectedItemTypeIds
            }
            val recordIds = records.mapTo(mutableSetOf()) { it.recordId }
            val itemIds = records.mapTo(mutableSetOf()) { it.itemId }
            return ReportSourceData(
                records = records,
                activities = data.activities.filter {
                    it.date in range && it.typeId in selectedItemTypeIds
                },
                itemFieldValues = data.itemFieldValues.filter {
                    it.ownerId in itemIds && it.fieldId in itemFieldIds
                },
                recordFieldValues = data.recordFieldValues.filter {
                    it.ownerId in recordIds && it.fieldId in recordFieldIds
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
