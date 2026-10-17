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

data class RemoteApprovalUiState(
    val activeRequest: RemoteApprovalRequest? = null,
    val submitting: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

class RemoteApprovalController(
    private val scope: CoroutineScope,
    private val remoteEvents: Flow<RemoteEventPayload>,
    private val responder: suspend (String, RemoteApprovalDecision) -> RemoteApprovalResponseResult,
) {
    private val queue = ArrayDeque<RemoteApprovalRequest>()
    private val seenRequestIds = LinkedHashSet<String>()
    private val mutableState = MutableStateFlow(RemoteApprovalUiState())
    private var eventJob: Job? = null

    val state: StateFlow<RemoteApprovalUiState> = mutableState.asStateFlow()

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
        mutableState.value = RemoteApprovalUiState()
    }

    fun respond(decision: RemoteApprovalDecision) {
        val request = mutableState.value.activeRequest ?: return
        if (mutableState.value.submitting) {
            return
        }

        scope.launch {
            mutableState.value = mutableState.value.copy(
                submitting = true,
                statusText = "正在提交审批。",
                errorCode = null,
            )
            val result = responder(request.requestId, decision)
            if (result.state == RemoteApprovalResponseState.Completed && result.completed) {
                completeRequest(request.requestId)
                return@launch
            }

            val errorCode = result.errorCode ?: result.state.name.lowercase()
            if (errorCode == "request_not_found") {
                completeRequest(request.requestId)
                return@launch
            }

            mutableState.value = mutableState.value.copy(
                submitting = false,
                statusText = "提交失败：$errorCode",
                errorCode = errorCode,
            )
        }
    }

    private fun handleEvent(event: RemoteEventPayload) {
        when (event.eventType) {
            "approval.requested" -> enqueue(event.payload.toRemoteApprovalRequest())
            "approval.completed" -> completeRequest(event.payload.optString("requestId").ifBlank { event.requestId.orEmpty() })
        }
    }

    private fun enqueue(request: RemoteApprovalRequest) {
        if (request.requestId.isBlank() || !seenRequestIds.add(request.requestId)) {
            return
        }

        if (mutableState.value.activeRequest == null) {
            mutableState.value = RemoteApprovalUiState(activeRequest = request)
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
            mutableState.value = RemoteApprovalUiState(activeRequest = next)
        }
    }
}
