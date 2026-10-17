package com.mycodex.mobile.settings

import com.mycodex.mobile.relay.RemoteCommandGateway
import com.mycodex.mobile.relay.RemoteCommandPayload
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface WindowsSettingsDataSource {
    suspend fun load(): WindowsSettingsLoadResult

    suspend fun update(update: WindowsSettingsUpdate): WindowsSettingsUpdateResult
}

class WindowsSettingsRepository(
    private val gateway: RemoteCommandGateway?,
) : WindowsSettingsDataSource {
    override suspend fun load(): WindowsSettingsLoadResult {
        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = "host.settings.get",
                payload = JSONObject(),
            ),
        ) ?: return WindowsSettingsLoadResult(WindowsSettingsLoadState.Unavailable)

        if (event.errorCode != null) {
            return WindowsSettingsLoadResult(
                state = WindowsSettingsLoadState.Error,
                errorCode = event.errorCode,
            )
        }
        if (event.eventType != "host.settings.result") {
            return WindowsSettingsLoadResult(
                state = WindowsSettingsLoadState.Error,
                errorCode = "settings_not_loaded",
            )
        }

        return try {
            WindowsSettingsLoadResult(
                state = WindowsSettingsLoadState.Loaded,
                settings = event.payload.toWindowsSettingsSnapshot(),
            )
        } catch (_: JSONException) {
            WindowsSettingsLoadResult(
                state = WindowsSettingsLoadState.Error,
                errorCode = "settings_not_loaded",
            )
        }
    }

    override suspend fun update(update: WindowsSettingsUpdate): WindowsSettingsUpdateResult {
        val event = gateway?.sendCommand(
            RemoteCommandPayload(
                commandType = "host.settings.update",
                payload = update.toJson(),
            ),
        ) ?: return WindowsSettingsUpdateResult(WindowsSettingsUpdateState.Unavailable)

        if (event.errorCode != null) {
            return WindowsSettingsUpdateResult(
                state = WindowsSettingsUpdateState.Error,
                errorCode = event.errorCode,
            )
        }
        if (event.eventType != "host.settings.update.result") {
            return WindowsSettingsUpdateResult(
                state = WindowsSettingsUpdateState.Error,
                errorCode = "settings_not_updated",
            )
        }

        return try {
            WindowsSettingsUpdateResult(
                state = WindowsSettingsUpdateState.Completed,
                updated = event.payload.getBoolean("updated"),
                settings = event.payload.getJSONObject("settings").toWindowsSettingsSnapshot(),
            )
        } catch (_: JSONException) {
            WindowsSettingsUpdateResult(
                state = WindowsSettingsUpdateState.Error,
                errorCode = "settings_not_updated",
            )
        }
    }
}

private fun WindowsSettingsUpdate.toJson(): JSONObject {
    return JSONObject().also { payload ->
        defaults?.let { payload.put("defaults", it.toJson()) }
        customApproval?.let { payload.put("customApproval", it.toJson()) }
        official?.let { payload.put("official", it.toJson()) }
    }
}

private fun WindowsSettingsDefaults.toJson(): JSONObject {
    return JSONObject()
        .put("modelId", modelId)
        .put("reasoningEffort", reasoningEffort ?: JSONObject.NULL)
        .put("approvalMode", approvalMode.wireValue)
}

private fun WindowsCustomApprovalUpdate.toJson(): JSONObject {
    return JSONObject()
        .put("enabledPermissions", JSONArray(enabledPermissions))
        .put("commandPrefixes", JSONArray(commandPrefixes))
}

