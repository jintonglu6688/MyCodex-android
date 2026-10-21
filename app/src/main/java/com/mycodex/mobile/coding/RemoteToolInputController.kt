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

data class RemoteToolInputUiState(
    val activeRequest: RemoteToolInputRequest? = null,
    val answers: Map<String, List<String>> = emptyMap(),
    val submitting: Boolean = false,
    val canceling: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

class RemoteToolInputController(
    private val scope: CoroutineScope,
    private val remoteEvents: Flow<RemoteEventPayload>,
    private val responder: suspend (String, Map<String, List<String>>) -> RemoteToolInputResponseResult,
    private val canceler: suspend (String) -> RemoteToolInputResponseResult,
) {
    private val queue = ArrayDeque<RemoteToolInputRequest>()
    private val seenRequestIds = LinkedHashSet<String>()
    private val mutableState = MutableStateFlow(RemoteToolInputUiState())
    private var eventJob: Job? = null

    val state: StateFlow<RemoteToolInputUiState> = mutableState.asStateFlow()

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
        mutableState.value = RemoteToolInputUiState()
    }

    fun updateAnswer(questionId: String, values: List<String>) {
        if (questionId.isBlank()) {
            return
        }

        mutableState.value = mutableState.value.copy(
            answers = mutableState.value.answers + (questionId to values),
            statusText = null,
            errorCode = null,
        )
    }

    fun submit() {
        val request = mutableState.value.activeRequest ?: return
        if (mutableState.value.submitting || mutableState.value.canceling) {
            return
        }

        val answers = normalizedAnswersFor(request)
        if (answers == null) {
            mutableState.value = mutableState.value.copy(
                statusText = "请完成所有输入。",
                errorCode = "invalid_input",
            )
            return
        }

        scope.launch {
            mutableState.value = mutableState.value.copy(
                submitting = true,
                statusText = "正在提交。",
                errorCode = null,
            )
            val result = responder(request.requestId, answers)
            if (result.state == RemoteToolInputResponseState.Completed && result.completed || result.errorCode == "request_not_found") {
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

    fun cancel() {
        val request = mutableState.value.activeRequest ?: return
        if (mutableState.value.submitting || mutableState.value.canceling) {
            return
        }

        scope.launch {
            mutableState.value = mutableState.value.copy(
                canceling = true,
                statusText = "正在${request.dismissActionLabel}。",
                errorCode = null,
            )
            val result = canceler(request.requestId)
            if (result.completed || result.errorCode == "request_not_found") {
                completeRequest(request.requestId)
                return@launch
            }

            val error = result.errorCode ?: result.state.name.lowercase()
            mutableState.value = mutableState.value.copy(
                canceling = false,
                statusText = "${request.dismissActionLabel}失败：$error",
                errorCode = error,
            )
        }
    }

    private fun normalizedAnswersFor(request: RemoteToolInputRequest): Map<String, List<String>>? {
        val result = linkedMapOf<String, List<String>>()
        for (question in request.questions) {
            val values = mutableState.value.answers[question.id].orEmpty()
            val normalized = if (values.firstOrNull() == "__other__") {
                values.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { listOf(it) }
            } else {
                values.map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
            }

            if (normalized == null) {
                return null
            }

            result[question.id] = normalized
        }

        return result
    }

    private fun handleEvent(event: RemoteEventPayload) {
        when (event.eventType) {
            "tool.input.requested" -> enqueue(event.payload.toRemoteToolInputRequest())
            "tool.input.completed" -> completeRequest(event.payload.optString("requestId").ifBlank { event.requestId.orEmpty() })
        }
    }

    private fun enqueue(request: RemoteToolInputRequest) {
        if (request.requestId.isBlank() || request.questions.isEmpty() || !seenRequestIds.add(request.requestId)) {
            return
        }

        if (mutableState.value.activeRequest == null) {
            mutableState.value = RemoteToolInputUiState(activeRequest = request)
            return
        }

        queue.addLast(request)
    }

    private fun completeRequest(requestId: String) {
        if (requestId.isNotBlank()) {
            seenRequestIds.remove(requestId)
            queue.removeAll { request -> request.requestId == requestId }
        }

        val active = mutableState.value.activeRequest
        if (active != null && (requestId.isBlank() || active.requestId == requestId)) {
            val next = queue.removeFirstOrNull()
            mutableState.value = RemoteToolInputUiState(activeRequest = next)
        }
    }
}
