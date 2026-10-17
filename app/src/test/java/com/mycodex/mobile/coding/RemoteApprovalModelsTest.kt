package com.mycodex.mobile.coding

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteApprovalModelsTest {
    @Test
    fun jsonPayloadMapsToApprovalRequest() {
        val payload = JSONObject()
            .put("requestId", "approval-1")
            .put("sessionId", "session-a")
            .put("title", "命令请求")
            .put("questionText", "运行构建？")
            .put("summaryText", "审批请求：运行构建。")
            .put("detailsText", "Full command: dotnet build")
            .put("canApproveForSession", true)
            .put(
                "decisions",
                JSONArray()
                    .put(JSONObject().put("id", "allow_once").put("label", "允许一次").put("enabled", true))
                    .put(JSONObject().put("id", "allow_for_session").put("label", "允许本会话").put("enabled", true))
                    .put(JSONObject().put("id", "decline").put("label", "拒绝").put("enabled", true)),
            )

        val request = payload.toRemoteApprovalRequest()

        assertEquals("approval-1", request.requestId)
        assertEquals("命令请求", request.title)
        assertEquals("运行构建？", request.questionText)
        assertTrue(request.canApproveForSession)
        assertEquals(RemoteApprovalDecision.AllowOnce, request.decisions.first().decision)
    }
}
