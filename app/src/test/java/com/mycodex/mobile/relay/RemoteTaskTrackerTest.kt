package com.mycodex.mobile.relay

import com.mycodex.mobile.coding.CodingRemoteMode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTaskTrackerTest {
    @Test
    fun completedMessageVibratesOnceAndRemovesTask() {
        val tracker = RemoteTaskTracker()
        tracker.add(CodingRemoteMode.Coding, "session_a", "assistant_old")

        val action = tracker.handle(
            RemoteEventPayload(
                requestId = null,
                operationId = null,
                eventType = "coding.message.completed",
                payload = JSONObject().put("sessionId", "session_a"),
            ),
        )

        assertEquals(RemoteTaskEventAction.Vibrate, action)
        assertFalse(tracker.hasTasks)
        assertEquals(RemoteTaskEventAction.Ignore, tracker.handle(completedEvent()))
    }

    @Test
    fun updateRequestsSnapshotWithoutVibratingOrRemovingTask() {
        val tracker = RemoteTaskTracker()
        tracker.add(CodingRemoteMode.Office, "session_a", "assistant_old")

        val action = tracker.handle(
            RemoteEventPayload(
                requestId = null,
                operationId = null,
                eventType = "office.session.updated",
                payload = JSONObject().put("sessionId", "session_a"),
            ),
        )

        assertEquals(RemoteTaskEventAction.Refresh, action)
        assertTrue(tracker.hasTasks)
    }

    @Test
    fun snapshotWithNewAssistantMessageVibratesAndRemovesTask() {
        val tracker = RemoteTaskTracker()
        tracker.add(CodingRemoteMode.Coding, "session_a", "assistant_old")

        val action = tracker.handleSnapshot(
            mode = CodingRemoteMode.Coding,
            sessionId = "session_a",
            latestAssistantMessageId = "assistant_new",
            generating = false,
        )

        assertEquals(RemoteTaskEventAction.Vibrate, action)
        assertFalse(tracker.hasTasks)
    }

    @Test
    fun consecutiveSnapshotsDuringGenerationEachVibrateAndKeepTracking() {
        val tracker = RemoteTaskTracker()
        tracker.add(CodingRemoteMode.Coding, "session_a", "assistant_old")

        val firstAction = tracker.handleSnapshot(
            mode = CodingRemoteMode.Coding,
            sessionId = "session_a",
            latestAssistantMessageId = "assistant_new_1",
            generating = true,
        )
        val secondAction = tracker.handleSnapshot(
            mode = CodingRemoteMode.Coding,
            sessionId = "session_a",
            latestAssistantMessageId = "assistant_new_2",
            generating = true,
        )

        assertEquals(RemoteTaskEventAction.Vibrate, firstAction)
        assertEquals(RemoteTaskEventAction.Vibrate, secondAction)
        assertTrue(tracker.hasTasks)
    }

    @Test
    fun failedMessageRemovesTaskWithoutVibration() {
        val tracker = RemoteTaskTracker()
        tracker.add(CodingRemoteMode.Coding, "session_a", "assistant_old")

        val action = tracker.handle(
            RemoteEventPayload(
                requestId = null,
                operationId = null,
                eventType = "coding.message.failed",
                payload = JSONObject().put("sessionId", "session_a"),
            ),
        )

        assertEquals(RemoteTaskEventAction.Stop, action)
        assertFalse(tracker.hasTasks)
    }

    private fun completedEvent(): RemoteEventPayload {
        return RemoteEventPayload(
            requestId = null,
            operationId = null,
            eventType = "coding.message.completed",
            payload = JSONObject().put("sessionId", "session_a"),
        )
    }
}