private fun WindowsOfficialSettings.toJson(): JSONObject {
    return JSONObject()
        // ponytail: keep explicit nulls until older Windows hosts no longer require both fields.
        .put("modelContextWindow", JSONObject.NULL)
        .put("modelAutoCompactTokenLimit", JSONObject.NULL)
        .putNullable("modelReasoningSummary", modelReasoningSummary)
        .putNullable("modelVerbosity", modelVerbosity)
        .putNullable("serviceTier", serviceTier)
        .putNullable("webSearchMode", webSearchMode)
        .putNullable("webSearchContextSize", webSearchContextSize)
        .putNullable("viewImageToolEnabled", viewImageToolEnabled)
        .putNullable("toolOutputTokenLimit", toolOutputTokenLimit)
        .putNullable("projectDocMaxBytes", projectDocMaxBytes)
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
    return put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.toWindowsSettingsSnapshot(): WindowsSettingsSnapshot {
    val scope = getJSONObject("scope")
    val defaults = getJSONObject("defaults")
    val custom = getJSONObject("customApproval")
    val approvalMode = WindowsApprovalMode.fromWire(defaults.getString("approvalMode"))
        ?: throw JSONException("Unknown approval mode")
    return WindowsSettingsSnapshot(
        scope = WindowsSettingsScope(
            hostName = scope.getString("hostName"),
            runtimeSource = scope.getString("runtimeSource"),
            runtimeDisplayName = scope.getString("runtimeDisplayName"),
            runtimeProfileId = scope.nullableString("runtimeProfileId"),
            authenticationSourceKind = scope.optionalNullableString("authenticationSourceKind"),
            authenticationDisplayName = scope.optionalNullableString("authenticationDisplayName"),
        ),
        defaults = WindowsSettingsDefaults(
            modelId = defaults.getString("modelId"),
            reasoningEffort = defaults.nullableString("reasoningEffort"),
            approvalMode = approvalMode,
        ),
        models = getJSONArray("models").objects().map { model ->
            WindowsSettingsModelOption(
                modelId = model.getString("modelId"),
                displayName = model.getString("displayName"),
                defaultReasoningEffort = model.nullableString("defaultReasoningEffort"),
                reasoningEfforts = model.getJSONArray("reasoningEfforts").strings(),
            )
        },
        customApproval = WindowsCustomApprovalSettings(
            availablePermissions = custom.getJSONArray("availablePermissions").objects().map { permission ->
                WindowsPermissionOption(
                    id = permission.getString("id"),
                    displayName = permission.getString("displayName"),
                )
            },
            enabledPermissions = custom.getJSONArray("enabledPermissions").strings(),
            commandPrefixes = custom.getJSONArray("commandPrefixes").strings(),
        ),
        official = if (!has("official") || isNull("official")) null else getJSONObject("official").toOfficialSettings(),
    )
}

private fun JSONObject.toOfficialSettings(): WindowsOfficialSettings {
    return WindowsOfficialSettings(
        modelReasoningSummary = nullableString("modelReasoningSummary"),
        modelVerbosity = nullableString("modelVerbosity"),
        serviceTier = nullableString("serviceTier"),
        webSearchMode = nullableString("webSearchMode"),
        webSearchContextSize = nullableString("webSearchContextSize"),
        viewImageToolEnabled = nullableBoolean("viewImageToolEnabled"),
        toolOutputTokenLimit = nullableLong("toolOutputTokenLimit"),
        projectDocMaxBytes = nullableLong("projectDocMaxBytes"),
    )
}

private fun JSONObject.optionalNullableString(key: String): String? {
    return if (has(key)) nullableString(key) else null
}

private fun JSONObject.nullableString(key: String): String? {
    requirePresent(key)
    return if (isNull(key)) null else getString(key)
}

private fun JSONObject.nullableLong(key: String): Long? {
    requirePresent(key)
    return if (isNull(key)) null else getLong(key)
}

private fun JSONObject.nullableBoolean(key: String): Boolean? {
    requirePresent(key)
    return if (isNull(key)) null else getBoolean(key)
}

private fun JSONObject.requirePresent(key: String) {
    if (!has(key)) {
        throw JSONException("Missing $key")
    }
}

private fun JSONArray.objects(): List<JSONObject> {
    return List(length()) { index -> getJSONObject(index) }
}

private fun JSONArray.strings(): List<String> {
    return List(length()) { index -> getString(index) }
}
