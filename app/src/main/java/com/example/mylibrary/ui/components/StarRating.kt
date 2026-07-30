package com.example.mylibrary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object RatingStarsDefaults {
    val RecordCardStarSize = 18.dp
    val RecordCardStarSpacing = 5.dp
}

@Composable
fun StarRatingBar(
    ratingHalfStars: Int?,
    modifier: Modifier = Modifier,
    onRatingChange: ((Int?) -> Unit)? = null,
    starSize: Dp = 22.dp,
    starSpacing: Dp = 5.dp,
    touchTargetSize: Dp = starSize,
    clearFullStarOnRepeat: Boolean = false
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier.testTag("star_rating_bar"),
        horizontalArrangement = Arrangement.spacedBy(starSpacing)
    ) {
        repeat(5) { index ->
            val starNumber = index + 1
            val fillFraction = starFillFraction(ratingHalfStars, starNumber)
            Box(
                modifier = Modifier
                    .size(touchTargetSize)
                    .testTag("star_rating_star_$starNumber")
                    .semantics {
                        contentDescription = "第 $starNumber 颗星"
                        stateDescription = when (fillFraction) {
                            1f -> "已选中"
                            0.5f -> "半星"
                            else -> "未选中"
                        }
                    }
                    .noRippleClickable(
                        enabled = onRatingChange != null,
                        onClick = {
                            onRatingChange?.invoke(
                                if (
                                    clearFullStarOnRepeat &&
                                    ratingHalfStars == starNumber * 2
                                ) {
                                    null
                                } else {
                                    nextHalfStarRating(
                                        ratingHalfStars,
                                        starNumber
                                    )
                                }
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                RatingStar(
                    fillFraction = fillFraction,
                    fillColor = colors.accent,
                    outlineColor = colors.mutedText,
                    starSize = starSize,
                    modifier = Modifier.testTag(
                        "rating_star_graphic_$starNumber"
                    )
                )
            }
        }
    }
}

@Composable
private fun RatingStar(
    fillFraction: Float,
    fillColor: Color,
    outlineColor: Color,
    starSize: Dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(starSize)) {
        val fraction = fillFraction.coerceIn(0f, 1f)
        val path = starPath(size.width, size.height)
        val stroke = Stroke(width = 1.6.dp.toPx())
        if (fraction > 0f) {
            clipRect(
                left = 0f,
                top = 0f,
                right = size.width * fraction,
                bottom = size.height
            ) {
                drawPath(path, color = fillColor)
            }
        }
        drawPath(path, color = outlineColor, style = stroke)
    }
}

private fun starPath(width: Float, height: Float): Path {
    val center = Offset(width / 2f, height / 2f)
    val outerRadius = minOf(width, height) * 0.46f
    val innerRadius = outerRadius * 0.48f
    return Path().apply {
        repeat(10) { point ->
            val radius = if (point % 2 == 0) outerRadius else innerRadius
            val angle = -PI / 2.0 + point * PI / 5.0
            val x = center.x + cos(angle).toFloat() * radius
            val y = center.y + sin(angle).toFloat() * radius
            if (point == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
