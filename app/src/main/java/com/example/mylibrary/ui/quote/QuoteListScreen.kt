package com.example.mylibrary.ui.quote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.QuoteListItem
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.LibrarySearchField
import com.example.mylibrary.ui.components.SecondaryPageHeader
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CardContentPadding
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarToContentGap

@Composable
fun QuoteListScreen(
    state: QuoteListUiState,
    showQuoteChapter: Boolean = true,
    showQuotePage: Boolean = true,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onQuoteSelected: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppScreenContainer(
        modifier = modifier.testTag("screen_quote_list")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SecondaryPageHeader(title = "", onBack = onBack)
            LibrarySearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = "搜索摘录、作品或作者 / 导演",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = ScreenHorizontalPadding,
                        end = ScreenHorizontalPadding,
                        top = TopBarToContentGap
                    )
                    .testTag("quote_search")
            )
            if (!state.isLoading && state.quotes.isEmpty()) {
                Text(
                    text = if (state.query.isBlank()) "暂无摘录" else "未找到相关摘录",
                    modifier = Modifier.padding(
                        start = ScreenHorizontalPadding,
                        end = ScreenHorizontalPadding,
                        top = 24.dp
                    ),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.mutedText
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ScreenHorizontalPadding,
                        end = ScreenHorizontalPadding,
                        top = 14.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = state.quotes,
                        key = { _, item -> item.quote.id }
                    ) { index, quote ->
                        if (index == state.quotes.lastIndex && state.hasMore) {
                            LaunchedEffect(state.query, index) { onLoadMore() }
                        }
                        QuoteListCard(
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
}

@Composable
private fun QuoteListCard(
    quote: QuoteListItem,
    showQuoteChapter: Boolean,
    showQuotePage: Boolean,
    onClick: () -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "• ${quote.quote.content}",
                style = AppTheme.typography.body,
                color = AppTheme.colors.textPrimary
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "《${quote.itemTitle}》",
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    style = AppTheme.typography.creator,
                    color = AppTheme.colors.textSecondary
                )
                quote.creator.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        style = AppTheme.typography.metadata,
                        color = AppTheme.colors.mutedText
                    )
                }
                formatQuoteLocation(
                    chapter = quote.quote.chapter,
                    page = quote.quote.page,
                    showChapter = showQuoteChapter,
                    showPage = showQuotePage
                )?.let {
                    Text(
                        text = it,
                        style = AppTheme.typography.metadata,
                        color = AppTheme.colors.mutedText
                    )
                }
                Text(
                    text = formatQuoteDate(quote.quote.createdTime),
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
            }
        }
    }
}
