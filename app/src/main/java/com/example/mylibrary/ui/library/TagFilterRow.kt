package com.example.mylibrary.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.ui.components.ActiveFilterChip

@Composable
fun LibrarySelectedTagFilterRow(
    tags: List<LibraryTag>,
    selectedIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTags = selectedTagsInDisplayOrder(tags, selectedIds)
    if (selectedTags.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("library_selected_tag_filters"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "reset") {
            ActiveFilterChip(
                text = "重置",
                onClear = { onSelectionChange(emptySet()) },
                contentDescription = "清除全部标签筛选",
                modifier = Modifier.testTag("library_selected_tags_reset")
            )
        }
        items(selectedTags, key = { it.id }) { tag ->
            ActiveFilterChip(
                text = tag.name,
                onClear = { onSelectionChange(selectedIds - tag.id) },
                contentDescription = "清除标签 ${tag.name}",
                modifier = Modifier.testTag("library_selected_tag_${tag.id}")
            )
        }
    }
}

internal fun selectedTagsInDisplayOrder(
    tags: List<LibraryTag>,
    selectedIds: Set<Long>
): List<LibraryTag> =
    tags.asSequence()
        .filter { it.id in selectedIds }
        .distinctBy { it.id }
        .toList()
