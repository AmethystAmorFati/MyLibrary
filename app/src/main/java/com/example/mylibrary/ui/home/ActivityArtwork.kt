package com.example.mylibrary.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.ui.components.CoverDisplayMode
import com.example.mylibrary.ui.components.CoverImage
import com.example.mylibrary.ui.theme.LibraryShapes

internal enum class CalendarCoverLayout {
    EMPTY,
    SINGLE,
    TWO_ROWS,
    TWO_OVER_ONE,
    TWO_BY_TWO
}

internal fun calendarCoverLayout(count: Int): CalendarCoverLayout = when {
    count <= 0 -> CalendarCoverLayout.EMPTY
    count == 1 -> CalendarCoverLayout.SINGLE
    count == 2 -> CalendarCoverLayout.TWO_ROWS
    count == 3 -> CalendarCoverLayout.TWO_OVER_ONE
    else -> CalendarCoverLayout.TWO_BY_TWO
}

@Composable
fun ActivityArtwork(
    activities: List<LibraryActivity>,
    modifier: Modifier = Modifier,
    shape: Shape = LibraryShapes.small
) {
    val items = activities
    Box(
        modifier = modifier
            .clip(shape)
            .testTag("calendar_cover_${calendarCoverLayout(items.size).name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        when (calendarCoverLayout(items.size)) {
            CalendarCoverLayout.EMPTY -> Unit
            CalendarCoverLayout.SINGLE -> {
                ActivityCover(
                    activity = items[0],
                    modifier = Modifier.fillMaxSize()
                )
            }
            CalendarCoverLayout.TWO_ROWS -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items.forEach { activity ->
                        ActivityCover(
                            activity = activity,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
            CalendarCoverLayout.TWO_OVER_ONE -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    CoverRow(
                        activities = items.take(2),
                        modifier = Modifier.weight(1f),
                    )
                    ActivityCover(
                        activity = items[2],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
            CalendarCoverLayout.TWO_BY_TWO -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    CoverRow(
                        activities = items.take(2),
                        modifier = Modifier.weight(1f)
                    )
                    CoverRow(
                        activities = items.drop(2).take(2),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverRow(
    activities: List<LibraryActivity>,
    modifier: Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        activities.forEach { activity ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ActivityCover(
                    activity = activity,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ActivityCover(
    activity: LibraryActivity,
    modifier: Modifier
) {
    CoverImage(
        thumbnailPath = activity.thumbnailPath,
        title = activity.title,
        typeName = activity.typeName,
        typeId = activity.typeId,
        displayMode = CoverDisplayMode.CALENDAR,
        modifier = modifier,
        shape = RectangleShape
    )
}
