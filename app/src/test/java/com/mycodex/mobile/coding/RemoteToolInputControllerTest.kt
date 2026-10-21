package com.mycodex.mobile.coding

import com.mycodex.mobile.relay.RemoteEventPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteToolInputControllerTest {
    @Test
    fun requestedEvent_becomesActiveRequest() = runTest {
        val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 4)
        val controller = testController(events)

        controller.start()
        events.emit(toolInputRequested("input-1"))
        advanceUntilIdle()

        assertEquals("input-1", controller.state.value.activeRequest?.requestId)
        controller.stop()
    }

    @Test
    fun submit_sendsAnswersAndClearsOnSuccess() = runTest {
        val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 4)
        var capturedAnswers: Map<String, List<String>>? = null
        val controller = RemoteToolInputController(
            scope = this,
            remoteEvents = events,
            responder = { _, answers ->
                capturedAnswers = answers
                RemoteToolInputResponseResult(RemoteToolInputResponseState.Completed, completed = true)
            },
            canceler = { RemoteToolInputResponseResult(RemoteToolInputResponseState.Canceled, completed = true, canceled = true) },
        )

        controller.start()
        events.emit(toolInputRequested("input-1"))
        advanceUntilIdle()
        controller.updateAnswer("choice", listOf("continue"))
        controller.submit()
        advanceUntilIdle()

        assertEquals(listOf("continue"), capturedAnswers?.get("choice"))
        assertNull(controller.state.value.activeRequest)
        controller.stop()
    }

    @Test
    fun cancelAsyncQuestion_reportsIgnoreFailureAndKeepsRequest() = runTest {
        val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 4)
        var canceledRequestId: String? = null
        val controller = RemoteToolInputController(
            scope = this,
            remoteEvents = events,
            responder = { _, _ -> RemoteToolInputResponseResult(RemoteToolInputResponseState.Completed, completed = true) },
            canceler = { requestId ->
                canceledRequestId = requestId
                RemoteToolInputResponseResult(RemoteToolInputResponseState.Error, errorCode = "operation_failed")
            },
        )

        controller.start()
        events.emit(toolInputRequested("async:thread-1:item-1", kind = "async"))
        advanceUntilIdle()
        controller.cancel()
        advanceUntilIdle()

        assertEquals("async:thread-1:item-1", canceledRequestId)
        assertEquals("async:thread-1:item-1", controller.state.value.activeRequest?.requestId)
        assertEquals("忽略失败：operation_failed", controller.state.value.statusText)
        controller.stop()
    }

    @Test
    fun retainPendingKeepsAnswersForStillPendingRequestAndDropsStaleOnes() = runTest {
        val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 4)
        val controller = testController(events)
        controller.start()
        events.emit(toolInputRequested("input-1"))
        events.emit(toolInputRequested("input-2"))
        advanceUntilIdle()
        controller.updateAnswer("choice", listOf("A"))

        val known = controller.knownRequestIds()
        controller.retainPending(known, setOf("input-1"))
        assertEquals("input-1", controller.state.value.activeRequest?.requestId)
        assertEquals(listOf("A"), controller.state.value.answers["choice"])
        assertEquals(setOf("input-1"), controller.knownRequestIds())

        controller.retainPending(controller.knownRequestIds(), emptySet())
        assertNull(controller.state.value.activeRequest)
        controller.stop()
    }

    private fun TestScope.testController(events: MutableSharedFlow<RemoteEventPayload>): RemoteToolInputController {
        return RemoteToolInputController(
            scope = this,
            remoteEvents = events,
            responder = { _, _ -> RemoteToolInputResponseResult(RemoteToolInputResponseState.Completed, completed = true) },
            canceler = { RemoteToolInputResponseResult(RemoteToolInputResponseState.Canceled, completed = true, canceled = true) },
        )
    }

    private fun toolInputRequested(requestId: String, kind: String? = null): RemoteEventPayload {
        return RemoteEventPayload(
            requestId = requestId,
            operationId = null,
            eventType = "tool.input.requested",
            payload = JSONObject()
                .put("requestId", requestId)
                .apply { if (kind != null) put("kind", kind) }
                .put("questions", JSONArray().put(JSONObject().put("id", "choice").put("question", "请选择"))),
        )
    }
}
