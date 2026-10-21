package com.mycodex.mobile.notifications

import com.mycodex.mobile.relay.RemoteEventPayload
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSoundModelsTest {
    @Test
    fun defaultsEnableAllFourCues() {
        val settings = NotificationSoundSettings()

        assertTrue(settings.turnCompletedEnabled)
        assertTrue(settings.subAgentCompletedEnabled)
        assertTrue(settings.approvalRequestedEnabled)
        assertTrue(settings.messageSentEnabled)
    }

    @Test
    fun completionUsesDedicatedSubAgentCue() {
        val event = event(
            eventType = "coding.message.completed",
            turnId = "turn-subagent",
            isSubAgent = true,
        )

        assertEquals(
            NotificationSoundCue.SubAgentCompleted,
            NotificationSoundEventPolicy.cueFor(event, NotificationSoundSettings()),
        )
    }

    @Test
    fun completionUsesTurnCueForNormalOfficeSession() {
        val event = event(
            eventType = "office.message.completed",
            turnId = "turn-office",
            isSubAgent = false,
        )

        assertEquals(
            NotificationSoundCue.TurnCompleted,
            NotificationSoundEventPolicy.cueFor(event, NotificationSoundSettings()),
        )
    }

    @Test
    fun questionsShareTheApprovalCueAndSwitch() {
        val event = event(
            eventType = "tool.input.requested",
            requestId = "async:thread-1:item-1",
        )

        assertEquals(
            NotificationSoundCue.ApprovalRequested,
            NotificationSoundEventPolicy.cueFor(event, NotificationSoundSettings()),
        )
        assertNull(NotificationSoundEventPolicy.cueFor(event, NotificationSoundSettings(approvalRequestedEnabled = false)))
    }

    @Test
    fun disabledCueIsNotSelected() {
        val event = event(
            eventType = "approval.requested",
            requestId = "approval-1",
        )
        val settings = NotificationSoundSettings(approvalRequestedEnabled = false)

        assertNull(NotificationSoundEventPolicy.cueFor(event, settings))
    }

    @Test
    fun duplicateRemoteEventOnlyPlaysOnce() {
        val event = event(
            eventType = "coding.message.completed",
            turnId = "turn-1",
        )
        val deduplicator = NotificationSoundEventDeduplicator()

        assertTrue(deduplicator.shouldPlay(event))
        assertFalse(deduplicator.shouldPlay(event))
    }

    @Test
    fun updatingOneCuePreservesOtherSettings() {
        val updated = NotificationSoundSettings()
            .withCueEnabled(NotificationSoundCue.MessageSent, false)

        assertFalse(updated.messageSentEnabled)
        assertTrue(updated.turnCompletedEnabled)
        assertTrue(updated.subAgentCompletedEnabled)
        assertTrue(updated.approvalRequestedEnabled)
    }

    private fun event(
        eventType: String,
        requestId: String? = null,
        turnId: String? = null,
        isSubAgent: Boolean? = null,
    ): RemoteEventPayload {
        val payload = JSONObject()
        requestId?.let { payload.put("requestId", it) }
        turnId?.let { payload.put("turnId", it) }
        isSubAgent?.let { payload.put("isSubAgent", it) }
        return RemoteEventPayload(
            requestId = requestId,
            operationId = turnId,
            eventType = eventType,
            payload = payload,
        )
    }
}
