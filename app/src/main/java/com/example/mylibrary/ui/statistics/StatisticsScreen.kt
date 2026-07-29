package com.example.mylibrary.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.QuoteListItem
import com.example.mylibrary.domain.model.MediaCategoryStatistics
import com.example.mylibrary.domain.model.CustomFieldStatistic
import com.example.mylibrary.domain.model.DistributionEntry
import com.example.mylibrary.domain.model.FieldAggregation
import com.example.mylibrary.domain.model.NumericMetric
import com.example.mylibrary.ui.components.MainPageHeader
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.MainPageLayout
import com.example.mylibrary.ui.components.SectionTitle
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.quote.formatQuoteDate
import com.example.mylibrary.ui.quote.formatQuoteLocation
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.BottomContentPadding
import com.example.mylibrary.ui.theme.CardContentPadding
import com.example.mylibrary.ui.theme.ContentSpacing
import com.example.mylibrary.ui.theme.SectionSpacing
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.util.formatDuration

@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    showQuoteChapter: Boolean = true,
    showQuotePage: Boolean = true,
    onQuoteSelected: (Long) -> Unit,
    onViewAllQuotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    MainPageLayout(
        modifier = modifier.testTag("screen_statistics")
    ) {
        MainPageHeader(title = "统计与摘录")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 0.dp,
                bottom = BottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            if (state.mediaStatistics.reading.itemCount > 0L) {
                item(key = "reading_statistics") {
                    MediaCategoryStatisticsCard(
                        title = "阅读",
                        itemUnit = "本",
                        longestLabel = "阅读最久作品",
                        statistics = state.mediaStatistics.reading,
                        testTag = "reading_statistics_card"
                    )
                }
            }
            if (state.mediaStatistics.watching.itemCount > 0L) {
                item(key = "watching_statistics") {
                    MediaCategoryStatisticsCard(
                        title = "观看",
                        itemUnit = "部",
                        longestLabel = "观看最久作品",
                        statistics = state.mediaStatistics.watching,
                        testTag = "watching_statistics_card"
                    )
                }
            }
            if (state.customFieldStatistics.isNotEmpty()) {
                item(key = "custom_field_statistics") {
                    CustomFieldStatisticsSection(state.customFieldStatistics)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ContentSpacing)) {
                    SectionTitle(title = "最近 5 条摘录")
                    RecentQuotesCard(
                        quotes = state.recentQuotes,
                        showQuoteChapter = showQuoteChapter,
                        showQuotePage = showQuotePage,
                        onQuoteSelected = onQuoteSelected
                    )
                    Text(
                        text = "查看全部摘录",
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable(onClick = onViewAllQuotes)
                            .padding(vertical = 8.dp),
                        style = AppTheme.typography.button,
                        color = AppTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFieldStatisticsSection(
    statistics: List<CustomFieldStatistic>
) {
    Column(
        modifier = Modifier.testTag("custom_statistics_section"),
        verticalArrangement = Arrangement.spacedBy(ContentSpacing)
    ) {
        SectionTitle(title = "自定义字段统计")
        Column(verticalArrangement = Arrangement.spacedBy(ContentSpacing)) {
            statistics.forEach { statistic ->
                CustomStatisticCard(statistic)
            }
        }
    }
}

@Composable
private fun CustomStatisticCard(statistic: CustomFieldStatistic) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_statistic_${statistic.fieldId}"),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = statistic.fieldName,
                style = AppTheme.typography.sectionTitle,
                color = AppTheme.colors.textPrimary
            )
            when (statistic) {
                is CustomFieldStatistic.Numeric -> NumericStatisticContent(statistic.metrics)
                is CustomFieldStatistic.OptionDistribution ->
                    DistributionContent(statistic.entries)
                is CustomFieldStatistic.Rating -> RatingStatisticContent(statistic)
            }
        }
    }
}

