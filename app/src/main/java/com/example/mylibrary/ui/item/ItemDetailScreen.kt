package com.example.mylibrary.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.DynamicFieldValue
import com.example.mylibrary.domain.model.FieldDataType
import com.example.mylibrary.domain.model.decodeFieldSelection
import com.example.mylibrary.ui.components.AppBottomActionBar
import com.example.mylibrary.ui.components.AppCapsule
import com.example.mylibrary.ui.components.AppConfirmDialog
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.AppScreenContainer
import com.example.mylibrary.ui.components.BottomAction
import com.example.mylibrary.ui.components.BottomActionStyle
import com.example.mylibrary.ui.components.SecondaryPageHeader
import com.example.mylibrary.ui.components.CoverDisplayMode
import com.example.mylibrary.ui.components.CoverImage
import com.example.mylibrary.ui.components.FieldRow
import com.example.mylibrary.ui.quote.formatQuoteLocation
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CardContentPadding
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.FloatingActionBarContentPadding
import com.example.mylibrary.ui.theme.ScreenHorizontalPadding
import com.example.mylibrary.ui.theme.TopBarToContentGap
import com.example.mylibrary.util.formatDuration
import com.example.mylibrary.util.totalDurationLabel

@Composable
fun ItemDetailScreen(
    state: ItemDetailUiState,
    showQuoteChapter: Boolean = true,
    showQuotePage: Boolean = true,
    showTotalDuration: Boolean = true,
    destinationEnterCompleted: Boolean = true,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val colors = AppTheme.colors

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onDeleted()
    }

    AppScreenContainer(modifier = modifier.testTag("screen_item_detail")) {
        Column(Modifier.fillMaxSize()) {
            SecondaryPageHeader(
                title = "作品详情",
                onBack = onBack
            )
            val detail = state.detail
            if (detail == null || !destinationEnterCompleted) {
                Text(
                    text = state.errorMessage
                        ?: if (state.isLoading || !destinationEnterCompleted) {
                            "正在加载"
                        } else {
                            "作品不存在"
                        },
                    modifier = Modifier.padding(
                        start = ScreenHorizontalPadding,
                        top = TopBarToContentGap,
                        end = ScreenHorizontalPadding
                    ),
                    color = colors.mutedText,
                    style = AppTheme.typography.body
                )
            } else {
                val actualFields = state.visibleFields
                val currentStatus = state.currentStatus
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = ScreenHorizontalPadding,
                            end = ScreenHorizontalPadding,
                            top = TopBarToContentGap,
                            bottom = FloatingActionBarContentPadding
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DetailCard {
                        CoverImage(
                            thumbnailPath = detail.item.thumbnailPath,
                            originalPath = detail.item.coverPath,
                            title = detail.item.title,
                            creator = detail.item.creator,
                            typeName = detail.item.typeName,
                            typeId = detail.item.typeId,
                            displayMode = CoverDisplayMode.DETAIL,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .heightIn(max = 420.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = detail.item.title,
                            style = AppTheme.typography.pageTitle,
                            color = colors.textPrimary
                        )
                        detail.item.creator.takeIf(String::isNotBlank)?.let {
                            Text(
                                text = it,
                                color = colors.textSecondary,
                                style = AppTheme.typography.creator
                            )
                        }
                        Text(
                            text = detail.item.typeName,
                            color = colors.mutedText,
                            style = AppTheme.typography.metadata
                        )
                        if (showTotalDuration) {
                            detail.item.totalDurationMinutes
                                ?.let(::formatDuration)
                                ?.let { duration ->
                                    FieldRow(
                                        label = totalDurationLabel(detail.item.typeId),
                                        value = duration,
                                        editable = false,
                                        onClick = {},
                                        modifier = Modifier.testTag(
                                            "item_detail_total_duration"
                                        )
                                    )
                                }
                        }
                    }

                    if (currentStatus != null || detail.tags.isNotEmpty()) {
                        DetailCard {
                            currentStatus?.let {
                                AppCapsule(
                                    text = it.name,
                                    selected = true,
                                    onClick = {},
                                    enabled = false
                                )
                            }
                            if (detail.tags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    detail.tags.forEach { tag ->
                                        AppCapsule(
                                            text = tag.name,
                                            selected = false,
                                            onClick = {},
                                            enabled = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (actualFields.isNotEmpty()) {
                        DetailCard {
                            Text(
                                text = "自定义字段",
                                style = AppTheme.typography.cardTitle,
                                color = colors.textSecondary
                            )
                            actualFields.forEachIndexed { index, field ->
                                if (index > 0) {
                                    HorizontalDivider(color = colors.subtleBorder)
                                }
                                FieldRow(
                                    label = field.name,
                                    value = field.detailDisplayValue(),
                                    editable = false,
                                    onClick = {}
                                )
                            }
                        }
                    }

                    if (detail.records.isNotEmpty()) {
                        DetailCard {
                            detail.records.forEachIndexed { index, record ->
                                if (index > 0) {
                                    HorizontalDivider(color = colors.subtleBorder)
                                }
                                RecordContentBlock(
                                    record = record,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (state.quotes.isNotEmpty()) {
                        DetailCard {
                            Text(
                                text = "摘录",
                                style = AppTheme.typography.cardTitle,
                                color = colors.textSecondary
                            )
                            state.quotes.forEachIndexed { index, quote ->
                                if (index > 0) {
                                    HorizontalDivider(color = colors.subtleBorder)
                                }
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "• ${quote.content}",
                                        style = AppTheme.typography.body,
                                        color = colors.textPrimary
                                    )
                                    formatQuoteLocation(
                                        chapter = quote.chapter,
                                        page = quote.page,
                                        showChapter = showQuoteChapter,
                                        showPage = showQuotePage
                                    )?.let {
                                        Text(
                                            text = it,
                                            style = AppTheme.typography.metadata,
                                            color = colors.mutedText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    state.errorMessage?.let {
                        Text(
                            text = it,
                            color = colors.textSecondary,
                            style = AppTheme.typography.body
                        )
                    }
                }
            }
        }

        state.detail?.takeIf { destinationEnterCompleted }?.let { detail ->
            AppBottomActionBar(
                actions = listOf(
                    BottomAction(
                        text = "编辑",
                        icon = Icons.Outlined.Edit,
                        style = BottomActionStyle.SECONDARY,
                        onClick = { onEdit(detail.item.id) },
                        testTag = "edit_item_button"
                    ),
                    BottomAction(
                        text = "移除",
                        icon = Icons.Outlined.DeleteOutline,
                        style = BottomActionStyle.DANGER,
                        enabled = !state.isDeleting,
                        onClick = { showDeleteConfirmation = true },
                        testTag = "delete_item_button"
                    )
                ),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    val detail = state.detail
    if (showDeleteConfirmation && detail != null) {
        AppConfirmDialog(
            title = "移除",
            message = "确定移除？",
            confirmText = "移除",
            dismissText = "取消",
            destructive = true,
            confirmTestTag = "confirm_delete_button",
            onConfirm = {
                showDeleteConfirmation = false
                onDelete()
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}

private fun DynamicFieldValue.detailDisplayValue(): String = when {
    dataType == FieldDataType.BOOLEAN -> if (value == "true") "是" else "否"
    dataType == FieldDataType.MULTI_SELECT ->
        decodeFieldSelection(value).joinToString("  ")
    else -> value
}

@Composable
private fun DetailCard(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        forceOpaqueFallback = true
    ) {
        Column(
            modifier = Modifier.padding(CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
