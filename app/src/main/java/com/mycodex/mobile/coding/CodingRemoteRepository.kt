package com.mycodex.mobile.coding

import com.mycodex.mobile.relay.RemoteCommandGateway
import com.mycodex.mobile.relay.RemoteCommandPayload
import com.mycodex.mobile.relay.RemoteEventPayload
import com.mycodex.mobile.relay.RemoteEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

interface CodingConversationRepository {
    val mode: CodingRemoteMode

    val remoteEvents: Flow<RemoteEventPayload>

    suspend fun loadMessages(sessionId: String): CodingListResult<CodingMessage>

    suspend fun loadMessagePage(
        sessionId: String,
        beforeCursor: String? = null,
        afterCursor: String? = null,
        anchorMessageId: String? = null,
    ): CodingMessagePageResult {
        val legacy = loadMessages(sessionId)
        return CodingMessagePageResult(
            state = legacy.state,
            items = legacy.items,
            errorCode = legacy.errorCode,
            generating = legacy.generating,
            tailConnected = true,
            capabilityState = RemoteHostCapabilityState.Legacy,
        )
    }

    suspend fun readMessageContent(
        sessionId: String,
        messageId: String,
        contentVersion: String?,
        cursor: String? = null,
    ): CodingMessageContentResult = CodingMessageContentResult(CodingLoadState.Error, errorCode = "operation_not_available")

    suspend fun sendMessage(
        sessionId: String,
        text: String,
        attachments: List<CodingInputAttachment> = emptyList(),
    ): CodingSendResult

    suspend fun stopGeneration(sessionId: String): CodingStopResult
}

