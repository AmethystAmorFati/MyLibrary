package com.example.mylibrary.ui.components

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
import com.example.mylibrary.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelectionSheet(
    tags: List<LibraryTag>,
    selectedIds: Set<Long>,
    onDone: (Set<Long>) -> Unit,
    onCreateTag: (String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val roots = tags.filter { it.parentId == null }
    var query by remember { mutableStateOf("") }
    var temporarySelection by remember { mutableStateOf(selectedIds) }
    var activeRootId by remember { mutableStateOf<Long?>(roots.firstOrNull()?.id) }
    var pendingCreation by remember { mutableStateOf<PendingTagCreation?>(null) }
    val colors = AppTheme.colors

    LaunchedEffect(roots) {
        if (activeRootId == null || roots.none { it.id == activeRootId }) {
            activeRootId = roots.firstOrNull()?.id
        }
    }
    LaunchedEffect(tags, pendingCreation) {
        val pending = pendingCreation ?: return@LaunchedEffect
        val created = tags.firstOrNull {
            it.parentId == pending.parentId &&
                it.name.equals(pending.name, ignoreCase = true)
        } ?: return@LaunchedEffect
        temporarySelection = temporarySelection + created.id
        pendingCreation = null
    }

    val activeRoot = roots.firstOrNull { it.id == activeRootId }
    val searchResult = contextualTagSearch(
        tags = tags,
        activeRootId = activeRootId,
        query = query,
        allowCreation = true
    )

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    text = "选择标签",
                    modifier = Modifier.weight(1f),
                    style = AppTheme.typography.sectionTitle,
                    color = colors.textPrimary
                )
                Text(
                    text = "完成",
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .noRippleClickable {
                            onDone(temporarySelection)
                            onDismiss()
                        },
                    style = AppTheme.typography.button,
                    color = colors.textPrimary
                )
            }

            LibrarySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索或创建标签",
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
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    if (searchResult.canCreate) {
                        AppCapsule(
                            text = "＋${searchResult.normalizedQuery}",
                            selected = false,
                            onClick = {
                                val parentId = requireNotNull(activeRoot).id
                                pendingCreation = PendingTagCreation(
                                    name = searchResult.normalizedQuery,
                                    parentId = parentId
                                )
                                onCreateTag(searchResult.normalizedQuery, parentId)
                                query = ""
                            }
                        )
                    }
                }

                if (roots.isEmpty()) {
                    Text(
                        text = "暂无一级标签，请先在标签设置中创建",
                        style = AppTheme.typography.metadata,
                        color = colors.mutedText
                    )
                }
            }
        }
    }
}

private data class PendingTagCreation(
    val name: String,
    val parentId: Long?
)

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (id in this) this - id else this + id
