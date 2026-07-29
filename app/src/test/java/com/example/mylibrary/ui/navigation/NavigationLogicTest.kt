package com.example.mylibrary.ui.navigation

import com.example.mylibrary.ui.home.annualInitialMonthIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLogicTest {
    @Test
    fun secondaryTransitionsAreHorizontalOnly() {
        assertEquals(PageSlideDirection.LEFT, AppNavigationTransitions.forwardEnterDirection)
        assertEquals(PageSlideDirection.LEFT, AppNavigationTransitions.forwardExitDirection)
        assertEquals(PageSlideDirection.RIGHT, AppNavigationTransitions.backEnterDirection)
        assertEquals(PageSlideDirection.RIGHT, AppNavigationTransitions.backExitDirection)
        assertFalse(AppNavigationTransitions.usesFade)
    }

    @Test
    fun mainTabTargetDirectionMatchesDestinationPosition() {
        assertEquals(MainTabSlideDirection.LEFT, mainTabSlideDirection(0, 3))
        assertEquals(MainTabSlideDirection.RIGHT, mainTabSlideDirection(3, 1))
        assertEquals(MainTabSlideDirection.NONE, mainTabSlideDirection(2, 2))
    }

    @Test
    fun mainTabHostDisablesGesturesAndComposesAtMostTwoTabs() {
        assertFalse(MainTabPolicy.userSwipeEnabled)
        assertTrue(MainTabPolicy.clickNavigationAnimated)
        assertEquals(2, MainTabPolicy.maxComposedTabs)
    }

    @Test
    fun rapidRetargetUsesTheCurrentlyDominantTabAsTheNewSource() {
        assertEquals(0, retargetMainTabSource(fromTab = 0, toTab = 3, progress = 0.49f))
        assertEquals(3, retargetMainTabSource(fromTab = 0, toTab = 3, progress = 0.5f))
    }

    @Test
    fun annualCalendarIsAddressedAsASecondaryRoute() {
        assertEquals("home/annual/2026/7", HomeRoutes.annual(2026, 7))
        assertTrue(HomeRoutes.ANNUAL.contains("{${HomeRoutes.YEAR}}"))
        assertTrue(HomeRoutes.ANNUAL.contains("{${HomeRoutes.MONTH}}"))
    }

    @Test
    fun annualCalendarUsesTheRouteMonthInsteadOfTheSelectedDateYear() {
        assertEquals(6, annualInitialMonthIndex(2026, 2026, 7))
        assertEquals(0, annualInitialMonthIndex(2027, 2026, 7))
    }
}
