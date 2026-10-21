package com.mycodex.mobile.coding

import com.mycodex.mobile.relay.RemoteEventPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodingConversationControllerTest {
    @Test
    fun start_loadsInitialMessages() = runTest {
        val repository = FakeCodingConversationRepository(
            initialMessages = listOf(CodingMessage("message_a", CodingMessageRole.Assistant, "ready", null)),
        )
        val controller = CodingConversationController("session_a", repository, this)

        controller.start()
        advanceUntilIdle()

        assertEquals(CodingLoadState.Loaded, controller.state.value.loadState)
        assertEquals("message_a", controller.state.value.messages.single().id)
        controller.stop()
    }

    @Test
    fun remoteSessionUpdatedForCurrentSessionReloadsSnapshot() = runTest {
        val repository = FakeCodingConversationRepository(
            initialMessages = emptyList(),
            refreshedMessages = listOf(CodingMessage("message_b", CodingMessageRole.Assistant, "done", null)),
        )
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        repository.emit(
            RemoteEventPayload(
                requestId = null,
                operationId = null,
                eventType = "coding.session.updated",
                payload = JSONObject().put("sessionId", "session_a"),
            ),
        )
        advanceUntilIdle()

        assertEquals("message_b", controller.state.value.messages.single().id)
        assertEquals(2, repository.loadCount)
        controller.stop()
    }

    @Test
    fun completedEventDuringInFlightRefreshRequestsFreshSnapshot() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val firstLoad = CompletableDeferred<Unit>()
        repository.loadGate = firstLoad
        val controller = CodingConversationController("session_a", repository, this)

        controller.start()
        repository.emit(
            RemoteEventPayload(
                requestId = null,
                operationId = null,
                eventType = "coding.message.completed",
                payload = JSONObject().put("sessionId", "session_a"),
            ),
        )
        repository.refreshedMessages = listOf(CodingMessage("message_b", CodingMessageRole.Assistant, "done", null))
        firstLoad.complete(Unit)
        advanceUntilIdle()

        assertEquals("message_b", controller.state.value.messages.single().id)
        assertEquals(2, repository.loadCount)
        assertEquals(false, controller.state.value.generating)
        controller.stop()
    }

    @Test
    fun tailEventsDuringIncrementalLoadQueueAnotherPageAndKeepTheFinalMessage() = runTest {
        val first = CodingMessage("message_a", CodingMessageRole.Assistant, "first", null)
        val second = CodingMessage("message_b", CodingMessageRole.Assistant, "second", null)
        val third = CodingMessage("message_c", CodingMessageRole.Assistant, "third", null)
        val repository = FakeCodingConversationRepository(initialMessages = listOf(first)).apply {
            pagingSupported = true
            pageSnapshots = mutableListOf(
                listOf(first),
                listOf(first, second),
                listOf(first, second, third),
            )
            blockedPageLoadNumber = 2
            pageLoadGate = CompletableDeferred()
        }
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        repository.emitCurrentSessionUpdated()
        runCurrent()
        repository.emitCurrentSessionUpdated()
        runCurrent()
        repository.pageLoadGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("message_a", "message_b", "message_c"), controller.state.value.messages.map { it.id })
        assertEquals(3, repository.loadCount)
        assertEquals(2L, controller.state.value.scrollToLatestGeneration)
        controller.stop()
    }

    @Test
    fun tailEventsWhileReaderIsAwayFromBottomAppendWithoutRequestingScroll() = runTest {
        val first = CodingMessage("message_a", CodingMessageRole.Assistant, "first", null)
        val second = CodingMessage("message_b", CodingMessageRole.Assistant, "second", null)
        val repository = FakeCodingConversationRepository(initialMessages = listOf(first)).apply {
            pagingSupported = true
            pageSnapshots = mutableListOf(
                listOf(first),
                listOf(first, second),
            )
        }
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        controller.updateReaderAtBottom(false)
        repository.emitCurrentSessionUpdated()
        advanceUntilIdle()

        assertEquals(listOf("message_a", "message_b"), controller.state.value.messages.map { it.id })
        assertEquals(2, repository.loadCount)
        assertEquals(0L, controller.state.value.scrollToLatestGeneration)
        controller.stop()
    }

    @Test
    fun tailEventWithInvalidatedCursorReloadsTheLatestSnapshot() = runTest {
        val first = CodingMessage("message_a", CodingMessageRole.Assistant, "first", null)
        val second = CodingMessage("message_b", CodingMessageRole.Assistant, "second", null)
        val repository = FakeCodingConversationRepository(initialMessages = listOf(first)).apply {
            pagingSupported = true
            failAfterCursorWithHistoryChanged = true
            pageSnapshots = mutableListOf(
                listOf(first),
                listOf(first),
                listOf(first, second),
            )
        }
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        repository.emitCurrentSessionUpdated()
        advanceUntilIdle()

        assertEquals(listOf("message_a", "message_b"), controller.state.value.messages.map { it.id })
        assertEquals(3, repository.loadCount)
        assertEquals(CodingLoadState.Loaded, controller.state.value.loadState)
        controller.stop()
    }

    @Test
    fun officeMessageCompletedForCurrentSessionReloadsSnapshotAndStopsGenerating() = runTest {
        val repository = FakeCodingConversationRepository(
            initialMessages = emptyList(),
            refreshedMessages = listOf(
                CodingMessage("message_a", CodingMessageRole.User, "整理周报", null),
                CodingMessage("message_b", CodingMessageRole.Assistant, "done", null),
            ),
            mode = CodingRemoteMode.Office,
        )
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        controller.send("整理周报")
        advanceUntilIdle()
        assertEquals(true, controller.state.value.generating)

        repository.emit(
            RemoteEventPayload(
                requestId = null,
                operationId = null,
                eventType = "office.message.completed",
                payload = JSONObject().put("sessionId", "session_a"),
            ),
        )
        advanceUntilIdle()

        assertEquals("message_b", controller.state.value.messages.last().id)
        assertEquals(false, controller.state.value.generating)
        assertEquals(3, repository.loadCount)
        controller.stop()
    }

    @Test
    fun foregroundReloadUsesSnapshotGenerationState() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        repository.snapshotGenerating = true
        controller.send("继续处理")
        advanceUntilIdle()
        assertEquals(true, controller.state.value.generating)

        repository.snapshotGenerating = false
        controller.reload()
        advanceUntilIdle()

        assertEquals(false, controller.state.value.generating)
        controller.stop()
    }

    @Test
    fun sendShowsLocalUserMessageBeforeRemoteAcceptance() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        controller.send("hello")

        assertEquals(CodingMessageRole.User, controller.state.value.messages.single().role)
        assertEquals("hello", controller.state.value.messages.single().content)
        controller.stop()
    }

    @Test
    fun snapshotWithoutCanonicalUserKeepsPendingMessage() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        controller.send("hello")
        advanceUntilIdle()

        assertEquals(CodingMessageRole.User, controller.state.value.messages.single().role)
        assertEquals("hello", controller.state.value.messages.single().content)
        controller.stop()
    }

    @Test
    fun canonicalUserSnapshotReplacesPendingMessage() = runTest {
        val canonical = CodingMessage("canonical_user", CodingMessageRole.User, "hello", null)
        val repository = FakeCodingConversationRepository(
            initialMessages = emptyList(),
            refreshedMessages = listOf(canonical),
        )
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        controller.send("hello")
        advanceUntilIdle()

        assertEquals(listOf(canonical), controller.state.value.messages)
        controller.stop()
    }

    @Test
    fun remoteSessionUpdatedForDifferentSessionIsIgnored() = runTest {
        val repository = FakeCodingConversationRepository(
            initialMessages = listOf(CodingMessage("message_a", CodingMessageRole.Assistant, "ready", null)),
            refreshedMessages = listOf(CodingMessage("message_b", CodingMessageRole.Assistant, "done", null)),
        )
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        repository.emit(
            RemoteEventPayload(
                requestId = null,
                operationId = null,
                eventType = "coding.session.updated",
                payload = JSONObject().put("sessionId", "session_b"),
            ),
        )
        advanceUntilIdle()

        assertEquals("message_a", controller.state.value.messages.single().id)
        assertEquals(1, repository.loadCount)
        controller.stop()
    }

    @Test
    fun remoteSessionUpdatedReplacesFullSnapshotWhenMessageIdentityChanges() = runTest {
        val repository = FakeCodingConversationRepository(
            initialMessages = listOf(CodingMessage("temporary_user", CodingMessageRole.User, "hello", null)),
            refreshedMessages = listOf(CodingMessage("canonical_user", CodingMessageRole.User, "hello", null)),
        )
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        repository.emit(
            RemoteEventPayload(
                requestId = null,
                operationId = null,
                eventType = "coding.session.updated",
                payload = JSONObject().put("sessionId", "session_a"),
            ),
        )
        advanceUntilIdle()

        assertEquals(1, controller.state.value.messages.size)
        assertEquals("canonical_user", controller.state.value.messages.single().id)
        assertEquals("hello", controller.state.value.messages.single().content)
        controller.stop()
    }

    @Test
    fun sendAllowsAttachmentOnlyAndPassesAttachmentsToRepository() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val controller = CodingConversationController("session_a", repository, this)
        val attachment = CodingInputAttachment(
            kind = CodingAttachmentKind.Skill,
            type = "skill",
            name = "writing-plans",
            displayName = "Writing Plans",
            path = "E:/Skills/writing-plans",
        )
        var accepted = false

        controller.send("   ", listOf(attachment)) {
            accepted = true
        }
        advanceUntilIdle()

        assertEquals("session_a", repository.lastSendSessionId)
        assertEquals("", repository.lastSendText)
        assertEquals(listOf(attachment), repository.lastSendAttachments)
        assertEquals(true, accepted)
    }

    @Test
    fun failedAttachmentSendDoesNotCallAcceptedCallback() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        repository.nextSendResult = CodingSendResult(CodingSendState.Error, errorCode = "write_lock_denied")
        val controller = CodingConversationController("session_a", repository, this)
        val attachment = CodingInputAttachment(
            kind = CodingAttachmentKind.Plugin,
            type = "mention",
            name = "mycodex-office",
            displayName = "MyCodex Office",
            path = "E:/Plugins/mycodex-office",
        )
        var accepted = false

        controller.send("   ", listOf(attachment)) {
            accepted = true
        }
        advanceUntilIdle()

        assertEquals(false, accepted)
        assertEquals(false, controller.state.value.sending)
        assertEquals("write_lock_denied", controller.state.value.errorCode)
        assertEquals(emptyList<CodingMessage>(), controller.state.value.messages)
    }

    @Test
    fun generatingConversationAcceptsFollowUpMessage() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val controller = CodingConversationController("session_a", repository, this)

        controller.send("first")
        advanceUntilIdle()
        assertEquals(true, controller.state.value.generating)

        controller.send("follow-up")
        advanceUntilIdle()

        assertEquals("follow-up", repository.lastSendText)
        assertEquals(true, controller.state.value.generating)
        assertEquals(2, controller.state.value.messages.size)
    }

    @Test
    fun failedFollowUpSendKeepsExistingGenerationRunning() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val controller = CodingConversationController("session_a", repository, this)

        controller.send("first")
        advanceUntilIdle()
        repository.nextSendResult = CodingSendResult(CodingSendState.Error, errorCode = "send_not_accepted")

        controller.send("follow-up")
        advanceUntilIdle()

        assertEquals(false, controller.state.value.sending)
        assertEquals(true, controller.state.value.generating)
        assertEquals("send_not_accepted", controller.state.value.errorCode)
        assertEquals("发送失败：send_not_accepted", controller.state.value.composerStatusText)
        assertEquals(listOf("first"), controller.state.value.messages.map { it.content })
    }

    @Test
    fun queuedFollowUpShowsComposerStatus() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val controller = CodingConversationController("session_a", repository, this)

        controller.send("first")
        advanceUntilIdle()
        repository.nextSendResult = CodingSendResult(
            state = CodingSendState.Accepted,
            dispatchMode = "queue",
        )

        controller.send("follow-up")
        advanceUntilIdle()

        assertEquals(true, controller.state.value.generating)
        assertEquals("消息已排队", controller.state.value.composerStatusText)
    }

    @Test
    fun unknownSendResultDoesNotCallAcceptedOrAutomaticallySendAgain() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        repository.nextSendResult = CodingSendResult(CodingSendState.Error, errorCode = "request_timeout")
        val controller = CodingConversationController("session_a", repository, this)
        var accepted = false
        controller.send("uncertain") { accepted = true }
        advanceUntilIdle()
        assertEquals(false, accepted)
        assertEquals("send_result_unknown", controller.state.value.errorCode)
        assertEquals(1, repository.sendCount)
        controller.reload()
        advanceUntilIdle()
        assertEquals(1, repository.sendCount)
    }

    @Test
    fun stoppedControllerIgnoresLateAcceptedResultEvenWhenRepositoryIgnoresCancellation() = runTest {
        val repository = FakeCodingConversationRepository(initialMessages = emptyList())
        val gate = CompletableDeferred<Unit>()
        repository.sendGate = gate
        val controller = CodingConversationController("session_a", repository, this)
        var accepted = false
        controller.send("old host") { accepted = true }
        runCurrent()
        controller.stop()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, accepted)
        assertEquals(1, repository.sendCount)
    }

    @Test
    fun olderPageKeepsTailCursorAndConnectionFromLatestPage() = runTest {
        val first = CodingMessage("message_a", CodingMessageRole.Assistant, "first", null)
        val second = CodingMessage("message_b", CodingMessageRole.Assistant, "second", null)
        val latest = CodingMessage("message_c", CodingMessageRole.Assistant, "latest", null)
        val repository = FakeCodingConversationRepository(initialMessages = emptyList()).apply {
            pageResults = mutableListOf(
                pagedResult(listOf(latest), olderCursor = "older-1", newerCursor = "tail-1", hasOlder = true),
                pagedResult(listOf(first, second), newerCursor = "after-b", hasNewer = true, tailConnected = false),
                pagedResult(emptyList(), newerCursor = "tail-2"),
            )
        }
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        controller.loadOlder()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(listOf("message_a", "message_b", "message_c"), state.messages.map { it.id })
        assertEquals(false, state.hasOlder)
        assertEquals(false, state.hasNewer)
        assertEquals(true, state.tailConnected)
        assertEquals(false, state.hasNewMessagesAwayFromBottom)

        repository.emitCurrentSessionUpdated()
        advanceUntilIdle()

        assertEquals("tail-1", repository.requestedAfterCursors.last())
        controller.stop()
    }

    @Test
    fun refreshWhileReadingHistoryRebuildsAroundReadingPosition() = runTest {
        val first = CodingMessage("message_a", CodingMessageRole.Assistant, "first", null)
        val repository = FakeCodingConversationRepository(initialMessages = listOf(first))
        val controller = CodingConversationController("session_a", repository, this)
        controller.start()
        advanceUntilIdle()

        controller.updateReadingAnchor("message_a")
        controller.updateReaderAtBottom(false)
        controller.reload()
        advanceUntilIdle()
        assertEquals("message_a", repository.requestedAnchors.last())

        controller.updateReaderAtBottom(true)
        controller.reload()
        advanceUntilIdle()
        assertEquals(null, repository.requestedAnchors.last())
        controller.stop()
    }

    private fun pagedResult(
        items: List<CodingMessage>,
        olderCursor: String? = null,
        newerCursor: String? = null,
        hasOlder: Boolean = false,
        hasNewer: Boolean = false,
        tailConnected: Boolean = true,
    ) = CodingMessagePageResult(
        state = CodingLoadState.Loaded,
        items = items,
        olderCursor = olderCursor,
        newerCursor = newerCursor,
        hasOlder = hasOlder,
        hasNewer = hasNewer,
        tailConnected = tailConnected,
        capabilityState = RemoteHostCapabilityState.Supported,
    )

    private class FakeCodingConversationRepository(
        private val initialMessages: List<CodingMessage>,
        var refreshedMessages: List<CodingMessage> = initialMessages,
        override val mode: CodingRemoteMode = CodingRemoteMode.Coding,
    ) : CodingConversationRepository {
        private val events = MutableSharedFlow<RemoteEventPayload>(extraBufferCapacity = 8)

        var loadCount = 0

        var sendCount = 0
        var sendGate: CompletableDeferred<Unit>? = null

        var nextSendResult: CodingSendResult = CodingSendResult(CodingSendState.Accepted)

        var snapshotGenerating: Boolean? = null

        var loadGate: CompletableDeferred<Unit>? = null

        var pageLoadGate: CompletableDeferred<Unit>? = null

        var blockedPageLoadNumber: Int? = null

        var pagingSupported: Boolean = false

        var failAfterCursorWithHistoryChanged: Boolean = false

        var pageSnapshots: MutableList<List<CodingMessage>> = mutableListOf()

        var pageResults: MutableList<CodingMessagePageResult>? = null

        val requestedAfterCursors = mutableListOf<String?>()

        val requestedAnchors = mutableListOf<String?>()

        var lastSendSessionId: String? = null

        var lastSendText: String? = null

        var lastSendAttachments: List<CodingInputAttachment> = emptyList()

        override val remoteEvents: Flow<RemoteEventPayload> = events

        override suspend fun loadMessages(sessionId: String): CodingListResult<CodingMessage> {
            loadCount += 1
            loadGate?.await()
            return CodingListResult(
                state = CodingLoadState.Loaded,
                items = if (loadCount == 1) initialMessages else refreshedMessages,
                generating = snapshotGenerating,
            )
        }

        override suspend fun loadMessagePage(
            sessionId: String,
            beforeCursor: String?,
            afterCursor: String?,
            anchorMessageId: String?,
        ): CodingMessagePageResult {
            loadCount += 1
            requestedAfterCursors += afterCursor
            requestedAnchors += anchorMessageId
            pageResults?.getOrNull(loadCount - 1)?.let { return it }
            if (failAfterCursorWithHistoryChanged && afterCursor != null) {
                return CodingMessagePageResult(
                    state = CodingLoadState.Error,
                    errorCode = "history_changed",
                    capabilityState = RemoteHostCapabilityState.Supported,
                )
            }
            val snapshot = pageSnapshots.getOrNull(loadCount - 1)
                ?: if (loadCount == 1) initialMessages else refreshedMessages
            if (blockedPageLoadNumber == loadCount) {
                pageLoadGate?.await()
            } else {
                loadGate?.await()
            }
            return CodingMessagePageResult(
                state = CodingLoadState.Loaded,
                items = snapshot,
                generating = snapshotGenerating,
                olderCursor = if (pagingSupported) "older-$loadCount" else null,
                newerCursor = if (pagingSupported) "newer-$loadCount" else null,
                tailConnected = true,
                capabilityState = if (pagingSupported) {
                    RemoteHostCapabilityState.Supported
                } else {
                    RemoteHostCapabilityState.Legacy
                },
            )
        }

        override suspend fun sendMessage(
            sessionId: String,
            text: String,
            attachments: List<CodingInputAttachment>,
        ): CodingSendResult {
            sendCount += 1
            sendGate?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { it.await() } }
            lastSendSessionId = sessionId
            lastSendText = text
            lastSendAttachments = attachments
            return nextSendResult
        }

        override suspend fun stopGeneration(sessionId: String): CodingStopResult {
            return CodingStopResult(CodingStopState.Completed, stopped = true)
        }

        fun emit(event: RemoteEventPayload) {
            events.tryEmit(event)
        }

        fun emitCurrentSessionUpdated() {
            emit(
                RemoteEventPayload(
                    requestId = null,
                    operationId = null,
                    eventType = "coding.session.updated",
                    payload = JSONObject().put("sessionId", "session_a"),
                ),
            )
        }
    }
}
