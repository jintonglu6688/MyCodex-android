package com.mycodex.mobile.coding

import com.mycodex.mobile.relay.RemoteEventPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteApprovalControllerTest {
    @Test
    fun approvalRequestedBecomesActiveRequestAndCompletionClearsIt() = runTest {
        val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 4)
        val controller = RemoteApprovalController(
            scope = this,
            remoteEvents = events,
            responder = { _, _ -> RemoteApprovalResponseResult(RemoteApprovalResponseState.Completed, completed = true) },
        )

        controller.start()
        events.tryEmit(
            RemoteEventPayload(
                requestId = "approval-1",
                operationId = null,
                eventType = "approval.requested",
                payload = JSONObject()
                    .put("requestId", "approval-1")
                    .put("title", "命令请求")
                    .put("questionText", "运行构建？"),
            ),
        )
        advanceUntilIdle()

        assertEquals("approval-1", controller.state.value.activeRequest?.requestId)

        events.tryEmit(
            RemoteEventPayload(
                requestId = "approval-1",
                operationId = null,
                eventType = "approval.completed",
                payload = JSONObject().put("requestId", "approval-1"),
            ),
        )
        advanceUntilIdle()

        assertNull(controller.state.value.activeRequest)
        controller.stop()
    }

    @Test
    fun requestNotFoundResponseClearsActiveRequest() = runTest {
        val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 4)
        val controller = RemoteApprovalController(
            scope = this,
            remoteEvents = events,
            responder = { _, _ ->
                RemoteApprovalResponseResult(
                    RemoteApprovalResponseState.Error,
                    errorCode = "request_not_found",
                )
            },
        )

        controller.start()
        events.tryEmit(
            RemoteEventPayload(
                requestId = "approval-1",
                operationId = null,
                eventType = "approval.requested",
                payload = JSONObject()
                    .put("requestId", "approval-1")
                    .put("title", "命令请求")
                    .put("questionText", "运行构建？"),
            ),
        )
        advanceUntilIdle()

        controller.respond(RemoteApprovalDecision.Decline)
        advanceUntilIdle()

        assertNull(controller.state.value.activeRequest)
        controller.stop()
    }
}
