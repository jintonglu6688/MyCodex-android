package com.mycodex.mobile.coding

import org.junit.Assert.assertEquals
import org.junit.Test

class CodingMessageOperationTextTest {
    @Test
    fun rollbackSummaryForLatestTurn() {
        assertEquals(
            "将删除第 4 轮内容。此操作会修改当前会话历史。",
            rollbackSummaryText(turnIndex = 4, rollbackAffectedTurns = 1),
        )
    }

    @Test
    fun rollbackSummaryForEarlierTurn() {
        assertEquals(
            "将删除第 2 轮和之后 2 轮内容。此操作会修改当前会话历史。",
            rollbackSummaryText(turnIndex = 2, rollbackAffectedTurns = 3),
        )
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
