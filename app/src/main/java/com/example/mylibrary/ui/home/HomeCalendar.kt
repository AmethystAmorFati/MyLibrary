package com.example.mylibrary.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.navigation.AppNavigationTransitions
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.CalendarAnchorThreshold
import com.example.mylibrary.ui.theme.CalendarCellAspectRatio
import com.example.mylibrary.ui.theme.CalendarCollapsedHeight
import com.example.mylibrary.ui.theme.CalendarDayRowHeight
import com.example.mylibrary.ui.theme.CalendarDaySpacing
import com.example.mylibrary.ui.theme.CalendarHandleHeight
import com.example.mylibrary.ui.theme.CalendarHeaderHeight
import com.example.mylibrary.ui.theme.CalendarVelocityThreshold
import com.example.mylibrary.ui.theme.CalendarWeekdayHeight
import com.example.mylibrary.util.buildMonthDates
import com.example.mylibrary.util.toLocalDate
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.distinctUntilChanged

private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeCalendarOverlay(
    mode: HomeCalendarMode,
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    activities: List<LibraryActivity>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onOpenAnnualCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val monthDates = remember(displayedMonth) { calendarMonthDates(displayedMonth) }
    val monthRowCount = monthDates.size / 7
    val expandedGridHeight =
        CalendarDayRowHeight * monthRowCount +
            CalendarDaySpacing * (monthRowCount - 1)
    val expandedHeight =
        CalendarCollapsedHeight + expandedGridHeight - CalendarDayRowHeight
    val collapsedPx = with(density) { CalendarCollapsedHeight.toPx() }
    val expandedPx = with(density) { expandedHeight.toPx() }
    val anchorDistancePx = expandedPx - collapsedPx
    val velocityThresholdPx = with(density) { CalendarVelocityThreshold.toPx() }
    val initialAnchor = if (mode == HomeCalendarMode.MONTH) {
        CalendarAnchor.EXPANDED
    } else {
        CalendarAnchor.COLLAPSED
    }

    @Suppress("DEPRECATION")
    val dragState = remember {
        AnchoredDraggableState(
            initialValue = initialAnchor,
            positionalThreshold = { distance -> distance * CalendarAnchorThreshold },
            velocityThreshold = { velocityThresholdPx },
            snapAnimationSpec = tween(AppNavigationTransitions.durationMillis),
            decayAnimationSpec = exponentialDecay()
        )
    }
    SideEffect {
        dragState.updateAnchors(
            DraggableAnchors {
                CalendarAnchor.COLLAPSED at 0f
                CalendarAnchor.EXPANDED at anchorDistancePx
            }
        )
    }

    LaunchedEffect(mode) {
        val target = if (mode == HomeCalendarMode.MONTH) {
            CalendarAnchor.EXPANDED
        } else {
            CalendarAnchor.COLLAPSED
        }
        if (dragState.targetValue != target) {
            dragState.animateTo(target)
        }
    }
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.currentValue }
            .distinctUntilChanged()
            .collect { anchor ->
                when (anchor) {
                    CalendarAnchor.COLLAPSED -> onCollapse()
                    CalendarAnchor.EXPANDED -> onExpand()
                }
            }
    }

    val anchorOffset = dragState.offset.takeIf(Float::isFinite)
        ?: if (initialAnchor == CalendarAnchor.EXPANDED) anchorDistancePx else 0f
    val progress = if (anchorDistancePx == 0f) {
        0f
    } else {
        (anchorOffset / anchorDistancePx).coerceIn(0f, 1f)
    }
    val currentHeight = with(density) {
        (collapsedPx + anchorOffset).toDp()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(currentHeight)
            // This opaque mask prevents timeline rows from showing through the
            // expanding overlay. It deliberately uses only the fallback color;
            // the full-screen BACKGROUND image is not restarted inside the page.
            .background(AppTheme.surface(SurfaceRole.BACKGROUND).fallbackColor)
            .clipToBounds()
            .anchoredDraggable(
                state = dragState,
                orientation = Orientation.Vertical
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
        HomeCalendarHeader(
            displayedMonth = displayedMonth,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
            onTitleClick = onOpenAnnualCalendar
        )
        Box(Modifier.height(5.dp))
        WeekdayHeader()
        Box(Modifier.height(CalendarDaySpacing))
        CalendarMonthGrid(
            displayedMonth = displayedMonth,
            selectedDate = selectedDate,
            activities = activities,
            expansionProgress = progress,
            onDateSelected = onDateSelected,
            modifier = Modifier
                .height(
                    CalendarDayRowHeight +
                        (expandedGridHeight - CalendarDayRowHeight) * progress
                )
                .testTag(
                    if (mode == HomeCalendarMode.WEEK) {
                        "home_week_calendar"
                    } else {
                        "home_month_calendar"
                    }
                )
        )
            CalendarExpansionHandle(
                expanded = mode == HomeCalendarMode.MONTH,
                onClick = if (mode == HomeCalendarMode.MONTH) onCollapse else onExpand
            )
        }
    }
}

