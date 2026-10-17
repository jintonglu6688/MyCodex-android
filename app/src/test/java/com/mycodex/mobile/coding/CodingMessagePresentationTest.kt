package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodingMessagePresentationTest {
    @Test
    fun accessibilityTextIncludesFullLongMessageContent() {
        val longContent = (1..40).joinToString(separator = "") { "长消息第${it}段。" }
        val message = CodingMessage(
            id = "message-1",
            role = CodingMessageRole.Assistant,
            content = longContent,
            createdAt = null,
        )

        val text = message.accessibilityText()

        assertEquals("codex：$longContent", text)
        assertTrue(text.length > 120)
        assertTrue(text.endsWith("长消息第40段。"))
    }

    @Test
    fun longMessagesCollapseVisuallyByDefault() {
        val longContent = (1..40).joinToString(separator = "") { "消息${it}。" }

        assertTrue(shouldCollapseMessageContent(longContent))
        assertFalse(shouldCollapseMessageContent("短消息"))
    }

    @Test
    fun visualToggleLabelReflectsExpandedState() {
        assertEquals("展开全文", messageExpansionLabel(expanded = false))
        assertEquals("收起", messageExpansionLabel(expanded = true))
    }

    @Test
    fun officeModeFeatureFlagsHideCodingOnlyActions() {
        assertFalse(CodingRemoteMode.Office.supportsWorkspaceOperations)
        assertFalse(CodingRemoteMode.Office.supportsThreadOperations)
        assertFalse(CodingRemoteMode.Office.supportsSubAgents)
        assertFalse(CodingRemoteMode.Office.supportsCompact)
    }
}
