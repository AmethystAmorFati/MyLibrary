package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.CapsuleHeight
import com.example.mylibrary.ui.theme.CapsuleHorizontalPadding

@Composable
fun AppCapsule(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = AppTheme.colors
    Surface(
        modifier = modifier
            .height(CapsuleHeight)
            .noRippleClickable(enabled, onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) colors.accent else colors.subtleBorder,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = CapsuleHorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = AppTheme.typography.capsule,
                color = if (selected) colors.onAccent else colors.textPrimary
            )
        }
    }
}

@Composable
fun AppIconCapsule(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val colors = AppTheme.colors
    Surface(
        modifier = modifier
            .height(CapsuleHeight)
            .noRippleClickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) colors.accent else colors.subtleBorder,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) colors.onAccent else colors.textPrimary
            )
        }
    }
}
