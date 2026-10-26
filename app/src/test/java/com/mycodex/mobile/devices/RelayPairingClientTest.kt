package com.mycodex.mobile.devices

import com.mycodex.mobile.security.PairingClaimHeader
import com.mycodex.mobile.security.SecureRemoteProtocol
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPairingClientTest {
    @Test
    fun submitClaimSendsLockedOpaqueDtoToClaimsEndpoint() {
        TestHttpsRelayServer().use { server ->
            val submission = submission()

            val created = runBlocking {
                RelayPairingClient().submitClaim(
                    relayUrl = server.baseUrl,
                    certificateSha256 = server.certificateSha256,
                    submission = submission,
                )
            }

            assertEquals(CLAIM_ID, created.claimId)
            assertEquals(CLAIM_ACCESS_TOKEN, created.claimAccessToken)
            assertEquals("pending", created.status)
            val request = server.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/v1/pairing/claims", request.path)
            assertNull(request.authorization)
            val body = JSONObject(request.body)
            assertEquals(
                setOf("header", "nonce", "ciphertext"),
                body.keys().asSequence().toSet(),
            )
            val header = body.getJSONObject("header")
            assertEquals(
                setOf(
                    "inviteId",
                    "tenantId",
                    "hostId",
                    "claimId",
                    "deviceId",
                    "deviceAgreementPublicKey",
                    "deviceKeyVersion",
                    "clientNonce",
                    "ciphertextLength",
                ),
                header.keys().asSequence().toSet(),
            )
            assertEquals(CLAIM_ID, header.getString("claimId"))
            assertEquals(
                SecureRemoteProtocol.encodeBase64Url(submission.ciphertext),
                body.getString("ciphertext"),
            )
        }
    }

    @Test
    fun claimAccessTokenIsSentOnlyAsAuthorizationHeader() {
        TestHttpsRelayServer().use { server ->
            val token = CLAIM_ACCESS_TOKEN
            server.claimStatusResponses.add(
                """{"claimId":"$CLAIM_ID","status":"pending","updatedAt":1893456002000,"approvalHeader":null,"approvalNonce":"","approvalCiphertext":""}""",
            )

            val status = runBlocking {
                RelayPairingClient().getClaim(
                    relayUrl = server.baseUrl,
                    certificateSha256 = server.certificateSha256,
                    claimId = CLAIM_ID,
                    claimAccessToken = token,
                )
            }

            assertEquals("pending", status.status)
            val request = server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/v1/pairing/claims/$CLAIM_ID", request.path)
            assertNull(request.query)
            assertEquals("Bearer $token", request.authorization)
            assertFalse(request.body.contains(token))
            assertFalse(request.path.contains(token))
        }
    }

    @Test
    fun cancelClaimUsesAuthorizationHeaderAndLockedPath() {
        TestHttpsRelayServer().use { server ->
            runBlocking {
                RelayPairingClient().cancelClaim(
                    relayUrl = server.baseUrl,
                    certificateSha256 = server.certificateSha256,
                    claimId = CLAIM_ID,
                    claimAccessToken = CLAIM_ACCESS_TOKEN,
                )
            }

            val request = server.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/v1/pairing/claims/$CLAIM_ID/cancel", request.path)
            assertEquals("Bearer $CLAIM_ACCESS_TOKEN", request.authorization)
            assertEquals("{}", request.body)
        }
    }

    @Test
    fun submitWithMismatchedPinDoesNotSendOpaqueClaim() {
        TestHttpsRelayServer().use { server ->
            val mismatch = ByteArray(32) { 7 }

            assertThrows(RelayPairingException::class.java) {
                runBlocking {
                    RelayPairingClient().submitClaim(
                        relayUrl = server.baseUrl,
                        certificateSha256 = RelayCertificatePinning.sha256Hex(mismatch),
                        submission = submission(),
                    )
                }
            }
            assertTrue(server.requests.isEmpty())
        }
    }

    @Test
    fun submitRejectsClaimAccessTokenThatIsNotCanonicalBase64Url32Bytes() {
        TestHttpsRelayServer().use { server ->
            server.claimCreatedResponse =
                """{"claimId":"$CLAIM_ID","claimAccessToken":"not-a-token","status":"pending","expiresAt":1893456600000}"""

            val error = assertThrows(RelayPairingException::class.java) {
                runBlocking {
                    RelayPairingClient().submitClaim(
                        server.baseUrl,
                        server.certificateSha256,
                        submission(),
                    )
                }
            }

            assertEquals("invalid_response", error.code)
            assertFalse(error.message.orEmpty().contains("not-a-token"))
        }
    }

    @Test
    fun terminalApprovalFieldsAreAllPresentOnlyForApproved() {
        TestHttpsRelayServer().use { server ->
            server.claimStatusResponses.add(
                """{"claimId":"$CLAIM_ID","status":"pending","updatedAt":1,"approvalHeader":"AA","approvalNonce":"","approvalCiphertext":""}""",
            )
            assertThrows(RelayPairingException::class.java) {
                runBlocking {
                    RelayPairingClient().getClaim(
                        server.baseUrl,
                        server.certificateSha256,
                        CLAIM_ID,
                        CLAIM_ACCESS_TOKEN,
                    )
                }
            }

            server.claimStatusResponses.add(
                """{"claimId":"$CLAIM_ID","status":"approved","updatedAt":1,"approvalHeader":"","approvalNonce":"","approvalCiphertext":""}""",
            )
            assertThrows(RelayPairingException::class.java) {
                runBlocking {
                    RelayPairingClient().getClaim(
                        server.baseUrl,
                        server.certificateSha256,
                        CLAIM_ID,
                        CLAIM_ACCESS_TOKEN,
                    )
                }
            }
        }
    }

    @Test
    fun invalidRelayUrlIsRejectedBeforeRequestConstructionWithoutEcho() {
        val secretUrl = "https://user:secret@example.com"

        val error = assertThrows(RelayPairingException::class.java) {
            runBlocking {
                RelayPairingClient().submitClaim(secretUrl, null, submission())
            }
        }

        assertEquals("invalid_relay_url", error.code)
        assertFalse(error.message.orEmpty().contains(secretUrl))
        assertFalse(error.message.orEmpty().contains("secret"))
    }

    private fun submission(): PairingClaimSubmission {
        val vector = vector()
        val inputs = vector.getJSONObject("inputs")
        val ids = inputs.getJSONObject("ids")
        val publicKeys = vector.getJSONObject("publicKeys")
        val header = PairingClaimHeader(
            protocolVersion = 1,
            inviteId = ids.getString("inviteId"),
            tenantId = ids.getString("tenantId"),
            hostId = ids.getString("hostId"),
            claimId = ids.getString("claimId"),
            deviceId = ids.getString("deviceId"),
            deviceAgreementPublicKey = SecureRemoteProtocol.decodeBase64Url(
                publicKeys.getString("deviceAgreement"),
            ),
            deviceKeyVersion = 1,
            clientNonce = hex(inputs.getString("clientNonceHex")),
            ciphertextLength = SecureRemoteProtocol.decodeBase64Url(
                vector.getJSONObject("pairing").getString("claimCiphertext"),
            ).size.toLong(),
        )
        return PairingClaimSubmission(
            header = header,
            nonce = hex(inputs.getString("claimNonceHex")),
            ciphertext = SecureRemoteProtocol.decodeBase64Url(
                vector.getJSONObject("pairing").getString("claimCiphertext"),
            ),
        )
    }

    private fun vector(): JSONObject {
        val stream = requireNotNull(javaClass.getResourceAsStream("/protocol/secure-remote-v1.json"))
        return stream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }

    private fun hex(value: String): ByteArray {
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val CLAIM_ID = "55555555-5555-5555-5555-555555555555"
        val CLAIM_ACCESS_TOKEN: String =
            SecureRemoteProtocol.encodeBase64Url(ByteArray(32) { 9 })
    }
}
