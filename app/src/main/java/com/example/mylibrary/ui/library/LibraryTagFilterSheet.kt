package com.example.mylibrary.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryTag
import com.example.mylibrary.ui.components.AppCapsule
import com.example.mylibrary.ui.components.AppModalBottomSheet
import com.example.mylibrary.ui.components.LibrarySearchField
import com.example.mylibrary.ui.components.contextualTagSearch
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTagFilterSheet(
    tags: List<LibraryTag>,
    selectedIds: Set<Long>,
    onApply: (Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val roots = tags.filter { it.parentId == null }
    val selected = tags.firstOrNull { it.id in selectedIds }
    var query by remember { mutableStateOf("") }
    var temporarySelection by remember(selectedIds) { mutableStateOf(selectedIds) }
    var activeRootId by remember {
        mutableStateOf(selected?.parentId ?: selected?.id ?: roots.firstOrNull()?.id)
    }
    LaunchedEffect(roots) {
        if (activeRootId == null || roots.none { it.id == activeRootId }) {
            activeRootId = roots.firstOrNull()?.id
        }
    }
    val searchResult = contextualTagSearch(
        tags = tags,
        activeRootId = activeRootId,
        query = query,
        allowCreation = false
    )
    val colors = AppTheme.colors

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .noRippleClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = colors.textPrimary
                    )
                }
                Text(
                    "标签筛选",
                    modifier = Modifier.weight(1f),
                    style = AppTheme.typography.sectionTitle,
                    color = colors.textPrimary
                )
                Box(Modifier.size(40.dp))
            }
            Text(
                text = "已选择 ${temporarySelection.size} 个标签",
                style = AppTheme.typography.metadata,
                color = colors.textSecondary
            )

            LibrarySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索标签",
                modifier = Modifier.fillMaxWidth()
            )

            if (roots.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(roots, key = { it.id }) { root ->
                        AppCapsule(
                            text = root.name,
                            selected = root.id == activeRootId,
                            onClick = {
                                activeRootId = root.id
                                query = ""
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    searchResult.visibleTags.forEach { tag ->
                        AppCapsule(
                            text = tag.name,
                            selected = tag.id in temporarySelection,
                            onClick = {
                                temporarySelection = temporarySelection.toggle(tag.id)
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "重置",
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .noRippleClickable { temporarySelection = emptySet() },
                    style = AppTheme.typography.button,
                    color = colors.textPrimary
                )
                Text(
                    text = if (temporarySelection.isEmpty()) {
                        "确定"
                    } else {
                        "确定（${temporarySelection.size}）"
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .noRippleClickable {
                            onApply(temporarySelection)
                            onDismiss()
                        },
                    style = AppTheme.typography.button,
                    color = colors.textPrimary
                )
            }
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (id in this) this - id else this + id
