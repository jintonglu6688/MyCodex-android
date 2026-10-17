package com.mycodex.mobile.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceRegistryTest {
    @Test
    fun addBindingSelectsFirstHostWithoutTransientPairingValues() {
        val updated = DeviceRegistry(DeviceStoreSnapshot()).addBinding(
            binding(hostId = "host_a", hostName = "Office PC"),
            nowEpochMillis = 1000L,
        )

        assertEquals("host_a", updated.currentDeviceId)
        assertEquals(1, updated.devices.size)
        assertEquals("Office PC", updated.devices.single().displayName)
        assertEquals("device_a", updated.devices.single().binding.deviceId)
    }

    @Test
    fun replacingHostBindingPreservesCreationAndUpdatesLastUsed() {
        val original = DeviceRegistry(DeviceStoreSnapshot()).addBinding(
            binding(hostId = "host_a", hostName = "Office PC"),
            nowEpochMillis = 1000L,
        )

        val replaced = DeviceRegistry(original).addBinding(
            binding(hostId = "host_a", hostName = "Office PC 2").copy(bindingVersion = 2),
            nowEpochMillis = 2000L,
        )

        assertEquals(1, replaced.devices.size)
        assertEquals(1000L, replaced.devices.single().createdAtEpochMillis)
        assertEquals(2000L, replaced.devices.single().lastUsedAtEpochMillis)
        assertEquals(2, replaced.devices.single().binding.bindingVersion)
    }

    @Test
    fun addingAnotherHostPreservesCurrentDevice() {
        var snapshot = DeviceRegistry(DeviceStoreSnapshot()).addBinding(
            binding(hostId = "host_a", hostName = "Office PC"),
            nowEpochMillis = 1000L,
        )

        snapshot = DeviceRegistry(snapshot).addBinding(
            binding(hostId = "host_b", hostName = "Home PC"),
            nowEpochMillis = 2000L,
        )

        assertEquals("host_a", snapshot.currentDeviceId)
    }

    @Test
    fun addingHostDoesNotSilentlySelectItWhenExistingSelectionIsMissing() {
        val first = DeviceRegistry(DeviceStoreSnapshot()).addBinding(
            binding(hostId = "host_a", hostName = "Office PC"),
            nowEpochMillis = 1000L,
        )
        val snapshotWithoutSelection = first.copy(currentDeviceId = null)

        val updated = DeviceRegistry(snapshotWithoutSelection).addBinding(
            binding(hostId = "host_b", hostName = "Home PC"),
            nowEpochMillis = 2000L,
        )

        assertNull(updated.currentDeviceId)
    }

    @Test
    fun removeCurrentDeviceClearsSelection() {
        var snapshot = DeviceRegistry(DeviceStoreSnapshot()).addBinding(
            binding(hostId = "host_a", hostName = "Office PC"),
            nowEpochMillis = 1000L,
        )
        snapshot = DeviceRegistry(snapshot).addBinding(
            binding(hostId = "host_b", hostName = "Home PC"),
            nowEpochMillis = 2000L,
        )
        snapshot = DeviceRegistry(snapshot).selectDevice("host_a", nowEpochMillis = 3000L)

        val updated = DeviceRegistry(snapshot).removeDevice("host_a")

        assertNull(updated.currentDeviceId)
        assertEquals(1, updated.devices.size)
    }

    @Test
    fun removingOnlyDeviceClearsCurrentDevice() {
        val snapshot = DeviceRegistry(DeviceStoreSnapshot()).addBinding(
            binding(hostId = "host_a", hostName = "Office PC"),
            nowEpochMillis = 1000L,
        )

        val updated = DeviceRegistry(snapshot).removeDevice("host_a")

        assertNull(updated.currentDeviceId)
        assertEquals(0, updated.devices.size)
    }

    @Test
    fun testingConnectionDoesNotSilentlySelectDeviceWhenSelectionIsMissing() {
        var snapshot = DeviceRegistry(DeviceStoreSnapshot()).addBinding(
            binding(hostId = "host_a", hostName = "Office PC"),
            nowEpochMillis = 1000L,
        )
        snapshot = DeviceRegistry(snapshot).addBinding(
            binding(hostId = "host_b", hostName = "Home PC"),
            nowEpochMillis = 2000L,
        ).copy(currentDeviceId = null)

        val updated = DeviceRegistry(snapshot).markConnected("host_b", nowEpochMillis = 3000L)

        assertNull(updated.currentDeviceId)
        assertEquals(PairingState.Connected, updated.devices.first { it.id == "host_b" }.pairingState)
    }

    @Test
    fun testingAnotherDevicePreservesCurrentSelection() {
        var snapshot = DeviceRegistry(DeviceStoreSnapshot()).addBinding(
            binding(hostId = "host_a", hostName = "Office PC"),
            nowEpochMillis = 1000L,
        )
        snapshot = DeviceRegistry(snapshot).addBinding(
            binding(hostId = "host_b", hostName = "Home PC"),
            nowEpochMillis = 2000L,
        )

        val updated = DeviceRegistry(snapshot).markConnected("host_b", nowEpochMillis = 3000L)

        assertEquals("host_a", updated.currentDeviceId)
    }

    private fun binding(hostId: String, hostName: String): DeviceBinding {
        return DeviceBinding(
            relayUrl = "https://relay.example.com",
            relaySigningPublicKey = "relay-signing-key",
            relaySigningKeyFingerprint = "relay-fingerprint",
            certificateSha256 = null,
            tenantId = "tenant_a",
            hostId = hostId,
            hostName = hostName,
            deviceId = "device_a",
            hostSigningPublicKey = "host-signing-key",
            hostAgreementPublicKey = "host-agreement-key",
            deviceSigningPublicKey = "device-signing-key",
            deviceAgreementPublicKey = "device-agreement-key",
            hostKeyVersion = 1,
            deviceKeyVersion = 1,
            bindingVersion = 1,
        )
    }
}