class CodingRemoteRepository(
    private val gateway: RemoteCommandGateway?,
    private val eventSource: RemoteEventSource? = gateway as? RemoteEventSource,
    override val mode: CodingRemoteMode = CodingRemoteMode.Coding,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CodingConversationRepository {
    override val remoteEvents: Flow<RemoteEventPayload> = eventSource?.remoteEvents ?: emptyFlow()
    private var capabilityState = RemoteHostCapabilityState.Unknown
    private var capabilityRetryAtMillis = 0L

    suspend fun loadWorkspaces(): CodingListResult<CodingWorkspace> {
        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("workspaces.list"),
                payload = JSONObject(),
            ),
        ) ?: return CodingListResult(CodingLoadState.Unavailable)

        if (event.errorCode != null) {
            return CodingListResult(CodingLoadState.Error, errorCode = event.errorCode)
        }

        val workspaces = event.payload.optJSONArray("workspaces")
        if (event.eventType != event("workspaces.list.result") || workspaces == null) {
            return CodingListResult(CodingLoadState.Error, errorCode = "workspaces_not_loaded")
        }

        return CodingListResult(
            state = CodingLoadState.Loaded,
            items = workspaces.toWorkspaces(),
        )
    }

    suspend fun archiveWorkspace(workspaceId: String): CodingWorkspaceOperationResult {
        if (workspaceId.isBlank()) {
            return CodingWorkspaceOperationResult(CodingWorkspaceOperationState.Error, errorCode = "invalid_input")
        }

        return operateWorkspace(
            commandType = "coding.workspace.archive",
            payload = JSONObject().put("workspaceId", workspaceId),
        )
    }

    suspend fun deleteWorkspace(workspaceId: String, deleteLocalHistory: Boolean): CodingWorkspaceOperationResult {
        if (workspaceId.isBlank()) {
            return CodingWorkspaceOperationResult(CodingWorkspaceOperationState.Error, errorCode = "invalid_input")
        }

        return operateWorkspace(
            commandType = "coding.workspace.delete",
            payload = JSONObject()
                .put("workspaceId", workspaceId)
                .put("deleteLocalHistory", deleteLocalHistory),
        )
    }

    suspend fun archiveSession(sessionId: String): CodingSessionOperationResult {
        if (sessionId.isBlank()) {
            return CodingSessionOperationResult(CodingSessionOperationState.Error, errorCode = "invalid_input")
        }

        return operateSession(
            commandType = command("session.archive"),
            payload = JSONObject().put("sessionId", sessionId),
        )
    }

    suspend fun deleteSession(sessionId: String, deleteLocalHistory: Boolean): CodingSessionOperationResult {
        if (sessionId.isBlank()) {
            return CodingSessionOperationResult(CodingSessionOperationState.Error, errorCode = "invalid_input")
        }

        return operateSession(
            commandType = command("session.delete"),
            payload = JSONObject()
                .put("sessionId", sessionId)
                .put("deleteLocalHistory", deleteLocalHistory),
        )
    }

    suspend fun renameSession(sessionId: String, title: String): CodingSessionOperationResult {
        val submittedTitle = title.trim()
        if (sessionId.isBlank() || submittedTitle.isBlank()) {
            return CodingSessionOperationResult(CodingSessionOperationState.Error, errorCode = "invalid_input")
        }

        return operateSession(
            commandType = command("session.rename"),
            payload = JSONObject()
                .put("sessionId", sessionId)
                .put("title", submittedTitle),
        )
    }

    suspend fun forkSession(
        sessionId: String,
        title: String,
        turnId: String?,
        switchToCreatedSession: Boolean,
    ): CodingSessionThreadOperationResult {
        val submittedTitle = title.trim()
        if (sessionId.isBlank() || submittedTitle.isBlank()) {
            return CodingSessionThreadOperationResult(CodingSessionOperationState.Error, errorCode = "invalid_input")
        }

        val payload = JSONObject()
            .put("sessionId", sessionId)
            .put("mode", if (turnId.isNullOrBlank()) "full" else "fromTurn")
            .put("title", submittedTitle)
            .put("switchToCreatedSession", switchToCreatedSession)
        if (!turnId.isNullOrBlank()) {
            payload.put("turnId", turnId)
        }

        return sendThreadOperation("coding.session.fork", payload)
    }

    suspend fun compactSession(sessionId: String): CodingSessionThreadOperationResult {
        if (sessionId.isBlank()) {
            return CodingSessionThreadOperationResult(CodingSessionOperationState.Error, errorCode = "invalid_input")
        }

        return sendThreadOperation(
            commandType = "coding.session.compact",
            payload = JSONObject().put("sessionId", sessionId),
        )
    }

    suspend fun createSession(workspaceId: String, title: String): CodingSessionCreateResult {
        val submittedTitle = title.trim()
        if (workspaceId.isBlank() || submittedTitle.isBlank()) {
            return CodingSessionCreateResult(CodingSessionCreateState.Error, errorCode = "invalid_input")
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("session.create"),
                payload = JSONObject()
                    .put("workspaceId", workspaceId)
                    .put("title", submittedTitle),
            ),
        ) ?: return CodingSessionCreateResult(CodingSessionCreateState.Unavailable)

        if (event.errorCode != null) {
            return CodingSessionCreateResult(CodingSessionCreateState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != event("session.created")) {
            return CodingSessionCreateResult(CodingSessionCreateState.Error, errorCode = "session_not_created")
        }

        val session = event.payload.optJSONObject("session")
            ?: return CodingSessionCreateResult(CodingSessionCreateState.Error, errorCode = "session_not_created")
        val sessionId = session.optString("id")
        if (sessionId.isBlank()) {
            return CodingSessionCreateResult(CodingSessionCreateState.Error, errorCode = "session_not_created")
        }

        return CodingSessionCreateResult(
            state = CodingSessionCreateState.Created,
            session = session.toSession(sessionId),
        )
    }

    suspend fun loadSessionSettings(sessionId: String): CodingSessionSettingsResult {
        if (sessionId.isBlank()) {
            return CodingSessionSettingsResult(CodingSessionSettingsState.Error, errorCode = "invalid_input")
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("session.settings.get"),
                payload = JSONObject().put("sessionId", sessionId),
            ),
        ) ?: return CodingSessionSettingsResult(CodingSessionSettingsState.Unavailable)

        if (event.errorCode != null) {
            return CodingSessionSettingsResult(CodingSessionSettingsState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != event("session.settings.result")) {
            return CodingSessionSettingsResult(CodingSessionSettingsState.Error, errorCode = "settings_not_loaded")
        }

        return CodingSessionSettingsResult(
            state = CodingSessionSettingsState.Loaded,
            settings = event.payload.toSessionSettings(),
        )
    }

    suspend fun updateSessionSettings(update: CodingSessionSettingsUpdate): CodingSessionSettingsUpdateResult {
        if (update.sessionId.isBlank()) {
            return CodingSessionSettingsUpdateResult(CodingSessionSettingsUpdateState.Error, errorCode = "invalid_input")
        }

        val payload = JSONObject()
            .put("sessionId", update.sessionId)
        if (update.updateModelSettings) {
            payload
                .put("modelId", update.modelId ?: JSONObject.NULL)
                .put("reasoningEffort", update.reasoningEffort ?: JSONObject.NULL)
                .put("approvalMode", update.approvalMode?.toWireValue() ?: JSONObject.NULL)
        }
        if (update.isPlanModeEnabled != null) {
            payload.put("isPlanModeEnabled", update.isPlanModeEnabled)
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("session.settings.update"),
                payload = payload,
            ),
        ) ?: return CodingSessionSettingsUpdateResult(CodingSessionSettingsUpdateState.Unavailable)

        if (event.errorCode != null) {
            return CodingSessionSettingsUpdateResult(CodingSessionSettingsUpdateState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != event("session.settings.update.result") || !event.payload.optBoolean("updated", false)) {
            return CodingSessionSettingsUpdateResult(CodingSessionSettingsUpdateState.Error, errorCode = "settings_not_updated")
        }

        return CodingSessionSettingsUpdateResult(
            state = CodingSessionSettingsUpdateState.Completed,
            sessionId = event.payload.optionalString("sessionId"),
            workspaceId = event.payload.optionalString("workspaceId"),
            updated = event.payload.optBoolean("updated", false),
            statusText = event.payload.optionalString("statusText"),
        )
    }

    suspend fun respondApproval(requestId: String, decision: RemoteApprovalDecision): RemoteApprovalResponseResult {
        if (requestId.isBlank()) {
            return RemoteApprovalResponseResult(RemoteApprovalResponseState.Error, errorCode = "invalid_input")
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = "approval.respond",
                payload = JSONObject()
                    .put("requestId", requestId)
                    .put("decision", decision.wireValue),
            ),
        ) ?: return RemoteApprovalResponseResult(RemoteApprovalResponseState.Unavailable)

        if (event.errorCode != null) {
            return RemoteApprovalResponseResult(RemoteApprovalResponseState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != "approval.response.result" || !event.payload.optBoolean("completed", false)) {
            return RemoteApprovalResponseResult(RemoteApprovalResponseState.Error, errorCode = "approval_not_completed")
        }

        return RemoteApprovalResponseResult(
            state = RemoteApprovalResponseState.Completed,
            completed = true,
            statusText = event.payload.optionalString("statusText"),
        )
    }

    suspend fun respondToolInput(
        requestId: String,
        answers: Map<String, List<String>>,
    ): RemoteToolInputResponseResult {
        if (requestId.isBlank()) {
            return RemoteToolInputResponseResult(RemoteToolInputResponseState.Error, errorCode = "invalid_input")
        }

        val answersJson = JSONObject()
        answers.forEach { (questionId, values) ->
            if (questionId.isNotBlank()) {
                answersJson.put(questionId, JSONArray(values))
            }
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = "tool.input.respond",
                payload = JSONObject().put("requestId", requestId).put("answers", answersJson),
            ),
        ) ?: return RemoteToolInputResponseResult(RemoteToolInputResponseState.Unavailable)

        return event.toRemoteToolInputResponseResult(expectCanceled = false)
    }

    suspend fun cancelToolInput(requestId: String): RemoteToolInputResponseResult {
        if (requestId.isBlank()) {
            return RemoteToolInputResponseResult(RemoteToolInputResponseState.Error, errorCode = "invalid_input")
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = "tool.input.cancel",
                payload = JSONObject().put("requestId", requestId),
            ),
        ) ?: return RemoteToolInputResponseResult(RemoteToolInputResponseState.Unavailable)

        return event.toRemoteToolInputResponseResult(expectCanceled = true)
    }

    suspend fun respondPlanExecution(
        request: RemotePlanExecutionRequest,
        action: RemotePlanExecutionAction,
    ): RemotePlanExecutionResponseResult {
        if (request.requestId.isBlank()) {
            return RemotePlanExecutionResponseResult(RemotePlanExecutionResponseState.Error, errorCode = "invalid_input")
        }

        val payload = JSONObject()
            .put("requestId", request.requestId)
            .put("action", action.wireValue)
        request.threadId?.takeIf { it.isNotBlank() }?.let { payload.put("threadId", it) }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = "plan.execution.respond",
                payload = payload,
            ),
        ) ?: return RemotePlanExecutionResponseResult(RemotePlanExecutionResponseState.Unavailable)

        if (event.errorCode != null) {
            return RemotePlanExecutionResponseResult(RemotePlanExecutionResponseState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != "plan.execution.response.result" || !event.payload.optBoolean("completed", false)) {
            return RemotePlanExecutionResponseResult(RemotePlanExecutionResponseState.Error, errorCode = "plan_execution_not_completed")
        }

        return RemotePlanExecutionResponseResult(
            state = RemotePlanExecutionResponseState.Completed,
            completed = true,
            statusText = event.payload.optionalString("statusText"),
        )
    }

    override suspend fun sendMessage(
        sessionId: String,
        text: String,
        attachments: List<CodingInputAttachment>,
    ): CodingSendResult {
        val submittedText = text.trim()
        val submittedAttachments = attachments.filter { it.path.isNotBlank() && it.type.isNotBlank() }
        if (sessionId.isBlank() || (submittedText.isBlank() && submittedAttachments.isEmpty())) {
            return CodingSendResult(CodingSendState.Error, errorCode = "invalid_input")
        }

        val payload = JSONObject().put("sessionId", sessionId)
        if (submittedText.isNotBlank()) {
            payload.put("text", submittedText)
        }
        if (submittedAttachments.isNotEmpty()) {
            payload.put("inputItems", submittedAttachments.toInputItemsJson())
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                operationId = newRemoteOperationId(),
                commandType = command("message.send"),
                payload = payload,
            ),
        ) ?: return CodingSendResult(CodingSendState.Unavailable)

        if (event.errorCode != null) {
            return CodingSendResult(CodingSendState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != event("message.accepted") || !event.payload.optBoolean("accepted", true)) {
            return CodingSendResult(CodingSendState.Error, errorCode = "send_not_accepted")
        }

        return CodingSendResult(
            state = CodingSendState.Accepted,
            dispatchMode = event.payload.optionalString("dispatchMode"),
        )
    }

    suspend fun loadPluginAttachments(sessionId: String): CodingAttachmentCatalogResult {
        if (sessionId.isBlank()) {
            return CodingAttachmentCatalogResult(CodingAttachmentCatalogState.Error, errorCode = "invalid_input")
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("attachments.plugins.list"),
                payload = JSONObject().put("sessionId", sessionId),
            ),
        ) ?: return CodingAttachmentCatalogResult(CodingAttachmentCatalogState.Unavailable)

        if (event.errorCode != null) {
            return CodingAttachmentCatalogResult(CodingAttachmentCatalogState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != event("attachments.plugins.list.result")) {
            return CodingAttachmentCatalogResult(CodingAttachmentCatalogState.Error, errorCode = "attachments_not_loaded")
        }

        return CodingAttachmentCatalogResult(
            state = CodingAttachmentCatalogState.Loaded,
            items = event.payload.optJSONArray("plugins").toInputAttachments(CodingAttachmentKind.Plugin, "mention"),
        )
    }

    suspend fun loadSkillAttachments(
        sessionId: String,
        forceReload: Boolean = true,
    ): CodingAttachmentCatalogResult {
        if (sessionId.isBlank()) {
            return CodingAttachmentCatalogResult(CodingAttachmentCatalogState.Error, errorCode = "invalid_input")
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("attachments.skills.list"),
                payload = JSONObject()
                    .put("sessionId", sessionId)
                    .put("forceReload", forceReload),
            ),
        ) ?: return CodingAttachmentCatalogResult(CodingAttachmentCatalogState.Unavailable)

        if (event.errorCode != null) {
            return CodingAttachmentCatalogResult(CodingAttachmentCatalogState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != event("attachments.skills.list.result")) {
            return CodingAttachmentCatalogResult(CodingAttachmentCatalogState.Error, errorCode = "attachments_not_loaded")
        }

        return CodingAttachmentCatalogResult(
            state = CodingAttachmentCatalogState.Loaded,
            items = event.payload.optJSONArray("skills").toInputAttachments(CodingAttachmentKind.Skill, "skill"),
        )
    }

    override suspend fun stopGeneration(sessionId: String): CodingStopResult {
        if (sessionId.isBlank()) {
            return CodingStopResult(CodingStopState.Error, errorCode = "invalid_input")
        }

        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("session.stop"),
                payload = JSONObject().put("sessionId", sessionId),
            ),
        ) ?: return CodingStopResult(CodingStopState.Unavailable)

        if (event.errorCode != null) {
            return CodingStopResult(CodingStopState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != event("session.stop.result") || !event.payload.optBoolean("stopped", false)) {
            return CodingStopResult(CodingStopState.Error, errorCode = "stop_not_accepted")
        }

        return CodingStopResult(
            state = CodingStopState.Completed,
            stopped = true,
            statusText = event.payload.optionalString("statusText"),
        )
    }

    suspend fun loadSessions(workspaceId: String): CodingListResult<CodingSession> {
        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("sessions.list"),
                payload = JSONObject().put("workspaceId", workspaceId),
            ),
        ) ?: return CodingListResult(CodingLoadState.Unavailable)

        if (event.errorCode != null) {
            return CodingListResult(CodingLoadState.Error, errorCode = event.errorCode)
        }

        return CodingListResult(
            state = CodingLoadState.Loaded,
            items = event.payload.optJSONArray("sessions").toSessions(),
        )
    }

    override suspend fun loadMessages(sessionId: String): CodingListResult<CodingMessage> {
        val page = loadMessagePage(sessionId)
        return CodingListResult(page.state, page.items, page.errorCode, page.generating)
    }

    override suspend fun loadMessagePage(
        sessionId: String,
        beforeCursor: String?,
        afterCursor: String?,
        anchorMessageId: String?,
    ): CodingMessagePageResult {
        if (sessionId.isBlank()) return CodingMessagePageResult(CodingLoadState.Error, errorCode = "invalid_input")
        val capability = resolveCapability()
        val payload = JSONObject().put("sessionId", sessionId).put("limit", 100)
        if (capability == RemoteHostCapabilityState.Supported) {
            payload.put("pagingVersion", 1)
            beforeCursor?.let { payload.put("beforeCursor", it) }
            afterCursor?.let { payload.put("afterCursor", it) }
            anchorMessageId?.let { payload.put("anchorMessageId", it) }
        }
        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = command("messages.list"),
                payload = payload,
                timeoutMillis = 30_000L,
                keepConnectionOnTimeout = true,
            ),
        ) ?: return CodingMessagePageResult(CodingLoadState.Unavailable, capabilityState = capability)

        if (event.errorCode != null) {
            return CodingMessagePageResult(CodingLoadState.Error, errorCode = event.errorCode, capabilityState = capability)
        }

        return CodingMessagePageResult(
            state = CodingLoadState.Loaded,
            items = event.payload.optJSONArray("messages").toMessages(),
            generating = if (event.payload.has("generating") && !event.payload.isNull("generating")) {
                event.payload.optBoolean("generating")
            } else {
                null
            },
            olderCursor = event.payload.optionalString("olderCursor") ?: event.payload.optionalString("nextCursor"),
            newerCursor = event.payload.optionalString("newerCursor"),
            hasOlder = event.payload.optBoolean("hasOlder", false),
            hasNewer = event.payload.optBoolean("hasNewer", false),
            tailConnected = event.payload.optBoolean("tailConnected", !event.payload.optBoolean("hasNewer", false)),
            latestAssistantMessageId = event.payload.optionalString("latestAssistantMessageId"),
            historyRevision = event.payload.optionalString("historyRevision"),
            anchorFound = !event.payload.has("anchorFound") || event.payload.optBoolean("anchorFound", true),
            resolvedAnchorMessageId = event.payload.optionalString("resolvedAnchorMessageId"),
            capabilityState = capability,
        )
    }

    override suspend fun readMessageContent(sessionId: String, messageId: String, contentVersion: String?, cursor: String?): CodingMessageContentResult {
        if (resolveCapability() != RemoteHostCapabilityState.Supported) return CodingMessageContentResult(CodingLoadState.Error, errorCode = "operation_not_available")
        val event = gateway?.sendCommand(RemoteCommandPayload(
            commandType = command("message.content.read"),
            payload = JSONObject().put("sessionId", sessionId).put("messageId", messageId).put("contentVersion", contentVersion ?: JSONObject.NULL).put("cursor", cursor ?: JSONObject.NULL),
            timeoutMillis = 30_000L,
            keepConnectionOnTimeout = true,
        )) ?: return CodingMessageContentResult(CodingLoadState.Unavailable)
        if (event.errorCode != null) return CodingMessageContentResult(CodingLoadState.Error, errorCode = event.errorCode)
        if (event.eventType != event("message.content.result")) return CodingMessageContentResult(CodingLoadState.Error, errorCode = "content_not_loaded")
        return CodingMessageContentResult(
            state = CodingLoadState.Loaded,
            text = event.payload.optionalString("text").orEmpty(),
            nextCursor = event.payload.optionalString("nextCursor"),
            loadedUtf8Bytes = event.payload.optLong("loadedUtf8Bytes", 0),
            totalUtf8Bytes = event.payload.optLong("totalUtf8Bytes", 0),
            complete = event.payload.optBoolean("complete", false),
        )
    }

    private suspend fun resolveCapability(): RemoteHostCapabilityState {
        if (capabilityState != RemoteHostCapabilityState.Unknown) return capabilityState
        if (nowMillis() < capabilityRetryAtMillis) return capabilityState
        val event = gateway?.sendCommand(RemoteCommandPayload(commandType = "host.capabilities.get", timeoutMillis = 30_000L, keepConnectionOnTimeout = true))
            ?: return RemoteHostCapabilityState.Unknown
        capabilityState = when {
            event.errorCode == "unsupported_command" -> RemoteHostCapabilityState.Legacy
            event.errorCode != null || event.eventType != "host.capabilities.result" -> RemoteHostCapabilityState.Unknown
            event.payload.optJSONArray("capabilities")?.let { values -> (0 until values.length()).any { values.optString(it) == "conversation.messages.pagination.v1" } } == true -> RemoteHostCapabilityState.Supported
            else -> RemoteHostCapabilityState.Unknown
        }
        // A timed-out probe would otherwise delay every page load by the full timeout; retry it later instead.
        if (event.errorCode == "request_timeout") capabilityRetryAtMillis = nowMillis() + CapabilityRetryDelayMillis
        return capabilityState
    }

    private suspend fun operateWorkspace(commandType: String, payload: JSONObject): CodingWorkspaceOperationResult {
        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = commandType,
                payload = payload,
            ),
        ) ?: return CodingWorkspaceOperationResult(CodingWorkspaceOperationState.Unavailable)

        if (event.errorCode != null) {
            return CodingWorkspaceOperationResult(CodingWorkspaceOperationState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != "coding.workspace.operation.result") {
            return CodingWorkspaceOperationResult(CodingWorkspaceOperationState.Error, errorCode = "operation_not_completed")
        }

        return CodingWorkspaceOperationResult(
            state = CodingWorkspaceOperationState.Completed,
            workspaceId = event.payload.optionalString("workspaceId"),
            action = event.payload.optionalString("action"),
            deleteLocalHistory = event.payload.optBoolean("deleteLocalHistory", false),
            processedCount = event.payload.optInt("processedCount", 0),
            removed = event.payload.optBoolean("removed", false),
            statusText = event.payload.optionalString("statusText"),
        )
    }

    private suspend fun operateSession(commandType: String, payload: JSONObject): CodingSessionOperationResult {
        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = commandType,
                payload = payload,
            ),
        ) ?: return CodingSessionOperationResult(CodingSessionOperationState.Unavailable)

        if (event.errorCode != null) {
            return CodingSessionOperationResult(CodingSessionOperationState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != event("session.operation.result")) {
            return CodingSessionOperationResult(CodingSessionOperationState.Error, errorCode = "operation_not_completed")
        }

        return CodingSessionOperationResult(
            state = CodingSessionOperationState.Completed,
            sessionId = event.payload.optionalString("sessionId"),
            workspaceId = event.payload.optionalString("workspaceId"),
            action = event.payload.optionalString("action"),
            deleteLocalHistory = event.payload.optBoolean("deleteLocalHistory", false),
            processedCount = event.payload.optInt("processedCount", 0),
            removed = event.payload.optBoolean("removed", false),
            updated = event.payload.optBoolean("updated", false),
            title = event.payload.optionalString("title"),
            statusText = event.payload.optionalString("statusText"),
        )
    }

    private suspend fun sendThreadOperation(commandType: String, payload: JSONObject): CodingSessionThreadOperationResult {
        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = commandType,
                payload = payload,
            ),
        ) ?: return CodingSessionThreadOperationResult(CodingSessionOperationState.Unavailable)

        if (event.errorCode != null) {
            return CodingSessionThreadOperationResult(CodingSessionOperationState.Error, errorCode = event.errorCode)
        }

        if (event.eventType != "coding.session.thread.operation.result") {
            return CodingSessionThreadOperationResult(CodingSessionOperationState.Error, errorCode = "operation_not_completed")
        }

        return event.payload.toThreadOperationResult()
    }

    private fun RemoteEventPayload.toRemoteToolInputResponseResult(expectCanceled: Boolean): RemoteToolInputResponseResult {
        if (errorCode != null) {
            return RemoteToolInputResponseResult(RemoteToolInputResponseState.Error, errorCode = errorCode)
        }

        if (eventType != "tool.input.response.result") {
            return RemoteToolInputResponseResult(RemoteToolInputResponseState.Error, errorCode = "tool_input_not_completed")
        }

        val completed = payload.optBoolean("completed", false)
        val canceled = payload.optBoolean("canceled", false)
        if (!completed) {
            return RemoteToolInputResponseResult(RemoteToolInputResponseState.Error, errorCode = "tool_input_not_completed")
        }

        return RemoteToolInputResponseResult(
            state = if (expectCanceled || canceled) RemoteToolInputResponseState.Canceled else RemoteToolInputResponseState.Completed,
            completed = completed,
            canceled = canceled,
            statusText = payload.optionalString("statusText"),
        )
    }

    private fun JSONArray?.toWorkspaces(): List<CodingWorkspace> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) {
                    continue
                }

                add(item.toWorkspace(id))
            }
        }
    }

    private fun JSONObject.toWorkspace(id: String = optString("id")): CodingWorkspace {
        return CodingWorkspace(
            id = id,
            name = optString("name").ifBlank { optString("path").ifBlank { id } },
            path = optString("path"),
            lastSessionId = optionalString("lastSessionId"),
        )
    }

    private fun JSONArray?.toSessions(): List<CodingSession> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) {
                    continue
                }

                add(item.toSession(id))
            }
        }
    }

    private fun JSONObject.toSession(id: String = optString("id")): CodingSession {
        return CodingSession(
            id = id,
            workspaceId = optString("workspaceId"),
            title = optString("title").ifBlank { id },
            createdAt = optionalString("createdAt"),
            updatedAt = optionalString("updatedAt"),
            branchKind = optionalString("branchKind").toCodingSessionBranchKind(),
            subAgentCount = optInt("subAgentCount", 0),
            subAgents = optJSONArray("subAgents").toSessions(),
            subAgentName = optionalString("subAgentName"),
            subAgentRole = optionalString("subAgentRole"),
        )
    }

    private fun JSONObject.toSessionSettings(): CodingSessionSettings {
        return CodingSessionSettings(
            sessionId = optString("sessionId"),
            workspaceId = optionalString("workspaceId"),
            modelId = optionalString("modelId"),
            reasoningEffort = optionalString("reasoningEffort"),
            approvalMode = optionalString("approvalMode").toCodingApprovalMode(),
            isPlanModeEnabled = optBoolean("isPlanModeEnabled", false),
            effectiveModelId = optionalString("effectiveModelId"),
            effectiveReasoningEffort = optionalString("effectiveReasoningEffort"),
            effectiveApprovalMode = optionalString("effectiveApprovalMode").toCodingApprovalMode(),
            defaultModelId = optionalString("defaultModelId"),
            defaultReasoningEffort = optionalString("defaultReasoningEffort"),
            defaultApprovalMode = optionalString("defaultApprovalMode").toCodingApprovalMode(),
            availableModels = optJSONArray("availableModels").toModelOptions(),
        )
    }

    private fun JSONArray?.toModelOptions(): List<CodingModelOption> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.optString("modelId")
                if (id.isBlank()) {
                    continue
                }

                add(
                    CodingModelOption(
                        modelId = id,
                        displayName = item.optString("displayName").ifBlank { id },
                        defaultReasoningEffort = item.optionalString("defaultReasoningEffort"),
                        reasoningEfforts = item.optJSONArray("reasoningEfforts").toStringList(),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val value = optString(index)
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private fun List<CodingInputAttachment>.toInputItemsJson(): JSONArray {
        val result = JSONArray()
        forEach { attachment ->
            result.put(
                JSONObject()
                    .put("type", attachment.type)
                    .put("name", attachment.name ?: JSONObject.NULL)
                    .put("displayName", attachment.displayName)
                    .put("path", attachment.path),
            )
        }
        return result
    }

    private fun JSONArray?.toInputAttachments(
        fallbackKind: CodingAttachmentKind,
        fallbackType: String,
    ): List<CodingInputAttachment> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isBlank()) {
                    continue
                }
                val type = item.optString("type").ifBlank { fallbackType }
                val name = item.optionalString("name")
                val displayName = item.optionalString("displayName") ?: name ?: path
                val scope = item.optionalString("scope")
                val kind = when (item.optionalString("kind")?.lowercase()) {
                    "plugin" -> CodingAttachmentKind.Plugin
                    "skill" -> CodingAttachmentKind.Skill
                    else -> fallbackKind
                }
                add(
                    CodingInputAttachment(
                        kind = kind,
                        type = type,
                        name = name,
                        displayName = displayName,
                        path = path,
                        enabled = item.optBoolean("enabled", false),
                        installed = item.optBoolean("installed", true),
                        sourceText = item.optionalString("sourceText") ?: scope,
                        scope = scope,
                        summary = item.optionalString("summary"),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toMessages(): List<CodingMessage> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) {
                    continue
                }

                add(
                    CodingMessage(
                        id = id,
                        role = item.optionalString("role").orEmpty().toMessageRole(),
                        content = item.optionalString("content").orEmpty(),
                        contentTruncated = item.optBoolean("contentTruncated", false),
                        contentUtf8ByteCount = item.optLong("contentUtf8ByteCount", item.optionalString("content").orEmpty().toByteArray(Charsets.UTF_8).size.toLong()),
                        contentVersion = item.optionalString("contentVersion"),
                        createdAt = item.optionalString("createdAt"),
                        threadId = item.optionalString("threadId"),
                        turnId = item.optionalString("turnId"),
                        turnIndex = item.optInt("turnIndex", 0),
                        turnCount = item.optInt("turnCount", 0),
                        turnPreview = item.optionalString("turnPreview"),
                        actions = item.optJSONObject("actions").toMessageActions(item.optionalString("content").orEmpty()),
                        operationPreview = item.optJSONObject("operationPreview").toMessageOperationPreview(),
                    ),
                )
            }
        }
    }

    private fun JSONObject.toThreadOperationResult(): CodingSessionThreadOperationResult {
        val createdSessionObject = optJSONObject("createdSession")
        return CodingSessionThreadOperationResult(
            state = CodingSessionOperationState.Completed,
            sessionId = optionalString("sessionId"),
            workspaceId = optionalString("workspaceId"),
            action = optionalString("action"),
            mode = optionalString("mode"),
            turnId = optionalString("turnId"),
            accepted = optBoolean("accepted", false),
            createdSession = createdSessionObject?.toSession(),
            switchToCreatedSession = optBoolean("switchToCreatedSession", false),
            statusText = optionalString("statusText"),
        )
    }

    private fun JSONObject?.toMessageActions(content: String): CodingMessageActions {
        return CodingMessageActions(
            copy = this?.optBoolean("copy", content.isNotBlank()) ?: content.isNotBlank(),
            forkFromTurn = this?.optBoolean("forkFromTurn", false) ?: false,
        )
    }

    private fun JSONObject?.toMessageOperationPreview(): CodingMessageOperationPreview {
        return CodingMessageOperationPreview(
            trimTurnsAfterTurn = this?.optInt("trimTurnsAfterTurn", 0) ?: 0,
        )
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return optString(key).takeUnless {
            it.isBlank() || it.equals("null", ignoreCase = true)
        }
    }

    private fun String.toMessageRole(): CodingMessageRole {
        return when (lowercase()) {
            "user" -> CodingMessageRole.User
            "assistant" -> CodingMessageRole.Assistant
            "system" -> CodingMessageRole.System
            "tool" -> CodingMessageRole.Tool
            else -> CodingMessageRole.Unknown
        }
    }

    private fun newRemoteOperationId(): String {
        return "android-operation-${UUID.randomUUID().toString().replace("-", "")}"
    }

    private fun command(name: String): String = mode.command(name)

    private fun event(name: String): String = mode.event(name)
}

private const val CapabilityRetryDelayMillis = 60_000L
