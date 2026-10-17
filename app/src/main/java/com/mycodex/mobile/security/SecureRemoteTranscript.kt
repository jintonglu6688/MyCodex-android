package com.mycodex.mobile.security

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

data class PairingPackage(
    val protocolVersion: Long,
    val relayUrl: String,
    val relaySigningPublicKey: ByteArray,
    val relaySigningKeyFingerprint: ByteArray,
    val embeddedTlsCertificateSha256: ByteArray,
    val tenantId: String,
    val hostId: String,
    val hostName: String,
    val inviteId: String,
    val inviteSecret: ByteArray,
    val expiresAt: Long,
    val hostSigningPublicKey: ByteArray,
    val hostAgreementPublicKey: ByteArray,
    val hostKeyVersion: Long,
)

data class PairingClaimHeader(
    val protocolVersion: Long,
    val inviteId: String,
    val tenantId: String,
    val hostId: String,
    val claimId: String,
    val deviceId: String,
    val deviceAgreementPublicKey: ByteArray,
    val deviceKeyVersion: Long,
    val clientNonce: ByteArray,
    val ciphertextLength: Long,
)

data class PairingClaim(
    val protocolVersion: Long,
    val claimId: String,
    val inviteId: String,
    val tenantId: String,
    val hostId: String,
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val deviceSigningPublicKey: ByteArray,
    val deviceAgreementPublicKey: ByteArray,
    val deviceKeyVersion: Long,
    val clientNonce: ByteArray,
    val createdAt: Long,
)

data class PairingApproval(
    val protocolVersion: Long,
    val bindingId: String,
    val tenantId: String,
    val hostId: String,
    val deviceId: String,
    val hostSigningPublicKey: ByteArray,
    val hostAgreementPublicKey: ByteArray,
    val hostKeyVersion: Long,
    val deviceSigningPublicKey: ByteArray,
    val deviceAgreementPublicKey: ByteArray,
    val deviceKeyVersion: Long,
    val relaySigningPublicKey: ByteArray,
    val relaySigningKeyFingerprint: ByteArray,
    val approvedAt: Long,
    val bindingVersion: Long,
)

data class PairingApprovalHeader(
    val protocolVersion: Long,
    val claimId: String,
    val inviteId: String,
    val tenantId: String,
    val hostId: String,
    val deviceId: String,
    val status: String,
    val bindingVersion: Long,
    val nonce: ByteArray,
    val ciphertextLength: Long,
)

data class RelayChallenge(
    val protocolVersion: Long,
    val relaySigningKeyFingerprint: ByteArray,
    val challengeId: String,
    val challengeValue: ByteArray,
    val subjectType: String,
    val subjectId: String,
    val tenantId: String,
    val hostId: String,
    val deviceId: String,
    val purpose: String,
    val issuedAt: Long,
    val expiresAt: Long,
)

data class SessionHello(
    val protocolVersion: Long,
    val frameType: String = "",
    val sessionId: String = "",
    val tenantId: String,
    val hostId: String,
    val deviceId: String,
    val direction: String = "",
    val bindingVersion: Long,
    val keyVersion: Long,
    val ephemeralPublicKey: ByteArray,
    val nonce: ByteArray,
    val createdAt: Long,
)

data class SecureEnvelope(
    val protocolVersion: Long,
    val frameType: String = "session.envelope",
    val sessionId: String,
    val tenantId: String,
    val hostId: String,
    val deviceId: String,
    val direction: String,
    val kind: String,
    val messageId: String,
    val sequence: ULong,
    val createdAt: Long,
    val payloadEncoding: String,
    val nonce: ByteArray,
)

object SecureRemoteTranscript {
    fun pairingPackage(value: PairingPackage): ByteArray {
        return canonical(PAIRING_PACKAGE_DOMAIN) {
            int64(value.protocolVersion)
            string(value.relayUrl)
            bytes(value.relaySigningPublicKey)
            bytes(value.relaySigningKeyFingerprint)
            bytes(value.embeddedTlsCertificateSha256)
            string(value.tenantId)
            string(value.hostId)
            string(value.hostName)
            string(value.inviteId)
            bytes(value.inviteSecret)
            int64(value.expiresAt)
            bytes(value.hostSigningPublicKey)
            bytes(value.hostAgreementPublicKey)
            int64(value.hostKeyVersion)
        }
    }

