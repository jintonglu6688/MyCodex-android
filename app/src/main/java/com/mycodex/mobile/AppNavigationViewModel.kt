package com.mycodex.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.json.JSONObject

internal class AppNavigationViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var selectedRoute by savedStateHandle.savedState(
        key = "selected_route",
        defaultValue = MainTabDestination.Coding.route,
    )
    var activeScreen by savedStateHandle.savedState(
        key = "active_screen",
        defaultValue = AppScreen.Main.name,
    )
    var detailDeviceId by savedStateHandle.savedState<String?>("detail_device_id", null)
    var codingWorkspaceId by savedStateHandle.savedState<String?>("coding_workspace_id", null)
    var codingWorkspaceTitle by savedStateHandle.savedState<String?>("coding_workspace_title", null)
    var codingSessionId by savedStateHandle.savedState<String?>("coding_session_id", null)
    var codingSessionTitle by savedStateHandle.savedState<String?>("coding_session_title", null)
    var officeWorkspaceId by savedStateHandle.savedState<String?>("office_workspace_id", null)
    var officeWorkspaceTitle by savedStateHandle.savedState<String?>("office_workspace_title", null)
    var officeSessionId by savedStateHandle.savedState<String?>("office_session_id", null)
    var officeSessionTitle by savedStateHandle.savedState<String?>("office_session_title", null)
    var mainRestoreFocusTarget by savedStateHandle.savedState<String?>("main_restore_focus_target", null)
    var deviceRestoreFocusTarget by savedStateHandle.savedState<String?>("device_restore_focus_target", null)
    var codingRestoreFocusTarget by savedStateHandle.savedState<String?>("coding_restore_focus_target", null)
    var officeRestoreFocusTarget by savedStateHandle.savedState<String?>("office_restore_focus_target", null)
    var windowsSettingsRestoreFocusTarget by savedStateHandle.savedState<String?>("windows_settings_restore_focus_target", null)
    var boundHostId by savedStateHandle.savedState<String?>("bound_host_id", null)

    fun workspaceSnapshot(): JSONObject = JSONObject()
        .put("selectedRoute", selectedRoute)
        .put("activeScreen", activeScreen)
        .put("codingWorkspaceId", codingWorkspaceId)
        .put("codingWorkspaceTitle", codingWorkspaceTitle)
        .put("codingSessionId", codingSessionId)
        .put("codingSessionTitle", codingSessionTitle)
        .put("officeWorkspaceId", officeWorkspaceId)
        .put("officeWorkspaceTitle", officeWorkspaceTitle)
        .put("officeSessionId", officeSessionId)
        .put("officeSessionTitle", officeSessionTitle)

    fun restoreWorkspace(state: JSONObject?) {
        selectedRoute = state?.optString("selectedRoute")?.takeIf { it.isNotEmpty() } ?: MainTabDestination.Coding.route
        activeScreen = state?.optString("activeScreen")?.takeIf { it.isNotEmpty() } ?: AppScreen.Main.name
        codingWorkspaceId = state?.optString("codingWorkspaceId")?.takeIf { it.isNotEmpty() }
        codingWorkspaceTitle = state?.optString("codingWorkspaceTitle")?.takeIf { it.isNotEmpty() }
        codingSessionId = state?.optString("codingSessionId")?.takeIf { it.isNotEmpty() }
        codingSessionTitle = state?.optString("codingSessionTitle")?.takeIf { it.isNotEmpty() }
        officeWorkspaceId = state?.optString("officeWorkspaceId")?.takeIf { it.isNotEmpty() }
        officeWorkspaceTitle = state?.optString("officeWorkspaceTitle")?.takeIf { it.isNotEmpty() }
        officeSessionId = state?.optString("officeSessionId")?.takeIf { it.isNotEmpty() }
        officeSessionTitle = state?.optString("officeSessionTitle")?.takeIf { it.isNotEmpty() }
    }

}

private fun <T> SavedStateHandle.savedState(
    key: String,
    defaultValue: T,
): ReadWriteProperty<Any?, T> = SavedStateHandleProperty(
    savedStateHandle = this,
    key = key,
    initialValue = get<T>(key) ?: defaultValue,
)

private class SavedStateHandleProperty<T>(
    private val savedStateHandle: SavedStateHandle,
    private val key: String,
    initialValue: T,
) : ReadWriteProperty<Any?, T> {
    private var value by mutableStateOf(initialValue)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
        savedStateHandle[key] = value
    }
}
