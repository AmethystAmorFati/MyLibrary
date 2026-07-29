package com.example.mylibrary.ui.settings

import com.example.mylibrary.ui.theme.BottomContentPadding
import com.example.mylibrary.ui.theme.MainHeaderContentSpacing
import com.example.mylibrary.ui.theme.MainHeaderHeight
import com.example.mylibrary.ui.theme.TopBarContentHeight
import com.example.mylibrary.ui.theme.TopBarExtraTopPadding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsPageLayoutPolicyTest {
    @Test
    fun settingsUsesUnifiedTopTokensWithoutDuplicateStatusPadding() {
        assertEquals(TopBarContentHeight, MainHeaderHeight)
        assertEquals(2f, TopBarExtraTopPadding.value)
        assertEquals(6f, MainHeaderContentSpacing.value)
        assertFalse(SettingsPageLayoutPolicy.hasOwnStatusBarPadding)
    }

    @Test
    fun settingsContentStartsAtTopAndUsesSharedBottomPadding() {
        assertFalse(SettingsPageLayoutPolicy.verticallyCentersContent)
        assertEquals(BottomContentPadding, SettingsPageLayoutPolicy.contentBottomPadding)
    }
}