    fun visibleClaimHeader(value: PairingClaimHeader): ByteArray {
        return canonical(PAIRING_CLAIM_DOMAIN) {
            int64(value.protocolVersion)
            string(value.inviteId)
            string(value.tenantId)
            string(value.hostId)
            string(value.claimId)
            string(value.deviceId)
            bytes(value.deviceAgreementPublicKey)
            int64(value.deviceKeyVersion)
            bytes(value.clientNonce)
            int64(value.ciphertextLength)
        }
    }

    fun pairingClaimWithoutProof(value: PairingClaim): ByteArray {
        return canonical(PAIRING_CLAIM_DOMAIN) {
            int64(value.protocolVersion)
            string(value.claimId)
            string(value.inviteId)
            string(value.tenantId)
            string(value.hostId)
            string(value.deviceId)
            string(value.deviceName)
            string(value.platform)
            string(value.appVersion)
            bytes(value.deviceSigningPublicKey)
            bytes(value.deviceAgreementPublicKey)
            int64(value.deviceKeyVersion)
            bytes(value.clientNonce)
            int64(value.createdAt)
        }
    }

    fun pairingClaimForSignature(value: PairingClaim, inviteProof: ByteArray): ByteArray {
        return pairingClaimWithoutProof(value) + encodedField(inviteProof)
    }

    fun pairingClaimPlaintext(
        value: PairingClaim,
        inviteProof: ByteArray,
        deviceSignature: ByteArray,
    ): ByteArray {
        return pairingClaimForSignature(value, inviteProof) + encodedField(deviceSignature)
    }

    fun pairingApproval(value: PairingApproval): ByteArray {
        return canonical(PAIRING_APPROVAL_DOMAIN) {
            int64(value.protocolVersion)
            string(value.bindingId)
            string(value.tenantId)
            string(value.hostId)
            string(value.deviceId)
            bytes(value.hostSigningPublicKey)
            bytes(value.hostAgreementPublicKey)
            int64(value.hostKeyVersion)
            bytes(value.deviceSigningPublicKey)
            bytes(value.deviceAgreementPublicKey)
            int64(value.deviceKeyVersion)
            bytes(value.relaySigningPublicKey)
            bytes(value.relaySigningKeyFingerprint)
            int64(value.approvedAt)
            int64(value.bindingVersion)
        }
    }

    fun pairingApprovalPlaintext(
        value: PairingApproval,
        hostSignature: ByteArray,
    ): ByteArray {
        return pairingApproval(value) + encodedField(hostSignature)
    }

    fun approvalHeader(value: PairingApprovalHeader): ByteArray {
        return canonical(PAIRING_APPROVAL_DOMAIN) {
            int64(value.protocolVersion)
            string(value.claimId)
            string(value.inviteId)
            string(value.tenantId)
            string(value.hostId)
            string(value.deviceId)
            string(value.status)
            int64(value.bindingVersion)
            bytes(value.nonce)
            int64(value.ciphertextLength)
        }
    }

    fun relayChallenge(value: RelayChallenge): ByteArray {
        return canonical(RELAY_CHALLENGE_DOMAIN) {
            int64(value.protocolVersion)
            bytes(value.relaySigningKeyFingerprint)
            string(value.challengeId)
            bytes(value.challengeValue)
            string(value.subjectType)
            string(value.subjectId)
            string(value.tenantId)
            string(value.hostId)
            string(value.deviceId)
            string(value.purpose)
            int64(value.issuedAt)
            int64(value.expiresAt)
        }
    }

