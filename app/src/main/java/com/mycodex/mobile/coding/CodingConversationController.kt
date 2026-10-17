package com.mycodex.mobile.coding

import com.mycodex.mobile.relay.RemoteEventPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class CodingConversationController(
    private val sessionId: String,
    private val repository: CodingConversationRepository,
    private val scope: CoroutineScope,
    private val initialAnchor: CodingMessageAnchor? = null,
) {
    private val mutableState = MutableStateFlow(CodingConversationState())
    private var started = false
    private var lifecycleGeneration = 0L
    private var eventJob: Job? = null
    private var loadJob: Job? = null
    private var pageLoadJob: Job? = null
    private var tailRefreshJob: Job? = null
    private var sendJob: Job? = null
    private var stopJob: Job? = null
    private var refreshQueued = false
    private var tailRefreshQueued = false
    private val pendingUserMessages = mutableListOf<CodingMessage>()
    private var pendingBaselineMessageIds: Set<String> = emptySet()
    private var olderCursor: String? = null
    private var newerCursor: String? = null
    private var historyRebuildAnchorId: String? = null
    private var readingAnchorId: String? = initialAnchor?.messageId
    private var readerAtBottom: Boolean = initialAnchor?.wasAtBottom ?: true

    val state: StateFlow<CodingConversationState> = mutableState.asStateFlow()

    fun start() {
        if (started) {
            return
        }

        started = true
        eventJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.remoteEvents.collect { event ->
                if (event.isHistoryChangedEvent()) {
                    historyRebuildAnchorId = readingAnchorId ?: mutableState.value.messages.firstOrNull()?.id
                    olderCursor = null
                    newerCursor = null
                    pendingUserMessages.clear()
                    pendingBaselineMessageIds = emptySet()
                    mutableState.value = mutableState.value.copy(
                        hasOlder = false,
                        hasNewer = false,
                        tailConnected = true,
                        errorCode = "history_changed",
                    )
                    requestMessageRefresh()
                    return@collect
                }
                if (event.isCurrentConversationEvent()) {
                    if (event.isCurrentGenerationFinishedEvent()) {
                        mutableState.value = mutableState.value.copy(
                            generating = false,
                            stopping = false,
                        )
                    }
                    if (mutableState.value.tailConnected) {
                        requestTailRefresh()
                    } else {
                        mutableState.value = mutableState.value.copy(hasNewMessagesAwayFromBottom = true)
                    }
                }
            }
        }
        requestMessageRefresh()
    }

    fun send(
        text: String,
        attachments: List<CodingInputAttachment> = emptyList(),
        onAccepted: () -> Unit = {},
    ) {
        val submittedText = text.trim()
        if (submittedText.isBlank() && attachments.isEmpty()) {
            return
        }
        val current = mutableState.value
        if (current.sending || current.stopping) {
            return
        }

        sendJob?.cancel()
        val sendGeneration = lifecycleGeneration
        val pendingMessage = CodingMessage(
            id = "local-user-${UUID.randomUUID()}",
            role = CodingMessageRole.User,
            content = submittedText.ifBlank { attachments.joinToString("、") { it.displayName } },
            createdAt = null,
        )
        if (pendingUserMessages.isEmpty()) {
            pendingBaselineMessageIds = current.messages
                .filterNot { it.id.startsWith(LocalUserMessageIdPrefix) }
                .mapTo(mutableSetOf()) { it.id }
        }
        pendingUserMessages += pendingMessage
        mutableState.value = current.copy(
            messages = if (current.tailConnected) current.messages + pendingMessage else current.messages,
            sending = true,
            composerStatusText = null,
            errorCode = null,
        )
        sendJob = scope.launch {
            val result = repository.sendMessage(sessionId, submittedText, attachments)
            if (sendGeneration != lifecycleGeneration) return@launch
            if (result.accepted) {
                mutableState.value = mutableState.value.copy(
                    sending = false,
                    generating = true,
                    composerStatusText = if (result.dispatchMode == "queue") "消息已排队" else null,
                    errorCode = null,
                )
                onAccepted()
                goToLatest()
                return@launch
            }

            val errorCode = result.errorCode ?: result.state.name.lowercase()
            if (result.state == CodingSendState.Unavailable || errorCode == "request_timeout") {
                mutableState.value = mutableState.value.copy(
                    sending = false,
                    composerStatusText = "发送结果待确认，请查看此设备的最新历史后再决定是否发送。",
                    errorCode = "send_result_unknown",
                )
                return@launch
            }
            pendingUserMessages.removeAll { it.id == pendingMessage.id }
            if (pendingUserMessages.isEmpty()) {
                pendingBaselineMessageIds = emptySet()
            }
            mutableState.value = mutableState.value.copy(
                messages = mutableState.value.messages.filterNot { it.id == pendingMessage.id },
                sending = false,
                generating = current.generating,
                composerStatusText = "发送失败：$errorCode",
                errorCode = errorCode,
            )
        }
    }

    fun stopGeneration() {
        val current = mutableState.value
        if (!current.generating || current.sending || current.stopping) {
            return
        }

        stopJob?.cancel()
        stopJob = scope.launch {
            mutableState.value = mutableState.value.copy(
                stopping = true,
                composerStatusText = null,
                errorCode = null,
            )

            val result = repository.stopGeneration(sessionId)
            if (result.state == CodingStopState.Completed && result.stopped) {
                mutableState.value = mutableState.value.copy(
                    generating = false,
                    stopping = false,
                    errorCode = null,
                )
                requestMessageRefresh()
                return@launch
            }

            val errorCode = result.errorCode ?: result.state.name.lowercase()
            mutableState.value = mutableState.value.copy(
                stopping = false,
                errorCode = errorCode,
            )
        }
    }

    fun reload() {
        requestMessageRefresh()
    }

    fun loadOlder(viewportAnchor: CodingPagingViewportAnchor? = null) = loadPage(
        direction = PageDirection.Older,
        viewportAnchor = viewportAnchor,
    ) {
        repository.loadMessagePage(sessionId, beforeCursor = olderCursor)
    }

    fun loadNewer(viewportAnchor: CodingPagingViewportAnchor? = null) = loadPage(
        direction = PageDirection.Newer,
        viewportAnchor = viewportAnchor,
    ) {
        repository.loadMessagePage(sessionId, afterCursor = newerCursor)
    }

    fun goToLatest() = loadPage(PageDirection.Latest) { repository.loadMessagePage(sessionId) }
    fun retryGoToLatest() { if (mutableState.value.needsRetryLatest) goToLatest() }

    fun onPagingFocusRestored(messageId: String) {
        if (mutableState.value.restoreFocusMessageId == messageId) {
            mutableState.value = mutableState.value.copy(restoreFocusMessageId = null)
        }
    }

    fun onPagingViewportRestored(restore: CodingPagingViewportRestore, targetWasPositioned: Boolean) {
        val current = mutableState.value
        if (current.pendingPagingViewportRestore != restore) {
            return
        }

        mutableState.value = current.copy(
            pendingPagingViewportRestore = null,
            restoreFocusMessageId = if (targetWasPositioned) restore.messageId else null,
            restoreFocusGeneration = if (targetWasPositioned) current.restoreFocusGeneration + 1 else current.restoreFocusGeneration,
        )
    }

    fun updateReadingAnchor(messageId: String?) { readingAnchorId = messageId }

    fun updateReaderAtBottom(atBottom: Boolean) {
        readerAtBottom = atBottom
        if (atBottom && mutableState.value.hasNewMessagesAwayFromBottom) {
            mutableState.value = mutableState.value.copy(hasNewMessagesAwayFromBottom = false)
        }
    }

    fun stop() {
        lifecycleGeneration += 1
        eventJob?.cancel()
        loadJob?.cancel()
        pageLoadJob?.cancel()
        tailRefreshJob?.cancel()
        sendJob?.cancel()
        stopJob?.cancel()
        eventJob = null
        loadJob = null
        pageLoadJob = null
        tailRefreshJob = null
        sendJob = null
        stopJob = null
        started = false
    }

    private fun requestMessageRefresh() {
        if (loadJob?.isActive == true) {
            refreshQueued = true
            return
        }

        loadJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            do {
                refreshQueued = false
                refreshMessages()
            } while (refreshQueued)
        }
    }

    private fun requestTailRefresh() {
        if (tailRefreshJob?.isActive == true) {
            tailRefreshQueued = true
            return
        }

        tailRefreshJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            do {
                tailRefreshQueued = false
                awaitExistingConversationLoad()
                if (!mutableState.value.tailConnected) {
                    continue
                }

                val followTail = readerAtBottom
                val cursor = newerCursor
                if (cursor != null) {
                    loadPageNow(
                        direction = PageDirection.Newer,
                        restoreBoundaryFocus = false,
                        followTail = followTail,
                    ) {
                        repository.loadMessagePage(sessionId, afterCursor = cursor)
                    }
                    if (hasInvalidatedPagingCursor()) {
                        refreshMessages(followTail = followTail)
                    }
                } else {
                    refreshMessages(followTail = followTail)
                }
            } while (tailRefreshQueued)
        }
    }

    private suspend fun awaitExistingConversationLoad() {
        loadJob?.takeIf { it.isActive }?.join()
        pageLoadJob?.takeIf { it.isActive }?.join()
    }

    private fun hasInvalidatedPagingCursor(): Boolean =
        mutableState.value.errorCode == "history_changed" || mutableState.value.errorCode == "invalid_cursor"

    private suspend fun refreshMessages(followTail: Boolean = false) {
        mutableState.value = mutableState.value.copy(
            loading = true,
            errorCode = null,
        )

        val anchor = historyRebuildAnchorId ?: initialAnchor?.takeIf { !it.wasAtBottom }?.messageId
        var result = repository.loadMessagePage(sessionId, anchorMessageId = anchor)
        if (anchor != null && result.state == CodingLoadState.Loaded && !result.anchorFound) {
            result = repository.loadMessagePage(sessionId)
        }
        when (result.state) {
            CodingLoadState.Loaded -> {
                reconcilePendingUserMessages(result.items)
                mutableState.value = mutableState.value.copy(
                    loadState = CodingLoadState.Loaded,
                    messages = appendPendingUserMessages(snapshotMessages(result, PageDirection.Latest)),
                    loading = false,
                    generating = result.generating ?: mutableState.value.generating,
                    stopping = if (result.generating == false) false else mutableState.value.stopping,
                    errorCode = null,
                    hasOlder = result.hasOlder,
                    hasNewer = result.hasNewer,
                    tailConnected = result.tailConnected,
                    latestAssistantMessageId = result.latestAssistantMessageId,
                    pagingSupported = result.capabilityState == RemoteHostCapabilityState.Supported,
                    legacyLimited = result.capabilityState == RemoteHostCapabilityState.Legacy,
                    scrollToLatestGeneration = if (followTail) {
                        mutableState.value.scrollToLatestGeneration + 1
                    } else {
                        mutableState.value.scrollToLatestGeneration
                    },
                )
                olderCursor = result.olderCursor
                newerCursor = result.newerCursor
                historyRebuildAnchorId = null
            }

            CodingLoadState.Unavailable -> {
                mutableState.value = mutableState.value.copy(
                    loadState = CodingLoadState.Unavailable,
                    loading = false,
                    errorCode = null,
                )
            }

            CodingLoadState.Error -> {
                val errorCode = result.errorCode ?: "operation_failed"
                mutableState.value = mutableState.value.copy(
                    loadState = CodingLoadState.Error,
                    loading = false,
                    errorCode = errorCode,
                )
            }
        }
    }

    private fun loadPage(
        direction: PageDirection,
        restoreBoundaryFocus: Boolean = true,
        viewportAnchor: CodingPagingViewportAnchor? = null,
        operation: suspend () -> CodingMessagePageResult,
    ) {
        val current = mutableState.value
        if (current.loading || current.sending) return
        pageLoadJob = scope.launch {
            loadPageNow(direction, restoreBoundaryFocus, viewportAnchor, false, operation)
        }
    }

    private suspend fun loadPageNow(
        direction: PageDirection,
        restoreBoundaryFocus: Boolean,
        viewportAnchor: CodingPagingViewportAnchor? = null,
        followTail: Boolean,
        operation: suspend () -> CodingMessagePageResult,
    ) {
        val current = mutableState.value
        if (current.loading || current.sending) return
        val boundaryMessageId = when {
            direction == PageDirection.Older -> current.messages.firstOrNull()?.id
            direction == PageDirection.Newer -> current.messages.lastOrNull()?.id
            else -> null
        }
        mutableState.value = current.copy(
            loading = true,
            loadingOlder = direction == PageDirection.Older,
            loadingNewer = direction == PageDirection.Newer,
            errorCode = null,
        )
        val result = operation()
        if (result.state != CodingLoadState.Loaded) {
            mutableState.value = mutableState.value.copy(loading = false, loadingOlder = false, loadingNewer = false, errorCode = result.errorCode, needsRetryLatest = direction == PageDirection.Latest)
            return
        }
        olderCursor = result.olderCursor
        newerCursor = result.newerCursor
        val viewportRestore = if (restoreBoundaryFocus && boundaryMessageId != null) {
            CodingPagingViewportRestore(
                messageId = boundaryMessageId,
                scrollOffset = viewportAnchor
                    ?.takeIf { it.messageId == boundaryMessageId }
                    ?.scrollOffset
                    ?: 0,
                generation = mutableState.value.pagingViewportRestoreGeneration + 1,
            )
        } else {
            null
        }
        mutableState.value = mutableState.value.copy(
            loadState = CodingLoadState.Loaded,
            messages = if (direction == PageDirection.Latest) {
                reconcilePendingUserMessages(result.items)
                appendPendingUserMessages(mergeMessages(mutableState.value.messages, result.items, direction))
            } else {
                mergeMessages(mutableState.value.messages, result.items, direction)
            },
            loading = false,
            loadingOlder = false,
            loadingNewer = false,
            generating = result.generating ?: mutableState.value.generating,
            hasOlder = result.hasOlder,
            hasNewer = result.hasNewer,
            tailConnected = result.tailConnected,
            latestAssistantMessageId = result.latestAssistantMessageId,
            pagingSupported = result.capabilityState == RemoteHostCapabilityState.Supported,
            legacyLimited = result.capabilityState == RemoteHostCapabilityState.Legacy,
            hasNewMessagesAwayFromBottom = !result.tailConnected,
            pendingPagingViewportRestore = viewportRestore,
            pagingViewportRestoreGeneration = viewportRestore?.generation ?: mutableState.value.pagingViewportRestoreGeneration,
            restoreFocusMessageId = null,
            scrollToLatestGeneration = if (direction == PageDirection.Latest || followTail) mutableState.value.scrollToLatestGeneration + 1 else mutableState.value.scrollToLatestGeneration,
            needsRetryLatest = false,
            errorCode = null,
        )
    }

    private fun mergeMessages(existing: List<CodingMessage>, incoming: List<CodingMessage>, direction: PageDirection): List<CodingMessage> {
        if (direction == PageDirection.Latest) return incoming
        if (existing.isEmpty()) return incoming
        if (incoming.isEmpty()) return existing
        return if (direction == PageDirection.Older) incoming + existing.filterNot { old -> incoming.any { it.id == old.id } }
        else existing + incoming.filterNot { next -> existing.any { it.id == next.id } }
    }

    private fun snapshotMessages(result: CodingMessagePageResult, direction: PageDirection): List<CodingMessage> =
        if (result.capabilityState == RemoteHostCapabilityState.Legacy) result.items else mergeMessages(mutableState.value.messages, result.items, direction)

    private fun reconcilePendingUserMessages(remoteMessages: List<CodingMessage>) {
        if (pendingUserMessages.isEmpty()) {
            return
        }

        val canonicalUserMessages = remoteMessages.filter { message ->
            message.role == CodingMessageRole.User &&
                !message.id.startsWith(LocalUserMessageIdPrefix) &&
                message.id !in pendingBaselineMessageIds
        }
        repeat(minOf(canonicalUserMessages.size, pendingUserMessages.size)) {
            pendingUserMessages.removeAt(0)
        }
        pendingBaselineMessageIds = pendingBaselineMessageIds + canonicalUserMessages.map { it.id }
        if (pendingUserMessages.isEmpty()) {
            pendingBaselineMessageIds = emptySet()
        }
    }

    private fun appendPendingUserMessages(messages: List<CodingMessage>): List<CodingMessage> {
        if (pendingUserMessages.isEmpty()) {
            return messages
        }

        val knownIds = messages.mapTo(mutableSetOf()) { it.id }
        return messages + pendingUserMessages.filter { knownIds.add(it.id) }
    }

    private enum class PageDirection { Refresh, Older, Newer, Latest }

    private companion object {
        const val LocalUserMessageIdPrefix = "local-user-"
    }

    private fun RemoteEventPayload.isCurrentConversationEvent(): Boolean {
        val eventSessionId = payload.optString("sessionId")
        if (eventSessionId != sessionId) {
            return false
        }

        return eventType == repository.mode.event("session.updated") ||
            eventType == repository.mode.event("message.completed") ||
            eventType == repository.mode.event("message.failed")
    }

    private fun RemoteEventPayload.isHistoryChangedEvent(): Boolean =
        payload.optString("sessionId") == sessionId && eventType == repository.mode.event("session.history.changed")

    private fun RemoteEventPayload.isCurrentGenerationFinishedEvent(): Boolean {
        return eventType == repository.mode.event("message.completed") ||
            eventType == repository.mode.event("message.failed")
    }
}
