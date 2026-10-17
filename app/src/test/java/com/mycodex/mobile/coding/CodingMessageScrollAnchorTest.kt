package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodingMessageScrollAnchorTest {
    @Test
    fun restoreIndexReturnsNullWhenAnchorIsAtBottom() {
        val messages = listOf(message("a"), message("b"))
        val anchor = CodingMessageAnchor("session", "a", 4, wasAtBottom = true, messageCount = 2, savedAtEpochMillis = 10)

        assertNull(messages.restoreIndexForAnchor(anchor))
    }

    @Test
    fun restoreIndexFindsMessageById() {
        val messages = listOf(message("a"), message("b"), message("c"))
        val anchor = CodingMessageAnchor("session", "b", 7, wasAtBottom = false, messageCount = 3, savedAtEpochMillis = 10)

        assertEquals(1, messages.restoreIndexForAnchor(anchor)?.messageIndex)
        assertEquals(7, messages.restoreIndexForAnchor(anchor)?.scrollOffset)
    }

    @Test
    fun restoreIndexReturnsNullWhenMessageIsMissing() {
        val messages = listOf(message("a"), message("b"))
        val anchor = CodingMessageAnchor("session", "missing", 0, wasAtBottom = false, messageCount = 2, savedAtEpochMillis = 10)

        assertNull(messages.restoreIndexForAnchor(anchor))
    }

    private fun message(id: String): CodingMessage {
        return CodingMessage(id = id, role = CodingMessageRole.Assistant, content = id, createdAt = null)
    }
}
