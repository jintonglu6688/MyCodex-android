package com.mycodex.mobile.notifications

import com.mycodex.mobile.relay.RemoteEventPayload

internal data class NotificationSoundSettings(
    val turnCompletedEnabled: Boolean = true,
    val subAgentCompletedEnabled: Boolean = true,
    val approvalRequestedEnabled: Boolean = true,
    val messageSentEnabled: Boolean = true,
)

internal enum class NotificationSoundCue {
    TurnCompleted,
    SubAgentCompleted,
    ApprovalRequested,
    MessageSent,
}

internal object NotificationSoundEventPolicy {
    fun cueFor(
        event: RemoteEventPayload,
        settings: NotificationSoundSettings,
    ): NotificationSoundCue? = when (event.eventType) {
        // Approvals and questions share one cue and switch, matching the Windows client.
        "approval.requested",
        "tool.input.requested",
        -> NotificationSoundCue.ApprovalRequested
            .takeIf { settings.approvalRequestedEnabled }

        "coding.message.completed",
        "office.message.completed",
        -> if (event.payload.optBoolean("isSubAgent", false)) {
            NotificationSoundCue.SubAgentCompleted
                .takeIf { settings.subAgentCompletedEnabled }
        } else {
            NotificationSoundCue.TurnCompleted
                .takeIf { settings.turnCompletedEnabled }
        }

        else -> null
    }
}

internal fun NotificationSoundSettings.withCueEnabled(
    cue: NotificationSoundCue,
    enabled: Boolean,
): NotificationSoundSettings = when (cue) {
    NotificationSoundCue.TurnCompleted -> copy(turnCompletedEnabled = enabled)
    NotificationSoundCue.SubAgentCompleted -> copy(subAgentCompletedEnabled = enabled)
    NotificationSoundCue.ApprovalRequested -> copy(approvalRequestedEnabled = enabled)
    NotificationSoundCue.MessageSent -> copy(messageSentEnabled = enabled)
}

internal class NotificationSoundEventDeduplicator(
    private val capacity: Int = 128,
) {
    private val recentKeys = LinkedHashSet<String>()

    fun shouldPlay(event: RemoteEventPayload): Boolean {
        val identity = event.payload.optString("requestId").takeIf(String::isNotBlank)
            ?: event.payload.optString("turnId").takeIf(String::isNotBlank)
            ?: event.operationId?.takeIf(String::isNotBlank)
            ?: event.requestId?.takeIf(String::isNotBlank)
            ?: return true
        val key = "${event.eventType}:$identity"
        if (!recentKeys.add(key)) {
            return false
        }
        while (recentKeys.size > capacity) {
            recentKeys.remove(recentKeys.first())
        }
        return true
    }

    fun clear() {
        recentKeys.clear()
    }
}
