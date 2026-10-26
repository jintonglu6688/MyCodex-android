package com.mycodex.mobile.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePreferencesStoreTest {
    @Test
    fun secureBindingRecordRoundTripsPublicMetadata() {
        val device = managedDevice()

        val serialized = DevicePreferencesStore.serializeDeviceForStorage(device)
        val restored = DevicePreferencesStore.deserializeDeviceForStorage(serialized)

        assertEquals(device, restored)
        assertTrue(serialized.startsWith("secure-v1\t"))
    }

    @Test
    fun storedBindingContainsNoDeviceTokenInviteSecretOrUseTls() {
        val serialized = DevicePreferencesStore.serializeDeviceForStorage(managedDevice())
        val bindingFieldNames = DeviceBinding::class.java.declaredFields.map { it.name }.toSet()

        assertFalse(bindingFieldNames.contains("deviceToken"))
        assertFalse(bindingFieldNames.contains("inviteSecret"))
        assertFalse(bindingFieldNames.contains("inviteId"))
        assertFalse(bindingFieldNames.contains("useTls"))
        assertFalse(serialized.contains("device-token-secret"))
        assertFalse(serialized.contains("invite-secret"))
        assertFalse(serialized.contains("claim-access-secret"))
    }

    @Test
    fun malformedOrLegacyStoredRecordsAreDiscarded() {
        val legacy = listOf(
            "host_a",
            "Office PC",
            "Windows",
            "relay.example.com",
            "443",
            "true",
            "",
            "tenant_a",
            "host_a",
            "Office PC",
            "invite_a",
            "device_a",
            "device-token-secret",
            "1000",
            "2000",
            "Paired",
        ).joinToString("\t")

        assertNull(DevicePreferencesStore.deserializeDeviceForStorage(legacy))
        assertNull(DevicePreferencesStore.deserializeDeviceForStorage("secure-v1\tbroken"))
    }

    @Test
    fun storedBindingRejectsNonHexCertificatePin() {
        val malformed = managedDevice().copy(
            binding = binding(certificateSha256 = "A".repeat(43)),
        )

        assertNull(
            DevicePreferencesStore.deserializeDeviceForStorage(
                DevicePreferencesStore.serializeDeviceForStorage(malformed),
            ),
        )
    }

    private fun managedDevice(): ManagedDevice {
        return ManagedDevice(
            binding = binding(),
            createdAtEpochMillis = 1000L,
            lastUsedAtEpochMillis = 2000L,
        )
    }

    private fun binding(certificateSha256: String? = null): DeviceBinding {
        return DeviceBinding(
            relayUrl = "https://relay.example.com",
            relaySigningPublicKey = "relay-signing-key",
            relaySigningKeyFingerprint = "relay-fingerprint",
            certificateSha256 = certificateSha256,
            tenantId = "tenant_a",
            hostId = "host_a",
            hostName = "Office PC",
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
