package com.example.mylibrary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppDanger
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.FloatingActionBarBottomPadding
import com.example.mylibrary.ui.theme.FloatingActionBarHeight
import com.example.mylibrary.ui.theme.FloatingActionBarHorizontalPadding
import com.example.mylibrary.ui.theme.LibraryShapes

enum class BottomActionStyle {
    PRIMARY,
    SECONDARY,
    DANGER
}

data class BottomAction(
    val text: String,
    val onClick: () -> Unit,
    val style: BottomActionStyle = BottomActionStyle.PRIMARY,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val testTag: String? = null
)

@Composable
fun AppBottomActionBar(
    actions: List<BottomAction>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = FloatingActionBarHorizontalPadding,
                end = FloatingActionBarHorizontalPadding,
                bottom = FloatingActionBarBottomPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        actions.forEach { action ->
            AppBottomActionButton(
                action = action,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AppBottomActionButton(
    action: BottomAction,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val background = when (action.style) {
        BottomActionStyle.PRIMARY -> colors.accent
        BottomActionStyle.SECONDARY, BottomActionStyle.DANGER -> colors.surfaces.card
    }
    val foreground = when (action.style) {
        BottomActionStyle.PRIMARY -> colors.onAccent
        BottomActionStyle.SECONDARY -> colors.textPrimary
        BottomActionStyle.DANGER -> AppDanger
    }
    val border = when (action.style) {
        BottomActionStyle.DANGER -> BorderStroke(1.dp, AppDanger)
        BottomActionStyle.SECONDARY -> BorderStroke(1.dp, colors.border)
        BottomActionStyle.PRIMARY -> null
    }
    Surface(
        modifier = modifier
            .height(FloatingActionBarHeight)
            .then(
                action.testTag?.let { Modifier.testTag(it) } ?: Modifier
            )
            .noRippleClickable(
                enabled = action.enabled,
                onClick = action.onClick
            ),
        shape = LibraryShapes.large,
        color = background,
        border = border,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            action.icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = foreground
                )
                Box(Modifier.padding(start = 7.dp))
            }
            Text(
                text = action.text,
                style = AppTheme.typography.button,
                color = foreground
            )
        }
    }
}
