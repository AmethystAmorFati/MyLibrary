package com.example.mylibrary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StarRatingBar(
    ratingHalfStars: Int?,
    modifier: Modifier = Modifier,
    onRatingChange: ((Int?) -> Unit)? = null,
    starSize: Dp = 22.dp,
    starSpacing: Dp = 5.dp
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier.testTag("star_rating_bar"),
        horizontalArrangement = Arrangement.spacedBy(starSpacing)
    ) {
        repeat(5) { index ->
            val starNumber = index + 1
            Box(
                modifier = Modifier
                    .size(starSize)
                    .testTag("star_rating_star_$starNumber")
                    .noRippleClickable(
                        enabled = onRatingChange != null,
                        onClick = {
                            onRatingChange?.invoke(
                                nextHalfStarRating(ratingHalfStars, starNumber)
                            )
                        }
                    )
            ) {
                StarIcon(
                    state = starFillState(ratingHalfStars, starNumber),
                    fillColor = colors.accent,
                    outlineColor = colors.mutedText,
                    starSize = starSize
                )
            }
        }
    }
}

@Composable
private fun StarIcon(
    state: StarFillState,
    fillColor: Color,
    outlineColor: Color,
    starSize: Dp
) {
    Canvas(modifier = Modifier.size(starSize)) {
        val path = starPath(size.width, size.height)
        val stroke = Stroke(width = 1.6.dp.toPx())
        when (state) {
            StarFillState.FULL -> drawPath(path, color = fillColor)
            StarFillState.HALF -> clipRect(right = size.width / 2f) {
                drawPath(path, color = fillColor)
            }
            StarFillState.EMPTY -> Unit
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
