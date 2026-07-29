package com.example.mylibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.example.mylibrary.ui.MyLibraryApp
import com.example.mylibrary.ui.theme.MyLibraryTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var appliedDarkSystemBarIcons: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MyLibraryApplication).container
        val themeRepository = container.themeRepository

        applySystemBarStyle(themeRepository.currentTheme.value.darkSystemBarIcons)
        lifecycleScope.launch {
            themeRepository.restore()
        }

        setContent {
            val resolvedTheme by themeRepository.currentTheme.collectAsState()
            LaunchedEffect(resolvedTheme.darkSystemBarIcons) {
                applySystemBarStyle(resolvedTheme.darkSystemBarIcons)
            }
            MyLibraryTheme(resolvedTheme = resolvedTheme) {
                MyLibraryApp(container = container)
            }
        }
    }

    private fun applySystemBarStyle(darkSystemBarIcons: Boolean) {
        if (appliedDarkSystemBarIcons == darkSystemBarIcons) return
        appliedDarkSystemBarIcons = darkSystemBarIcons

        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (darkSystemBarIcons) {
            SystemBarStyle.light(scrim = transparent, darkScrim = transparent)
        } else {
            SystemBarStyle.dark(scrim = transparent)
        }
        enableEdgeToEdge(
            statusBarStyle = style,
            navigationBarStyle = style
        )
    }
}
