package com.example.mylibrary.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import kotlinx.coroutines.flow.filter

@Composable
fun AppWheelPicker(
    values: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    formatter: (Int) -> String,
    cyclic: Boolean,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val itemHeight = 38.dp
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val virtualValues = remember(values, cyclic) {
        if (cyclic) {
            List(values.size * 1_000) { values[it % values.size] }
        } else {
            listOf<Int?>(null) + values + listOf<Int?>(null)
        }
    }
    val initialIndex = remember(values, selectedValue, cyclic) {
        val realIndex = values.indexOf(selectedValue).coerceAtLeast(0)
        if (cyclic) {
            (virtualValues.size / 2 / values.size) * values.size + realIndex
        } else {
            realIndex
        }
    }
    val initialScrollIndex = if (cyclic) initialIndex - 1 else initialIndex
    var centerIndex by remember { mutableIntStateOf(initialScrollIndex + 1) }

    LaunchedEffect(initialScrollIndex) {
        state.scrollToItem(initialScrollIndex)
        centerIndex = initialScrollIndex + 1
    }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect { centerIndex = it + 1 }
    }
    LaunchedEffect(state, virtualValues) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collect {
                val value = virtualValues.getOrNull(state.firstVisibleItemIndex + 1)
                if (value != null && value != selectedValue) {
                    onValueSelected(value)
                }
            }
    }

    LazyColumn(
        modifier = modifier
            .width(74.dp)
            .height(itemHeight * 3),
        state = state,
        flingBehavior = fling
    ) {
        itemsIndexed(virtualValues) { index, value ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                contentAlignment = Alignment.Center
            ) {
                if (value != null) {
                    Text(
                        text = formatter(value),
                        style = if (index == centerIndex) {
                            AppTheme.typography.sectionTitle
                        } else {
                            AppTheme.typography.body
                        },
                        fontWeight = if (index == centerIndex) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (index == centerIndex) {
                            AppTheme.colors.textPrimary
                        } else {
                            AppTheme.colors.mutedText
                        }
                    )
                }
            }
        }
    }
}
