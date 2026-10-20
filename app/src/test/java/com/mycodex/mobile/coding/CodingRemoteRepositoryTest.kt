package com.mycodex.mobile.coding

import com.mycodex.mobile.relay.RemoteCommandGateway
import com.mycodex.mobile.relay.RemoteCommandPayload
import com.mycodex.mobile.relay.RemoteEventPayload
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodingRemoteRepositoryTest {
    @Test
    fun workspaceVerificationRejectsWrongResponseFamilyAndMissingOrMalformedArrayInBothModes() = runTest {
        for (mode in CodingRemoteMode.entries) {
            val otherMode = CodingRemoteMode.entries.first { it != mode }
            val invalidResponses = listOf(
                RemoteEventPayload("read", null, "system.ready", JSONObject().put("workspaces", JSONArray())),
                RemoteEventPayload("read", null, otherMode.event("workspaces.list.result"), JSONObject().put("workspaces", JSONArray())),
                RemoteEventPayload("read", null, mode.event("workspaces.list.result"), JSONObject()),
                RemoteEventPayload("read", null, mode.event("workspaces.list.result"), JSONObject().put("workspaces", JSONObject.NULL)),
                RemoteEventPayload("read", null, mode.event("workspaces.list.result"), JSONObject().put("workspaces", JSONObject())),
            )
            for (response in invalidResponses) {
                val repository = CodingRemoteRepository(FakeRemoteCommandGateway(response), mode = mode)
                val result = repository.loadWorkspaces()
                assertEquals(CodingLoadState.Error, result.state)
                assertEquals("workspaces_not_loaded", result.errorCode)
                assertTrue(result.items.isEmpty())
            }
        }
    }

    @Test
    fun workspaceVerificationAcceptsCorrectlyShapedEmptyResultInBothModes() = runTest {
        for (mode in CodingRemoteMode.entries) {
            val response = RemoteEventPayload("read", null, mode.event("workspaces.list.result"), JSONObject().put("workspaces", JSONArray()))
            val result = CodingRemoteRepository(FakeRemoteCommandGateway(response), mode = mode).loadWorkspaces()
            assertEquals(CodingLoadState.Loaded, result.state)
            assertTrue(result.items.isEmpty())
        }
    }

    @Test
    fun loadWorkspaces_sendsWorkspaceListCommandAndMapsResult() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_a",
                operationId = null,
                eventType = "coding.workspaces.list.result",
                payload = JSONObject()
                    .put(
                        "workspaces",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "ws_a")
                                    .put("name", "Main")
                                    .put("path", "E:/MyCodex")
                                    .put("lastSessionId", "session_a"),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway).loadWorkspaces()

        assertEquals("coding.workspaces.list", gateway.lastCommand!!.commandType)
        assertEquals("ws_a", result.items.single().id)
        assertEquals("Main", result.items.single().name)
        assertEquals("E:/MyCodex", result.items.single().path)
        assertEquals("session_a", result.items.single().lastSessionId)
    }

    @Test
    fun officeMode_loadWorkspacesUsesOfficeCommandPrefix() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_office_workspaces",
                operationId = null,
                eventType = "office.workspaces.list.result",
                payload = JSONObject()
                    .put(
                        "workspaces",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "office:reports")
                                    .put("name", "Reports")
                                    .put("path", "E:/Documents/MyCodex-office/Reports"),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway, mode = CodingRemoteMode.Office).loadWorkspaces()

        assertEquals(CodingLoadState.Loaded, result.state)
        assertEquals("office.workspaces.list", gateway.lastCommand!!.commandType)
        assertEquals("office:reports", result.items.single().id)
    }

    @Test
    fun officeMode_loadSessionsUsesOfficeCommandPrefix() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_office_sessions",
                operationId = null,
                eventType = "office.sessions.list.result",
                payload = JSONObject()
                    .put(
                        "sessions",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "office_session")
                                    .put("workspaceId", "office:reports")
                                    .put("title", "周报"),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway, mode = CodingRemoteMode.Office).loadSessions("office:reports")

        assertEquals(CodingLoadState.Loaded, result.state)
        assertEquals("office.sessions.list", gateway.lastCommand!!.commandType)
        assertEquals("office:reports", gateway.lastCommand!!.payload.getString("workspaceId"))
        assertEquals("office_session", result.items.single().id)
    }

    @Test
    fun officeMode_sendMessageUsesOfficeCommandAndAcceptedEvent() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_office_send",
                operationId = "operation_office_send",
                eventType = "office.message.accepted",
                payload = JSONObject().put("accepted", true),
            ),
        )

        val result = CodingRemoteRepository(gateway, mode = CodingRemoteMode.Office).sendMessage("office_session", "  整理周报  ")

        assertEquals(CodingSendState.Accepted, result.state)
        assertEquals("office.message.send", gateway.lastCommand!!.commandType)
        assertEquals("office_session", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals("整理周报", gateway.lastCommand!!.payload.getString("text"))
    }

    @Test
    fun loadSessions_sendsWorkspaceIdAndMapsResult() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_a",
                operationId = null,
                eventType = "coding.sessions.list.result",
                payload = JSONObject()
                    .put(
                        "sessions",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "session_a")
                                    .put("workspaceId", "ws_a")
                                    .put("title", "Fix build")
                                    .put("createdAt", "2026-06-07T08:00:00Z")
                                    .put("updatedAt", "2026-06-07T09:00:00Z")
                                    .put("branchKind", "mainline")
                                    .put("subAgentCount", 2)
                                    .put(
                                        "subAgents",
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("id", "subagent_a")
                                                    .put("workspaceId", "ws_a")
                                                    .put("title", "Review worker")
                                                    .put("subAgentName", "Ampere")
                                                    .put("subAgentRole", "reviewer")
                                                    .put("createdAt", "2026-06-07T08:30:00Z")
                                                    .put("updatedAt", "2026-06-07T09:30:00Z")
                                                    .put("branchKind", "mainline")
                                                    .put("subAgentCount", 0),
                                            ),
                                    ),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway).loadSessions("ws_a")

        assertEquals("coding.sessions.list", gateway.lastCommand!!.commandType)
        assertEquals("ws_a", gateway.lastCommand!!.payload.getString("workspaceId"))
        assertEquals("session_a", result.items.single().id)
        assertEquals("Fix build", result.items.single().title)
        assertEquals("2026-06-07T08:00:00Z", result.items.single().createdAt)
        assertEquals("2026-06-07T09:00:00Z", result.items.single().updatedAt)
        assertEquals(CodingSessionBranchKind.Mainline, result.items.single().branchKind)
        assertEquals(2, result.items.single().subAgentCount)
        assertEquals("subagent_a", result.items.single().subAgents.single().id)
        assertEquals("Review worker", result.items.single().subAgents.single().title)
        assertEquals("Ampere", result.items.single().subAgents.single().subAgentName)
        assertEquals("reviewer", result.items.single().subAgents.single().subAgentRole)
    }

    @Test
    fun createSession_sendsWorkspaceIdAndMapsCreatedSession() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_create_session",
                operationId = null,
                eventType = "coding.session.created",
                payload = JSONObject()
                    .put("workspaceId", "ws_a")
                    .put(
                        "session",
                        JSONObject()
                            .put("id", "session_new")
                            .put("workspaceId", "ws_a")
                            .put("title", "Repo A")
                            .put("createdAt", "2026-06-08T06:00:00Z")
                            .put("updatedAt", "2026-06-08T06:00:00Z")
                            .put("branchKind", "mainline"),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway).createSession("ws_a", "Custom title")

        assertEquals(CodingSessionCreateState.Created, result.state)
        assertEquals("coding.session.create", gateway.lastCommand!!.commandType)
        assertEquals("ws_a", gateway.lastCommand!!.payload.getString("workspaceId"))
        assertEquals("Custom title", gateway.lastCommand!!.payload.getString("title"))
        assertEquals("session_new", result.session!!.id)
        assertEquals("Repo A", result.session.title)
    }

    @Test
    fun loadMessages_sendsSessionIdAndMapsAssistantContent() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_a",
                operationId = null,
                eventType = "coding.messages.list.result",
                payload = JSONObject()
                    .put("generating", true)
                    .put(
                        "messages",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "message_a")
                                    .put("role", "assistant")
                                    .put("content", "Done")
                                    .put("createdAt", "2026-06-07T09:01:00Z"),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway).loadMessages("session_a")

        assertEquals("coding.messages.list", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals("message_a", result.items.single().id)
        assertEquals(CodingMessageRole.Assistant, result.items.single().role)
        assertEquals("Done", result.items.single().content)
        assertEquals(true, result.generating)
    }

    @Test
    fun loadMessages_treatsJsonNullTimestampAsMissing() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_a",
                operationId = null,
                eventType = "coding.messages.list.result",
                payload = JSONObject()
                    .put(
                        "messages",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "message_a")
                                    .put("role", "assistant")
                                    .put("content", "Done")
                                    .put("createdAt", JSONObject.NULL),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway).loadMessages("session_a")

        assertNull(result.items.single().createdAt)
    }

    @Test
    fun loadMessagesParsesTurnMetadataAndActions() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_a",
                operationId = null,
                eventType = "coding.messages.list.result",
                payload = JSONObject()
                    .put("sessionId", "session-1")
                    .put(
                        "messages",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "message-1")
                                    .put("sessionId", "session-1")
                                    .put("role", "assistant")
                                    .put("content", "assistant reply")
                                    .put("turnId", "turn-2")
                                    .put("turnIndex", 2)
                                    .put("turnCount", 4)
                                    .put(
                                        "actions",
                                        JSONObject()
                                            .put("copy", true)
                                            .put("forkFromTurn", true),
                                    )
                                    .put(
                                        "operationPreview",
                                        JSONObject()
                                            .put("trimTurnsAfterTurn", 2),
                                    ),
                            ),
                    ),
            ),
        )
        val repository = CodingRemoteRepository(gateway)

        val result = repository.loadMessages("session-1")

        val message = result.items.single()
        assertEquals("turn-2", message.turnId)
        assertEquals(2, message.turnIndex)
        assertEquals(4, message.turnCount)
        assertTrue(message.actions.copy)
        assertTrue(message.actions.forkFromTurn)
        assertEquals(2, message.operationPreview.trimTurnsAfterTurn)
    }

    @Test
    fun forkSessionSendsFromTurnPayload() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_fork",
                operationId = null,
                eventType = "coding.session.thread.operation.result",
                payload = JSONObject()
                    .put("sessionId", "session-1")
                    .put("accepted", true),
            ),
        )
        val repository = CodingRemoteRepository(gateway)

        repository.forkSession(
            sessionId = "session-1",
            title = "Fork title",
            turnId = "turn-2",
            switchToCreatedSession = true,
        )

        val command = gateway.lastCommand!!
        assertEquals("coding.session.fork", command.commandType)
        assertEquals("fromTurn", command.payload.getString("mode"))
        assertEquals("turn-2", command.payload.getString("turnId"))
        assertEquals("Fork title", command.payload.getString("title"))
        assertEquals(true, command.payload.getBoolean("switchToCreatedSession"))
    }

    @Test
    fun sendMessage_sendsSessionTextAndOperationId() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_send",
                operationId = "operation_send",
                eventType = "coding.message.accepted",
                payload = JSONObject()
                    .put("sessionId", "session_a")
                    .put("accepted", true)
                    .put("dispatchMode", "queue"),
            ),
        )

        val result = CodingRemoteRepository(gateway).sendMessage("session_a", "  继续推进  ")

        assertEquals(CodingSendState.Accepted, result.state)
        assertEquals("queue", result.dispatchMode)
        assertEquals("coding.message.send", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals("继续推进", gateway.lastCommand!!.payload.getString("text"))
        assert(gateway.lastCommand!!.operationId!!.startsWith("android-operation-"))
    }

    @Test
    fun sendMessage_sendsStructuredInputItemsWhenAttachmentsProvided() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_send",
                operationId = "operation_send",
                eventType = "coding.message.accepted",
                payload = JSONObject().put("accepted", true),
            ),
        )
        val attachments = listOf(
            CodingInputAttachment(
                kind = CodingAttachmentKind.Plugin,
                type = "mention",
                name = "mycodex-office",
                displayName = "MyCodex Office",
                path = "E:/Plugins/mycodex-office",
                enabled = true,
                sourceText = "personal",
            ),
            CodingInputAttachment(
                kind = CodingAttachmentKind.Skill,
                type = "skill",
                name = "writing-plans",
                displayName = "Writing Plans",
                path = "E:/Skills/writing-plans",
                enabled = true,
                sourceText = "plugin",
            ),
        )

        val result = CodingRemoteRepository(gateway).sendMessage("session_a", "  继续推进  ", attachments)

        assertEquals(CodingSendState.Accepted, result.state)
        val command = gateway.lastCommand!!
        assertEquals("coding.message.send", command.commandType)
        assertEquals("继续推进", command.payload.getString("text"))
        val inputItems = command.payload.getJSONArray("inputItems")
        assertEquals(2, inputItems.length())
        assertEquals("mention", inputItems.getJSONObject(0).getString("type"))
        assertEquals("mycodex-office", inputItems.getJSONObject(0).getString("name"))
        assertEquals("MyCodex Office", inputItems.getJSONObject(0).getString("displayName"))
        assertEquals("E:/Plugins/mycodex-office", inputItems.getJSONObject(0).getString("path"))
        assertEquals("skill", inputItems.getJSONObject(1).getString("type"))
        assertEquals("writing-plans", inputItems.getJSONObject(1).getString("name"))
    }

    @Test
    fun sendMessage_allowsAttachmentOnlyPayload() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_send",
                operationId = "operation_send",
                eventType = "coding.message.accepted",
                payload = JSONObject().put("accepted", true),
            ),
        )
        val attachments = listOf(
            CodingInputAttachment(
                kind = CodingAttachmentKind.Skill,
                type = "skill",
                name = "writing-plans",
                displayName = "Writing Plans",
                path = "E:/Skills/writing-plans",
            ),
        )

        val result = CodingRemoteRepository(gateway).sendMessage("session_a", "   ", attachments)

        assertEquals(CodingSendState.Accepted, result.state)
        val command = gateway.lastCommand!!
        assertEquals(false, command.payload.has("text"))
        assertEquals("skill", command.payload.getJSONArray("inputItems").getJSONObject(0).getString("type"))
    }

    @Test
    fun loadPluginAttachments_sendsCatalogCommandAndMapsItems() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_plugins",
                operationId = null,
                eventType = "coding.attachments.plugins.list.result",
                payload = JSONObject()
                    .put(
                        "plugins",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("kind", "plugin")
                                    .put("type", "mention")
                                    .put("name", "mycodex-office")
                                    .put("displayName", "MyCodex Office")
                                    .put("path", "E:/Plugins/mycodex-office")
                                    .put("enabled", true)
                                    .put("installed", true)
                                    .put("sourceText", "personal")
                                    .put("summary", "Office tools"),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway).loadPluginAttachments("session_a")

        assertEquals(CodingAttachmentCatalogState.Loaded, result.state)
        assertEquals("coding.attachments.plugins.list", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        val item = result.items.single()
        assertEquals(CodingAttachmentKind.Plugin, item.kind)
        assertEquals("mention", item.type)
        assertEquals("mycodex-office", item.name)
        assertEquals("MyCodex Office", item.displayName)
        assertEquals("E:/Plugins/mycodex-office", item.path)
        assertEquals(true, item.enabled)
        assertEquals("personal", item.sourceText)
        assertEquals("Office tools", item.summary)
    }

    @Test
    fun loadSkillAttachments_sendsCatalogCommandAndMapsItems() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_skills",
                operationId = null,
                eventType = "coding.attachments.skills.list.result",
                payload = JSONObject()
                    .put("workspaceId", "workspace_a")
                    .put(
                        "skills",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("kind", "skill")
                                    .put("type", "skill")
                                    .put("name", "writing-plans")
                                    .put("displayName", "Writing Plans")
                                    .put("path", "E:/Skills/writing-plans")
                                    .put("enabled", true)
                                    .put("scope", "plugin")
                                    .put("sourceText", "plugin")
                                    .put("summary", "Plan work"),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway).loadSkillAttachments("session_a")

        assertEquals(CodingAttachmentCatalogState.Loaded, result.state)
        assertEquals("coding.attachments.skills.list", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals(true, gateway.lastCommand!!.payload.getBoolean("forceReload"))
        val item = result.items.single()
        assertEquals(CodingAttachmentKind.Skill, item.kind)
        assertEquals("skill", item.type)
        assertEquals("writing-plans", item.name)
        assertEquals("Writing Plans", item.displayName)
        assertEquals("E:/Skills/writing-plans", item.path)
        assertEquals("plugin", item.sourceText)
        assertEquals("plugin", item.scope)
    }

    @Test
    fun sendMessage_mapsRemoteError() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_send",
                operationId = "operation_send",
                eventType = "remote.error",
                payload = JSONObject().put("code", "write_lock_denied"),
                errorCode = "write_lock_denied",
            ),
        )

        val result = CodingRemoteRepository(gateway).sendMessage("session_a", "继续")

        assertEquals(CodingSendState.Error, result.state)
        assertEquals("write_lock_denied", result.errorCode)
    }

    @Test
    fun messageRoleDisplayNamesMatchWindowsClientLabels() {
        assertEquals("我", CodingMessageRole.User.displayName())
        assertEquals("codex", CodingMessageRole.Assistant.displayName())
    }

    @Test
    fun archiveWorkspace_sendsWorkspaceArchiveCommandAndMapsResult() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_archive",
                operationId = null,
                eventType = "coding.workspace.operation.result",
                payload = JSONObject()
                    .put("workspaceId", "ws_a")
                    .put("action", "archive")
                    .put("processedCount", 2)
                    .put("removed", true)
                    .put("statusText", "Archived 2 sessions."),
            ),
        )

        val result = CodingRemoteRepository(gateway).archiveWorkspace("ws_a")

        assertEquals(CodingWorkspaceOperationState.Completed, result.state)
        assertEquals("coding.workspace.archive", gateway.lastCommand!!.commandType)
        assertEquals("ws_a", gateway.lastCommand!!.payload.getString("workspaceId"))
        assertEquals("archive", result.action)
        assertEquals(2, result.processedCount)
        assertEquals("Archived 2 sessions.", result.statusText)
    }

    @Test
    fun deleteWorkspace_sendsDeleteLocalHistoryFlagAndMapsResult() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_delete",
                operationId = null,
                eventType = "coding.workspace.operation.result",
                payload = JSONObject()
                    .put("workspaceId", "ws_a")
                    .put("action", "delete")
                    .put("deleteLocalHistory", true)
                    .put("processedCount", 3)
                    .put("removed", true),
            ),
        )

        val result = CodingRemoteRepository(gateway).deleteWorkspace("ws_a", deleteLocalHistory = true)

        assertEquals(CodingWorkspaceOperationState.Completed, result.state)
        assertEquals("coding.workspace.delete", gateway.lastCommand!!.commandType)
        assertEquals("ws_a", gateway.lastCommand!!.payload.getString("workspaceId"))
        assertEquals(true, gateway.lastCommand!!.payload.getBoolean("deleteLocalHistory"))
        assertEquals("delete", result.action)
        assertEquals(true, result.deleteLocalHistory)
        assertEquals(3, result.processedCount)
    }

    @Test
    fun archiveSession_sendsSessionArchiveCommandAndMapsResult() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_archive_session",
                operationId = null,
                eventType = "coding.session.operation.result",
                payload = JSONObject()
                    .put("sessionId", "session_a")
                    .put("workspaceId", "ws_a")
                    .put("action", "archive")
                    .put("processedCount", 1)
                    .put("removed", true)
                    .put("statusText", "Archived session."),
            ),
        )

        val result = CodingRemoteRepository(gateway).archiveSession("session_a")

        assertEquals(CodingSessionOperationState.Completed, result.state)
        assertEquals("coding.session.archive", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals("session_a", result.sessionId)
        assertEquals("ws_a", result.workspaceId)
        assertEquals("archive", result.action)
        assertEquals(1, result.processedCount)
        assertEquals("Archived session.", result.statusText)
    }

    @Test
    fun deleteSession_sendsDeleteLocalHistoryFlagAndMapsResult() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_delete_session",
                operationId = null,
                eventType = "coding.session.operation.result",
                payload = JSONObject()
                    .put("sessionId", "session_a")
                    .put("workspaceId", "ws_a")
                    .put("action", "delete")
                    .put("deleteLocalHistory", true)
                    .put("processedCount", 1)
                    .put("removed", true),
            ),
        )

        val result = CodingRemoteRepository(gateway).deleteSession("session_a", deleteLocalHistory = true)

        assertEquals(CodingSessionOperationState.Completed, result.state)
        assertEquals("coding.session.delete", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals(true, gateway.lastCommand!!.payload.getBoolean("deleteLocalHistory"))
        assertEquals("delete", result.action)
        assertEquals(true, result.deleteLocalHistory)
        assertEquals(1, result.processedCount)
    }

    @Test
    fun renameSession_sendsTrimmedTitleAndMapsResult() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_rename_session",
                operationId = null,
                eventType = "coding.session.operation.result",
                payload = JSONObject()
                    .put("sessionId", "session_a")
                    .put("workspaceId", "ws_a")
                    .put("action", "rename")
                    .put("title", "Updated title")
                    .put("updated", true)
                    .put("processedCount", 1)
                    .put("statusText", "Renamed session."),
            ),
        )

        val result = CodingRemoteRepository(gateway).renameSession("session_a", "  Updated title  ")

        assertEquals(CodingSessionOperationState.Completed, result.state)
        assertEquals("coding.session.rename", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals("Updated title", gateway.lastCommand!!.payload.getString("title"))
        assertEquals("rename", result.action)
        assertEquals("Updated title", result.title)
        assertEquals(true, result.updated)
        assertEquals(1, result.processedCount)
        assertEquals("Renamed session.", result.statusText)
    }

    @Test
    fun workspaceOperation_mapsRemoteError() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_delete",
                operationId = null,
                eventType = "remote.error",
                payload = JSONObject().put("code", "workspace_not_found"),
                errorCode = "workspace_not_found",
            ),
        )

        val result = CodingRemoteRepository(gateway).deleteWorkspace("ws_a", deleteLocalHistory = false)

        assertEquals(CodingWorkspaceOperationState.Error, result.state)
        assertEquals("workspace_not_found", result.errorCode)
    }

    @Test
    fun loadSessionSettings_sendsSessionIdAndMapsModelsDefaultsAndOverrides() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_settings",
                operationId = null,
                eventType = "coding.session.settings.result",
                payload = JSONObject()
                    .put("sessionId", "session_a")
                    .put("workspaceId", "ws_a")
                    .put("modelId", "gpt-custom")
                    .put("reasoningEffort", "high")
                    .put("approvalMode", "auto")
                    .put("isPlanModeEnabled", true)
                    .put("effectiveModelId", "gpt-custom")
                    .put("effectiveReasoningEffort", "high")
                    .put("effectiveApprovalMode", "auto")
                    .put("defaultModelId", "gpt-default")
                    .put("defaultReasoningEffort", "medium")
                    .put("defaultApprovalMode", "read-only")
                    .put(
                        "availableModels",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("modelId", "gpt-custom")
                                    .put("displayName", "GPT Custom")
                                    .put("defaultReasoningEffort", "medium")
                                    .put("reasoningEfforts", JSONArray().put("low").put("medium").put("high")),
                            ),
                    ),
            ),
        )

        val result = CodingRemoteRepository(gateway).loadSessionSettings("session_a")

        assertEquals(CodingSessionSettingsState.Loaded, result.state)
        assertEquals("coding.session.settings.get", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals("session_a", result.settings!!.sessionId)
        assertEquals("gpt-custom", result.settings.modelId)
        assertEquals("high", result.settings.reasoningEffort)
        assertEquals(CodingApprovalMode.Auto, result.settings.approvalMode)
        assertEquals("gpt-default", result.settings.defaultModelId)
        assertEquals(CodingApprovalMode.ReadOnly, result.settings.defaultApprovalMode)
        assertEquals(true, result.settings.isPlanModeEnabled)
        assertEquals("gpt-custom", result.settings.availableModels.single().modelId)
        assertEquals(listOf("low", "medium", "high"), result.settings.availableModels.single().reasoningEfforts)
    }

    @Test
    fun updateSessionSettings_sendsNullableOverridesAndMapsResult() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_settings_update",
                operationId = null,
                eventType = "coding.session.settings.update.result",
                payload = JSONObject()
                    .put("sessionId", "session_a")
                    .put("workspaceId", "ws_a")
                    .put("updated", true)
                    .put("statusText", "Session settings updated."),
            ),
        )

        val result = CodingRemoteRepository(gateway).updateSessionSettings(
            CodingSessionSettingsUpdate(
                sessionId = "session_a",
                modelId = null,
                reasoningEffort = null,
                approvalMode = CodingApprovalMode.FullAccess,
            ),
        )

        assertEquals(CodingSessionSettingsUpdateState.Completed, result.state)
        assertEquals("coding.session.settings.update", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals(true, gateway.lastCommand!!.payload.isNull("modelId"))
        assertEquals(true, gateway.lastCommand!!.payload.isNull("reasoningEffort"))
        assertEquals("full-access", gateway.lastCommand!!.payload.getString("approvalMode"))
        assertEquals("Session settings updated.", result.statusText)
    }

    @Test
    fun updateSessionSettings_planModeOnlyOmitsModelSettings() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_plan_mode_update",
                operationId = null,
                eventType = "coding.session.settings.update.result",
                payload = JSONObject()
                    .put("sessionId", "session_a")
                    .put("workspaceId", "ws_a")
                    .put("updated", true)
                    .put("isPlanModeEnabled", true)
                    .put("statusText", "Session settings updated."),
            ),
        )

        val result = CodingRemoteRepository(gateway).updateSessionSettings(
            CodingSessionSettingsUpdate(
                sessionId = "session_a",
                updateModelSettings = false,
                isPlanModeEnabled = true,
            ),
        )

        assertEquals(CodingSessionSettingsUpdateState.Completed, result.state)
        assertEquals("coding.session.settings.update", gateway.lastCommand!!.commandType)
        assertEquals("session_a", gateway.lastCommand!!.payload.getString("sessionId"))
        assertEquals(false, gateway.lastCommand!!.payload.has("modelId"))
        assertEquals(false, gateway.lastCommand!!.payload.has("reasoningEffort"))
        assertEquals(false, gateway.lastCommand!!.payload.has("approvalMode"))
        assertEquals(true, gateway.lastCommand!!.payload.getBoolean("isPlanModeEnabled"))
        assertEquals("Session settings updated.", result.statusText)
    }

    @Test
    fun respondApproval_sendsDecisionAndMapsCompletion() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "request_approval",
                operationId = null,
                eventType = "approval.response.result",
                payload = JSONObject()
                    .put("requestId", "approval-1")
                    .put("decision", "allow_once")
                    .put("completed", true)
                    .put("statusText", "Approval response submitted."),
            ),
        )

        val result = CodingRemoteRepository(gateway).respondApproval("approval-1", RemoteApprovalDecision.AllowOnce)

        assertEquals(RemoteApprovalResponseState.Completed, result.state)
        assertEquals("approval.respond", gateway.lastCommand!!.commandType)
        assertEquals("approval-1", gateway.lastCommand!!.payload.getString("requestId"))
        assertEquals("allow_once", gateway.lastCommand!!.payload.getString("decision"))
        assertEquals("Approval response submitted.", result.statusText)
    }

    @Test
    fun respondToolInput_sendsAnswersAndMapsCompletion() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "cmd-1",
                operationId = null,
                eventType = "tool.input.response.result",
                payload = JSONObject()
                    .put("requestId", "input-1")
                    .put("completed", true)
                    .put("canceled", false)
                    .put("statusText", "ok"),
            ),
        )

        val result = CodingRemoteRepository(gateway).respondToolInput(
            requestId = "input-1",
            answers = mapOf("choice" to listOf("continue")),
        )

        assertEquals(RemoteToolInputResponseState.Completed, result.state)
        assertEquals("tool.input.respond", gateway.lastCommand?.commandType)
        assertEquals("continue", gateway.lastCommand?.payload?.getJSONObject("answers")?.getJSONArray("choice")?.getString(0))
    }

    @Test
    fun cancelToolInput_sendsCancelAndMapsCompletion() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "cmd-1",
                operationId = null,
                eventType = "tool.input.response.result",
                payload = JSONObject()
                    .put("requestId", "input-1")
                    .put("completed", true)
                    .put("canceled", true),
            ),
        )

        val result = CodingRemoteRepository(gateway).cancelToolInput("input-1")

        assertEquals(RemoteToolInputResponseState.Canceled, result.state)
        assertEquals("tool.input.cancel", gateway.lastCommand?.commandType)
    }

    @Test
    fun respondPlanExecution_sendsActionAndMapsCompletion() = runTest {
        val gateway = FakeRemoteCommandGateway(
            RemoteEventPayload(
                requestId = "cmd-1",
                operationId = null,
                eventType = "plan.execution.response.result",
                payload = JSONObject()
                    .put("requestId", "plan-1")
                    .put("threadId", "thread-1")
                    .put("action", "execute")
                    .put("completed", true)
                    .put("statusText", "ok"),
            ),
        )
        val request = RemotePlanExecutionRequest(
            requestId = "plan-1",
            threadId = "thread-1",
            title = "计划已生成",
            questionText = "是否执行计划？",
            planText = "# Plan",
            actions = emptyList(),
        )

        val result = CodingRemoteRepository(gateway).respondPlanExecution(request, RemotePlanExecutionAction.Execute)

        assertEquals(RemotePlanExecutionResponseState.Completed, result.state)
        assertEquals("plan.execution.respond", gateway.lastCommand?.commandType)
        assertEquals("plan-1", gateway.lastCommand?.payload?.getString("requestId"))
        assertEquals("thread-1", gateway.lastCommand?.payload?.getString("threadId"))
        assertEquals("execute", gateway.lastCommand?.payload?.getString("action"))
    }

    @Test
    fun timeoutOrMissingDeviceReturnsEmptyStateWithoutCommand() = runTest {
        val gateway = FakeRemoteCommandGateway(null)

        val result = CodingRemoteRepository(gateway).loadWorkspaces()

        assertEquals(CodingLoadState.Unavailable, result.state)
        assertNull(gateway.lastCommand)
    }

    private class FakeRemoteCommandGateway(
        private val response: RemoteEventPayload?,
    ) : RemoteCommandGateway {
        var lastCommand: RemoteCommandPayload? = null

        override suspend fun sendCommand(command: RemoteCommandPayload): RemoteEventPayload? {
            if (response == null) {
                return null
            }

            lastCommand = command.copy(requestId = response.requestId ?: command.requestId)
            return response
        }
    }
}
