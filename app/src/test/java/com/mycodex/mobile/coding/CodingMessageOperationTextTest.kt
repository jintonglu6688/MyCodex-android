package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Test

class CodingMessageOperationTextTest {
    @Test
    fun threadOperationFailureExplainsUnsupportedCommand() {
        assertEquals(
            "分叉失败：Windows 端不支持此操作，请先更新 Windows 版 MyCodex。",
            threadOperationFailureText("分叉", "unsupported_command"),
        )
    }

    @Test
    fun threadOperationFailureExplainsUnavailableAndBusy() {
        assertEquals(
            "完整分叉失败：Windows 端当前无法执行此操作。",
            threadOperationFailureText("完整分叉", "operation_not_available"),
        )
        assertEquals(
            "压缩上下文失败：会话正在处理其他请求，请稍后重试。",
            threadOperationFailureText("压缩上下文", "session_busy"),
        )
    }

    @Test
    fun threadOperationFailureKeepsUnknownErrorCode() {
        assertEquals("恢复默认失败：timeout", threadOperationFailureText("恢复默认", "timeout"))
    }

    @Test
    fun copyAllMessagesUsesSenderBlocks() {
        val text = copyAllMessagesText(
            listOf(
                CodingMessage(id = "1", role = CodingMessageRole.User, content = "hello", createdAt = null),
                CodingMessage(id = "2", role = CodingMessageRole.Assistant, content = "world", createdAt = null),
            ),
        )

        assertEquals("我: hello\n\ncodex: world", text)
    }
}
