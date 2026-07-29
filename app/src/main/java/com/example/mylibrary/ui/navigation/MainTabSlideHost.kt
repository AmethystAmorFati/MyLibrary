package com.example.mylibrary.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import com.example.mylibrary.ui.navigation.AppNavigationTransitions.durationMillis
import kotlin.math.roundToInt

@Composable
fun MainTabSlideHost(
    targetTab: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    val stateHolder = rememberSaveableStateHolder()
    val progress = remember { Animatable(1f) }
    var fromTab by rememberSaveable { mutableIntStateOf(targetTab) }
    var toTab by rememberSaveable { mutableIntStateOf(targetTab) }
    var widthPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(targetTab) {
        if (targetTab == toTab && progress.value >= 1f) return@LaunchedEffect

        val sourceTab = retargetMainTabSource(
            fromTab = fromTab,
            toTab = toTab,
            progress = progress.value
        )
        progress.snapTo(0f)
        fromTab = sourceTab
        toTab = targetTab

        if (fromTab != toTab) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis)
            )
        }
        fromTab = targetTab
        toTab = targetTab
        progress.snapTo(1f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { widthPx = it.width }
            .testTag("main_tab_host")
    ) {
        if (fromTab == toTab) {
            stateHolder.SaveableStateProvider(toTab) {
                Box(Modifier.fillMaxSize()) {
                    content(toTab)
                }
            }
        } else {
            val direction = mainTabSlideDirection(fromTab, toTab)
            val animatedProgress = progress.value
            val fromOffset = when (direction) {
                MainTabSlideDirection.LEFT -> -widthPx * animatedProgress
                MainTabSlideDirection.RIGHT -> widthPx * animatedProgress
                MainTabSlideDirection.NONE -> 0f
            }
            val toOffset = when (direction) {
                MainTabSlideDirection.LEFT -> widthPx * (1f - animatedProgress)
                MainTabSlideDirection.RIGHT -> -widthPx * (1f - animatedProgress)
                MainTabSlideDirection.NONE -> 0f
            }

            stateHolder.SaveableStateProvider(fromTab) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(fromOffset.roundToInt(), 0) }
                ) {
                    content(fromTab)
                }
            }
            stateHolder.SaveableStateProvider(toTab) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(toOffset.roundToInt(), 0) }
                ) {
                    content(toTab)
                }
            }
        }
    }
}
