package com.mycodex.mobile.coding

import com.mycodex.mobile.relay.RemoteEventPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemotePlanExecutionUiState(
    val activeRequest: RemotePlanExecutionRequest? = null,
    val submitting: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

class RemotePlanExecutionController(
    private val scope: CoroutineScope,
    private val remoteEvents: Flow<RemoteEventPayload>,
    private val responder: suspend (RemotePlanExecutionRequest, RemotePlanExecutionAction) -> RemotePlanExecutionResponseResult,
) {
    private val queue = ArrayDeque<RemotePlanExecutionRequest>()
    private val seenRequestIds = LinkedHashSet<String>()
    private val mutableState = MutableStateFlow(RemotePlanExecutionUiState())
    private var eventJob: Job? = null

    val state: StateFlow<RemotePlanExecutionUiState> = mutableState.asStateFlow()

    fun start() {
        if (eventJob != null) {
            return
        }

        eventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            remoteEvents.collect { event ->
                handleEvent(event)
            }
        }
    }

    fun stop() {
        eventJob?.cancel()
        eventJob = null
        queue.clear()
        seenRequestIds.clear()
        mutableState.value = RemotePlanExecutionUiState()
    }

    /** Request ids shown or queued now; pass them to [retainPending] once the pending snapshot arrives. */
    fun knownRequestIds(): Set<String> = seenRequestIds.toSet()

    /** Drops requests known before a reconnect that the target no longer lists, keeping answers for ones still pending. */
    fun retainPending(previouslyKnown: Set<String>, pending: Set<String>) {
        previouslyKnown.filterNot { it in pending }.forEach(::completeRequest)
    }

    fun respond(action: RemotePlanExecutionAction) {
        val request = mutableState.value.activeRequest ?: return
        if (mutableState.value.submitting) {
            return
        }

        scope.launch {
            mutableState.value = mutableState.value.copy(
                submitting = true,
                statusText = "正在提交。",
                errorCode = null,
            )
            val result = responder(request, action)
            if (result.state == RemotePlanExecutionResponseState.Completed && result.completed || result.errorCode == "request_not_found") {
                completeRequest(request.requestId)
                return@launch
            }

            val error = result.errorCode ?: result.state.name.lowercase()
            mutableState.value = mutableState.value.copy(
                submitting = false,
                statusText = "提交失败：$error",
                errorCode = error,
            )
        }
    }

    private fun handleEvent(event: RemoteEventPayload) {
        when (event.eventType) {
            "plan.execution.requested" -> enqueue(event.payload.toRemotePlanExecutionRequest())
            "plan.execution.completed" -> completeRequest(event.payload.optString("requestId").ifBlank { event.requestId.orEmpty() })
        }
    }

    private fun enqueue(request: RemotePlanExecutionRequest) {
        if (request.requestId.isBlank() || !seenRequestIds.add(request.requestId)) {
            return
        }

        if (mutableState.value.activeRequest == null) {
            mutableState.value = RemotePlanExecutionUiState(activeRequest = request)
            return
        }

        queue.addLast(request)
    }

    private fun completeRequest(requestId: String) {
        if (requestId.isNotBlank()) {
            seenRequestIds.remove(requestId)
            queue.removeAll { it.requestId == requestId }
        }

        val active = mutableState.value.activeRequest
        if (active != null && (requestId.isBlank() || active.requestId == requestId)) {
            val next = queue.removeFirstOrNull()
            mutableState.value = RemotePlanExecutionUiState(activeRequest = next)
        }
    }
}