@Composable
fun HomeCalendarHeader(
    displayedMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CalendarHeaderHeight),
        contentAlignment = Alignment.Center
    ) {
        CalendarArrow(
            previous = true,
            onClick = onPrevious,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        AnimatedContent(
            targetState = displayedMonth,
            transitionSpec = {
                val forward = targetState > initialState
                if (forward) {
                    slideInHorizontally(
                        animationSpec = tween(AppNavigationTransitions.durationMillis)
                    ) { it } togetherWith slideOutHorizontally(
                        animationSpec = tween(AppNavigationTransitions.durationMillis)
                    ) { -it }
                } else {
                    slideInHorizontally(
                        animationSpec = tween(AppNavigationTransitions.durationMillis)
                    ) { -it } togetherWith slideOutHorizontally(
                        animationSpec = tween(AppNavigationTransitions.durationMillis)
                    ) { it }
                }
            },
            label = "calendar_month_title"
        ) { month ->
            Text(
                text = "${month.year}年" +
                    "${month.monthValue.toString().padStart(2, '0')}月",
                modifier = Modifier
                    .testTag("calendar_month_title")
                    .noRippleClickable(onClick = onTitleClick),
                style = AppTheme.typography.calendarMonth,
                color = AppTheme.colors.textPrimary
            )
        }
        CalendarArrow(
            previous = false,
            onClick = onNext,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun WeekdayHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CalendarWeekdayHeight),
        horizontalArrangement = Arrangement.spacedBy(CalendarDaySpacing)
    ) {
        weekdayLabels.forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = AppTheme.typography.calendarWeekday,
                    color = AppTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    activities: List<LibraryActivity>,
    expansionProgress: Float,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val activitiesByDate = remember(activities) {
        activities.groupBy { it.date.toLocalDate() }
    }
    AnimatedContent(
        targetState = displayedMonth,
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        transitionSpec = {
            val forward = targetState > initialState
            if (forward) {
                slideInHorizontally(
                    animationSpec = tween(AppNavigationTransitions.durationMillis)
                ) { it } togetherWith slideOutHorizontally(
                    animationSpec = tween(AppNavigationTransitions.durationMillis)
                ) { -it }
            } else {
                slideInHorizontally(
                    animationSpec = tween(AppNavigationTransitions.durationMillis)
                ) { -it } togetherWith slideOutHorizontally(
                    animationSpec = tween(AppNavigationTransitions.durationMillis)
                ) { it }
            }
        },
        label = "calendar_month_grid"
    ) { month ->
        val dates = remember(month) { calendarMonthDates(month) }
        val today = remember { LocalDate.now() }
        val selectedWeekIndex = calendarSelectedWeekIndex(
            month = month,
            selectedDate = selectedDate
        )
        val collapsedShift = selectedWeekIndex *
            with(LocalDensity.current) {
                (CalendarDayRowHeight + CalendarDaySpacing).toPx()
            }
        if (expansionProgress <= 0.001f) {
            CalendarWeekRow(
                week = calendarCollapsedWeekDates(month, selectedDate),
                selectedDate = selectedDate,
                today = today,
                activitiesByDate = activitiesByDate,
                onDateSelected = onDateSelected
            )
        } else {
            Column(
                modifier = Modifier.graphicsLayer {
                    translationY = -collapsedShift * (1f - expansionProgress)
                },
                verticalArrangement = Arrangement.spacedBy(CalendarDaySpacing)
            ) {
                dates.chunked(7).forEach { week ->
                    CalendarWeekRow(
                        week = week,
                        selectedDate = selectedDate,
                        today = today,
                        activitiesByDate = activitiesByDate,
                        onDateSelected = onDateSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarExpansionHandle(
    expanded: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CalendarHandleHeight)
            .noRippleClickable(onClick = onClick)
            .testTag("calendar_expand_handle"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = if (expanded) 48.dp else 56.dp, height = 5.dp)
                .background(AppTheme.colors.subtleBorder, CircleShape)
        )
    }
}

@Composable
private fun CalendarWeekRow(
    week: List<LocalDate?>,
    selectedDate: LocalDate,
    today: LocalDate,
    activitiesByDate: Map<LocalDate, List<LibraryActivity>>,
    onDateSelected: (LocalDate) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CalendarDayRowHeight),
        horizontalArrangement = Arrangement.spacedBy(CalendarDaySpacing)
    ) {
        repeat(7) { index ->
            val date = week.getOrNull(index)
            if (date == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(CalendarDayRowHeight)
                        .testTag("calendar_day_placeholder_$index")
                )
            } else {
                CalendarDayCell(
                    date = date,
                    isSelected = date == selectedDate,
                    isToday = date == today,
                    activities = activitiesByDate[date].orEmpty(),
                    onClick = { onDateSelected(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    activities: List<LibraryActivity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(CalendarDayRowHeight),
        contentAlignment = Alignment.Center
    ) {
        CalendarDayVisual(
            date = date,
            isInDisplayedMonth = true,
            isSelected = isSelected,
            isToday = isToday,
            activities = activities,
            onClick = onClick,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(
                    ratio = CalendarCellAspectRatio,
                    matchHeightConstraintsFirst = true
                )
        )
    }
}

@Composable
private fun CalendarArrow(
    previous: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (previous) {
                Icons.Rounded.ChevronLeft
            } else {
                Icons.Rounded.ChevronRight
            },
            contentDescription = if (previous) "上个月" else "下个月",
            tint = AppTheme.colors.textPrimary
        )
    }
}

internal fun calendarMonthDates(month: YearMonth): List<LocalDate?> =
    buildMonthDates(month)

internal fun calendarMonthRowCount(month: YearMonth): Int =
    calendarMonthDates(month).size / 7

internal fun calendarSelectedWeekIndex(
    month: YearMonth,
    selectedDate: LocalDate
): Int {
    val dates = calendarMonthDates(month)
    val targetDate = selectedDate.takeIf { YearMonth.from(it) == month }
        ?: month.atDay(1)
    return dates.indexOf(targetDate)
        .coerceAtLeast(0)
        .div(7)
}

internal fun calendarCollapsedWeekDates(
    month: YearMonth,
    selectedDate: LocalDate
): List<LocalDate?> =
    calendarMonthDates(month)
        .chunked(7)[calendarSelectedWeekIndex(month, selectedDate)]
