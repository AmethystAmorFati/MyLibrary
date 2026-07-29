package com.example.mylibrary.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationIconResolverTest {
    @Test
    fun defaultResolverKeepsCurrentNormalAndSelectedVectors() {
        assertIcons(
            NavigationIconSlot.HOME,
            Icons.Outlined.Home,
            Icons.Filled.Home
        )
        assertIcons(
            NavigationIconSlot.LIBRARY,
            Icons.Outlined.LocalLibrary,
            Icons.Filled.LocalLibrary
        )
        assertIcons(
            NavigationIconSlot.STATISTICS,
            Icons.Outlined.BarChart,
            Icons.Filled.BarChart
        )
        assertIcons(
            NavigationIconSlot.SETTINGS,
            Icons.Outlined.Settings,
            Icons.Filled.Settings
        )
    }

    @Test
    fun missingSelectedAssetFallsBackToNormalAsset() {
        val normal = NavigationIconAsset.Vector(Icons.Outlined.Home)
        val resource = NavigationIconResource(normal = normal, selected = null)

        assertEquals(normal, resource.forSelection(isSelected = false))
        assertEquals(normal, resource.forSelection(isSelected = true))
    }

    @Test
    fun partialResolvedResolverFreezesCustomSlotsAndKeepsBuiltInFallbacks() {
        val customHome = NavigationIconResource(
            normal = NavigationIconAsset.Vector(Icons.Filled.Settings),
            selected = null
        )
        val mutable = mutableMapOf(NavigationIconSlot.HOME to customHome)
        val resolver = ResolvedNavigationIconResolver(mutable)
        mutable.clear()

        assertEquals(customHome, resolver.resolve(NavigationIconSlot.HOME))
        assertTrue(resolver.hasCustomResource(NavigationIconSlot.HOME))
        assertEquals(
            DefaultNavigationIconResolver.resolve(NavigationIconSlot.LIBRARY),
            resolver.resolve(NavigationIconSlot.LIBRARY)
        )
        assertTrue(!resolver.hasCustomResource(NavigationIconSlot.LIBRARY))
    }

    private fun assertIcons(
        slot: NavigationIconSlot,
        normal: androidx.compose.ui.graphics.vector.ImageVector,
        selected: androidx.compose.ui.graphics.vector.ImageVector
    ) {
        val resource = DefaultNavigationIconResolver.resolve(slot)
        assertTrue(resource.normal is NavigationIconAsset.Vector)
        assertTrue(resource.selected is NavigationIconAsset.Vector)
        assertEquals(
            normal,
            (resource.normal as NavigationIconAsset.Vector).imageVector
        )
        assertEquals(
            selected,
            (resource.selected as NavigationIconAsset.Vector).imageVector
        )
    }
}
