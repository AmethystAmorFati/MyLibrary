package com.example.mylibrary.ui.item

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordDraftSheetPolicyTest {
    @Test
    fun reviewEditorUsesTheFixedRecordHeight() {
        assertEquals(152.dp, RecordReviewEditorHeight)
    }
}
