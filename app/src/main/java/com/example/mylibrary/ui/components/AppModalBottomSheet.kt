package com.example.mylibrary.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.mylibrary.ui.theme.ResolvedSurface
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
    val hasDialogImage = dialogSurface is ResolvedSurface.ImageSurface

    // When the DIALOG surface is an image, the fallback color + image must be
    // drawn INSIDE the sheet so they share the same offset, enter/exit
    // animation, and shape clip as the sheet content.
    //
    // Previously the image was drawn via ModalBottomSheet's *modifier*
    // parameter.  In Material3 that modifier is applied to the full-screen
    // Popup root — not the sheet — so the image rendered at the top of the
    // screen, separated from the sheet content and its slide animation.
    //
    // The wrapper Box uses onDrawBehind (via themeSurfaceBackground) so the
    // image sits beneath the text/buttons.  The inner Column preserves
    // ColumnScope for callers that rely on weight/align within the sheet.
    val sheetContent: @Composable ColumnScope.() -> Unit = if (hasDialogImage) {
        {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .themeSurfaceBackground(
                        surface = dialogSurface,
                        expectedRole = SurfaceRole.DIALOG,
                        shape = shape,
                        containerAlpha = 1f
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    content()
                }
            }
        }
    } else {
        content
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        // Always opaque: for COLOR this is the DIALOG color itself; for IMAGE
        // this is the fallback color that shows through transparent image
        // pixels and in any content-inset areas the image does not cover.
        containerColor = dialogSurface.fallbackColor,
        contentColor = AppTheme.colors.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = AppScrim,
        dragHandle = dragHandle,
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = AppTheme.darkSystemBarIcons,
            isAppearanceLightNavigationBars = AppTheme.darkSystemBarIcons
        ),
        content = sheetContent
    )
}
