package com.example.mylibrary.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme

@Composable
fun AppInlineSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        label = "inline-switch-thumb"
    )
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .background(
                color = if (checked) colors.accent else colors.border,
                shape = RoundedCornerShape(50)
            )
            .noRippleClickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset, y = 3.dp)
                .size(18.dp)
                .background(
                    color = if (checked) colors.onAccent else colors.textSecondary,
                    shape = CircleShape
                )
        )
    }
}
