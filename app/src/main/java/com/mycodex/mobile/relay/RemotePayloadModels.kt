package com.mycodex.mobile.relay

import com.mycodex.mobile.devices.ManagedDevice
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

data class RemoteCommandPayload(
    val requestId: String = newRemoteRequestId(),
    val operationId: String? = null,
    val commandType: String,
    val payload: JSONObject = JSONObject(),
    val timeoutMillis: Long? = null,
    val keepConnectionOnTimeout: Boolean = false,
)

data class RemoteEventPayload(
    val requestId: String?,
    val operationId: String?,
    val eventType: String,
    val payload: JSONObject = JSONObject(),
    val errorCode: String? = null,
)

interface RemoteCommandGateway {
    suspend fun sendCommand(command: RemoteCommandPayload): RemoteEventPayload?
}

interface RemoteEventSource {
    val remoteEvents: Flow<RemoteEventPayload>
}

internal fun pendingInteractionEvents(event: RemoteEventPayload?): List<RemoteEventPayload>? {
    if (event == null || event.errorCode != null || event.eventType != "interaction.pending.list.result") return null
    val items = event.payload.optJSONArray("events") ?: return null
    return (0 until items.length()).map { index ->
        val item = items.getJSONObject(index)
        val type = item.getString("eventType")
        require(type in setOf("approval.requested", "tool.input.requested", "plan.execution.requested"))
        RemoteEventPayload(
            item.optString("requestId").takeIf { it.isNotBlank() },
            item.optString("operationId").takeIf { it.isNotBlank() },
            type,
            item.getJSONObject("payload"),
        )
    }
}

/** Serializes live completions and snapshot replay; tombstones last only for one refresh. */
internal class PendingInteractionReplay {
    private var generation = 0L
    private var active = false
    private val completed = mutableSetOf<String>()

    @Synchronized fun begin(): Long {
        completed.clear()
        active = true
        return ++generation
    }

    @Synchronized fun live(event: RemoteEventPayload, publish: (RemoteEventPayload) -> Unit) {
        if (active && event.eventType in setOf("approval.completed", "tool.input.completed", "plan.execution.completed")) {
            completed += key(event)
        }
        publish(event)
    }

    @Synchronized fun replay(token: Long, event: RemoteEventPayload, publish: (RemoteEventPayload) -> Unit) {
        if (active && generation == token && key(event) !in completed) publish(event)
    }

    @Synchronized fun end(token: Long) {
        if (generation == token) { active = false; completed.clear() }
    }

    private fun key(event: RemoteEventPayload): String = event.eventType.substringBeforeLast('.') + ":" +
        event.payload.optString("requestId").ifBlank { event.requestId.orEmpty() }
}

internal object RelayRemoteEnvelopeFactory {
    fun buildWebSocketUrl(device: ManagedDevice): String {
        val scheme = if (device.useTls) "wss" else "ws"
        return "$scheme://${device.relayHost}:${device.relayPort}/v1/ws" +
            "?connection=device" +
            "&tenantId=${query(device.tenantId)}" +
            "&hostId=${query(device.hostId)}" +
            "&deviceId=${query(device.deviceId)}"
    }

    private fun query(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}

internal fun newRemoteRequestId(): String {
    return "android-request-${UUID.randomUUID().toString().replace("-", "")}"
}
