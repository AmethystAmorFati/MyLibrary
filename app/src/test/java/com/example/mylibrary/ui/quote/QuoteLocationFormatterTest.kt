package com.example.mylibrary.ui.quote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteLocationFormatterTest {
    @Test
    fun chapterAndPageUseOneSharedCompactFormat() {
        assertEquals(
            "第一章 · 第 23 页",
            formatQuoteLocation(
                chapter = " 第一章 ",
                page = " 23 ",
                showChapter = true,
                showPage = true
            )
        )
        assertEquals(
            "第一章",
            formatQuoteLocation(
                chapter = "第一章",
                page = "23",
                showChapter = true,
                showPage = false
            )
        )
        assertEquals(
            "第 23 页",
            formatQuoteLocation(
                chapter = "第一章",
                page = "23",
                showChapter = false,
                showPage = true
            )
        )
    }

    @Test
    fun existingPageAffixesAreNotDuplicated() {
        assertEquals("第 23 页", formatQuotePage("第 23 页"))
        assertEquals("序页 4", formatQuotePage("序页 4"))
    }

    @Test
    fun hiddenOrBlankLocationDoesNotOccupySpace() {
        assertNull(
            formatQuoteLocation(
                chapter = " ",
                page = "",
                showChapter = true,
                showPage = true
            )
        )
        assertNull(
            formatQuoteLocation(
                chapter = "第一章",
                page = "23",
                showChapter = false,
                showPage = false
            )
        )
    }
}
