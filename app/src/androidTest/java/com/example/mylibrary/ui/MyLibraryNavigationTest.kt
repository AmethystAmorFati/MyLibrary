package com.example.mylibrary.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import com.example.mylibrary.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MyLibraryNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationSwitchesBetweenAllFourScreens() {
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("资料库").performClick()
        waitForTag("screen_library")

        composeRule.onNodeWithContentDescription("统计与摘录").performClick()
        waitForTag("screen_statistics")

        composeRule.onNodeWithContentDescription("设置").performClick()
        waitForTag("screen_settings")
    }

    @Test
    fun mainTabHostDoesNotRespondToHorizontalSwipe() {
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
        composeRule.onNodeWithTag("main_tab_host").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("首页").assertIsSelected()
    }

    @Test
    fun homeAndSettingsSwitchDirectlyAndPreserveHomeState() {
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_expand_handle").performClick()
        waitForTag("home_month_calendar")

        composeRule.onNodeWithContentDescription("设置").performClick()
        waitForTag("screen_settings")
        composeRule.onNodeWithContentDescription("设置").assertIsSelected()

        assertEquals(
            0,
            composeRule.onAllNodesWithTag("screen_library")
                .fetchSemanticsNodes()
                .size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("screen_statistics")
                .fetchSemanticsNodes()
                .size
        )

        composeRule.onNodeWithContentDescription("首页").performClick()
        waitForTag("screen_home")
        composeRule.onNodeWithContentDescription("首页").assertIsSelected()
        composeRule.onNodeWithTag("home_month_calendar").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("screen_library")
                .fetchSemanticsNodes()
                .size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("screen_statistics")
                .fetchSemanticsNodes()
                .size
        )
    }

    @Test
    fun rapidBottomNavigationClicksEndAtTheLatestTarget() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithContentDescription("资料库").performClick()

        waitForTag("screen_library")
        composeRule.onNodeWithContentDescription("资料库").assertIsSelected()
    }

    @Test
    fun statisticsHasNoSearchAndOpensDedicatedQuoteSearch() {
        composeRule.onNodeWithContentDescription("统计与摘录").performClick()
        waitForTag("screen_statistics")

        composeRule.onNodeWithTag("quote_search").assertDoesNotExist()
        composeRule.onNodeWithText("查看全部摘录 >").performClick()

        waitForTag("screen_quote_list")
        composeRule.onNodeWithTag("quote_search").assertIsDisplayed()
        composeRule.onNodeWithText("全部摘录").assertDoesNotExist()
    }

    @Test
    fun librarySearchOpensAndSystemBackClosesIt() {
        composeRule.onNodeWithContentDescription("资料库").performClick()
        waitForTag("screen_library")
        composeRule.onNodeWithContentDescription("打开搜索").performClick()
        composeRule.onNodeWithContentDescription("关闭搜索").assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithContentDescription("打开搜索").assertIsDisplayed()
    }

    @Test
    fun librarySwitchesBetweenAllThreeViews() {
        composeRule.onNodeWithContentDescription("资料库").performClick()
        waitForTag("screen_library")

        val order = listOf("shelf", "list", "cover")
        var current = order.indexOfFirst {
            composeRule.onAllNodesWithTag("library_view_mode_$it")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        repeat(3) {
            composeRule.onNodeWithTag("library_view_mode_${order[current]}").performClick()
            current = (current + 1) % order.size
            waitForTag("library_view_mode_${order[current]}")
        }
    }

    @Test
    fun homeCalendarExpandsFromWeekToMonthToYearAndBack() {
        composeRule.onNodeWithTag("home_week_calendar").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_expand_handle").performClick()
        waitForTag("home_month_calendar")
        composeRule.onNodeWithTag("home_month_calendar").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_month_calendar").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("home_year_calendar")
                .fetchSemanticsNodes()
                .size
        )
        composeRule.onNodeWithTag("calendar_month_title").performClick()
        waitForTag("home_year_calendar")

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForTag("home_month_calendar")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
