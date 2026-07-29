package com.example.mylibrary.ui.navigation

object MainRoutes {
    const val ROOT = "main"
}

object HomeRoutes {
    const val YEAR = "year"
    const val MONTH = "month"
    const val ANNUAL = "home/annual/{$YEAR}/{$MONTH}"

    fun annual(year: Int, month: Int): String =
        "home/annual/$year/${month.coerceIn(1, 12)}"
}
