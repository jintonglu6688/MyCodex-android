package com.mycodex.mobile

import android.content.Context
import org.json.JSONObject
import com.mycodex.mobile.coding.CodingDraftViewModel
import java.util.Base64

/** Separate from pairing storage: never rewrites keys, bindings, pins, or device preferences. */
internal class DeviceWorkspaceStateStore(
    private val read: (String) -> String?,
    private val write: (String, String) -> Boolean,
    private val writeInBackground: (String, String) -> Unit,
) {
    constructor(read: (String) -> String?, write: (String, String) -> Boolean) :
        this(read, write, { key, value -> write(key, value) })
    constructor(context: Context) : this(
        read = { key -> context.getSharedPreferences("device_workspace_state", Context.MODE_PRIVATE).getString(key, null) },
        write = { key, value -> context.getSharedPreferences("device_workspace_state", Context.MODE_PRIVATE).edit().putString(key, value).commit() },
        writeInBackground = { key, value -> context.getSharedPreferences("device_workspace_state", Context.MODE_PRIVATE).edit().putString(key, value).apply() },
    )
    fun load(hostId: String): JSONObject? = read(key(hostId))?.let { encoded ->
        JSONObject(encoded).also { value ->
            // Validate the entire retained document before changing selection or any live draft.
            CodingDraftViewModel().restore(value.optJSONObject("coding"))
            CodingDraftViewModel().restore(value.optJSONObject("office"))
            listOf("codingWorkspaceIndex", "codingWorkspaceOffset", "codingSessionIndex", "codingSessionOffset", "officeSessionIndex", "officeSessionOffset")
                .forEach { require(value.optInt(it) >= 0) }
        }
    }
    fun save(hostId: String, state: JSONObject) {
        require(hostId.isNotBlank())
        check(write(key(hostId), state.toString())) { "workspace_state_save_failed" }
    }
    /** For autosaves while typing: queues the write off the UI thread. Saves that must land before going on use [save]. */
    fun saveInBackground(hostId: String, state: JSONObject) {
        require(hostId.isNotBlank())
        writeInBackground(key(hostId), state.toString())
    }
    private fun key(hostId: String) = "host_" + Base64.getUrlEncoder().withoutPadding().encodeToString(hostId.toByteArray(Charsets.UTF_8))
}
