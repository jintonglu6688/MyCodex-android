package com.mycodex.mobile.devices

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

class DevicePreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("managed_devices", Context.MODE_PRIVATE)

    fun load(): DeviceStoreSnapshot {
        val devices = preferences.getString(KEY_DEVICES, null)
            ?.lineSequence()
            ?.mapNotNull { deserializeDeviceForStorage(it) }
            ?.toList()
            ?: emptyList()
        val currentDeviceId = preferences.getString(KEY_CURRENT_DEVICE_ID, null)
            ?.takeIf { id -> devices.any { it.id == id } }
        return DeviceStoreSnapshot(devices, currentDeviceId)
    }

    fun save(snapshot: DeviceStoreSnapshot) {
        check(
            preferences.edit()
                .putString(KEY_DEVICES, snapshot.devices.joinToString("\n") { serializeDeviceForStorage(it) })
                .putString(KEY_CURRENT_DEVICE_ID, snapshot.currentDeviceId)
                .commit(),
        )
    }

    fun saveBinding(binding: DeviceBinding) {
        save(DeviceRegistry(load()).addBinding(binding, System.currentTimeMillis()))
    }

    companion object {
        private const val KEY_DEVICES = "devices"
        private const val KEY_CURRENT_DEVICE_ID = "current_device_id"
        private const val SCHEMA_VERSION = "secure-v1"
        private const val FIELD_COUNT = 19

        internal fun serializeDeviceForStorage(device: ManagedDevice): String {
            val binding = device.binding
            return listOf(
                SCHEMA_VERSION,
                encode(binding.relayUrl),
                encode(binding.relaySigningPublicKey),
                encode(binding.relaySigningKeyFingerprint),
                encode(binding.certificateSha256.orEmpty()),
                encode(binding.tenantId),
                encode(binding.hostId),
                encode(binding.hostName.orEmpty()),
                encode(binding.deviceId),
                encode(binding.hostSigningPublicKey),
                encode(binding.hostAgreementPublicKey),
                encode(binding.deviceSigningPublicKey),
                encode(binding.deviceAgreementPublicKey),
                binding.hostKeyVersion.toString(),
                binding.deviceKeyVersion.toString(),
                binding.bindingVersion.toString(),
                device.createdAtEpochMillis.toString(),
                device.lastUsedAtEpochMillis?.toString().orEmpty(),
                // Retired pairing-state column: always "Paired", kept so stored rows stay readable by older builds.
                encode("Paired"),
            ).joinToString("\t")
        }

        internal fun deserializeDeviceForStorage(line: String): ManagedDevice? {
            val parts = line.split('\t')
            if (parts.size != FIELD_COUNT || parts[0] != SCHEMA_VERSION) {
                return null
            }
            return try {
                val binding = DeviceBinding(
                    relayUrl = decodeRequired(parts[1]),
                    relaySigningPublicKey = decodeRequired(parts[2]),
                    relaySigningKeyFingerprint = decodeRequired(parts[3]),
                    certificateSha256 = decodeRequired(parts[4])
                        .takeIf { it.isNotEmpty() }
                        ?.let { requireNotNull(RelayCertificatePinning.normalizeSha256(it)) },
                    tenantId = decodeRequired(parts[5]),
                    hostId = decodeRequired(parts[6]),
                    hostName = decodeRequired(parts[7]).takeIf { it.isNotEmpty() },
                    deviceId = decodeRequired(parts[8]),
                    hostSigningPublicKey = decodeRequired(parts[9]),
                    hostAgreementPublicKey = decodeRequired(parts[10]),
                    deviceSigningPublicKey = decodeRequired(parts[11]),
                    deviceAgreementPublicKey = decodeRequired(parts[12]),
                    hostKeyVersion = parts[13].toInt().also { require(it > 0) },
                    deviceKeyVersion = parts[14].toInt().also { require(it > 0) },
                    bindingVersion = parts[15].toInt().also { require(it > 0) },
                )
                ManagedDevice(
                    binding = binding,
                    createdAtEpochMillis = parts[16].toLong(),
                    lastUsedAtEpochMillis = parts[17].takeIf { it.isNotEmpty() }?.toLong(),
                )
            } catch (_: RuntimeException) {
                null
            }
        }

        private fun encode(value: String): String {
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        }

        private fun decodeRequired(value: String): String {
            val bytes = Base64.getUrlDecoder().decode(value)
            val decoded = String(bytes, StandardCharsets.UTF_8)
            require(encode(decoded) == value)
            return decoded
        }
    }
}