@Composable
private fun NumericStatisticContent(metrics: List<NumericMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (metrics.size == 1) {
            NumericMetricContent(metrics.single())
        } else {
            metrics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    row.forEach { metric ->
                        NumericMetricContent(
                            metric = metric,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericMetricContent(
    metric: NumericMetric,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = buildString {
                append(metric.value)
                metric.unit?.takeIf(String::isNotBlank)?.let {
                    append(" ")
                    append(it)
                }
            },
            style = AppTheme.typography.pageTitle,
            color = AppTheme.colors.textPrimary
        )
        Text(
            text = metric.aggregation.displayName(),
            style = AppTheme.typography.metadata,
            color = AppTheme.colors.textSecondary
        )
    }
}

@Composable
private fun DistributionContent(entries: List<DistributionEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        entries.forEach { entry ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.label,
                    modifier = Modifier.weight(1f),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = entry.count.toString(),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun RatingStatisticContent(statistic: CustomFieldStatistic.Rating) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        statistic.average?.let { average ->
            Column {
                Text(
                    text = average,
                    style = AppTheme.typography.pageTitle,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = "平均评分",
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
        if (statistic.distribution.isNotEmpty()) {
            DistributionContent(statistic.distribution)
        }
    }
}

private fun FieldAggregation.displayName(): String = when (this) {
    FieldAggregation.SUM -> "总计"
    FieldAggregation.AVERAGE -> "平均"
    FieldAggregation.MAXIMUM -> "最大值"
    FieldAggregation.MINIMUM -> "最小值"
    FieldAggregation.OPTION_DISTRIBUTION -> "选项分布"
    FieldAggregation.RATING_AVERAGE -> "平均评分"
    FieldAggregation.RATING_DISTRIBUTION -> "评分分布"
}

@Composable
private fun MediaCategoryStatisticsCard(
    title: String,
    itemUnit: String,
    longestLabel: String,
    statistics: MediaCategoryStatistics,
    testTag: String
) {
    val hasDuration = statistics.valuedRecordCount > 0L
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = AppTheme.typography.sectionTitle,
                color = AppTheme.colors.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CategoryCountMetric("作品", "${statistics.itemCount} $itemUnit")
                CategoryCountMetric("记录", "${statistics.recordCount} 次")
                CategoryCountMetric("摘录", "${statistics.quoteCount} 条")
            }
            if (hasDuration) {
                HorizontalDivider(color = AppTheme.colors.subtleBorder)
                DurationMetricRow(
                    label = "总时长",
                    minutes = requireNotNull(statistics.totalDurationMinutes),
                    modifier = Modifier.testTag("${testTag}_duration")
                )
                DurationMetricRow(
                    label = "平均每次",
                    minutes = requireNotNull(statistics.averagePerRecordMinutes)
                )
                DurationMetricRow(
                    label = "最长单次",
                    minutes = requireNotNull(statistics.maximumSingleDurationMinutes)
                )
                DurationMetricRow(
                    label = "平均每$itemUnit",
                    minutes = requireNotNull(statistics.averagePerItemMinutes)
                )
                if (
                    statistics.longestItemTitle != null &&
                    statistics.longestItemDurationMinutes != null
                ) {
                    HorizontalDivider(color = AppTheme.colors.subtleBorder)
                    Text(
                        text = longestLabel,
                        style = AppTheme.typography.metadata,
                        color = AppTheme.colors.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "《${statistics.longestItemTitle}》",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = AppTheme.typography.body,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = requireNotNull(
                                formatDuration(
                                    statistics.longestItemDurationMinutes
                                )
                            ),
                            maxLines = 1,
                            style = AppTheme.typography.metadata,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCountMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTheme.typography.metadata,
            color = AppTheme.colors.textSecondary
        )
        Text(
            text = value,
            style = AppTheme.typography.body,
            color = AppTheme.colors.textPrimary
        )
    }
}

@Composable
private fun DurationMetricRow(
    label: String,
    minutes: Long,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = AppTheme.typography.metadata,
            color = AppTheme.colors.textSecondary
        )
        Text(
            text = requireNotNull(formatDuration(minutes)),
            maxLines = 1,
            style = AppTheme.typography.body,
            color = AppTheme.colors.textPrimary
        )
    }
}

@Composable
private fun RecentQuotesCard(
    quotes: List<QuoteListItem>,
    showQuoteChapter: Boolean,
    showQuotePage: Boolean,
    onQuoteSelected: (Long) -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_quotes"),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        if (quotes.isEmpty()) {
            EmptyCardText("暂无摘录")
        } else {
            Column(modifier = Modifier.padding(horizontal = CardContentPadding)) {
                quotes.forEachIndexed { index, quote ->
                    if (index > 0) {
                        HorizontalDivider(color = AppTheme.colors.subtleBorder)
                    }
                    RecentQuoteRow(
                        quote = quote,
                        showQuoteChapter = showQuoteChapter,
                        showQuotePage = showQuotePage,
                        onClick = { onQuoteSelected(quote.quote.itemId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentQuoteRow(
    quote: QuoteListItem,
    showQuoteChapter: Boolean,
    showQuotePage: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "• ${quote.quote.content}",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = AppTheme.typography.body,
            color = AppTheme.colors.textPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "《${quote.itemTitle}》",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.textSecondary
            )
            formatQuoteLocation(
                chapter = quote.quote.chapter,
                page = quote.quote.page,
                showChapter = showQuoteChapter,
                showPage = showQuotePage
            )?.let {
                Text(
                    text = "  $it",
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.textSecondary
                )
            }
            Text(
                text = "  ${formatQuoteDate(quote.quote.createdTime)}",
                style = AppTheme.typography.metadata,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun EmptyCardText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(CardContentPadding),
        style = AppTheme.typography.body,
        color = AppTheme.colors.mutedText
    )
}
