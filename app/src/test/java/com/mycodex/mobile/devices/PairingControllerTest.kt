package com.mycodex.mobile.devices

import com.mycodex.mobile.security.PairingPackage
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingControllerTest {
    @Test
    fun claimRejectsNonCanonicalDeviceId() {
        val identity = vectorIdentity().copy(deviceId = "android_not-a-uuid")

        assertThrows(IllegalArgumentException::class.java) {
            PairingClaimFactory.create(
                imported = importedPackage(),
                identity = identity,
                claimId = id("claimId"),
                clientNonce = hexInput("clientNonceHex"),
                claimNonce = hexInput("claimNonceHex"),
                createdAt = time("claimCreatedAt"),
                appVersion = string("appVersion"),
                sign = { vectorBytes("signatures", "devicePairingClaim") },
                deriveAgreement = { vectorBytes("pairing", "ecdhSharedSecret") },
            )
        }
    }

    @Test
    fun claimCiphertextMatchesSharedVector() {
        val vector = vector()

        val claim = PairingClaimFactory.create(
            imported = importedPackage(),
            identity = vectorIdentity(),
            claimId = id("claimId"),
            clientNonce = hexInput("clientNonceHex"),
            claimNonce = hexInput("claimNonceHex"),
            createdAt = time("claimCreatedAt"),
            appVersion = string("appVersion"),
            sign = { vectorBytes("signatures", "devicePairingClaim") },
            deriveAgreement = { vectorBytes("pairing", "ecdhSharedSecret") },
        )

        assertArrayEquals(
            vectorBytes("pairing", "claimCiphertext"),
            claim.submission.ciphertext,
        )
        assertEquals(vector.getJSONObject("pairing").getString("pairingCode"), claim.shortCode)
    }

    @Test
    fun pollingStopsOnEveryNonApprovedTerminalState() {
        listOf("rejected", "expired", "cancelled", "consumed").forEach { terminal ->
            val relay = FakeRelayPairingClient(
                ArrayDeque(listOf(status(terminal))),
            )
            val saved = mutableListOf<DeviceBinding>()
            val controller = controller(relay, saved)

            runBlocking { controller.importAndSubmit(pairingUri()) }

            assertEquals(1, relay.getClaimCalls)
            assertEquals(PairingUiState.Failed("pairing_$terminal"), controller.state.value)
            assertTrue(saved.isEmpty())
        }
    }

    @Test
    fun pendingPollsImmediatelyThenEveryTwoSecondsUntilTerminal() {
        val relay = FakeRelayPairingClient(
            ArrayDeque(listOf(status("pending"), status("pending"), status("rejected"))),
        )
        val delays = mutableListOf<Long>()
        val controller = controller(relay, mutableListOf(), pollDelay = { delays.add(it) })

        runBlocking { controller.importAndSubmit(pairingUri()) }

        assertEquals(3, relay.getClaimCalls)
        assertEquals(listOf(2_000L, 2_000L), delays)
        assertEquals(PairingUiState.Failed("pairing_rejected"), controller.state.value)
    }

    @Test
    fun waitingStateContainsSharedShortCodeBeforeSubmitReturns() {
        lateinit var controller: PairingController
        val relay = FakeRelayPairingClient(ArrayDeque(listOf(status("rejected")))).apply {
            onSubmit = {
                assertEquals(
                    PairingUiState.Waiting(
                        target = string("hostName"),
                        shortCode = vector().getJSONObject("pairing").getString("pairingCode"),
                    ),
                    controller.state.value,
                )
            }
        }
        controller = controller(relay, mutableListOf())

        runBlocking { controller.importAndSubmit(pairingUri()) }

        assertEquals(1, relay.submitCalls)
    }

    @Test
    fun deviceIdentityFailureIsReportedWithoutContactingRelay() {
        val relay = FakeRelayPairingClient(ArrayDeque())
        val controller = controller(
            relay = relay,
            saved = mutableListOf(),
            identityProvider = {
                throw DeviceIdentityException(DeviceIdentityException.SECURITY_UNAVAILABLE)
            },
        )

        runBlocking { controller.importAndSubmit(pairingUri()) }

        assertEquals(
            PairingUiState.Failed(DeviceIdentityException.SECURITY_UNAVAILABLE),
            controller.state.value,
        )
        assertEquals(0, relay.submitCalls)
    }

    @Test
    fun approvalIsPersistedOnlyAfterTagAndHostSignatureVerify() {
        val validSaved = mutableListOf<DeviceBinding>()
        val validController = controller(
            FakeRelayPairingClient(ArrayDeque(listOf(approvedStatus()))),
            validSaved,
        )

        runBlocking { validController.importAndSubmit(pairingUri()) }

        assertEquals(1, validSaved.size)
        assertEquals(PairingUiState.Succeeded(validSaved.single()), validController.state.value)
        assertEquals(id("hostId"), validSaved.single().hostId)
        assertEquals(publicKey("hostAgreement"), validSaved.single().hostAgreementPublicKey)

        val changedTag = vectorBytes("pairing", "approvalCiphertext").also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val tagSaved = mutableListOf<DeviceBinding>()
        val tagController = controller(
            FakeRelayPairingClient(
                ArrayDeque(listOf(approvedStatus(approvalCiphertext = changedTag))),
            ),
            tagSaved,
        )

        runBlocking { tagController.importAndSubmit(pairingUri()) }

        assertTrue(tagSaved.isEmpty())
        assertEquals(PairingUiState.Failed("pairing_verification_failed"), tagController.state.value)

        val forgedPlaintext = vectorBytes("pairing", "approvalPlaintext").also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val forgedCiphertext = SecureRemoteCrypto.encryptAesGcm(
            key = vectorBytes("pairing", "approvalKey"),
            nonce = hexInput("approvalNonceHex"),
            plaintext = forgedPlaintext,
            aad = vectorBytes("transcripts", "approvalHeader"),
        )
        val signatureSaved = mutableListOf<DeviceBinding>()
        val signatureController = controller(
            FakeRelayPairingClient(
                ArrayDeque(listOf(approvedStatus(approvalCiphertext = forgedCiphertext))),
            ),
            signatureSaved,
        )

        runBlocking { signatureController.importAndSubmit(pairingUri()) }

        assertTrue(signatureSaved.isEmpty())
        assertEquals(
            PairingUiState.Failed("pairing_verification_failed"),
            signatureController.state.value,
        )
    }

    @Test
    fun processRecreationDoesNotPersistInviteSecretOrClaimToken() {
        val relay = FakeRelayPairingClient(ArrayDeque())
        val recreated = controller(relay, mutableListOf())

        runBlocking { recreated.resumePolling() }

        assertEquals(PairingUiState.Idle, recreated.state.value)
        assertEquals(0, relay.getClaimCalls)
        assertEquals(0, relay.cancelCalls)
    }

    @Test
    fun terminalErrorRemainsUntilRetryCancelOrNewImport() {
        val relay = FakeRelayPairingClient(ArrayDeque(listOf(status("rejected"))))
        val controller = controller(relay, mutableListOf())

        runBlocking { controller.importAndSubmit(pairingUri()) }
        runBlocking { controller.resumePolling() }

        assertEquals(PairingUiState.Failed("pairing_rejected"), controller.state.value)
        assertEquals(1, relay.getClaimCalls)

        runBlocking { controller.cancel() }

        assertEquals(PairingUiState.Idle, controller.state.value)
    }

    private fun controller(
        relay: FakeRelayPairingClient,
        saved: MutableList<DeviceBinding>,
        pollDelay: suspend (Long) -> Unit = {},
        identityProvider: () -> LocalDeviceIdentity = { vectorIdentity() },
    ): PairingController {
        val randomValues = ArrayDeque(
            listOf(
                hexInput("clientNonceHex"),
                hexInput("claimNonceHex"),
            ),
        )
        return PairingController(
            identityProvider = identityProvider,
            sign = { vectorBytes("signatures", "devicePairingClaim") },
            deriveAgreement = { vectorBytes("pairing", "ecdhSharedSecret") },
            relayClient = relay,
            persistBinding = { saved.add(it) },
            clock = { time("claimCreatedAt") },
            claimIdFactory = { id("claimId") },
            randomBytes = { size ->
                randomValues.removeFirst().also { require(it.size == size) }
            },
            appVersion = string("appVersion"),
            pollDelay = pollDelay,
        )
    }

    private fun approvedStatus(
        approvalCiphertext: ByteArray = vectorBytes("pairing", "approvalCiphertext"),
    ): PairingClaimStatus {
        return PairingClaimStatus(
            claimId = id("claimId"),
            status = "approved",
            updatedAt = time("approvedAt"),
            approvalHeader = vectorBytes("transcripts", "approvalHeader"),
            approvalNonce = hexInput("approvalNonceHex"),
            approvalCiphertext = approvalCiphertext,
        )
    }

    private fun status(value: String): PairingClaimStatus {
        return PairingClaimStatus(
            claimId = id("claimId"),
            status = value,
            updatedAt = time("claimCreatedAt"),
            approvalHeader = null,
            approvalNonce = null,
            approvalCiphertext = null,
        )
    }

    private fun importedPackage(): ImportedPairingPackage {
        val vector = vector()
        val inputs = vector.getJSONObject("inputs")
        val times = inputs.getJSONObject("unixMilliseconds")
        return ImportedPairingPackage(
            pairingPackage = PairingPackage(
                protocolVersion = 1,
                relayUrl = string("relayUrl"),
                relaySigningPublicKey = vectorBytes("publicKeys", "relaySigning"),
                relaySigningKeyFingerprint = vectorBytes("publicKeys", "relayFingerprintSha256"),
                embeddedTlsCertificateSha256 = byteArrayOf(),
                tenantId = id("tenantId"),
                hostId = id("hostId"),
                hostName = string("hostName"),
                inviteId = id("inviteId"),
                inviteSecret = hexInput("inviteSecretHex"),
                expiresAt = times.getLong("inviteExpiresAt"),
                hostSigningPublicKey = vectorBytes("publicKeys", "hostSigning"),
                hostAgreementPublicKey = vectorBytes("publicKeys", "hostAgreement"),
                hostKeyVersion = 1,
            ),
            hostSignature = vectorBytes("signatures", "hostPairingPackage"),
            originalUri = pairingUri(),
        )
    }

    private fun pairingUri(): String {
        val imported = importedPackageWithoutUri()
        val value = imported.pairingPackage
        val relay = JSONObject()
            .put("url", value.relayUrl)
            .put("signingPublicKey", SecureRemoteProtocol.encodeBase64Url(value.relaySigningPublicKey))
            .put(
                "signingKeyFingerprint",
                SecureRemoteProtocol.encodeBase64Url(value.relaySigningKeyFingerprint),
            )
            .put("embeddedTlsCertificateSha256", "")
        val root = JSONObject()
            .put("version", value.protocolVersion)
            .put("relay", relay)
            .put("tenantId", value.tenantId)
            .put("hostId", value.hostId)
            .put("hostName", value.hostName)
            .put("inviteId", value.inviteId)
            .put("inviteSecret", SecureRemoteProtocol.encodeBase64Url(value.inviteSecret))
            .put("expiresAt", value.expiresAt)
            .put(
                "hostSigningPublicKey",
                SecureRemoteProtocol.encodeBase64Url(value.hostSigningPublicKey),
            )
            .put(
                "hostAgreementPublicKey",
                SecureRemoteProtocol.encodeBase64Url(value.hostAgreementPublicKey),
            )
            .put("hostKeyVersion", value.hostKeyVersion)
            .put("hostSignature", SecureRemoteProtocol.encodeBase64Url(imported.hostSignature))
        return "mycodex://pair?data=" + SecureRemoteProtocol.encodeBase64Url(
            root.toString().toByteArray(Charsets.UTF_8),
        )
    }

    private fun importedPackageWithoutUri(): ImportedPairingPackage {
        val vector = vector()
        val inputs = vector.getJSONObject("inputs")
        return ImportedPairingPackage(
            pairingPackage = PairingPackage(
                protocolVersion = 1,
                relayUrl = string("relayUrl"),
                relaySigningPublicKey = vectorBytes("publicKeys", "relaySigning"),
                relaySigningKeyFingerprint = vectorBytes("publicKeys", "relayFingerprintSha256"),
                embeddedTlsCertificateSha256 = byteArrayOf(),
                tenantId = id("tenantId"),
                hostId = id("hostId"),
                hostName = string("hostName"),
                inviteId = id("inviteId"),
                inviteSecret = hexInput("inviteSecretHex"),
                expiresAt = inputs.getJSONObject("unixMilliseconds").getLong("inviteExpiresAt"),
                hostSigningPublicKey = vectorBytes("publicKeys", "hostSigning"),
                hostAgreementPublicKey = vectorBytes("publicKeys", "hostAgreement"),
                hostKeyVersion = 1,
            ),
            hostSignature = vectorBytes("signatures", "hostPairingPackage"),
            originalUri = "",
        )
    }

    private fun vectorIdentity(): LocalDeviceIdentity {
        return LocalDeviceIdentity(
            deviceId = id("deviceId"),
            displayName = "Demo Android Device",
            keyVersion = 1,
            signingPublicKey = publicKey("deviceSigning"),
            agreementPublicKey = publicKey("deviceAgreement"),
        )
    }

    private fun vector(): JSONObject {
        val stream = requireNotNull(javaClass.getResourceAsStream("/protocol/secure-remote-v1.json"))
        return stream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }

    private fun id(name: String): String {
        return vector().getJSONObject("inputs").getJSONObject("ids").getString(name)
    }

    private fun string(name: String): String {
        return vector().getJSONObject("inputs").getJSONObject("strings").getString(name)
    }

    private fun time(name: String): Long {
        return vector().getJSONObject("inputs").getJSONObject("unixMilliseconds").getLong(name)
    }

    private fun publicKey(name: String): String {
        return vector().getJSONObject("publicKeys").getString(name)
    }

    private fun hexInput(name: String): ByteArray {
        val value = vector().getJSONObject("inputs").getString(name)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun vectorBytes(section: String, name: String): ByteArray {
        return SecureRemoteProtocol.decodeBase64Url(vector().getJSONObject(section).getString(name))
    }

    private class FakeRelayPairingClient(
        private val statuses: ArrayDeque<PairingClaimStatus>,
    ) : RelayPairingClient(OkHttpClient()) {
        var getClaimCalls = 0
        var cancelCalls = 0
        var submitCalls = 0
        var onSubmit: (() -> Unit)? = null

        override suspend fun submitClaim(
            relayUrl: String,
            certificateSha256: String?,
            submission: PairingClaimSubmission,
        ): PairingClaimCreated {
            submitCalls++
            onSubmit?.invoke()
            return PairingClaimCreated(
                claimId = submission.header.claimId,
                claimAccessToken = "claim-access-secret",
                status = "pending",
                expiresAt = 1_893_456_600_000L,
            )
        }

        override suspend fun getClaim(
            relayUrl: String,
            certificateSha256: String?,
            claimId: String,
            claimAccessToken: String,
        ): PairingClaimStatus {
            getClaimCalls++
            return statuses.removeFirst()
        }

        override suspend fun cancelClaim(
            relayUrl: String,
            certificateSha256: String?,
            claimId: String,
            claimAccessToken: String,
        ) {
            cancelCalls++
        }
    }
}