    fun relayProof(
        challengeTranscript: ByteArray,
        relaySignature: ByteArray,
        subjectType: String,
        subjectId: String,
    ): ByteArray {
        return canonical(RELAY_PROOF_DOMAIN) {
            int64(SecureRemoteProtocol.VERSION.toLong())
            bytes(challengeTranscript)
            bytes(relaySignature)
            string(subjectType)
            string(subjectId)
        }
    }

    fun sessionHello(value: SessionHello): ByteArray {
        return canonical(SESSION_HELLO_DOMAIN) {
            int64(value.protocolVersion)
            string(value.frameType)
            string(value.sessionId)
            string(value.tenantId)
            string(value.hostId)
            string(value.deviceId)
            string(value.direction)
            int64(value.bindingVersion)
            int64(value.keyVersion)
            bytes(value.ephemeralPublicKey)
            bytes(value.nonce)
            int64(value.createdAt)
        }
    }

    fun serverHello(
        clientHello: ByteArray,
        clientSignature: ByteArray,
        serverHello: SessionHello,
    ): ByteArray {
        return clientHello + encodedField(clientSignature) + sessionHello(serverHello)
    }

    fun sessionComplete(serverHello: ByteArray, serverSignature: ByteArray): ByteArray {
        return serverHello + encodedField(serverSignature)
    }

    fun sessionConfirmation(sessionComplete: ByteArray, senderRole: String): ByteArray {
        return canonical(SESSION_CONFIRM_DOMAIN) {
            bytes(sessionComplete)
            string(senderRole)
        }
    }

    fun envelopeAad(value: SecureEnvelope): ByteArray {
        return canonical(ENVELOPE_AAD_DOMAIN) {
            int64(value.protocolVersion)
            string(value.frameType)
            string(value.sessionId)
            string(value.tenantId)
            string(value.hostId)
            string(value.deviceId)
            string(value.direction)
            string(value.kind)
            string(value.messageId)
            uint64(value.sequence)
            int64(value.createdAt)
            string(value.payloadEncoding)
            bytes(value.nonce)
        }
    }

    fun pairingCodeTranscript(claimPlaintext: ByteArray): ByteArray {
        return canonical(PAIRING_CODE_DOMAIN) {
            bytes(claimPlaintext)
        }
    }

    private fun canonical(domain: String, fields: CanonicalWriter.() -> Unit): ByteArray {
        return CanonicalWriter().apply {
            string(domain)
            fields()
        }.toByteArray()
    }

    private fun encodedField(value: ByteArray): ByteArray {
        return CanonicalWriter().apply { bytes(value) }.toByteArray()
    }

    private class CanonicalWriter {
        private val output = ByteArrayOutputStream()
        private val data = DataOutputStream(output)

        fun string(value: String) {
            bytes(value.toByteArray(Charsets.UTF_8))
        }

        fun bytes(value: ByteArray) {
            data.writeInt(value.size)
            data.write(value)
        }

        fun int64(value: Long) {
            data.writeLong(value)
        }

        fun uint64(value: ULong) {
            data.writeLong(value.toLong())
        }

        fun toByteArray(): ByteArray = output.toByteArray()
    }

    private const val PAIRING_PACKAGE_DOMAIN = "MYCODEX-PAIRING-PACKAGE-V1"
    private const val PAIRING_CLAIM_DOMAIN = "MYCODEX-PAIRING-CLAIM-V1"
    private const val PAIRING_APPROVAL_DOMAIN = "MYCODEX-PAIRING-APPROVAL-V1"
    private const val PAIRING_CODE_DOMAIN = "MYCODEX-PAIRING-CODE-V1"
    private const val RELAY_CHALLENGE_DOMAIN = "MYCODEX-RELAY-CHALLENGE-V1"
    private const val RELAY_PROOF_DOMAIN = "MYCODEX-RELAY-PROOF-V1"
    private const val SESSION_HELLO_DOMAIN = "MYCODEX-SESSION-HELLO-V1"
    private const val SESSION_CONFIRM_DOMAIN = "MYCODEX-SESSION-CONFIRM-V1"
    private const val ENVELOPE_AAD_DOMAIN = "MYCODEX-ENVELOPE-AAD-V1"
}
