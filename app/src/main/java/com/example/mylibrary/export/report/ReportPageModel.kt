package com.example.mylibrary.export.report

import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.FixedMediaStatistics
import com.example.mylibrary.domain.model.ItemTypeKind
import com.example.mylibrary.export.calendar.CalendarExportSnapshot
import com.example.mylibrary.export.calendar.buildCalendarExportSnapshot
import com.example.mylibrary.ui.settings.ReportShowcaseStyle
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class ReportDocumentModel(
    val period: ReportPeriod,
    val pages: List<ReportPageModel>
)

sealed interface ReportPageModel {
    val pageNumber: Int

    data class TimeAndStatus(
        override val pageNumber: Int,
        val heading: String,
        val subtitle: String,
        val itemCountLine: String,
        val narrativeSections: List<ReportNarrativeSection>,
        val statusSentence: String?,
        val closingLine: String?
    ) : ReportPageModel

    data class WorkShowcase(
        override val pageNumber: Int,
        val heading: String,
        val style: ReportShowcaseStyle,
        val items: List<ReportItemSnapshot>
    ) : ReportPageModel

    data class AnnualCalendarOverview(
        override val pageNumber: Int,
        val heading: String,
        val subtitle: String,
        val mediaScope: ReportMediaScope,
        val months: List<ReportAnnualMonth>,
        val rhythmSummary: List<String>
    ) : ReportPageModel

    data class ItemCustomInformation(
        override val pageNumber: Int,
        val heading: String,
        val subtitle: String,
        val sections: List<ReportItemInformationSection>,
        val truncated: Boolean
    ) : ReportPageModel

    data class CustomInformationInsights(
        override val pageNumber: Int,
        val heading: String,
        val subtitle: String,
        val fieldStatistics: List<ReportFieldStatisticBlock>,
        val keywordHeading: String,
        val wordCloud: List<ReportWordCloudPlacement>
    ) : ReportPageModel

    data class QuoteSelection(
        override val pageNumber: Int,
        val heading: String,
        val quotes: List<ReportSelectedQuote>,
        val layout: ReportQuoteLayout
    ) : ReportPageModel
}

data class ReportNarrativeSection(
    val heading: String?,
    val paragraphs: List<String>
)

data class ReportItemInformationSection(
    val itemHeading: String,
    val columns: List<ReportItemInformationColumn>,
    val entries: List<ReportItemInformationEntry>
)

data class ReportItemInformationColumn(
    val fieldId: Long,
    val heading: String
)

data class ReportItemInformationEntry(
    val itemId: Long,
    val title: String,
    val values: List<String>
)

data class ReportFieldStatisticBlock(
    val typeKind: ItemTypeKind,
    val fieldType: FieldDataType,
    val statistic: CustomFieldStatistic
)

enum class ReportWordTier {
    HIGHEST,
    HIGH,
    MEDIUM,
    OTHER
}

data class ReportWordCloudPlacement(
    val text: String,
    val tier: ReportWordTier,
    val centerXFraction: Float,
    val centerYFraction: Float,
    val maxWidthFraction: Float
) {
    init {
        require(centerXFraction in 0f..1f)
        require(centerYFraction in 0f..1f)
        require(maxWidthFraction in 0f..1f)
    }
}

data class ReportSelectedQuote(
    val quote: ReportQuoteSnapshot,
    val creator: String?
)

data class ReportAnnualMonth(
    val month: Int,
    val calendar: CalendarExportSnapshot,
    val itemCount: Int,
    val totalDurationMinutes: Long?,
    val activityFraction: Float,
    val caption: String?
)

enum class ReportQuoteLayout {
    ONE_CENTER,
    TWO_ROWS,
    THREE_FEATURED,
    FOUR_GRID,
    SIX_GRID
}

