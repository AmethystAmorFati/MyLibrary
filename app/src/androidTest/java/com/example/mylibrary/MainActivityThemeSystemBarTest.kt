package com.example.mylibrary

import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityThemeSystemBarTest {
    @Test
    fun themeSystemBarModeUpdatesBothSystemBarIconPolicies() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.applySystemBarStyle(true)
                val controller = WindowCompat.getInsetsController(
                    activity.window,
                    activity.window.decorView
                )
                assertTrue(activity.appliedDarkSystemBarIcons == true)
                assertTrue(controller.isAppearanceLightStatusBars)
                assertTrue(controller.isAppearanceLightNavigationBars)

                activity.applySystemBarStyle(false)
                assertTrue(activity.appliedDarkSystemBarIcons == false)
                assertFalse(controller.isAppearanceLightStatusBars)
                assertFalse(controller.isAppearanceLightNavigationBars)
            }
        }
    }
}
