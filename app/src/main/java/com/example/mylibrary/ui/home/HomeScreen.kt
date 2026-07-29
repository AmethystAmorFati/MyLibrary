package com.example.mylibrary.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.MainPageHeader
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CalendarCollapsedHeight
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarToContentGap
import com.example.mylibrary.util.toLocalDate
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    state: HomeUiState,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onExpandCalendar: () -> Unit,
    onCollapseCalendar: () -> Unit,
    onOpenAnnualCalendar: () -> Unit,
    onTimelineRecordChanged: (Long) -> Unit,
    onItemSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(
        enabled = state.calendarMode == HomeCalendarMode.MONTH,
        onBack = onCollapseCalendar
    )
    AppScreenContainer(modifier = modifier.testTag("screen_home")) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(TopBarToContentGap)
        ) {
            MainPageHeader(
                title = "MyLibrary",
                isBrand = true,
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("home_month_content")
            ) {
                if (state.isInitialLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_initial_loading")
                    )
                } else {
                    val entries = state.timelineEntries
                    val initialIndex = remember(entries) {
                        TimelineCalendarCoordinator.initialIndex(
                            entries = entries,
                            today = LocalDate.now()
                        )
                    }
                    val listState = rememberSaveable(saver = LazyListState.Saver) {
                        LazyListState(
                            firstVisibleItemIndex = initialIndex
                        )
                    }
                    val scope = rememberCoroutineScope()
                    HomeTimeline(
                        entries = entries,
                        listState = listState,
                        topContentPadding = CalendarCollapsedHeight,
                        visibleRecordSyncEnabled = true,
                        showCreator = state.timelineShowCreator,
                        showRating = state.timelineShowRating,
                        showTimelineStatus = state.timelineShowStatus,
                        showTimelineDuration = state.timelineShowDuration,
                        onVisibleRecordChanged = onTimelineRecordChanged,
                        onItemSelected = onItemSelected,
                        modifier = Modifier.fillMaxSize()
                    )
                    HomeCalendarOverlay(
                        mode = state.calendarMode,
                        displayedMonth = state.calendarDisplayMonth,
                        selectedDate = state.calendarSelectedActivityDate,
                        activities = state.activities,
                        onDateSelected = { date ->
                            onDateSelected(date)
                            val activitiesOnDate = state.activities.filter {
                                it.date.toLocalDate() == date
                            }
                            val targetRecord =
                                TimelineCalendarCoordinator.targetRecordForActivities(
                                    activities = activitiesOnDate,
                                    timelineRecords = state.timelineRecords
                                )
                            targetRecord?.let { record ->
                                TimelineCalendarCoordinator.indexForRecord(
                                    entries = entries,
                                    recordId = record.recordId
                                )
                            }?.let { index ->
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        },
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onExpand = onExpandCalendar,
                        onCollapse = onCollapseCalendar,
                        onOpenAnnualCalendar = onOpenAnnualCalendar,
                        modifier = Modifier
                            .padding(horizontal = ScreenHorizontalPadding)
                            .zIndex(1f)
                    )
                }
            }
        }
        state.errorMessage?.let { message ->
            androidx.compose.material3.Text(
                text = message,
                modifier = Modifier.padding(ScreenHorizontalPadding),
                style = AppTheme.typography.body,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}