object ReportPageModelFactory {
    fun create(snapshot: ReportDataSnapshot): ReportDocumentModel {
        val config = snapshot.config
        val representativeItems = snapshot.items.sortedWith(representativeItemOrder())
        val pages = mutableListOf<ReportPageModel>()

        pages += ReportPageModel.TimeAndStatus(
            pageNumber = 1,
            heading = config.period.coverHeading(),
            subtitle = config.period.openingSubtitle(config.mediaScope),
            itemCountLine = config.period.itemCountLine(
                scope = config.mediaScope,
                summary = snapshot.summary
            ),
            narrativeSections = if (config.includeBasicStatistics) {
                basicNarratives(
                    scope = config.mediaScope,
                    statistics = snapshot.mediaStatistics
                )
            } else {
                emptyList()
            },
            statusSentence = if (config.includeItemStatusStatistics) {
                statusSentence(
                    period = config.period,
                    scope = config.mediaScope,
                    values = snapshot.summary.statusCounts
                )
            } else {
                null
            },
            closingLine = "这一年的片段，已经被好好保存。"
                .takeIf { config.period is ReportPeriod.Year }
        )

        if (config.period is ReportPeriod.Year) {
            pages += buildAnnualCalendarPage(snapshot, pageNumber = 2)
            buildInsightsPage(snapshot, pageNumber = pages.size + 1)
                ?.let(pages::add)
            if (config.includeQuotes) {
                val selectedQuotes = selectQuotes(
                    period = config.period,
                    orderedItems = representativeItems,
                    quotes = snapshot.quotes,
                    limit = YEARLY_QUOTE_LIMIT
                )
                if (selectedQuotes.isNotEmpty()) {
                    pages += ReportPageModel.QuoteSelection(
                        pageNumber = pages.size + 1,
                        heading = "年度摘录",
                        quotes = selectedQuotes,
                        layout = quoteLayout(selectedQuotes.size)
                    )
                }
            }
            check(pages.size in 2..MAX_YEARLY_REPORT_PAGES)
            return ReportDocumentModel(config.period, pages)
        }

        if (config.includeItemInformation) {
            val maximum = when (config.showcaseStyle) {
                ReportShowcaseStyle.COLLAGE -> MONTHLY_COLLAGE_LIMIT
                ReportShowcaseStyle.GRID -> GRID_LIMIT
            }
            val showcaseItems = representativeItems
                .asSequence()
                .filter {
                    config.showcaseStyle != ReportShowcaseStyle.COLLAGE ||
                        it.resolvedCoverAspectRatio != null
                }
                .take(maximum)
                .toList()
            if (showcaseItems.isNotEmpty()) {
                pages += ReportPageModel.WorkShowcase(
                    pageNumber = pages.size + 1,
                    heading = "作品展示",
                    style = config.showcaseStyle,
                    items = showcaseItems
                )
            }
        }

        if (config.includeItemFields) {
            buildItemInformationPage(
                snapshot = snapshot,
                orderedItems = representativeItems,
                pageNumber = pages.size + 1
            )?.let(pages::add)
        }

        buildInsightsPage(snapshot, pageNumber = pages.size + 1)
            ?.let(pages::add)

        if (config.includeQuotes) {
            val selectedQuotes = selectQuotes(
                period = config.period,
                orderedItems = representativeItems,
                quotes = snapshot.quotes,
                limit = MONTHLY_QUOTE_LIMIT
            )
            if (selectedQuotes.isNotEmpty()) {
                pages += ReportPageModel.QuoteSelection(
                    pageNumber = pages.size + 1,
                    heading = "摘录",
                    quotes = selectedQuotes,
                    layout = quoteLayout(selectedQuotes.size)
                )
            }
        }

        check(pages.size <= MAX_MONTHLY_REPORT_PAGES)
        return ReportDocumentModel(config.period, pages)
    }

    private fun buildInsightsPage(
        snapshot: ReportDataSnapshot,
        pageNumber: Int
    ): ReportPageModel.CustomInformationInsights? {
        val fieldLimit = if (snapshot.config.period is ReportPeriod.Month) {
            MONTHLY_FIELD_STATISTIC_LIMIT
        } else {
            YEARLY_FIELD_STATISTIC_LIMIT
        }
        val fieldBlocks = if (snapshot.config.includeFieldStatistics) {
            fieldStatisticBlocks(snapshot).take(fieldLimit)
        } else {
            emptyList()
        }
        val words = if (snapshot.config.includeTagStatistics) {
            buildWordCloud(snapshot.summary.tagCounts.take(WORD_CLOUD_LIMIT))
        } else {
            emptyList()
        }
        if (fieldBlocks.isEmpty() && words.isEmpty()) return null
        return ReportPageModel.CustomInformationInsights(
            pageNumber = pageNumber,
            heading = if (snapshot.config.period is ReportPeriod.Month) {
                "自定义信息统计"
            } else {
                "年度关键词与自定义信息"
            },
            subtitle = "这些作品在自定义信息上的整体分布",
            fieldStatistics = fieldBlocks,
            keywordHeading = if (snapshot.config.period is ReportPeriod.Month) {
                "本月关键词"
            } else {
                "年度关键词"
            },
            wordCloud = words
        )
    }

