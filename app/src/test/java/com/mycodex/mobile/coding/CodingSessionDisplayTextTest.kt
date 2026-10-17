package com.mycodex.mobile.coding

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CodingSessionDisplayTextTest {
    @Test
    fun displayTextMatchesWindowsStyleWithSubagentsAgeAndMainlineTitle() {
        val session = CodingSession(
            id = "session_a",
            workspaceId = "workspace_a",
            title = "MyCodex移动端远程模式开发",
            createdAt = "2026-06-07T08:00:00Z",
            updatedAt = "2026-06-07T10:00:00Z",
            branchKind = CodingSessionBranchKind.Mainline,
            subAgentCount = 2,
        )

        val text = session.displayText(
            nowEpochMillis = Instant.parse("2026-06-07T23:30:00Z").toEpochMilli(),
        )

        assertEquals("有 2 个子代理，15小时前，主线：MyCodex移动端远程模式开发", text)
    }

    @Test
    fun displayTextOmitsAgeWhenCreatedLessThanOneHourAgo() {
        val session = CodingSession(
            id = "session_a",
            workspaceId = "workspace_a",
            title = "Review changes",
            createdAt = "2026-06-07T23:00:00Z",
            updatedAt = "2026-06-07T23:10:00Z",
            branchKind = CodingSessionBranchKind.Review,
            subAgentCount = 0,
        )

        val text = session.displayText(
            nowEpochMillis = Instant.parse("2026-06-07T23:30:00Z").toEpochMilli(),
        )

        assertEquals("审核：Review changes", text)
    }

    @Test
    fun metadataTextUsesUpdateTimeAndKeepsTitleSeparate() {
        val session = CodingSession(
            id = "session_a",
            workspaceId = "workspace_a",
            title = "Long title",
            createdAt = "2026-06-07T08:00:00Z",
            updatedAt = "2026-06-07T22:00:00Z",
            branchKind = CodingSessionBranchKind.Fork,
            subAgentCount = 2,
        )

        assertEquals(
            "1小时前，分叉，2 个子代理",
            session.metadataText(Instant.parse("2026-06-07T23:30:00Z").toEpochMilli()),
        )
    }

    @Test
    fun subAgentDisplayTextPrefersSubAgentNameAndRole() {
        val session = CodingSession(
            id = "subagent_a",
            workspaceId = "workspace_a",
            title = "History session",
            createdAt = "2026-06-07T08:00:00Z",
            updatedAt = "2026-06-07T09:00:00Z",
            branchKind = CodingSessionBranchKind.Mainline,
            subAgentName = "Ampere",
            subAgentRole = "reviewer",
        )

        assertEquals("Ampere，reviewer，主线：History session", session.subAgentDisplayText())
    }

    @Test
    fun reasoningEffortDisplayNameUsesSuperHighForXHigh() {
        assertEquals("超高", reasoningEffortDisplayName("xhigh"))
    }
}
