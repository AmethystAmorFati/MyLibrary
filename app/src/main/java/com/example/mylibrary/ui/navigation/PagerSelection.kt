package com.example.mylibrary.ui.navigation

enum class MainTabSlideDirection {
    LEFT,
    RIGHT,
    NONE
}

object MainTabPolicy {
    const val userSwipeEnabled = false
    const val clickNavigationAnimated = true
    const val maxComposedTabs = 2
}

fun mainTabSlideDirection(
    currentTab: Int,
    targetTab: Int
): MainTabSlideDirection =
    when {
        targetTab > currentTab -> MainTabSlideDirection.LEFT
        targetTab < currentTab -> MainTabSlideDirection.RIGHT
        else -> MainTabSlideDirection.NONE
    }

internal fun retargetMainTabSource(
    fromTab: Int,
    toTab: Int,
    progress: Float
): Int =
    if (progress >= 0.5f) toTab else fromTab
