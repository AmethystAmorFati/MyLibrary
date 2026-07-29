package com.example.mylibrary.ui.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.SimpleTopBar
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarToContentGap

@Composable
fun ItemTagEditorScreen(
    state: ItemTagEditorUiState,
    onBack: () -> Unit,
    onSelected: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AppScreenContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
        SimpleTopBar(title = "作品标签", onBack = onBack)
        Text(
            text = "可同时选择多个标签",
            modifier = Modifier.padding(
                start = ScreenHorizontalPadding,
                end = ScreenHorizontalPadding,
                top = TopBarToContentGap
            ),
            color = AppTheme.colors.mutedText,
            style = AppTheme.typography.body
        )
        state.errorMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(ScreenHorizontalPadding),
                color = AppTheme.colors.mutedText,
                style = AppTheme.typography.metadata
            )
        }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.tags, key = { it.id }) { tag ->
                TagSelectionRow(
                    tag = tag,
                    selected = tag.id in state.selectedIds,
                    onSelected = { onSelected(tag.id, it) }
                )
            }
            }
        }
    }
}

@Composable
private fun TagSelectionRow(
    tag: LibraryTag,
    selected: Boolean,
    onSelected: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected(!selected) }
            .padding(
                start = ScreenHorizontalPadding + if (tag.parentId == null) 0.dp else 16.dp,
                end = ScreenHorizontalPadding,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tag.name,
                style = AppTheme.typography.body,
                color = AppTheme.colors.textPrimary
            )
        }
        Checkbox(checked = selected, onCheckedChange = onSelected)
    }
}
