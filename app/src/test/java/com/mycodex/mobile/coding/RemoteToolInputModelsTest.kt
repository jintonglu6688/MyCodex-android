package com.mycodex.mobile.coding

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteToolInputModelsTest {
    @Test
    fun toRemoteToolInputRequest_parsesQuestionsAndOptions() {
        val payload = JSONObject(
            """
            {
              "requestId": "input-1",
              "title": "计划模式",
              "questions": [
                {
                  "id": "choice",
                  "header": "选择",
                  "question": "请选择",
                  "description": "说明",
                  "isOther": true,
                  "isSecret": false,
                  "options": [
                    { "label": "继续", "value": "continue", "description": "继续当前计划" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val request = payload.toRemoteToolInputRequest()

        assertEquals("input-1", request.requestId)
        assertEquals("计划模式", request.title)
        assertEquals("choice", request.questions[0].id)
        assertTrue(request.questions[0].isOther)
        assertFalse(request.questions[0].isSecret)
        assertEquals("继续", request.questions[0].options[0].label)
        assertEquals("continue", request.questions[0].options[0].value)
    }
}
