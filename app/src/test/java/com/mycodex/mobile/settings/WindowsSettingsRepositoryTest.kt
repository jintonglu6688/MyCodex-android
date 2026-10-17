package com.mycodex.mobile.settings

import com.mycodex.mobile.relay.RemoteCommandGateway
import com.mycodex.mobile.relay.RemoteCommandPayload
import com.mycodex.mobile.relay.RemoteEventPayload
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsSettingsRepositoryTest {
    @Test
    fun load_acceptsCurrentHostSnapshotWithoutLegacyFields() = runTest {
        val snapshot = currentSnapshot(includeOfficial = false)
        val gateway = FakeGateway(
            RemoteEventPayload(
                requestId = "load",
                operationId = null,
                eventType = "host.settings.result",
                payload = snapshot,
            ),
        )

        val result = WindowsSettingsRepository(gateway).load()

        assertEquals(WindowsSettingsLoadState.Loaded, result.state)
        assertEquals("PC", result.settings!!.scope.hostName)
        assertEquals("gpt-test", result.settings!!.defaults.modelId)
        assertEquals(listOf("git status"), result.settings!!.customApproval.commandPrefixes)
        assertNull(result.settings!!.scope.authenticationSourceKind)
        assertNull(result.settings!!.scope.authenticationDisplayName)
        assertNull(result.settings!!.official)
    }

    @Test
    fun load_acceptsRestoredAdvancedSettingsWithoutAuthenticationMetadata() = runTest {
        val result = WindowsSettingsRepository(FakeGateway(
            RemoteEventPayload(
                requestId = "load", operationId = null, eventType = "host.settings.result",
                payload = currentSnapshot(includeOfficial = true),
            ),
        )).load()

        assertEquals(WindowsSettingsLoadState.Loaded, result.state)
        assertEquals("medium", result.settings!!.official!!.modelVerbosity)
        assertNull(result.settings!!.scope.authenticationDisplayName)
    }

    @Test
    fun update_acceptsCurrentHostResponseAndKeepsAdvancedSettings() = runTest {
        val gateway = FakeGateway(RemoteEventPayload(
            requestId = "update", operationId = null, eventType = "host.settings.update.result",
            payload = JSONObject().put("updated", true).put("settings", currentSnapshot(includeOfficial = true)),
        ))

        val result = WindowsSettingsRepository(gateway).update(
            WindowsSettingsUpdate(customApproval = WindowsCustomApprovalUpdate(listOf("GitInspect"), listOf("git status"))),
        )

        assertEquals(WindowsSettingsUpdateState.Completed, result.state)
        assertTrue(result.updated)
        assertEquals("medium", result.settings!!.official!!.modelVerbosity)
    }

    @Test
    fun load_stillRejectsMissingCoreDataAndIncompleteAdvancedSettings() = runTest {
        val missingCore = currentSnapshot(includeOfficial = true).apply { remove("defaults") }
        val malformedOfficial = currentSnapshot(includeOfficial = true).apply { getJSONObject("official").remove("serviceTier") }
        for (payload in listOf(missingCore, malformedOfficial)) {
            val result = WindowsSettingsRepository(FakeGateway(RemoteEventPayload(
                requestId = "load", operationId = null, eventType = "host.settings.result", payload = payload,
            ))).load()
            assertEquals(WindowsSettingsLoadState.Error, result.state)
            assertEquals("settings_not_loaded", result.errorCode)
        }
    }

    @Test
    fun load_mapsCompleteAggregateSnapshot() = runTest {
        val gateway = FakeGateway(
            RemoteEventPayload(
                requestId = "load",
                operationId = null,
                eventType = "host.settings.result",
                payload = completeSnapshot(),
            ),
        )

        val result = WindowsSettingsRepository(gateway).load()

        assertEquals("host.settings.get", gateway.lastCommand!!.commandType)
        assertEquals(WindowsSettingsLoadState.Loaded, result.state)
        assertEquals("PC", result.settings!!.scope.hostName)
        assertNull(result.settings!!.scope.runtimeProfileId)
        assertEquals(listOf("low", "high"), result.settings!!.models.single().reasoningEfforts)
        assertEquals("Git 检查", result.settings!!.customApproval.availablePermissions.single().displayName)
        assertEquals(listOf("git status"), result.settings!!.customApproval.commandPrefixes)
        assertNull(result.settings!!.official!!.serviceTier)
        assertEquals(true, result.settings!!.official!!.viewImageToolEnabled)
    }

    @Test
    fun load_rejectsUnexpectedEventAndMissingGatewayIsUnavailable() = runTest {
        val wrong = WindowsSettingsRepository(
            FakeGateway(
                RemoteEventPayload(
                    requestId = "load",
                    operationId = null,
                    eventType = "other",
                ),
            ),
        ).load()
        val unavailable = WindowsSettingsRepository(null).load()

        assertEquals(WindowsSettingsLoadState.Error, wrong.state)
        assertEquals("settings_not_loaded", wrong.errorCode)
        assertEquals(WindowsSettingsLoadState.Unavailable, unavailable.state)
    }

    @Test
    fun update_sendsOnlyIncludedSectionWithExplicitNullsAndAcceptsNoOp() = runTest {
        val gateway = FakeGateway(
            RemoteEventPayload(
                requestId = "update",
                operationId = null,
                eventType = "host.settings.update.result",
                payload = JSONObject()
                    .put("updated", false)
                    .put("settings", completeSnapshot()),
            ),
        )
        val official = WindowsOfficialSettings(
            modelReasoningSummary = null,
            modelVerbosity = null,
            serviceTier = null,
            webSearchMode = null,
            webSearchContextSize = null,
            viewImageToolEnabled = null,
            toolOutputTokenLimit = null,
            projectDocMaxBytes = null,
        )

        val result = WindowsSettingsRepository(gateway).update(
            WindowsSettingsUpdate(official = official),
        )

        val payload = gateway.lastCommand!!.payload
        assertEquals("host.settings.update", gateway.lastCommand!!.commandType)
        assertFalse(payload.has("defaults"))
        assertFalse(payload.has("customApproval"))
        assertTrue(payload.has("official"))
        assertTrue(payload.getJSONObject("official").isNull("modelContextWindow"))
        assertTrue(payload.getJSONObject("official").isNull("modelAutoCompactTokenLimit"))
        assertTrue(payload.getJSONObject("official").isNull("serviceTier"))
        assertTrue(payload.getJSONObject("official").isNull("viewImageToolEnabled"))
        assertEquals(WindowsSettingsUpdateState.Completed, result.state)
        assertFalse(result.updated)
        assertEquals("PC", result.settings!!.scope.hostName)
    }

    @Test
    fun update_encodesCompleteDefaultsAndCustomArrays() = runTest {
        val gateway = FakeGateway(
            RemoteEventPayload(
                requestId = "update",
                operationId = null,
                eventType = "host.settings.update.result",
                payload = JSONObject()
                    .put("updated", true)
                    .put("settings", completeSnapshot()),
            ),
        )

        WindowsSettingsRepository(gateway).update(
            WindowsSettingsUpdate(
                defaults = WindowsSettingsDefaults(
                    modelId = "gpt-test",
                    reasoningEffort = null,
                    approvalMode = WindowsApprovalMode.Auto,
                ),
                customApproval = WindowsCustomApprovalUpdate(
                    enabledPermissions = listOf("GitInspect"),
                    commandPrefixes = listOf("git status"),
                ),
            ),
        )

        val payload = gateway.lastCommand!!.payload
        val defaults = payload.getJSONObject("defaults")
        val custom = payload.getJSONObject("customApproval")
        assertTrue(defaults.has("modelId"))
        assertTrue(defaults.has("reasoningEffort"))
        assertTrue(defaults.has("approvalMode"))
        assertEquals("GitInspect", custom.getJSONArray("enabledPermissions").getString(0))
        assertEquals("git status", custom.getJSONArray("commandPrefixes").getString(0))
    }

    @Test
    fun remoteErrorCodeIsPreserved() = runTest {
        val result = WindowsSettingsRepository(
            FakeGateway(
                RemoteEventPayload(
                    requestId = "update",
                    operationId = null,
                    eventType = "remote.error",
                    errorCode = "write_lock_denied",
                ),
            ),
        ).update(WindowsSettingsUpdate())

        assertEquals(WindowsSettingsUpdateState.Error, result.state)
        assertEquals("write_lock_denied", result.errorCode)
    }

    private fun currentSnapshot(includeOfficial: Boolean): JSONObject {
        return completeSnapshot().apply {
            getJSONObject("scope").remove("authenticationSourceKind")
            getJSONObject("scope").put("authenticationDisplayName", JSONObject.NULL)
            if (!includeOfficial) remove("official")
        }
    }

    private fun completeSnapshot(): JSONObject {
        return JSONObject()
            .put(
                "scope",
                JSONObject()
                    .put("hostName", "PC")
                    .put("runtimeSource", "Wsl")
                    .put("runtimeDisplayName", "Ubuntu")
                    .put("runtimeProfileId", JSONObject.NULL)
                    .put("authenticationSourceKind", "chatgpt")
                    .put("authenticationDisplayName", "ChatGPT"),
            )
            .put(
                "defaults",
                JSONObject()
                    .put("modelId", "gpt-test")
                    .put("reasoningEffort", "high")
                    .put("approvalMode", "auto"),
            )
            .put(
                "models",
                JSONArray().put(
                    JSONObject()
                        .put("modelId", "gpt-test")
                        .put("displayName", "GPT Test")
                        .put("defaultReasoningEffort", "high")
                        .put("reasoningEfforts", JSONArray(listOf("low", "high"))),
                ),
            )
            .put(
                "customApproval",
                JSONObject()
                    .put(
                        "availablePermissions",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "GitInspect")
                                .put("displayName", "Git 检查"),
                        ),
                    )
                    .put("enabledPermissions", JSONArray(listOf("GitInspect")))
                    .put("commandPrefixes", JSONArray(listOf("git status"))),
            )
            .put(
                "official",
                JSONObject()
                    .put("modelReasoningSummary", "auto")
                    .put("modelVerbosity", "medium")
                    .put("serviceTier", JSONObject.NULL)
                    .put("webSearchMode", "cached")
                    .put("webSearchContextSize", "medium")
                    .put("viewImageToolEnabled", true)
                    .put("toolOutputTokenLimit", JSONObject.NULL)
                    .put("projectDocMaxBytes", JSONObject.NULL),
            )
    }

    private class FakeGateway(
        private val result: RemoteEventPayload?,
    ) : RemoteCommandGateway {
        var lastCommand: RemoteCommandPayload? = null

        override suspend fun sendCommand(command: RemoteCommandPayload): RemoteEventPayload? {
            lastCommand = command
            return result
        }
    }
}
