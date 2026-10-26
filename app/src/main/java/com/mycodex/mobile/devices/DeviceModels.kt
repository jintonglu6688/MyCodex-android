package com.mycodex.mobile.devices

import com.mycodex.mobile.security.PairingPackage
import com.mycodex.mobile.security.PairingClaimHeader

data class ImportedPairingPackage(
    val pairingPackage: PairingPackage,
    val hostSignature: ByteArray,
    val originalUri: String,
)

data class PairingClaimSubmission(
    val header: PairingClaimHeader,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

data class PairingClaimCreated(
    val claimId: String,
    val claimAccessToken: String,
    val status: String,
    val expiresAt: Long,
)

data class PairingClaimStatus(
    val claimId: String,
    val status: String,
    val updatedAt: Long,
    val approvalHeader: ByteArray?,
    val approvalNonce: ByteArray?,
    val approvalCiphertext: ByteArray?,
)

data class DeviceBinding(
    val relayUrl: String,
    val relaySigningPublicKey: String,
    val relaySigningKeyFingerprint: String,
    val certificateSha256: String?,
    val tenantId: String,
    val hostId: String,
    val hostName: String?,
    val deviceId: String,
    val hostSigningPublicKey: String,
    val hostAgreementPublicKey: String,
    val deviceSigningPublicKey: String,
    val deviceAgreementPublicKey: String,
    val hostKeyVersion: Int,
    val deviceKeyVersion: Int,
    val bindingVersion: Int,
)

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data class Waiting(val target: String, val shortCode: String) : PairingUiState
    data class Succeeded(val binding: DeviceBinding) : PairingUiState
    data class Failed(val code: String) : PairingUiState
}

data class ManagedDevice(
    val binding: DeviceBinding,
    val createdAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long?,
) {
    val id: String
        get() = binding.hostId
    val displayName: String
        get() = binding.hostName?.takeIf { it.isNotBlank() } ?: binding.hostId
    val relayHost: String
        get() = java.net.URI(binding.relayUrl).host
    val relayPort: Int
        get() = java.net.URI(binding.relayUrl).port.takeIf { it > 0 } ?: 443
    val certificateSha256: String?
        get() = binding.certificateSha256
    val tenantId: String
        get() = binding.tenantId
    val hostId: String
        get() = binding.hostId
    val hostName: String?
        get() = binding.hostName
    val deviceId: String
        get() = binding.deviceId
}

data class DeviceStoreSnapshot(
    val devices: List<ManagedDevice> = emptyList(),
    val currentDeviceId: String? = null,
)
