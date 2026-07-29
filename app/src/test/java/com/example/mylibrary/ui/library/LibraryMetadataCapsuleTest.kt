package com.example.mylibrary.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylibrary.ui.components.CardMetadataFontSize
import com.example.mylibrary.ui.components.CardMetadataLineHeight
import com.example.mylibrary.ui.components.CardMetadataStarSize
import com.example.mylibrary.ui.components.CardMetadataStarSpacing
import com.example.mylibrary.ui.components.cardMetadataTextStyle
import com.example.mylibrary.ui.theme.DefaultResolvedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryMetadataCapsuleTest {
    @Test
    fun compactMetricsUseOneCentralizedMetadataScale() {
        assertEquals(11.sp, CardMetadataFontSize)
        assertEquals(14.sp, CardMetadataLineHeight)
        assertEquals(14.dp, CardMetadataStarSize)
        assertEquals(2.dp, CardMetadataStarSpacing)
        assertEquals(CardMetadataFontSize, LibraryMetadataCapsuleFontSize)
        assertEquals(CardMetadataLineHeight, LibraryMetadataCapsuleLineHeight)
        assertEquals(20.dp, LibraryMetadataCapsuleHeight)
        assertEquals(7.dp, LibraryMetadataCapsuleHorizontalPadding)
        assertEquals(1.dp, LibraryMetadataCapsuleVerticalPadding)
        assertEquals(10.dp, LibraryMetadataCapsuleCornerRadius)
        assertEquals(4.dp, LibraryMetadataCapsuleSpacing)

        val base = DefaultResolvedTheme.typography.metadata
        val compact = cardMetadataTextStyle(base)
        assertEquals(base.fontFamily, compact.fontFamily)
        assertEquals(FontWeight.Normal, compact.fontWeight)
        assertEquals(11.sp, compact.fontSize)
        assertEquals(14.sp, compact.lineHeight)
        assertEquals(14.sp, DefaultResolvedTheme.typography.itemTitle.fontSize)
        assertEquals(18.sp, DefaultResolvedTheme.typography.itemTitle.lineHeight)
    }

    @Test
    fun metadataContentKeepsEveryTagInItsExistingOrder() {
        val content = libraryMetadataContent(
            showStatus = true,
            showTags = true,
            statusName = "已完成",
            tagNames = listOf("小说", "成长", "女性", "历史")
        )

        assertEquals("已完成", content.statusName)
        assertEquals(listOf("小说", "成长", "女性", "历史"), content.tagNames)
    }

    @Test
    fun metadataContentOmitsDisabledOrBlankPartsWithoutPlaceholders() {
        assertEquals(
            LibraryMetadataContent(statusName = null, tagNames = emptyList()),
            libraryMetadataContent(
                showStatus = false,
                showTags = false,
                statusName = "在读",
                tagNames = listOf("文学")
            )
        )
        assertEquals(
            listOf("小说", "成长"),
            libraryMetadataContent(
                showStatus = true,
                showTags = true,
                statusName = " ",
                tagNames = listOf("小说", "", "成长")
            ).tagNames
        )
    }

    @Test
    fun statusUsesSubtleAccentWhileTagUsesTransparentBorderedVisual() {
        val defaultColors = DefaultResolvedTheme.appColors
        val status = libraryMetadataCapsuleVisual(
            LibraryMetadataCapsuleRole.STATUS,
            defaultColors
        )
        val tag = libraryMetadataCapsuleVisual(
            LibraryMetadataCapsuleRole.TAG,
            defaultColors
        )

        assertEquals(defaultColors.accent.copy(alpha = 0.10f), status.background)
        assertEquals(defaultColors.accent, status.content)
        assertEquals(defaultColors.accent.copy(alpha = 0.28f), status.border)
        assertNotEquals(defaultColors.accent, status.background)
        assertEquals(Color.Transparent, tag.background)
        assertEquals(defaultColors.textSecondary, tag.content)
        assertEquals(defaultColors.border, tag.border)
    }
}