    private fun buildAnnualCalendarPage(
        snapshot: ReportDataSnapshot,
        pageNumber: Int
    ): ReportPageModel.AnnualCalendarOverview {
        val period = snapshot.config.period as ReportPeriod.Year
        val sourceMonths = snapshot.monthlySummaries.associateBy(ReportMonthSnapshot::month)
        val calendars = snapshot.annualCalendarSnapshots
            .associateBy { it.yearMonth.monthValue }
        val completeMonths = (1..12).map { month ->
            sourceMonths[month] ?: ReportMonthSnapshot(
                month = month,
                itemCount = 0,
                representativeItemId = null
            )
        }
        val maximumActivity = completeMonths.maxOfOrNull { month ->
            month.activityValue()
        }?.coerceAtLeast(1L) ?: 1L
        val months = completeMonths.map { month ->
            val yearMonth = YearMonth.of(period.year, month.month)
            val activity = month.activityValue()
            ReportAnnualMonth(
                month = month.month,
                calendar = calendars[month.month]
                    ?: buildCalendarExportSnapshot(yearMonth, emptyList()),
                itemCount = month.itemCount,
                totalDurationMinutes = month.totalDurationMinutes,
                activityFraction = (activity.toFloat() / maximumActivity)
                    .coerceIn(0f, 1f),
                caption = "全年最投入".takeIf {
                    activity > 0L && activity == maximumActivity
                }
            )
        }
        return ReportPageModel.AnnualCalendarOverview(
            pageNumber = pageNumber,
            heading = "十二个月的足迹",
            subtitle = "${period.year} · YEAR IN MONTHS",
            mediaScope = snapshot.config.mediaScope,
            months = months,
            rhythmSummary = annualRhythmSummary(
                completeMonths,
                snapshot.config.mediaScope
            )
        )
    }

    private fun annualRhythmSummary(
        months: List<ReportMonthSnapshot>,
        scope: ReportMediaScope
    ): List<String> {
        val active = months.filter { it.itemCount > 0 }
        if (active.isEmpty()) return listOf("这一年的记录仍在等待被填满。")
        val highest = active.maxWithOrNull(
            compareBy<ReportMonthSnapshot> { it.activityValue() }
                .thenByDescending { -it.month }
        )
        val lines = mutableListOf<String>()
        val itemLabel = when (scope) {
            ReportMediaScope.ALL -> "部作品"
            ReportMediaScope.BOOK -> "本书"
            ReportMediaScope.MOVIE -> "部电影"
        }
        highest?.let {
            lines += "${it.month}月是这一年最投入的月份，共有 " +
                "${it.itemCount} $itemLabel 留下记录。"
        }
        longestActiveRun(months)?.takeIf { it.last > it.first }?.let { run ->
            lines += "${run.first}月至${run.last}月，你保持了持续而稳定的记录节奏。"
        }
        val quiet = active.minOfOrNull {
            it.activityValue()
        }?.let { minimum ->
            active.filter {
                it.activityValue() == minimum
            }.take(2)
        }.orEmpty()
        if (quiet.isNotEmpty() && quiet.none { it.month == highest?.month }) {
            lines += quiet.joinToString("与") { "${it.month}月" } + "留下的记录最为轻盈。"
        }
        return lines.take(3)
    }

    private fun longestActiveRun(
        months: List<ReportMonthSnapshot>
    ): IntRange? {
        var best: IntRange? = null
        var start: Int? = null
        months.forEach { month ->
            if (month.itemCount > 0) {
                if (start == null) start = month.month
            } else {
                start?.let {
                    val candidate = it..(month.month - 1)
                    if (best == null || candidate.count() > best!!.count()) best = candidate
                }
                start = null
            }
        }
        start?.let {
            val candidate = it..12
            if (best == null || candidate.count() > best!!.count()) best = candidate
        }
        return best
    }

    private fun ReportMonthSnapshot.activityValue(): Long =
        totalDurationMinutes?.takeIf { it > 0L } ?: recordCount.toLong()

