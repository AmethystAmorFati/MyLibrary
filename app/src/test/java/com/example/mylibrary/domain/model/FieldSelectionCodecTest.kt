package com.example.mylibrary.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldSelectionCodecTest {
    @Test
    fun multiSelectionRoundTripsWithoutDisplayPunctuation() {
        val encoded = encodeFieldSelection(listOf("孤独", "成长", "家庭"))

        assertEquals(listOf("孤独", "成长", "家庭"), decodeFieldSelection(encoded))
        assertEquals("孤独  成长  家庭", decodeFieldSelection(encoded).joinToString("  "))
        assertTrue("、" !in encoded)
    }

    @Test
    fun encodingTrimsDropsBlanksAndKeepsStableOrder() {
        val encoded = encodeFieldSelection(
            listOf(" 纸质书 ", "", "电子书", "纸质书")
        )

        assertEquals(listOf("纸质书", "电子书"), decodeFieldSelection(encoded))
    }
}
