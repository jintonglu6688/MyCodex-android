package com.mycodex.mobile.devices

import com.mycodex.mobile.security.PairingApproval
import com.mycodex.mobile.security.PairingApprovalHeader
import com.mycodex.mobile.security.PairingClaim
import com.mycodex.mobile.security.PairingClaimHeader
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.SecureRemoteTranscript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

internal data class PendingPairingClaim(
    val submission: PairingClaimSubmission,
    val shortCode: String,
    val pairingBaseKey: ByteArray,
)

internal object PairingClaimFactory {
    fun create(
        imported: ImportedPairingPackage,
        identity: LocalDeviceIdentity,
        claimId: String,
        clientNonce: ByteArray,
        claimNonce: ByteArray,
        createdAt: Long,
        appVersion: String,
        sign: (ByteArray) -> ByteArray,
        deriveAgreement: (ByteArray) -> ByteArray,
    ): PendingPairingClaim {
        val packageValue = imported.pairingPackage
        require(UUID_PATTERN.matches(claimId))
        require(UUID_PATTERN.matches(identity.deviceId))
        require(clientNonce.size == CLIENT_NONCE_SIZE)
        require(claimNonce.size == GCM_NONCE_SIZE)
        require(appVersion.isNotBlank() && appVersion.length <= MAX_APP_VERSION_LENGTH)
        val deviceSigningPublicKey = SecureRemoteProtocol.decodeBase64Url(identity.signingPublicKey)
        val deviceAgreementPublicKey = SecureRemoteProtocol.decodeBase64Url(identity.agreementPublicKey)
        require(SecureRemoteCrypto.isValidP256PublicKey(deviceSigningPublicKey))
        require(SecureRemoteCrypto.isValidP256PublicKey(deviceAgreementPublicKey))
        val claim = PairingClaim(
            protocolVersion = SecureRemoteProtocol.VERSION.toLong(),
            claimId = claimId,
            inviteId = packageValue.inviteId,
            tenantId = packageValue.tenantId,
            hostId = packageValue.hostId,
            deviceId = identity.deviceId,
            deviceName = identity.displayName,
            platform = PLATFORM_ANDROID,
            appVersion = appVersion,
            deviceSigningPublicKey = deviceSigningPublicKey,
            deviceAgreementPublicKey = deviceAgreementPublicKey,
            deviceKeyVersion = identity.keyVersion.toLong(),
            clientNonce = clientNonce,
            createdAt = createdAt,
        )
        val withoutProof = SecureRemoteTranscript.pairingClaimWithoutProof(claim)
        val inviteProof = SecureRemoteCrypto.hmacSha256(packageValue.inviteSecret, withoutProof)
        val signatureTranscript = SecureRemoteTranscript.pairingClaimForSignature(claim, inviteProof)
        val deviceSignature = sign(signatureTranscript)
        require(deviceSignature.size == RAW_SIGNATURE_SIZE)
        val plaintext = SecureRemoteTranscript.pairingClaimPlaintext(
            claim,
            inviteProof,
            deviceSignature,
        )
        val header = PairingClaimHeader(
            protocolVersion = SecureRemoteProtocol.VERSION.toLong(),
            inviteId = packageValue.inviteId,
            tenantId = packageValue.tenantId,
            hostId = packageValue.hostId,
            claimId = claimId,
            deviceId = identity.deviceId,
            deviceAgreementPublicKey = deviceAgreementPublicKey,
            deviceKeyVersion = identity.keyVersion.toLong(),
            clientNonce = clientNonce,
            ciphertextLength = (plaintext.size + GCM_TAG_SIZE).toLong(),
        )
        val headerTranscript = SecureRemoteTranscript.visibleClaimHeader(header)
        val sharedSecret = deriveAgreement(packageValue.hostAgreementPublicKey)
        require(sharedSecret.size == SHARED_SECRET_SIZE)
        val pairingInfo = SecureRemoteCrypto.sha256(
            SecureRemoteTranscript.pairingPackage(packageValue) +
                encodedBytes(imported.hostSignature) +
                headerTranscript,
        )
        val pairingBaseKey = SecureRemoteCrypto.hkdfSha256(
            ikm = sharedSecret,
            salt = SecureRemoteCrypto.sha256(packageValue.inviteSecret),
            info = pairingInfo,
            length = KEY_SIZE,
        )
        val claimKey = SecureRemoteCrypto.hkdfSha256(
            pairingBaseKey,
            byteArrayOf(),
            CLAIM_ENCRYPTION_LABEL,
            KEY_SIZE,
        )
        return try {
            PendingPairingClaim(
                submission = PairingClaimSubmission(
                    header = header,
                    nonce = claimNonce,
                    ciphertext = SecureRemoteCrypto.encryptAesGcm(
                        claimKey,
                        claimNonce,
                        plaintext,
                        headerTranscript,
                    ),
                ),
                shortCode = SecureRemoteCrypto.pairingCode(plaintext),
                pairingBaseKey = pairingBaseKey,
            )
        } finally {
            sharedSecret.fill(0)
            claimKey.fill(0)
        }
    }