    private fun buildItemInformationPage(
        snapshot: ReportDataSnapshot,
        orderedItems: List<ReportItemSnapshot>,
        pageNumber: Int
    ): ReportPageModel.ItemCustomInformation? {
        val limit = if (snapshot.config.period is ReportPeriod.Month) {
            MONTHLY_ITEM_INFORMATION_LIMIT
        } else {
            YEARLY_ITEM_INFORMATION_LIMIT
        }
        val selectedItems = orderedItems.take(limit)
        val sectionKinds = when (snapshot.config.mediaScope) {
            ReportMediaScope.ALL -> listOf(
                ItemTypeKind.BOOK to "书籍",
                ItemTypeKind.MOVIE to "电影"
            )
            ReportMediaScope.BOOK -> listOf(ItemTypeKind.BOOK to "书籍")
            ReportMediaScope.MOVIE -> listOf(ItemTypeKind.MOVIE to "电影")
        }
        val sections = sectionKinds.mapNotNull { (kind, itemHeading) ->
            val sectionItems = selectedItems.filter { it.typeKind == kind }
            if (sectionItems.isEmpty()) return@mapNotNull null
            val columns = snapshot.config.workFields
                .asSequence()
                .filter { it.itemTypeId == kind.stableTypeId }
                .filter { field ->
                    sectionItems.any { item ->
                        item.customFields.any {
                            it.field.fieldId == field.fieldId &&
                                it.formattedValue.isNotBlank()
                        }
                    }
                }
                .sortedWith(
                    compareBy<ResolvedReportField> { it.fieldSortOrder }
                        .thenBy { it.fieldId }
                )
                .distinctBy { it.fieldId }
                .take(ReportItemTablePolicy.MAX_FIELD_COLUMNS)
                .map { ReportItemInformationColumn(it.fieldId, it.fieldName) }
                .toList()
            if (columns.isEmpty()) return@mapNotNull null
            ReportItemInformationSection(
                itemHeading = itemHeading,
                columns = columns,
                entries = sectionItems.map { item ->
                    val valuesByField = item.customFields.associateBy {
                        it.field.fieldId
                    }
                    ReportItemInformationEntry(
                        itemId = item.itemId,
                        title = item.title,
                        values = columns.map { column ->
                            valuesByField[column.fieldId]
                                ?.formattedValue
                                ?.takeIf(String::isNotBlank)
                                ?: "—"
                        }
                    )
                }
            )
        }
        if (sections.isEmpty()) return null
        return ReportPageModel.ItemCustomInformation(
            pageNumber = pageNumber,
            heading = "作品的自定义信息",
            subtitle = "每部作品所记录的补充内容",
            sections = sections,
            truncated = orderedItems.size > selectedItems.size
        )
    }

    private fun fieldStatisticBlocks(
        snapshot: ReportDataSnapshot
    ): List<ReportFieldStatisticBlock> {
        val fields = snapshot.config.statisticFields.associateBy(ResolvedReportField::fieldId)
        return snapshot.customFieldStatistics.flatMap { group ->
            group.statistics.mapNotNull { statistic ->
                val field = fields[statistic.fieldId] ?: return@mapNotNull null
                ReportFieldStatisticBlock(
                    typeKind = group.typeKind,
                    fieldType = field.fieldType,
                    statistic = statistic
                )
            }
        }
    }

    private fun basicNarratives(
        scope: ReportMediaScope,
        statistics: FixedMediaStatistics
    ): List<ReportNarrativeSection> = when (scope) {
        ReportMediaScope.BOOK ->
            listOfNotNull(mediaNarrative(statistics.reading, reading = true, null))
        ReportMediaScope.MOVIE ->
            listOfNotNull(mediaNarrative(statistics.watching, reading = false, null))
        ReportMediaScope.ALL -> listOfNotNull(
            mediaNarrative(statistics.reading, reading = true, heading = "阅读"),
            mediaNarrative(statistics.watching, reading = false, heading = "观看")
        )
    }

    private fun mediaNarrative(
        statistics: com.example.mylibrary.domain.model.MediaCategoryStatistics,
        reading: Boolean,
        heading: String?
    ): ReportNarrativeSection? {
        if (statistics.itemCount <= 0L) return null
        val activity = if (reading) "阅读" else "观看"
        val perItem = if (reading) "每本书" else "每部电影"
        val longestLabel = if (reading) "停留最久" else "观看最久"
        val paragraphs = buildList {
            statistics.totalDurationMinutes?.let {
                add("一共在${activity}中停留了 ${formatDuration(it)}。")
            }
            val perRecord = statistics.averagePerRecordMinutes
            val maximum = statistics.maximumSingleDurationMinutes
            if (perRecord != null || maximum != null) {
                add(
                    listOfNotNull(
                        perRecord?.let { "平均每次${activity} ${formatDuration(it)}" },
                        maximum?.let { "最长的一次持续了 ${formatDuration(it)}" }
                    ).joinToString("，") + "。"
                )
            }
            statistics.averagePerItemMinutes?.let {
                add("平均$perItem 陪伴了你 ${formatDuration(it)}。")
            }
            if (
                statistics.longestItemTitle != null &&
                statistics.longestItemDurationMinutes != null
            ) {
                add(
                    "《${statistics.longestItemTitle}》$longestLabel，" +
                        "共 ${formatDuration(statistics.longestItemDurationMinutes)}。"
                )
            }
        }
        return paragraphs.takeIf(List<String>::isNotEmpty)
            ?.let { ReportNarrativeSection(heading, it) }
    }

