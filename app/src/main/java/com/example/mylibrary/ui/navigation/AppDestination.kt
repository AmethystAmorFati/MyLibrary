package com.example.mylibrary.ui.navigation

sealed class AppDestination(
    val route: String,
    val label: String,
    val iconSlot: NavigationIconSlot
) {
    data object Home : AppDestination("home", "首页", NavigationIconSlot.HOME)
    data object Library : AppDestination("library", "资料库", NavigationIconSlot.LIBRARY)
    data object Statistics :
        AppDestination("statistics", "统计与摘录", NavigationIconSlot.STATISTICS)
    data object Settings : AppDestination("settings", "设置", NavigationIconSlot.SETTINGS)
}

val bottomDestinations = listOf(
    AppDestination.Home,
    AppDestination.Library,
    AppDestination.Statistics,
    AppDestination.Settings
)
