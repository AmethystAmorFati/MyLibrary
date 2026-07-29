package com.example.mylibrary

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.mylibrary.di.AppContainer
import com.example.mylibrary.di.DefaultAppContainer
import com.example.mylibrary.ui.theme.clearThemeNavigationIconCache
import com.example.mylibrary.ui.theme.clearThemeSurfaceImageCache

class MyLibraryApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val shouldClearThemeImages =
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (shouldClearThemeImages) {
            clearThemeSurfaceImageCache()
            clearThemeNavigationIconCache()
        }
    }
}