    private fun statusSentence(
        period: ReportPeriod,
        scope: ReportMediaScope,
        values: List<ReportNamedCount>
    ): String? {
        val positiveValues = values.filter { it.count > 0 }
        if (positiveValues.isEmpty()) return null
        val unit = if (scope == ReportMediaScope.BOOK) "本" else "部"
        val ordered = positiveValues.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<ReportNamedCount>> { it.value.count }
                    .thenBy { it.index }
            )
            .map(IndexedValue<ReportNamedCount>::value)
        val periodText = if (period is ReportPeriod.Month) "这个月" else "这一年"
        return "$periodText：" + ordered.joinToString("，") {
            "${it.name} ${it.count}$unit"
        } + "。"
    }

    private fun selectQuotes(
        period: ReportPeriod,
        orderedItems: List<ReportItemSnapshot>,
        quotes: List<ReportQuoteSnapshot>,
        limit: Int
    ): List<ReportSelectedQuote> {
        val quotesByItem = quotes.groupBy(ReportQuoteSnapshot::itemId)
        val selectedByItem = orderedItems.asSequence()
            .mapNotNull { item ->
                val candidates = quotesByItem[item.itemId]
                    .orEmpty()
                    .sortedWith(
                        compareBy<ReportQuoteSnapshot> { it.createdTime }
                            .thenBy { it.quoteId }
                    )
                if (candidates.isEmpty()) return@mapNotNull null
                val index = stableQuoteIndex(period, item.itemId, candidates.size)
                ReportSelectedQuote(candidates[index], item.creator)
            }
            .toList()
        if (period !is ReportPeriod.Year) return selectedByItem.take(limit)
        val seenMonths = linkedSetOf<Int>()
        val diverse = selectedByItem.filter { selected ->
            val month = Instant.ofEpochMilli(selected.quote.createdTime)
                .atZone(ZoneId.systemDefault())
                .monthValue
            seenMonths.add(month)
        }
        return (diverse + selectedByItem.filterNot(diverse::contains))
            .distinctBy { it.quote.quoteId }
            .take(limit)
    }

    internal fun stableQuoteIndex(
        period: ReportPeriod,
        itemId: Long,
        size: Int
    ): Int {
        require(size > 0)
        val periodSeed = when (period) {
            is ReportPeriod.Month ->
                period.year.toLong() * 100L + period.month
            is ReportPeriod.Year -> period.year.toLong()
        }
        var value = periodSeed xor (itemId * -7046029254386353131L)
        value = value xor (value ushr 33)
        value *= -49064778989728563L
        value = value xor (value ushr 33)
        return Math.floorMod(value, size.toLong()).toInt()
    }

    private fun quoteLayout(count: Int): ReportQuoteLayout = when (count) {
        1 -> ReportQuoteLayout.ONE_CENTER
        2 -> ReportQuoteLayout.TWO_ROWS
        3 -> ReportQuoteLayout.THREE_FEATURED
        4 -> ReportQuoteLayout.FOUR_GRID
        else -> ReportQuoteLayout.SIX_GRID
    }

    internal fun buildWordCloud(
        values: List<ReportNamedCount>
    ): List<ReportWordCloudPlacement> {
        if (values.isEmpty()) return emptyList()
        val slots = wordCloudSlots(values.size)
        return values.zip(slots).mapIndexed { index, (value, slot) ->
            ReportWordCloudPlacement(
                text = value.name,
                tier = when {
                    index == 0 -> ReportWordTier.HIGHEST
                    index < maxOf(2, values.size / 4) -> ReportWordTier.HIGH
                    index < maxOf(3, values.size / 2) -> ReportWordTier.MEDIUM
                    else -> ReportWordTier.OTHER
                },
                centerXFraction = slot.first,
                centerYFraction = slot.second,
                maxWidthFraction = slot.third
            )
        }
    }

    private fun wordCloudSlots(count: Int): List<Triple<Float, Float, Float>> {
        require(count in 1..WORD_CLOUD_LIMIT)
        if (count == 1) return listOf(Triple(0.5f, 0.5f, 0.9f))
        if (count == 2) return listOf(
            Triple(0.33f, 0.45f, 0.58f),
            Triple(0.7f, 0.6f, 0.5f)
        )
        if (count == 3) return listOf(
            Triple(0.5f, 0.28f, 0.7f),
            Triple(0.28f, 0.68f, 0.46f),
            Triple(0.72f, 0.68f, 0.46f)
        )
        val rowCount = when (count) {
            in 4..6 -> 2
            in 7..9 -> 3
            else -> 4
        }
        val rows = List(rowCount) { mutableListOf<Int>() }
        (0 until count).forEach { index ->
            rows[index % rowCount] += index
        }
        val slots = MutableList<Triple<Float, Float, Float>?>(count) { null }
        rows.forEachIndexed { rowIndex, indices ->
            indices.forEachIndexed { columnIndex, originalIndex ->
                val width = 1f / indices.size
                slots[originalIndex] = Triple(
                    (columnIndex + 0.5f) * width,
                    (rowIndex + 0.5f) / rowCount,
                    width * 0.9f
                )
            }
        }
        return slots.map(::requireNotNull)
    }

    private fun representativeItemOrder(): Comparator<ReportItemSnapshot> =
        compareByDescending<ReportItemSnapshot> { it.activityDayCount }
            .thenByDescending { it.periodDurationMinutes != null }
            .thenByDescending { it.periodDurationMinutes ?: 0L }
            .thenBy { it.firstActivityDate }
            .thenBy { it.itemId }

    private fun ReportPeriod.coverHeading(): String = when (this) {
        is ReportPeriod.Month -> "${year}年${month}月"
        is ReportPeriod.Year -> year.toString()
    }

    private val ItemTypeKind.stableTypeId: Long
        get() = when (this) {
            ItemTypeKind.BOOK -> ItemTypeKind.BOOK_TYPE_ID
            ItemTypeKind.MOVIE -> ItemTypeKind.MOVIE_TYPE_ID
            ItemTypeKind.CUSTOM -> Long.MIN_VALUE
        }

    private fun ReportPeriod.openingSubtitle(scope: ReportMediaScope): String =
        when (this) {
            is ReportPeriod.Month -> "时间与状态"
            is ReportPeriod.Year -> when (scope) {
                ReportMediaScope.ALL -> "年度文化回顾"
                ReportMediaScope.BOOK -> "年度阅读回顾"
                ReportMediaScope.MOVIE -> "年度观看回顾"
            }
        }

    private fun ReportPeriod.itemCountLine(
        scope: ReportMediaScope,
        summary: ReportSummarySnapshot
    ): String {
        val periodText = if (this is ReportPeriod.Month) "这个月" else "这一年"
        val countText = when (scope) {
            ReportMediaScope.ALL -> "记录了 ${summary.itemCount} 部作品"
            ReportMediaScope.BOOK -> "读了 ${summary.itemCount} 本书"
            ReportMediaScope.MOVIE -> "看了 ${summary.itemCount} 部电影"
        }
        return if (this is ReportPeriod.Month) {
            "$periodText，你$countText。"
        } else {
            "$periodText，你$countText，写下了 ${summary.recordCount} 条记录，" +
                "也留下了 ${summary.quoteCount} 段文字。"
        }
    }

    private fun formatDuration(minutes: Long): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 ->
                String.format(Locale.ROOT, "%d小时%d分钟", hours, rest)
            hours > 0 -> "${hours}小时"
            else -> "${rest}分钟"
        }
    }

    private const val MAX_MONTHLY_REPORT_PAGES = 5
    private const val MAX_YEARLY_REPORT_PAGES = 4
    private const val GRID_LIMIT = 6
    private const val MONTHLY_COLLAGE_LIMIT = 20
    private const val MONTHLY_ITEM_INFORMATION_LIMIT = 8
    private const val YEARLY_ITEM_INFORMATION_LIMIT = 16
    private const val MONTHLY_FIELD_STATISTIC_LIMIT = 4
    private const val YEARLY_FIELD_STATISTIC_LIMIT = 6
    private const val WORD_CLOUD_LIMIT = 12
    private const val MONTHLY_QUOTE_LIMIT = 4
    private const val YEARLY_QUOTE_LIMIT = 4
}
