package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.AppScrim
import com.example.mylibrary.ui.theme.SurfaceRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    dragHandle: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = BottomSheetDefaults.ExpandedShape
    val dialogSurface = resolvedThemeSurfaceForContainer(
        surface = AppTheme.surface(SurfaceRole.DIALOG),
        drawImageSurface = true,
        forceOpaqueFallback = true
    )
    val containerPolicy = themeSurfaceContainerPolicy(
        surface = dialogSurface,
        containerAlpha = 1f
    )
    val sheetModifier = if (containerPolicy.drawsResolvedBackground) {
        modifier.themeSurfaceBackground(
            surface = dialogSurface,
            expectedRole = SurfaceRole.DIALOG,
            shape = shape,
            containerAlpha = 1f
        )
    } else {
        modifier
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = sheetModifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = containerPolicy.materialColor,
        contentColor = AppTheme.colors.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = AppScrim,
        dragHandle = dragHandle,
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = AppTheme.darkSystemBarIcons,
            isAppearanceLightNavigationBars = AppTheme.darkSystemBarIcons
        ),
        content = content
    )
}
