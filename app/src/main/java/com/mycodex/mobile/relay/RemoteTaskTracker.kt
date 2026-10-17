package com.mycodex.mobile.relay

import com.mycodex.mobile.coding.CodingRemoteMode

internal enum class RemoteTaskEventAction {
    Ignore,
    Refresh,
    Vibrate,
    Stop,
}

internal class RemoteTaskTracker {
    private val tasks = mutableMapOf<RemoteTaskKey, String?>()

    val hasTasks: Boolean
        get() = tasks.isNotEmpty()

    fun clear() { tasks.clear() }

    fun add(mode: CodingRemoteMode, sessionId: String, latestAssistantMessageId: String?) {
        tasks[RemoteTaskKey(mode, sessionId)] = latestAssistantMessageId
    }

    fun remove(mode: CodingRemoteMode, sessionId: String) {
        tasks.remove(RemoteTaskKey(mode, sessionId))
    }

    fun handle(event: RemoteEventPayload): RemoteTaskEventAction {
        val sessionId = event.payload.optString("sessionId")
        val task = tasks.keys.firstOrNull { it.sessionId == sessionId && event.eventType.startsWith("${it.mode.eventPrefix}.") }
            ?: return RemoteTaskEventAction.Ignore
        val action = when (event.eventType) {
            task.mode.event("session.updated") -> RemoteTaskEventAction.Refresh
            task.mode.event("message.completed") -> RemoteTaskEventAction.Vibrate
            task.mode.event("message.failed") -> RemoteTaskEventAction.Stop
            else -> return RemoteTaskEventAction.Ignore
        }
        if (action != RemoteTaskEventAction.Refresh) {
            tasks.remove(task)
        }
        return action
    }

    fun handleSnapshot(
        mode: CodingRemoteMode,
        sessionId: String,
        latestAssistantMessageId: String?,
        generating: Boolean?,
    ): RemoteTaskEventAction {
        val task = RemoteTaskKey(mode, sessionId)
        if (!tasks.containsKey(task)) {
            return RemoteTaskEventAction.Ignore
        }
        if (latestAssistantMessageId != null && latestAssistantMessageId != tasks[task]) {
            tasks[task] = latestAssistantMessageId
            if (generating == false) {
                tasks.remove(task)
            }
            return RemoteTaskEventAction.Vibrate
        }
        if (generating == false) {
            tasks.remove(task)
            return RemoteTaskEventAction.Stop
        }
        return RemoteTaskEventAction.Ignore
    }

    private data class RemoteTaskKey(
        val mode: CodingRemoteMode,
        val sessionId: String,
    )
}
