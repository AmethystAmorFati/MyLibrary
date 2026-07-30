package com.example.mylibrary.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import com.example.mylibrary.domain.model.LibraryActivity
import com.example.mylibrary.ui.components.CoverDisplayMode
import com.example.mylibrary.ui.components.CoverImage
import com.example.mylibrary.ui.theme.LibraryShapes
import kotlin.math.roundToInt

internal enum class CalendarCoverLayout {
    EMPTY,
    SINGLE,
    TWO_ROWS,
    TWO_OVER_ONE,
    TWO_BY_TWO
}

internal data class CalendarCoverPlacement(
    val leftFraction: Float,
    val topFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val zIndex: Int
)

internal fun calendarCoverLayout(count: Int): CalendarCoverLayout = when {
    count <= 0 -> CalendarCoverLayout.EMPTY
    count == 1 -> CalendarCoverLayout.SINGLE
    count == 2 -> CalendarCoverLayout.TWO_ROWS
    count == 3 -> CalendarCoverLayout.TWO_OVER_ONE
    else -> CalendarCoverLayout.TWO_BY_TWO
}

internal fun calendarCoverPlacements(
    count: Int
): List<CalendarCoverPlacement> {
    val halfWidth = 0.5f
    val halfHeight = 0.5f
    fun placement(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        zIndex: Int
    ) = CalendarCoverPlacement(left, top, width, height, zIndex)
    return when (calendarCoverLayout(count)) {
        CalendarCoverLayout.EMPTY -> emptyList()
        CalendarCoverLayout.SINGLE -> listOf(
            placement(0f, 0f, 1f, 1f, 0)
        )
        CalendarCoverLayout.TWO_ROWS -> listOf(
            placement(0f, 0f, 1f, halfHeight, 0),
            placement(0f, halfHeight, 1f, halfHeight, 1)
        )
        CalendarCoverLayout.TWO_OVER_ONE -> listOf(
            placement(0f, 0f, halfWidth, halfHeight, 0),
            placement(halfWidth, 0f, halfWidth, halfHeight, 1),
            placement(0f, halfHeight, 1f, halfHeight, 2)
        )
        CalendarCoverLayout.TWO_BY_TWO -> listOf(
            placement(0f, 0f, halfWidth, halfHeight, 0),
            placement(halfWidth, 0f, halfWidth, halfHeight, 1),
            placement(0f, halfHeight, halfWidth, halfHeight, 2),
            placement(halfWidth, halfHeight, halfWidth, halfHeight, 3)
        )
    }
}

@Composable
fun ActivityArtwork(
    activities: List<LibraryActivity>,
    modifier: Modifier = Modifier,
    shape: Shape = LibraryShapes.small
) {
    val items = activities.take(MAX_CALENDAR_COVERS)
    Box(
        modifier = modifier
            .testTag("calendar_cover_${calendarCoverLayout(items.size).name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Layout(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(
                    ratio = CALENDAR_COVER_ASPECT_RATIO,
                    matchHeightConstraintsFirst = true
                )
                .clip(shape),
            content = {
                items.forEach { activity ->
                    ActivityCover(activity = activity)
                }
            }
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            if (
                width == Constraints.Infinity ||
                height == Constraints.Infinity ||
                width <= 0 ||
                height <= 0
            ) {
                layout(0, 0) {}
            } else {
                val placements = calendarCoverPlacements(measurables.size)
                val placed = measurables.zip(placements).map { (measurable, placement) ->
                    val left = (placement.leftFraction * width).roundToInt()
                    val top = (placement.topFraction * height).roundToInt()
                    val right = (
                        (placement.leftFraction + placement.widthFraction) * width
                        ).roundToInt()
                    val bottom = (
                        (placement.topFraction + placement.heightFraction) * height
                        ).roundToInt()
                    Triple(
                        measurable.measure(
                            Constraints.fixed(
                                (right - left).coerceAtLeast(1),
                                (bottom - top).coerceAtLeast(1)
                            )
                        ),
                        left to top,
                        placement.zIndex
                    )
                }
                layout(width, height) {
                    placed.forEach { (placeable, offset, zIndex) ->
                        placeable.placeRelative(
                            x = offset.first,
                            y = offset.second,
                            zIndex = zIndex.toFloat()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCover(activity: LibraryActivity) {
    CoverImage(
        thumbnailPath = activity.thumbnailPath,
        title = activity.title,
        typeName = activity.typeName,
        typeId = activity.typeId,
        displayMode = CoverDisplayMode.CALENDAR,
        modifier = Modifier.fillMaxSize(),
        shape = RectangleShape
    )
}

private const val MAX_CALENDAR_COVERS = 4
internal const val CALENDAR_COVER_ASPECT_RATIO = 2f / 3f
