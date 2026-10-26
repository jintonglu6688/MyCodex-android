package com.mycodex.mobile.relay

import com.mycodex.mobile.devices.DeviceBinding
import com.mycodex.mobile.devices.ManagedDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class RelayDeviceConnectionRevocationTest {
    @Test fun closedManagerCannotReconnectAuthenticateOrSend() = runTest {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor {
            requests++
            error("network must not be reached")
        }.build()
        val binding = DeviceBinding(
            "https://relay.example.com", "relay-sign", "fingerprint", null, "tenant", "A", "A", "phone",
            "host-sign", "host-agree", "phone-sign", "phone-agree", 1, 1, 1,
        )
        val manager = RelayDeviceConnectionManager(
            ManagedDevice(binding, 1L, null),
            RelayAuthenticationClient(client, { error("signer must not run") }),
            { error("signer must not run") }, client,
        )
        manager.close()
        val actions: List<suspend () -> Unit> = listOf(
            { manager.connectForForegroundEvents(); Unit },
            { manager.reconnectForForegroundEvents(); Unit },
            { manager.sendCommand(RemoteCommandPayload(commandType = "coding.message.send")); Unit },
        )
        for (action in actions) {
            try { action(); fail("revoked selection must cancel the caller") } catch (_: CancellationException) {}
        }
        assertEquals(0, requests)
        assertFalse(manager.connectionEstablished.value)
    }
}
