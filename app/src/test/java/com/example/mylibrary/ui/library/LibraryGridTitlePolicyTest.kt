package com.example.mylibrary.ui.library

import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGridTitlePolicyTest {
    @Test
    fun gridTitleIsCenteredAndKeepsTwoLineLimit() {
        assertEquals(TextAlign.Center, LibraryGridTitlePolicy.textAlign)
        assertEquals(2, LibraryGridTitlePolicy.maxLines)
    }
}
