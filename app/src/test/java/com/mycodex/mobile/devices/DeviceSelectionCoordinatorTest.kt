package com.mycodex.mobile.devices

import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSelectionCoordinatorTest {
    private class Connection(val host: String, val closed: () -> Unit) : Closeable {
        override fun close() = closed()
    }
    private class Harness {
        val log = mutableListOf<String>()
        var live = 0
        var maxLive = 0
        var commitFailure = false
        var verify: suspend (Connection) -> Boolean = { true }
        val coordinator = DeviceSelectionCoordinator(
            initialDevice = device("A"),
            create = { host ->
                log += "open:${host.id}"
                live++
                maxLive = maxOf(maxLive, live)
                Connection(host.id) { live--; log += "close:${host.id}" }
            },
            verify = { verify(it) },
            revokeMonitoring = { log += "revoke" },
            commit = { if (commitFailure) error("disk full"); log += "commit:${it.id}" },
            now = { 42L },
        )
    }

    @Test fun saveAndRevokeCloseBeforeVerifyAndCommit() = runTest {
        val h = Harness()
        val oldGeneration = h.coordinator.state.value.generation
        h.verify = { h.log += "verify:${it.host}"; true }
        assertTrue(h.coordinator.select(device("B")) { h.log += "save:A" })
        assertEquals(listOf("open:A", "save:A", "revoke", "close:A", "open:B", "verify:B", "commit:B"), h.log)
        assertEquals(1, h.maxLive)
        assertFalse(h.coordinator.isCurrent("A", oldGeneration))
        assertFalse(h.coordinator.isCurrent("B", oldGeneration))
        assertTrue(h.coordinator.state.value.connected)
    }

    @Test fun saveFailureKeepsOriginalConnectionAndGeneration() = runTest {
        val h = Harness()
        val before = h.coordinator.state.value
        assertFalse(h.coordinator.select(device("B")) { error("disk full") })
        assertEquals(listOf("open:A"), h.log)
        assertEquals(before.generation, h.coordinator.state.value.generation)
        assertNotNull(h.coordinator.currentConnection("A"))
    }

    @Test fun failedTargetClosesBeforeRestoringOriginalAndRejectsOldTokens() = runTest {
        val h = Harness()
        h.verify = { it.host == "A" }
        assertFalse(h.coordinator.select(device("B")) {})
        assertTrue(h.log.indexOf("close:B") < h.log.lastIndexOf("open:A"))
        assertEquals("A", h.coordinator.state.value.currentHostId)
        assertTrue(h.coordinator.state.value.connected)
        assertFalse(h.coordinator.state.value.checks.getValue("B").available)
        assertEquals(1, h.maxLive)
    }

    @Test fun failedRestorationRetainsOriginalIdentityDisconnected() = runTest {
        val h = Harness()
        h.verify = { false }
        assertFalse(h.coordinator.select(device("B")) {})
        assertEquals("A", h.coordinator.state.value.currentHostId)
        assertFalse(h.coordinator.state.value.connected)
        assertNull(h.coordinator.currentConnection("A"))
        assertEquals(0, h.live)
    }

    @Test fun cancellationAndDuplicateSelectionCannotOpenSecondConnection() = runTest {
        val h = Harness()
        val verifying = CompletableDeferred<Unit>()
        h.verify = { if (it.host == "B") { verifying.complete(Unit); CompletableDeferred<Boolean>().await() } else true }
        val job = launch { h.coordinator.select(device("B")) {} }
        verifying.await()
        assertFalse(h.coordinator.select(device("C")) {})
        job.cancel()
        runCurrent()
        job.join()
        assertEquals("A", h.coordinator.state.value.currentHostId)
        assertTrue(h.coordinator.state.value.connected)
        assertTrue(h.log.indexOf("close:B") < h.log.lastIndexOf("open:A"))
        assertEquals(1, h.maxLive)
    }

    @Test fun selectionCommitFailureClosesTargetAndRestoresOriginal() = runTest {
        val h = Harness()
        h.commitFailure = true
        assertFalse(h.coordinator.select(device("B")) {})
        assertEquals("A", h.coordinator.state.value.currentHostId)
        assertTrue(h.coordinator.state.value.connected)
        assertEquals(1, h.maxLive)
    }

    companion object {
        private fun device(host: String) = ManagedDevice(DeviceBinding(
            relayUrl = "https://relay.example.com", relaySigningPublicKey = "relay-key",
            relaySigningKeyFingerprint = "fingerprint", certificateSha256 = null, tenantId = "tenant",
            hostId = host, hostName = host, deviceId = "phone", hostSigningPublicKey = "host-sign",
            hostAgreementPublicKey = "host-agree", deviceSigningPublicKey = "phone-sign",
            deviceAgreementPublicKey = "phone-agree", hostKeyVersion = 1, deviceKeyVersion = 1, bindingVersion = 1,
        ), createdAtEpochMillis = 1L, lastUsedAtEpochMillis = null, pairingState = PairingState.Paired)
    }
}
