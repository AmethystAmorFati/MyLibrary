package com.example.mylibrary.ui.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryStatus
import com.example.mylibrary.domain.model.LibraryViewMode
import com.example.mylibrary.ui.components.AppCapsule
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.LibraryFilterActionsGap
import com.example.mylibrary.ui.theme.LibraryStatusToActionsGap

@Composable
fun LibraryFilterBar(
    statuses: List<LibraryStatus>,
    selectedStatusId: Long?,
    viewMode: LibraryViewMode,
    onStatusSelected: (Long?) -> Unit,
    onTagFilter: () -> Unit,
    onViewModeCycle: () -> Unit,
    onConfigureList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            item {
                AppCapsule(
                    text = "全部",
                    selected = selectedStatusId == null,
                    onClick = { onStatusSelected(null) }
                )
            }
            items(statuses, key = { it.id }) { status ->
                AppCapsule(
                    text = status.name,
                    selected = selectedStatusId == status.id,
                    onClick = { onStatusSelected(status.id) }
                )
            }
        }
        Spacer(Modifier.width(LibraryStatusToActionsGap))
        TransparentLibraryIconButton(
            icon = Icons.AutoMirrored.Outlined.Label,
            contentDescription = "标签筛选",
            modifier = Modifier.testTag("library_tag_filter_icon"),
            onClick = onTagFilter
        )
        Spacer(Modifier.width(LibraryFilterActionsGap))
        TransparentLibraryIconButton(
            icon = when (viewMode) {
                LibraryViewMode.SHELF -> Icons.Outlined.ViewModule
                LibraryViewMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
                LibraryViewMode.COVER -> Icons.Outlined.GridView
            },
            contentDescription = "资料库显示模式：${viewMode.displayName}，点击切换",
            modifier = Modifier.testTag(
                "library_view_mode_${viewMode.name.lowercase()}"
            ),
            onClick = onViewModeCycle,
            onLongClick = if (viewMode == LibraryViewMode.LIST) {
                onConfigureList
            } else {
                null
            }
        )
    }
}

@Composable
private fun TransparentLibraryIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(40.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = AppTheme.colors.textPrimary
        )
    }
}
