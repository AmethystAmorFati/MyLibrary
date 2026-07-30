package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.domain.model.MediaCategoryStatistics
import com.example.mylibrary.domain.model.NumericMetric
import com.example.mylibrary.ui.settings.ReportOutputFormat
import com.example.mylibrary.ui.settings.ReportShowcaseStyle
import com.example.mylibrary.export.calendar.buildCalendarExportSnapshot
import com.example.mylibrary.export.visual.VisualExportActivity
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class ReportPageModelTest {
    @Test
    fun annualReportUsesTheFormalCalendarSnapshotBuilderForEveryMonth() {
        val activities = listOf(
            visualActivity(2, 2, 20, 20),
            visualActivity(1, 1, 10, 10)
        )
        val reportCalendars = buildAnnualReportCalendarSnapshots(
            year = 2026,
            selectedItemTypeIds = setOf(1L),
            activities = activities
        )
        val formalJuly = buildCalendarExportSnapshot(
            YearMonth.of(2026, 7),
            activities.filter { it.typeId == 1L }
        )

        assertEquals(12, reportCalendars.size)
        assertEquals(formalJuly, reportCalendars[6])
        assertEquals(
            formalJuly.cells.map { it?.covers?.map { cover -> cover.itemId } },
            reportCalendars[6].cells.map {
                it?.covers?.map { cover -> cover.itemId }
            }
        )
    }

    @Test
    fun monthlyReportUsesAtMostFiveEditorialPagesInFixedOrder() {
        val document = ReportPageModelFactory.create(
            snapshot(
                period = ReportPeriod.Month(2026, 6),
                items = (1L..12L).map { item(it, withCover = true, withFields = true) },
                quotes = (1L..12L).map { quote(it, it) },
                tags = (1..12).map { ReportNamedCount("标签 $it", 13 - it) },
                statuses = listOf(
                    ReportNamedCount("已完成", 8),
                    ReportNamedCount("进行中", 4)
                ),
                fieldGroups = listOf(fieldGroup())
            )
        )

        assertEquals(5, document.pages.size)
        assertTrue(document.pages[0] is ReportPageModel.TimeAndStatus)
        assertTrue(document.pages[1] is ReportPageModel.WorkShowcase)
        assertTrue(document.pages[2] is ReportPageModel.ItemCustomInformation)
        assertTrue(document.pages[3] is ReportPageModel.CustomInformationInsights)
        assertTrue(document.pages[4] is ReportPageModel.QuoteSelection)
        assertEquals((1..5).toList(), document.pages.map { it.pageNumber })
    }

    @Test
    fun firstPageUsesNarrativeStatisticsAndGenericStatusSentence() {
        val page = ReportPageModelFactory.create(
            snapshot(
                period = ReportPeriod.Month(2026, 6),
                items = listOf(item(1)),
                quotes = emptyList(),
                statuses = listOf(
                    ReportNamedCount("正在慢慢读", 1),
                    ReportNamedCount("搁置", 2)
                ),
                statistics = FixedMediaStatistics(
                    reading = MediaCategoryStatistics(
                        itemCount = 1,
                        recordCount = 3,
                        valuedRecordCount = 3,
                        valuedItemCount = 1,
                        totalDurationMinutes = 180,
                        maximumSingleDurationMinutes = 90,
                        longestItemTitle = "作品 1",
                        longestItemDurationMinutes = 180
                    )
                )
            )
        ).pages.first() as ReportPageModel.TimeAndStatus

        assertTrue(page.itemCountLine.contains("1 本书"))
        assertTrue(page.narrativeSections.flatMap { it.paragraphs }
            .any { it.contains("平均每次阅读") })
        assertEquals("这个月：搁置 2本，正在慢慢读 1本。", page.statusSentence)
    }

    @Test
    fun collageContainsOnlyCoversAndGridStopsAtSixWorks() {
        val items = (1L..22L).map { item(it, withCover = it != 22L) }
        val collage = ReportPageModelFactory.create(
            snapshot(
                period = ReportPeriod.Month(2026, 6),
                items = items,
                quotes = emptyList()
            )
        ).pages.filterIsInstance<ReportPageModel.WorkShowcase>().single()
        val grid = ReportPageModelFactory.create(
            snapshot(
                period = ReportPeriod.Month(2026, 6),
                items = items,
                quotes = emptyList(),
                showcaseStyle = ReportShowcaseStyle.GRID
            )
        ).pages.filterIsInstance<ReportPageModel.WorkShowcase>().single()

        assertEquals(ReportShowcaseStyle.COLLAGE, collage.style)
        assertEquals(20, collage.items.size)
        assertTrue(collage.items.all { it.resolvedCoverAspectRatio != null })
        assertEquals(ReportShowcaseStyle.GRID, grid.style)
        assertEquals(6, grid.items.size)
    }

    @Test
    fun itemCustomInformationIsIndependentAndKeepsThreeValuesPerWork() {
        val snapshot = snapshot(
            period = ReportPeriod.Month(2026, 6),
            items = listOf(item(1, withFields = true)),
            quotes = emptyList(),
            includeShowcase = false,
            includeItemFields = true
        )

        val pages = ReportPageModelFactory.create(snapshot).pages
        val information = pages.filterIsInstance<ReportPageModel.ItemCustomInformation>()
            .single()

        assertFalse(pages.any { it is ReportPageModel.WorkShowcase })
        val section = information.sections.single()
        assertEquals("书籍", section.itemHeading)
        assertEquals(listOf("字段 1", "字段 2", "字段 3"), section.columns.map {
            it.heading
        })
        assertEquals(listOf("值1", "值2", "值3"), section.entries.single().values)
    }

    @Test
    fun wordCloudPlacementsAndPerItemQuoteChoiceAreStable() {
        val tags = (1..12).map { ReportNamedCount("标签 $it", 13 - it) }
        val quotes = listOf(
            quote(1, 1),
            quote(2, 1),
            quote(3, 2),
            quote(4, 2)
        )
        val source = snapshot(
            period = ReportPeriod.Month(2026, 6),
            items = listOf(item(1), item(2)),
            quotes = quotes,
            tags = tags
        )

        val first = ReportPageModelFactory.create(source)
        val second = ReportPageModelFactory.create(source)
        val firstWords = first.pages
            .filterIsInstance<ReportPageModel.CustomInformationInsights>()
            .single()
            .wordCloud
        val firstQuotes = first.pages
            .filterIsInstance<ReportPageModel.QuoteSelection>()
            .single()
            .quotes
        val secondQuotes = second.pages
            .filterIsInstance<ReportPageModel.QuoteSelection>()
            .single()
            .quotes

        assertEquals(firstWords, ReportPageModelFactory.buildWordCloud(tags))
        assertEquals(2, firstQuotes.size)
        assertEquals(2, firstQuotes.map { it.quote.itemId }.distinct().size)
        assertEquals(
            firstQuotes.map { it.quote.quoteId },
            secondQuotes.map { it.quote.quoteId }
        )
    }

    @Test
    fun yearlyReportAlwaysContainsTwelveMonthCalendarAndAtMostFourPages() {
        val months = (1..12).map { month ->
            ReportMonthSnapshot(
                month = month,
                itemCount = if (month == 7) 3 else 0,
                recordCount = if (month == 7) 4 else 0,
                totalDurationMinutes = if (month == 7) 240 else null,
                representativeItemId = 1
            )
        }
        val calendars = (1..12).map { month ->
            buildCalendarExportSnapshot(
                YearMonth.of(2026, month),
                if (month == 7) {
                    listOf(
                        VisualExportActivity(
                            activityId = 1,
                            date = LocalDate.of(2026, 7, 1).toStartOfDayMillis(),
                            itemId = 1,
                            typeId = 1,
                            recordId = 1,
                            recordCreatedAt = 1,
                            title = "作品 1",
                            coverPath = "cover-1.jpg",
                            thumbnailPath = "thumb-1.jpg"
                        )
                    )
                } else {
                    emptyList()
                }
            )
        }
        val document = ReportPageModelFactory.create(
            snapshot(
                period = ReportPeriod.Year(2026),
                items = (1L..6L).map {
                    item(it, withCover = true, withFields = true)
                },
                quotes = (1L..6L).map { quote(it, it) },
                tags = listOf(ReportNamedCount("年度", 1)),
                fieldGroups = listOf(fieldGroup()),
                monthlySummaries = months,
                annualCalendarSnapshots = calendars
            )
        )
        val calendar = document.pages
            .filterIsInstance<ReportPageModel.AnnualCalendarOverview>()
            .single()

        assertTrue(document.pages.size in 2..4)
        assertEquals(
            "这一年的片段，已经被好好保存。",
            (document.pages.first() as ReportPageModel.TimeAndStatus).closingLine
        )
        assertEquals(12, calendar.months.size)
        assertEquals((1..12).toList(), calendar.months.map { it.month })
        assertEquals(1f, calendar.months[6].activityFraction)
        assertEquals(
            2,
            calendar.months[6].calendar.cells.indexOfFirst {
                it?.date?.dayOfMonth == 1
            }
        )
        assertSame(calendars[6], calendar.months[6].calendar)
        assertTrue(calendar.rhythmSummary.first().contains("7月"))
        assertFalse(document.pages.any { it is ReportPageModel.WorkShowcase })
        assertFalse(document.pages.any { it is ReportPageModel.ItemCustomInformation })
        assertEquals(
            4,
            document.pages.filterIsInstance<ReportPageModel.QuoteSelection>()
                .single()
                .quotes.size
        )
    }

    @Test
    fun pngNamesUseActualFrozenPageCount() {
        assertEquals(
            listOf(
                "MyLibrary_Monthly_2026_06_P01.png",
                "MyLibrary_Monthly_2026_06_P02.png",
                "MyLibrary_Monthly_2026_06_P03.png"
            ),
            ReportFileNames.pngPages(ReportPeriod.Month(2026, 6), 3)
        )
    }

    private fun snapshot(
        period: ReportPeriod,
        items: List<ReportItemSnapshot>,
        quotes: List<ReportQuoteSnapshot>,
        tags: List<ReportNamedCount> = emptyList(),
        statuses: List<ReportNamedCount> = emptyList(),
        fieldGroups: List<ReportFieldStatisticGroup> = emptyList(),
        statistics: FixedMediaStatistics = FixedMediaStatistics(),
        showcaseStyle: ReportShowcaseStyle = ReportShowcaseStyle.COLLAGE,
        includeShowcase: Boolean = true,
        includeItemFields: Boolean = true,
        monthlySummaries: List<ReportMonthSnapshot> = emptyList(),
        annualCalendarSnapshots:
            List<com.example.mylibrary.export.calendar.CalendarExportSnapshot> =
            emptyList()
    ) = ReportDataSnapshot(
        config = config(
            period,
            showcaseStyle,
            includeShowcase,
            includeItemFields
        ),
        summary = ReportSummarySnapshot(
            itemCount = items.size,
            readingItemCount = items.size,
            viewingItemCount = 0,
            recordCount = items.size,
            activeDayCount = items.size,
            quoteCount = quotes.size,
            totalDurationMinutes = null,
            statusCounts = statuses,
            tagCounts = tags,
            creatorCounts = emptyList(),
            topActivityDays = emptyList()
        ),
        items = items,
        quotes = quotes,
        representativeItemId = null,
        monthlySummaries = monthlySummaries,
        companionItems = emptyList(),
        mediaStatistics = statistics,
        customFieldStatistics = fieldGroups,
        annualCalendarSnapshots = annualCalendarSnapshots
    )

    private fun config(
        period: ReportPeriod,
        showcaseStyle: ReportShowcaseStyle,
        includeShowcase: Boolean,
        includeItemFields: Boolean
    ) = ResolvedReportConfig(
        period = period,
        selectedItemTypeIds = setOf(1L),
        basicStatistics = emptySet(),
        workFields = (1L..3L).map(::fieldDefinition),
        statisticFields = listOf(fieldDefinition(11, aggregation = FieldAggregation.SUM)),
        includeCover = true,
        includeTitle = true,
        includeCreator = true,
        includeStatus = false,
        includeTags = false,
        includeQuotes = true,
        includeAllStatuses = true,
        statusIds = emptySet(),
        includeBasicStatistics = true,
        includeTagStatistics = true,
        includeFieldStatistics = true,
        includeItemInformation = includeShowcase,
        includeItemFields = includeItemFields,
        includeItemStatusStatistics = true,
        showcaseStyle = showcaseStyle,
        outputFormat = ReportOutputFormat.PNG
    )

    private fun item(
        id: Long,
        withCover: Boolean = false,
        withFields: Boolean = false
    ) = ReportItemSnapshot(
        itemId = id,
        typeId = 1,
        typeName = "书籍",
        typeKind = ItemTypeKind.BOOK,
        title = "作品 $id",
        creator = "作者",
        coverPath = if (withCover) "cover-$id.jpg" else null,
        resolvedCoverWidth = if (withCover) 600 else null,
        resolvedCoverHeight = if (withCover) 900 else null,
        currentStatusId = null,
        currentStatus = null,
        currentStatusSortOrder = null,
        tags = emptyList(),
        customFields = if (withFields) {
            (1L..4L).map { fieldId ->
                ReportFieldValueSnapshot(
                    fieldDefinition(fieldId),
                    fieldId.toString(),
                    "值$fieldId"
                )
            }
        } else {
            emptyList()
        },
        firstActivityDate = id,
        firstRecordCreatedAt = id,
        activityDayCount = id.toInt(),
        periodDurationMinutes = id * 10
    )

    private fun quote(id: Long, itemId: Long) = ReportQuoteSnapshot(
        quoteId = id,
        itemId = itemId,
        itemTitle = "作品 $itemId",
        content = "摘录 $id",
        source = null,
        chapter = null,
        page = null,
        createdTime = id
    )

    private fun visualActivity(
        activityId: Long,
        typeId: Long,
        recordCreatedAt: Long,
        recordId: Long
    ) = VisualExportActivity(
        activityId = activityId,
        date = LocalDate.of(2026, 7, 8).toStartOfDayMillis(),
        itemId = activityId,
        typeId = typeId,
        recordId = recordId,
        recordCreatedAt = recordCreatedAt,
        title = "作品 $activityId",
        coverPath = "cover-$activityId.jpg",
        thumbnailPath = "thumb-$activityId.jpg"
    )

    private fun fieldDefinition(
        id: Long,
        aggregation: FieldAggregation? = null
    ) = ResolvedReportField(
        fieldId = id,
        itemTypeId = 1,
        itemTypeName = "书籍",
        itemTypeSortOrder = 0,
        fieldName = "字段 $id",
        fieldType = FieldDataType.NUMBER,
        unit = null,
        aggregation = aggregation,
        fieldSortOrder = id.toInt(),
        optionDefinitions = emptyList()
    )

    private fun fieldGroup() = ReportFieldStatisticGroup(
        typeId = 1,
        typeKind = ItemTypeKind.BOOK,
        statistics = listOf(
            CustomFieldStatistic.Numeric(
                fieldId = 11,
                fieldName = "页数",
                sortOrder = 0,
                metrics = listOf(
                    NumericMetric(FieldAggregation.AVERAGE, "320", "页"),
                    NumericMetric(FieldAggregation.MINIMUM, "120", "页"),
                    NumericMetric(FieldAggregation.MAXIMUM, "560", "页")
                )
            )
        )
    )
}