    private fun encodedBytes(value: ByteArray): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES + value.size)
            .putInt(value.size)
            .put(value)
            .array()
    }

    private const val CLIENT_NONCE_SIZE = 32
    private const val GCM_NONCE_SIZE = 12
    private const val GCM_TAG_SIZE = 16
    private const val RAW_SIGNATURE_SIZE = 64
    private const val SHARED_SECRET_SIZE = 32
    private const val KEY_SIZE = 32
    private const val MAX_APP_VERSION_LENGTH = 64
    private const val PLATFORM_ANDROID = "android"
    private val CLAIM_ENCRYPTION_LABEL = "claim encryption".toByteArray(Charsets.UTF_8)
    private val UUID_PATTERN = Regex(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    )
}

class PairingController internal constructor(
    private val identityProvider: () -> LocalDeviceIdentity,
    private val sign: (ByteArray) -> ByteArray,
    private val deriveAgreement: (ByteArray) -> ByteArray,
    private val relayClient: RelayPairingClient,
    private val persistBinding: (DeviceBinding) -> Unit,
    private val clock: () -> Long,
    private val claimIdFactory: () -> String,
    private val randomBytes: (Int) -> ByteArray,
    private val appVersion: String,
    private val pollDelay: suspend (Long) -> Unit,
) {
    constructor(
        identityStore: LocalDeviceIdentityStore,
        relayClient: RelayPairingClient,
        preferencesStore: DevicePreferencesStore,
        appVersion: String,
    ) : this(
        identityProvider = identityStore::load,
        sign = identityStore::sign,
        deriveAgreement = identityStore::deriveAgreement,
        relayClient = relayClient,
        persistBinding = preferencesStore::saveBinding,
        clock = System::currentTimeMillis,
        claimIdFactory = { UUID.randomUUID().toString() },
        randomBytes = { size -> ByteArray(size).also(RANDOM::nextBytes) },
        appVersion = appVersion,
        pollDelay = { delay(it) },
    )

    private val mutableState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val state: StateFlow<PairingUiState> = mutableState.asStateFlow()
    private var activeAttempt: ActiveAttempt? = null
    private var foreground = false
    private var polling = false

    suspend fun importAndSubmit(uri: String) {
        importAndSubmit(uri) {}
    }

    internal suspend fun importAndSubmit(uri: String, onImported: () -> Unit) {
        clearAttempt()
        foreground = true
        val imported = PairingPayloadParser.parseUri(uri, clock())
        if (imported == null) {
            mutableState.value = PairingUiState.Failed(ERROR_INVALID_PACKAGE)
            return
        }
        onImported()
        try {
            val identity = identityProvider()
            val claim = PairingClaimFactory.create(
                imported = imported,
                identity = identity,
                claimId = claimIdFactory(),
                clientNonce = randomBytes(CLIENT_NONCE_SIZE),
                claimNonce = randomBytes(GCM_NONCE_SIZE),
                createdAt = clock(),
                appVersion = appVersion,
                sign = sign,
                deriveAgreement = deriveAgreement,
            )
            val attempt = ActiveAttempt(imported, identity, claim)
            activeAttempt = attempt
            mutableState.value = PairingUiState.Waiting(
                target = imported.pairingPackage.hostName,
                shortCode = claim.shortCode,
            )
            val created = relayClient.submitClaim(
                relayUrl = imported.pairingPackage.relayUrl,
                certificateSha256 = certificateText(imported),
                submission = claim.submission,
            )
            require(created.claimId == claim.submission.header.claimId)
            require(created.status == STATUS_PENDING)
            require(created.expiresAt > clock())
            require(created.expiresAt <= imported.pairingPackage.expiresAt)
            attempt.claimAccessToken = created.claimAccessToken
            poll()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: DeviceIdentityException) {
            fail(ex.code)
        } catch (ex: RelayPairingException) {
            fail(ex.code)
        } catch (_: Exception) {
            fail(ERROR_VERIFICATION)
        }
    }

    suspend fun cancel() {
        foreground = false
        val attempt = activeAttempt
        try {
            val token = attempt?.claimAccessToken
            if (attempt != null && token != null) {
                relayClient.cancelClaim(
                    relayUrl = attempt.imported.pairingPackage.relayUrl,
                    certificateSha256 = certificateText(attempt.imported),
                    claimId = attempt.claim.submission.header.claimId,
                    claimAccessToken = token,
                )
            }
        } finally {
            clearAttempt()
            mutableState.value = PairingUiState.Idle
        }
    }

    fun pausePolling() {
        foreground = false
    }

    internal fun clearLocalState() {
        foreground = false
        clearAttempt()
        mutableState.value = PairingUiState.Idle
    }

    suspend fun resumePolling() {
        if (activeAttempt?.claimAccessToken == null) {
            return
        }
        foreground = true
        poll()
    }

    private suspend fun poll() {
        if (polling) {
            return
        }
        val attempt = activeAttempt ?: return
        val token = attempt.claimAccessToken ?: return
        polling = true
        var retryDelay = POLL_INTERVAL_MILLIS
        try {
            while (foreground && activeAttempt === attempt) {
                val status = try {
                    relayClient.getClaim(
                        relayUrl = attempt.imported.pairingPackage.relayUrl,
                        certificateSha256 = certificateText(attempt.imported),
                        claimId = attempt.claim.submission.header.claimId,
                        claimAccessToken = token,
                    )
                } catch (ex: RelayPairingException) {
                    if (ex.code != ERROR_NETWORK) {
                        throw ex
                    }
                    pollDelay(retryDelay)
                    retryDelay = minOf(retryDelay * 2, MAX_RETRY_MILLIS)
                    continue
                }
                require(status.claimId == attempt.claim.submission.header.claimId)
                retryDelay = POLL_INTERVAL_MILLIS
                when (status.status) {
                    STATUS_PENDING -> {
                        require(status.approvalHeader == null)
                        require(status.approvalNonce == null)
                        require(status.approvalCiphertext == null)
                        pollDelay(POLL_INTERVAL_MILLIS)
                    }
                    STATUS_APPROVED -> {
                        val binding = verifyApproval(attempt, status)
                        persistBinding(binding)
                        mutableState.value = PairingUiState.Succeeded(binding)
                        clearAttempt()
                    }
                    STATUS_REJECTED,
                    STATUS_EXPIRED,
                    STATUS_CANCELLED,
                    STATUS_CONSUMED,
                    -> {
                        require(status.approvalHeader == null)
                        require(status.approvalNonce == null)
                        require(status.approvalCiphertext == null)
                        fail("pairing_${status.status}")
                    }
                    else -> throw IllegalArgumentException("Unknown pairing status")
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: RelayPairingException) {
            fail(ex.code)
        } catch (_: Exception) {
            fail(ERROR_VERIFICATION)
        } finally {
            polling = false
        }
    }

    private fun verifyApproval(
        attempt: ActiveAttempt,
        status: PairingClaimStatus,
    ): DeviceBinding {
        val encodedHeader = requireNotNull(status.approvalHeader)
        val nonce = requireNotNull(status.approvalNonce)
        val ciphertext = requireNotNull(status.approvalCiphertext)
        val header = decodeApprovalHeader(encodedHeader)
        val packageValue = attempt.imported.pairingPackage
        val claimHeader = attempt.claim.submission.header
        require(header.protocolVersion == SecureRemoteProtocol.VERSION.toLong())
        require(header.claimId == claimHeader.claimId)
        require(header.inviteId == packageValue.inviteId)
        require(header.tenantId == packageValue.tenantId)
        require(header.hostId == packageValue.hostId)
        require(header.deviceId == attempt.identity.deviceId)
        require(header.status == STATUS_APPROVED)
        require(header.bindingVersion > 0)
        require(header.nonce.contentEquals(nonce))
        require(header.ciphertextLength == ciphertext.size.toLong())
        val approvalKey = SecureRemoteCrypto.hkdfSha256(
            attempt.claim.pairingBaseKey,
            byteArrayOf(),
            APPROVAL_ENCRYPTION_LABEL,
            KEY_SIZE,
        )
        val plaintext = try {
            SecureRemoteCrypto.decryptAesGcm(
                approvalKey,
                nonce,
                ciphertext,
                encodedHeader,
            )
        } finally {
            approvalKey.fill(0)
        }
        val decoded = decodeApprovalPlaintext(plaintext)
        val approval = decoded.approval
        require(approval.protocolVersion == SecureRemoteProtocol.VERSION.toLong())
        require(UUID_PATTERN.matches(approval.bindingId))
        require(approval.tenantId == packageValue.tenantId)
        require(approval.hostId == packageValue.hostId)
        require(approval.deviceId == attempt.identity.deviceId)
        require(equal(approval.hostSigningPublicKey, packageValue.hostSigningPublicKey))
        require(equal(approval.hostAgreementPublicKey, packageValue.hostAgreementPublicKey))
        require(approval.hostKeyVersion == packageValue.hostKeyVersion)
        require(
            equal(
                approval.deviceSigningPublicKey,
                SecureRemoteProtocol.decodeBase64Url(attempt.identity.signingPublicKey),
            ),
        )
        require(
            equal(
                approval.deviceAgreementPublicKey,
                SecureRemoteProtocol.decodeBase64Url(attempt.identity.agreementPublicKey),
            ),
        )
        require(approval.deviceKeyVersion == attempt.identity.keyVersion.toLong())
        require(equal(approval.relaySigningPublicKey, packageValue.relaySigningPublicKey))
        require(
            equal(
                approval.relaySigningKeyFingerprint,
                packageValue.relaySigningKeyFingerprint,
            ),
        )
        require(approval.bindingVersion == header.bindingVersion)
        require(approval.approvedAt > 0)
        require(
            SecureRemoteCrypto.verifyRawEcdsa(
                packageValue.hostSigningPublicKey,
                SecureRemoteTranscript.pairingApproval(approval),
                decoded.hostSignature,
            ),
        )
        return DeviceBinding(
            relayUrl = packageValue.relayUrl,
            relaySigningPublicKey = SecureRemoteProtocol.encodeBase64Url(
                packageValue.relaySigningPublicKey,
            ),
            relaySigningKeyFingerprint = SecureRemoteProtocol.encodeBase64Url(
                packageValue.relaySigningKeyFingerprint,
            ),
            certificateSha256 = packageValue.embeddedTlsCertificateSha256
                .takeIf { it.isNotEmpty() }
                ?.let(RelayCertificatePinning::sha256Hex),
            tenantId = packageValue.tenantId,
            hostId = packageValue.hostId,
            hostName = packageValue.hostName,
            deviceId = attempt.identity.deviceId,
            hostSigningPublicKey = SecureRemoteProtocol.encodeBase64Url(
                packageValue.hostSigningPublicKey,
            ),
            hostAgreementPublicKey = SecureRemoteProtocol.encodeBase64Url(
                packageValue.hostAgreementPublicKey,
            ),
            deviceSigningPublicKey = attempt.identity.signingPublicKey,
            deviceAgreementPublicKey = attempt.identity.agreementPublicKey,
            hostKeyVersion = exactInt(packageValue.hostKeyVersion),
            deviceKeyVersion = attempt.identity.keyVersion,
            bindingVersion = exactInt(approval.bindingVersion),
        )
    }

    private fun decodeApprovalHeader(encoded: ByteArray): PairingApprovalHeader {
        val reader = CanonicalReader(encoded)
        require(reader.string(MAX_DOMAIN_LENGTH) == APPROVAL_DOMAIN)
        val value = PairingApprovalHeader(
            protocolVersion = reader.long(),
            claimId = reader.string(MAX_ID_LENGTH),
            inviteId = reader.string(MAX_ID_LENGTH),
            tenantId = reader.string(MAX_ID_LENGTH),
            hostId = reader.string(MAX_ID_LENGTH),
            deviceId = reader.string(MAX_ID_LENGTH),
            status = reader.string(MAX_STATUS_LENGTH),
            bindingVersion = reader.long(),
            nonce = reader.bytes(GCM_NONCE_SIZE, GCM_NONCE_SIZE),
            ciphertextLength = reader.long(),
        )
        reader.requireEnd()
        require(SecureRemoteTranscript.approvalHeader(value).contentEquals(encoded))
        return value
    }

    private fun decodeApprovalPlaintext(encoded: ByteArray): DecodedApproval {
        val reader = CanonicalReader(encoded)
        require(reader.string(MAX_DOMAIN_LENGTH) == APPROVAL_DOMAIN)
        val approval = PairingApproval(
            protocolVersion = reader.long(),
            bindingId = reader.string(MAX_ID_LENGTH),
            tenantId = reader.string(MAX_ID_LENGTH),
            hostId = reader.string(MAX_ID_LENGTH),
            deviceId = reader.string(MAX_ID_LENGTH),
            hostSigningPublicKey = reader.bytes(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE),
            hostAgreementPublicKey = reader.bytes(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE),
            hostKeyVersion = reader.long(),
            deviceSigningPublicKey = reader.bytes(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE),
            deviceAgreementPublicKey = reader.bytes(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE),
            deviceKeyVersion = reader.long(),
            relaySigningPublicKey = reader.bytes(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE),
            relaySigningKeyFingerprint = reader.bytes(SHA256_SIZE, SHA256_SIZE),
            approvedAt = reader.long(),
            bindingVersion = reader.long(),
        )
        val hostSignature = reader.bytes(RAW_SIGNATURE_SIZE, RAW_SIGNATURE_SIZE)
        reader.requireEnd()
        require(
            SecureRemoteTranscript.pairingApprovalPlaintext(approval, hostSignature)
                .contentEquals(encoded),
        )
        return DecodedApproval(approval, hostSignature)
    }

    private fun fail(code: String) {
        mutableState.value = PairingUiState.Failed(code)
        clearAttempt()
    }

    private fun clearAttempt() {
        activeAttempt?.let { attempt ->
            attempt.imported.pairingPackage.inviteSecret.fill(0)
            attempt.claim.pairingBaseKey.fill(0)
        }
        activeAttempt = null
    }

    private fun certificateText(imported: ImportedPairingPackage): String? {
        return imported.pairingPackage.embeddedTlsCertificateSha256
            .takeIf { it.isNotEmpty() }
            ?.let(SecureRemoteProtocol::encodeBase64Url)
    }

    private fun exactInt(value: Long): Int {
        val result = value.toInt()
        require(result.toLong() == value)
        return result
    }

    private fun equal(left: ByteArray, right: ByteArray): Boolean {
        return MessageDigest.isEqual(left, right)
    }

    private data class ActiveAttempt(
        val imported: ImportedPairingPackage,
        val identity: LocalDeviceIdentity,
        val claim: PendingPairingClaim,
        var claimAccessToken: String? = null,
    )

    private data class DecodedApproval(
        val approval: PairingApproval,
        val hostSignature: ByteArray,
    )

    private class CanonicalReader(encoded: ByteArray) {
        private val input = DataInputStream(ByteArrayInputStream(encoded))

        fun string(maxLength: Int): String {
            val bytes = bytes(0, maxLength * 4)
            val value = bytes.toString(Charsets.UTF_8)
            require(value.toByteArray(Charsets.UTF_8).contentEquals(bytes))
            require(value.length <= maxLength)
            return value
        }

        fun bytes(minLength: Int, maxLength: Int): ByteArray {
            val length = input.readInt()
            require(length in minLength..maxLength)
            return ByteArray(length).also(input::readFully)
        }

        fun long(): Long = input.readLong()

        fun requireEnd() {
            require(input.read() == -1)
        }
    }

    private companion object {
        const val CLIENT_NONCE_SIZE = 32
        const val GCM_NONCE_SIZE = 12
        const val PUBLIC_KEY_SIZE = 65
        const val SHA256_SIZE = 32
        const val RAW_SIGNATURE_SIZE = 64
        const val KEY_SIZE = 32
        const val MAX_DOMAIN_LENGTH = 64
        const val MAX_ID_LENGTH = 128
        const val MAX_STATUS_LENGTH = 32
        const val POLL_INTERVAL_MILLIS = 2_000L
        const val MAX_RETRY_MILLIS = 30_000L
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_EXPIRED = "expired"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_CONSUMED = "consumed"
        const val ERROR_INVALID_PACKAGE = "invalid_pairing_package"
        const val ERROR_VERIFICATION = "pairing_verification_failed"
        const val ERROR_NETWORK = "network_error"
        const val APPROVAL_DOMAIN = "MYCODEX-PAIRING-APPROVAL-V1"
        val APPROVAL_ENCRYPTION_LABEL = "approval encryption".toByteArray(Charsets.UTF_8)
        val UUID_PATTERN = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )
        val RANDOM = SecureRandom()
    }
}
