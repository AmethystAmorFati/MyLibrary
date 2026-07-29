package com.example.mylibrary.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.SimpleTopBar
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarActionSize
import com.example.mylibrary.ui.theme.TopBarToContentGap
import java.time.LocalDate

@Composable
fun AnnualCalendarScreen(
    state: AnnualCalendarUiState,
    initialYear: Int,
    initialMonth: Int,
    selectedDate: LocalDate,
    onBack: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    AppScreenContainer(
        modifier = modifier.testTag("home_year_calendar")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            SimpleTopBar(title = "全年月历", onBack = onBack)
            YearHeader(
                year = state.year,
                onPrevious = onPreviousYear,
                onNext = onNextYear,
                modifier = Modifier
                    .padding(
                        horizontal = ScreenHorizontalPadding,
                        vertical = TopBarToContentGap
                    )
            )
            key(state.year) {
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = annualInitialMonthIndex(
                        currentYear = state.year,
                        initialYear = initialYear,
                        initialMonth = initialMonth
                    )
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = ScreenHorizontalPadding),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    items(
                        items = state.months,
                        key = { it.yearMonth }
                    ) { month ->
                        AnnualCalendarMonth(
                            month = month,
                            selectedDate = selectedDate,
                            onDateSelected = onDateSelected
                        )
                    }
                }
            }
        }
        state.errorMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(ScreenHorizontalPadding),
                style = AppTheme.typography.body,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}

internal fun annualInitialMonthIndex(
    currentYear: Int,
    initialYear: Int,
    initialMonth: Int
): Int =
    if (currentYear == initialYear) {
        initialMonth.coerceIn(1, 12) - 1
    } else {
        0
    }

@Composable
private fun YearHeader(
    year: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AnnualArrow(
            previous = true,
            onClick = onPrevious,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = "${year}年",
            style = AppTheme.typography.pageTitle,
            color = AppTheme.colors.textPrimary
        )
        AnnualArrow(
            previous = false,
            onClick = onNext,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun AnnualArrow(
    previous: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(TopBarActionSize)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (previous) {
                Icons.Rounded.ChevronLeft
            } else {
                Icons.Rounded.ChevronRight
            },
            contentDescription = if (previous) "上一年" else "下一年",
            tint = AppTheme.colors.textPrimary
        )
    }
}
