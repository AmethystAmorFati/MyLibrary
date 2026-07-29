package com.example.mylibrary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.util.buildMonthDates
import com.example.mylibrary.util.toLocalDate
import com.example.mylibrary.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerBottomSheet(
    initialDateMillis: Long?,
    allowClear: Boolean,
    onConfirm: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val initial = remember(initialDateMillis) {
        initialDateMillis?.toLocalDate() ?: LocalDate.now()
    }
    var selectedDate by remember { mutableStateOf(initial) }
    var displayedMonth by remember { mutableStateOf(YearMonth.from(initial)) }
    var wheelMode by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetAction("取消", Modifier.weight(1f), Alignment.CenterStart, onDismiss)
                if (allowClear) {
                    SheetAction("清空", Modifier.weight(1f), Alignment.Center) {
                        onClear()
                        onDismiss()
                    }
                } else {
                    Box(Modifier.weight(1f))
                }
                SheetAction("确定", Modifier.weight(1f), Alignment.CenterEnd) {
                    onConfirm(selectedDate.toStartOfDayMillis())
                    onDismiss()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp),
                contentAlignment = if (wheelMode) {
                    Alignment.Center
                } else {
                    Alignment.TopCenter
                }
            ) {
                if (wheelMode) {
                    YearMonthWheelSelector(
                        value = displayedMonth,
                        onChange = { month ->
                            displayedMonth = month
                            selectedDate = month.atDay(
                                selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
                            )
                        },
                        onCalendarClick = { wheelMode = false }
                    )
                } else {
                    CalendarSelector(
                        month = displayedMonth,
                        selectedDate = selectedDate,
                        onMonthChange = { month ->
                            displayedMonth = month
                            selectedDate = month.atDay(
                                selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
                            )
                        },
                        onTitleClick = { wheelMode = true },
                        onDateSelected = { selectedDate = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    text: String,
    modifier: Modifier,
    alignment: Alignment,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .noRippleClickable(onClick = onClick),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            style = AppTheme.typography.button,
            color = AppTheme.colors.textPrimary
        )
    }
}

@Composable
private fun CalendarSelector(
    month: YearMonth,
    selectedDate: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onTitleClick: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    Column {
        MonthSwitcher(
            title = "${month.year}年${month.monthValue.toString().padStart(2, '0')}月",
            onPrevious = { onMonthChange(month.minusMonths(1)) },
            onNext = { onMonthChange(month.plusMonths(1)) },
            onTitleClick = onTitleClick
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { title ->
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = AppTheme.typography.metadata,
                    color = AppTheme.colors.mutedText
                )
            }
        }
        buildMonthDates(month, fixedSixRows = true).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    CalendarDay(
                        date = date,
                        selected = date == selectedDate,
                        today = date == LocalDate.now(),
                        onClick = { date?.let(onDateSelected) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        contentAlignment = Alignment.Center
    ) {
        MonthArrow(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            description = "上个月",
            onClick = onPrevious,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = title,
            modifier = Modifier.noRippleClickable(onClick = onTitleClick),
            style = AppTheme.typography.calendarMonth,
            color = AppTheme.colors.textPrimary
        )
        MonthArrow(
            icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            description = "下个月",
            onClick = onNext,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun MonthArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = AppTheme.colors.textPrimary)
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate?,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier.height(30.dp),
        contentAlignment = Alignment.Center
    ) {
        if (date != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> colors.accent
                            today -> colors.subtleBorder
                            else -> colors.surfaces.card
                        }
                    )
                    .noRippleClickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = AppTheme.typography.metadata,
                    color = if (selected) colors.onAccent else colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun YearMonthWheelSelector(
    value: YearMonth,
    onChange: (YearMonth) -> Unit,
    onCalendarClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${value.year}年${value.monthValue.toString().padStart(2, '0')}月",
            modifier = Modifier
                .padding(vertical = 4.dp)
                .noRippleClickable(onClick = onCalendarClick),
            style = AppTheme.typography.calendarMonth,
            color = AppTheme.colors.textPrimary
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top
        ) {
            AppWheelPicker(
                values = (1970..2100).toList(),
                selectedValue = value.year,
                onValueSelected = { onChange(YearMonth.of(it, value.monthValue)) },
                formatter = Int::toString,
                cyclic = false
            )
            Spacer(Modifier.width(16.dp))
            AppWheelPicker(
                values = (1..12).toList(),
                selectedValue = value.monthValue,
                onValueSelected = { onChange(YearMonth.of(value.year, it)) },
                formatter = Int::toString,
                cyclic = true
            )
        }
    }
}
