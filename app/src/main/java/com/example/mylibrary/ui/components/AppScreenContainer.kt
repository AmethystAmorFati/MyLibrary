package com.example.mylibrary.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.mylibrary.ui.theme.SurfaceRole
import com.example.mylibrary.ui.theme.TopBarExtraTopPadding

@Composable
fun AppScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Capture the root background layer's coordinates so that sticky
    // overlays (e.g. the home calendar) can align their background image
    // drawing with the root's viewport.
    var rootCoordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    // The resolved background is fixed to this full-screen layer. Insets apply
    // only to content, so the same image continues behind the system bar.
    AppThemeSurface(
        role = SurfaceRole.BACKGROUND,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoordinates = it },
        compositeOverBaseColor = true
    ) {
        CompositionLocalProvider(
            LocalRootBackgroundCoordinates provides rootCoordinates
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = TopBarExtraTopPadding),
                content = content
            )
        }
    }
}
