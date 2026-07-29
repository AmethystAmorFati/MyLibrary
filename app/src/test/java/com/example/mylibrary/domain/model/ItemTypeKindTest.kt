package com.example.mylibrary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemTypeKindTest {
    @Test
    fun builtInKindDependsOnStableIdNotDisplayName() {
        assertEquals(ItemTypeKind.BOOK, ItemType(1, "图书", 0).kind)
        assertEquals(ItemTypeKind.MOVIE, ItemType(2, "影片", 1).kind)
        assertEquals("作者", ItemType(1, "任意名称", 0).creatorLabel)
        assertEquals("导演", ItemType(2, "任意名称", 1).creatorLabel)
    }

    @Test
    fun customTypeNeverBecomesBuiltInByUsingABuiltInName() {
        assertEquals(ItemTypeKind.CUSTOM, ItemType(99, "Movie", 2).kind)
        assertEquals("作者", ItemType(99, "Movie", 2).creatorLabel)
    }
}
