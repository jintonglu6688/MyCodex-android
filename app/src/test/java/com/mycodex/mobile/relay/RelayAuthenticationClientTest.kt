package com.mycodex.mobile.relay

import com.mycodex.mobile.devices.DeviceBinding
import com.mycodex.mobile.devices.TestHttpsRelayServer
import com.mycodex.mobile.security.RelayChallenge
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.SecureRemoteTranscript
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class RelayAuthenticationClientTest {
    @Test
    fun deviceProofAllowsBoundedClientClockSkew() {
        TestHttpsRelayServer().use { server ->
            val relayKey = keyPair()
            val deviceKey = keyPair()
            val relayPublic = SecureRemoteCrypto.encodeP256PublicKey(relayKey.public)
            val relayFingerprint = SecureRemoteCrypto.sha256(relayPublic)
            val now = 1_893_456_000_000L
            val clientNow = now - 250L
            val challenge = RelayChallenge(
                protocolVersion = 1,
                relaySigningKeyFingerprint = relayFingerprint,
                challengeId = "challenge-1",
                challengeValue = ByteArray(32) { 3 },
                subjectType = "device",
                subjectId = DEVICE_ID,
                tenantId = TENANT_ID,
                hostId = HOST_ID,
                deviceId = DEVICE_ID,
                purpose = "websocket_device",
                issuedAt = now,
                expiresAt = now + 60_000,
            )
            val challengeSignature = SecureRemoteCrypto.signRawEcdsa(
                relayKey.private,
                SecureRemoteTranscript.relayChallenge(challenge),
            )
            server.authChallengeResponse = challengeJson(challenge, challengeSignature).toString()
            val expectedTicket = SecureRemoteProtocol.encodeBase64Url(ByteArray(32) { 9 })
            server.authTicketResponse =
                """{"ticket":"$expectedTicket","purpose":"websocket_device","expiresAt":${now + 60_000}}"""
            val binding = binding(
                server,
                relayPublic,
                relayFingerprint,
                SecureRemoteCrypto.encodeP256PublicKey(deviceKey.public),
            )

            val result = runBlocking {
                RelayAuthenticationClient(
                    signer = { SecureRemoteCrypto.signRawEcdsa(deviceKey.private, it) },
                    nowMilliseconds = { clientNow },
                ).ticket(binding)
            }

            assertEquals(expectedTicket, result)
            assertEquals(2, server.requests.size)
            val challengeRequest = JSONObject(server.requests[0].body)
            assertEquals("device", challengeRequest.getString("subjectType"))
            assertEquals(DEVICE_ID, challengeRequest.getString("subjectId"))
            assertEquals("websocket_device", challengeRequest.getString("purpose"))
            val proof = JSONObject(server.requests[1].body)
            val proofSignature = SecureRemoteProtocol.decodeBase64Url(
                proof.getString("subjectSignature"),
            )
            assertTrue(
                SecureRemoteCrypto.verifyRawEcdsa(
                    SecureRemoteProtocol.decodeBase64Url(binding.deviceSigningPublicKey),
                    SecureRemoteTranscript.relayProof(
                        SecureRemoteTranscript.relayChallenge(challenge),
                        challengeSignature,
                        "device",
                        DEVICE_ID,
                    ),
                    proofSignature,
                ),
            )
            assertFalse(server.requests.any { it.authorization != null })
        }
    }

    @Test
    fun challengeIssuedBeyondClockSkewIsRejected() {
        TestHttpsRelayServer().use { server ->
            val relayKey = keyPair()
            val deviceKey = keyPair()
            val relayPublic = SecureRemoteCrypto.encodeP256PublicKey(relayKey.public)
            val relayFingerprint = SecureRemoteCrypto.sha256(relayPublic)
            val clientNow = 1_893_456_000_000L
            val challenge = RelayChallenge(
                protocolVersion = 1,
                relaySigningKeyFingerprint = relayFingerprint,
                challengeId = "challenge-1",
                challengeValue = ByteArray(32) { 3 },
                subjectType = "device",
                subjectId = DEVICE_ID,
                tenantId = TENANT_ID,
                hostId = HOST_ID,
                deviceId = DEVICE_ID,
                purpose = "websocket_device",
                issuedAt = clientNow + 5_001L,
                expiresAt = clientNow + 65_001L,
            )
            server.authChallengeResponse = challengeJson(
                challenge,
                SecureRemoteCrypto.signRawEcdsa(
                    relayKey.private,
                    SecureRemoteTranscript.relayChallenge(challenge),
                ),
            ).toString()
            val binding = binding(
                server,
                relayPublic,
                relayFingerprint,
                SecureRemoteCrypto.encodeP256PublicKey(deviceKey.public),
            )

            val error = assertThrows(RelayAuthenticationException::class.java) {
                runBlocking {
                    RelayAuthenticationClient(
                        signer = { SecureRemoteCrypto.signRawEcdsa(deviceKey.private, it) },
                        nowMilliseconds = { clientNow },
                    ).ticket(binding)
                }
            }

            assertEquals("invalid_challenge", error.code)
            assertEquals(1, server.requests.size)
        }
    }

    private fun challengeJson(challenge: RelayChallenge, signature: ByteArray): JSONObject {
        return JSONObject()
            .put("protocolVersion", challenge.protocolVersion)
            .put(
                "relayFingerprint",
                SecureRemoteProtocol.encodeBase64Url(challenge.relaySigningKeyFingerprint),
            )
            .put("challengeId", challenge.challengeId)
            .put("challenge", SecureRemoteProtocol.encodeBase64Url(challenge.challengeValue))
            .put("subjectType", challenge.subjectType)
            .put("subjectId", challenge.subjectId)
            .put("tenantId", challenge.tenantId)
            .put("hostId", challenge.hostId)
            .put("deviceId", challenge.deviceId)
            .put("purpose", challenge.purpose)
            .put("issuedAt", challenge.issuedAt)
            .put("expiresAt", challenge.expiresAt)
            .put("relaySignature", SecureRemoteProtocol.encodeBase64Url(signature))
    }

    private fun binding(
        server: TestHttpsRelayServer,
        relayPublic: ByteArray,
        relayFingerprint: ByteArray,
        deviceSigningPublic: ByteArray,
    ): DeviceBinding {
        return DeviceBinding(
            relayUrl = server.baseUrl,
            relaySigningPublicKey = SecureRemoteProtocol.encodeBase64Url(relayPublic),
            relaySigningKeyFingerprint = SecureRemoteProtocol.encodeBase64Url(relayFingerprint),
            certificateSha256 = server.certificateSha256,
            tenantId = TENANT_ID,
            hostId = HOST_ID,
            hostName = "Windows",
            deviceId = DEVICE_ID,
            hostSigningPublicKey = SecureRemoteProtocol.encodeBase64Url(relayPublic),
            hostAgreementPublicKey = SecureRemoteProtocol.encodeBase64Url(relayPublic),
            deviceSigningPublicKey = SecureRemoteProtocol.encodeBase64Url(deviceSigningPublic),
            deviceAgreementPublicKey = SecureRemoteProtocol.encodeBase64Url(deviceSigningPublic),
            hostKeyVersion = 1,
            deviceKeyVersion = 1,
            bindingVersion = 1,
        )
    }

    private fun keyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private companion object {
        const val TENANT_ID = "tenant-a"
        const val HOST_ID = "host-a"
        const val DEVICE_ID = "device-a"
    }
}
