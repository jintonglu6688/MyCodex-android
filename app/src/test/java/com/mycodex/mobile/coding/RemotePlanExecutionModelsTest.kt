package com.mycodex.mobile.coding

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePlanExecutionModelsTest {
    @Test
    fun toRemotePlanExecutionRequest_parsesPlanAndActions() {
        val payload = JSONObject()
            .put("requestId", "plan-1")
            .put("threadId", "thread-1")
            .put("title", "计划已生成")
            .put("questionText", "是否执行计划？")
            .put("planText", "# Plan")
            .put(
                "actions",
                JSONArray()
                    .put(JSONObject().put("id", "execute").put("label", "执行计划").put("enabled", true))
                    .put(JSONObject().put("id", "continue").put("label", "继续讨论").put("enabled", true)),
            )

        val request = payload.toRemotePlanExecutionRequest()

        assertEquals("plan-1", request.requestId)
        assertEquals("thread-1", request.threadId)
        assertEquals("# Plan", request.planText)
        assertEquals(RemotePlanExecutionAction.Execute, request.actions[0].action)
        assertEquals(RemotePlanExecutionAction.Continue, request.actions[1].action)
    }
}
