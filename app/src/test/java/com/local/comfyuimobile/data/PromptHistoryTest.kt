package com.local.comfyuimobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptHistoryTest {
    @Test fun deduplicatesAndMovesReusedTextToFront() {
        assertEquals(listOf("猫", "狗", "鸟"), PromptHistory.add(listOf("狗", "猫", "鸟"), " 猫 "))
    }

    @Test fun retainsAtMostFiftyEntries() {
        val result = (1..60).fold(emptyList<String>()) { history, value -> PromptHistory.add(history, "p$value") }
        assertEquals(50, result.size)
        assertEquals("p60", result.first())
        assertEquals("p11", result.last())
    }
}
