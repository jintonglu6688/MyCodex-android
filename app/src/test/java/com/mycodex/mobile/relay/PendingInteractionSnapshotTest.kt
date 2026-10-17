package com.mycodex.mobile.relay

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PendingInteractionSnapshotTest {
    @Test fun mapsOnlyCurrentPendingInteractionFamiliesWithOriginalPayloads() {
        val items = JSONArray()
        listOf("approval.requested", "tool.input.requested", "plan.execution.requested").forEach { type ->
            items.put(JSONObject().put("eventType", type).put("requestId", type)
                .put("payload", JSONObject().put("requestId", type).put("sessionId", "same-session")))
        }
        val result = pendingInteractionEvents(RemoteEventPayload("read", null, "interaction.pending.list.result", JSONObject().put("events", items)))!!
        assertEquals(3, result.size)
        assertEquals("approval.requested", result[0].eventType)
        assertEquals("same-session", result[2].payload.getString("sessionId"))
        assertNull(pendingInteractionEvents(RemoteEventPayload("read", null, "system.error", errorCode = "unsupported")))
    }

    @Test fun completionArrivingWhileSnapshotIsInFlightCannotBeReplayedAsPending() {
        val replay = PendingInteractionReplay()
        val published = mutableListOf<RemoteEventPayload>()
        val request = RemoteEventPayload("request-a", null, "approval.requested", JSONObject().put("requestId", "request-a"))
        val completion = request.copy(eventType = "approval.completed")
        val token = replay.begin()
        replay.live(completion, published::add)
        replay.replay(token, request, published::add)
        assertEquals(listOf(completion), published)
        replay.end(token)
        val nextToken = replay.begin()
        replay.replay(nextToken, request, published::add)
        assertEquals(listOf(completion, request), published)
        replay.end(nextToken)
    }

    @Test fun olderRefreshCannotClearNewRefreshCompletionTracking() {
        val replay = PendingInteractionReplay()
        val old = replay.begin()
        val current = replay.begin()
        val published = mutableListOf<RemoteEventPayload>()
        val request = RemoteEventPayload("same", null, "tool.input.requested", JSONObject().put("requestId", "same"))
        replay.live(request.copy(eventType = "tool.input.completed"), published::add)
        replay.end(old)
        replay.replay(current, request, published::add)
        assertEquals(1, published.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonInteractionEventsInSnapshot() {
        pendingInteractionEvents(RemoteEventPayload("read", null, "interaction.pending.list.result",
            JSONObject().put("events", JSONArray().put(JSONObject().put("eventType", "coding.message.accepted").put("payload", JSONObject())))))
    }
}
