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
class RemotePlanExecutionControllerTest {
    @Test
    fun requestedEvent_becomesActiveRequest() = runTest {
        val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 4)
        val controller = testController(events)

        controller.start()
        events.emit(planRequested("plan-1"))
        advanceUntilIdle()

        assertEquals("plan-1", controller.state.value.activeRequest?.requestId)
        controller.stop()
    }

    @Test
    fun respond_sendsActionAndClearsOnSuccess() = runTest {
        val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 4)
        var capturedAction: RemotePlanExecutionAction? = null
        val controller = RemotePlanExecutionController(
            scope = this,
            remoteEvents = events,
            responder = { _, action ->
                capturedAction = action
                RemotePlanExecutionResponseResult(RemotePlanExecutionResponseState.Completed, completed = true)
            },
        )

        controller.start()
        events.emit(planRequested("plan-1"))
        advanceUntilIdle()
        controller.respond(RemotePlanExecutionAction.Execute)
        advanceUntilIdle()

        assertEquals(RemotePlanExecutionAction.Execute, capturedAction)
        assertNull(controller.state.value.activeRequest)
        controller.stop()
    }

    private fun kotlinx.coroutines.test.TestScope.testController(events: MutableSharedFlow<RemoteEventPayload>): RemotePlanExecutionController {
        return RemotePlanExecutionController(
            scope = this,
            remoteEvents = events,
            responder = { _, _ -> RemotePlanExecutionResponseResult(RemotePlanExecutionResponseState.Completed, completed = true) },
        )
    }

    private fun planRequested(requestId: String): RemoteEventPayload {
        return RemoteEventPayload(
            requestId = requestId,
            operationId = null,
            eventType = "plan.execution.requested",
            payload = JSONObject()
                .put("requestId", requestId)
                .put("threadId", "thread-1")
                .put("planText", "# Plan"),
        )
    }
}
