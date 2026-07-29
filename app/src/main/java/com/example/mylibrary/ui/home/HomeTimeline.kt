package com.example.mylibrary.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryTimelineRecord
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.components.CardMetadataStarSize
import com.example.mylibrary.ui.components.CardMetadataStarSpacing
import com.example.mylibrary.ui.components.CoverDisplayMode
import com.example.mylibrary.ui.components.CoverImage
import com.example.mylibrary.ui.components.StarRatingBar
import com.example.mylibrary.ui.components.cardMetadataTextStyle
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.library.LibraryMetadataCapsule
import com.example.mylibrary.ui.library.LibraryMetadataCapsuleRole
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.BottomContentPadding
import com.example.mylibrary.ui.theme.TimelineCardCornerRadius
import com.example.mylibrary.ui.theme.TimelineCardPadding
import com.example.mylibrary.ui.theme.TimelineCardSpacing
import com.example.mylibrary.ui.theme.TimelineCoverHeight
import com.example.mylibrary.ui.theme.TimelineCoverWidth
import com.example.mylibrary.ui.theme.TimelineDateGroupSpacing
import com.example.mylibrary.ui.theme.TimelineEndPadding
import com.example.mylibrary.ui.theme.TimelineLineOffset
import com.example.mylibrary.ui.theme.TimelineNodeSize
import com.example.mylibrary.ui.theme.TimelinePageHorizontalPadding
import com.example.mylibrary.ui.theme.TimelineRailWidth
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.util.formatDuration
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

@Composable
fun HomeTimeline(
    entries: List<TimelineListEntry>,
    listState: LazyListState,
    topContentPadding: Dp,
    visibleRecordSyncEnabled: Boolean,
    showCreator: Boolean,
    showRating: Boolean,
    showTimelineStatus: Boolean = false,
    showTimelineDuration: Boolean = true,
    onVisibleRecordChanged: (Long) -> Unit,
    onItemSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentCallback = rememberUpdatedState(onVisibleRecordChanged)
    LaunchedEffect(listState, entries, visibleRecordSyncEnabled) {
        if (!visibleRecordSyncEnabled) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .mapNotNull { index ->
                TimelineCalendarCoordinator.visibleRecord(
                    entries = entries,
                    firstVisibleItemIndex = index
                )
            }
            .distinctUntilChanged()
            .collect { currentCallback.value(it.recordId) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = TimelinePageHorizontalPadding,
                end = TimelinePageHorizontalPadding + TimelineEndPadding
            )
    ) {
        if (entries.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .offset(x = TimelineLineOffset)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(AppTheme.colors.subtleBorder)
            )
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topContentPadding,
                        bottom = BottomContentPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有阅读或观看记录",
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.mutedText
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topContentPadding,
                    bottom = BottomContentPadding
                )
            ) {
                items(
                    items = entries,
                    key = { "record_${it.record.recordId}" }
                ) { entry ->
                    TimelineEntryContent(
                        entry = entry,
                        showCreator = showCreator,
                        showRating = showRating,
                        showTimelineStatus = showTimelineStatus,
                        showTimelineDuration = showTimelineDuration,
                        onItemSelected = onItemSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryContent(
    entry: TimelineListEntry,
    showCreator: Boolean,
    showRating: Boolean,
    showTimelineStatus: Boolean,
    showTimelineDuration: Boolean,
    onItemSelected: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = if (entry.isLastInDateGroup) {
                    TimelineDateGroupSpacing
                } else {
                    TimelineCardSpacing
                }
            )
    ) {
        Box(
            modifier = Modifier.width(TimelineRailWidth),
            contentAlignment = Alignment.TopCenter
        ) {
            if (entry.showDateLabel) {
                Box(
                    modifier = Modifier
                        .size(TimelineNodeSize)
                        .background(AppTheme.colors.accent, CircleShape)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (entry.showDateLabel) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = entry.recordStartDate.monthValue.toString().padStart(2, '0'),
                        style = AppTheme.typography.timelineMonth,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.colors.textSecondary
                    )
                    Text(
                        text = entry.recordStartDate.dayOfMonth.toString().padStart(2, '0'),
                        modifier = Modifier.padding(start = 3.dp),
                        style = AppTheme.typography.timelineDay,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
            TimelineRecordCard(
                record = entry.record,
                showCreator = showCreator,
                showRating = showRating,
                showTimelineStatus = showTimelineStatus,
                showTimelineDuration = showTimelineDuration,
                onClick = { onItemSelected(entry.record.itemId) }
            )
        }
    }
}

@Composable
private fun TimelineRecordCard(
    record: LibraryTimelineRecord,
    showCreator: Boolean,
    showRating: Boolean,
    showTimelineStatus: Boolean,
    showTimelineDuration: Boolean,
    onClick: () -> Unit
) {
    AppThemeSurface(
        role = SurfaceRole.CARD,
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(TimelineCardCornerRadius),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(TimelineCardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoverImage(
                thumbnailPath = record.thumbnailPath,
                title = record.title,
                typeName = record.typeName,
                typeId = record.typeId,
                displayMode = CoverDisplayMode.TIMELINE,
                modifier = Modifier.size(
                    width = TimelineCoverWidth,
                    height = TimelineCoverHeight
                )
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = record.title,
                    modifier = Modifier.testTag(
                        "timeline_title_${record.recordId}"
                    ),
                    style = AppTheme.typography.itemTitle,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2
                )
                if (showCreator) {
                    record.creator.takeIf(String::isNotBlank)?.let {
                        Text(
                            text = it,
                            modifier = Modifier.testTag(
                                "timeline_creator_${record.recordId}"
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = cardMetadataTextStyle(AppTheme.typography.metadata),
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
                val visibleStatus = record.statusSnapshot
                    ?.takeIf(String::isNotBlank)
                    ?.takeIf { showTimelineStatus }
                val visibleDuration = record.durationMinutes
                    ?.let(::formatDuration)
                    ?.takeIf { showTimelineDuration }
                if (visibleStatus != null || visibleDuration != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        visibleStatus?.let {
                            LibraryMetadataCapsule(
                                text = it,
                                role = LibraryMetadataCapsuleRole.STATUS,
                                modifier = Modifier.testTag(
                                    "timeline_status_${record.recordId}"
                                )
                            )
                        }
                        if (visibleStatus != null && visibleDuration != null) {
                            Text(
                                text = "·",
                                style = cardMetadataTextStyle(AppTheme.typography.metadata),
                                color = AppTheme.colors.textSecondary
                            )
                        }
                        visibleDuration?.let {
                            Text(
                                text = it,
                                modifier = Modifier.testTag(
                                    "timeline_duration_${record.recordId}"
                                ),
                                style = cardMetadataTextStyle(AppTheme.typography.metadata),
                                color = AppTheme.colors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (showRating) {
                    record.ratingHalfStars?.let {
                        StarRatingBar(
                            ratingHalfStars = it,
                            starSize = CardMetadataStarSize,
                            starSpacing = CardMetadataStarSpacing
                        )
                    }
                }
            }
        }
    }
}
