package com.mycodex.mobile.devices

import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.PairingPackage
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteTranscript
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec

class PairingPayloadParserTest {
    @Test
    fun parserAcceptsOnlyMyCodexPairWithOneDataParameter() {
        val uri = pairingUri()

        val parsed = PairingPayloadParser.parseUri(uri, VECTOR_NOW)

        assertNotNull(parsed)
        assertEquals(uri, parsed?.originalUri)
        assertEquals("https://relay.example.com", parsed?.pairingPackage?.relayUrl)
        assertEquals(HOST_ID, parsed?.pairingPackage?.hostId)
        assertNull(PairingPayloadParser.parseUri(uri.replace("mycodex://", "https://"), VECTOR_NOW))
        assertNull(PairingPayloadParser.parseUri(uri.replace("mycodex://pair", "mycodex://other"), VECTOR_NOW))
        assertNull(PairingPayloadParser.parseUri("$uri&extra=value", VECTOR_NOW))
        assertNull(PairingPayloadParser.parseUri(uri.replace("?data=", "?data=one&data="), VECTOR_NOW))
    }

    @Test
    fun parserRejectsHttpExpiredWrongSignatureAndMalformedKeys() {
        assertNull(
            PairingPayloadParser.parseUri(
                pairingUri { root ->
                    root.getJSONObject("relay").put("url", "http://relay.example.com")
                },
                VECTOR_NOW,
            ),
        )
        assertNull(PairingPayloadParser.parseUri(pairingUri(), VECTOR_EXPIRES_AT))
        assertNull(
            PairingPayloadParser.parseUri(
                pairingUri { root -> root.put("hostSignature", "A".repeat(86)) },
                VECTOR_NOW,
            ),
        )
        assertNull(
            PairingPayloadParser.parseUri(
                pairingUri { root ->
                    root.put(
                        "hostAgreementPublicKey",
                        SecureRemoteProtocol.encodeBase64Url(byteArrayOf(4) + ByteArray(64)),
                    )
                },
                VECTOR_NOW,
            ),
        )
    }

    @Test
    fun parserRejectsOversizeDeepUnknownAndNonCanonicalPayloadsBeforeUse() {
        assertNull(PairingPayloadParser.parseUri("mycodex://pair?data=${"A".repeat(20_000)}", VECTOR_NOW))
        val deeplyNested = """{"value":${"[".repeat(40)}0${"]".repeat(40)}}"""
        assertNull(
            PairingPayloadParser.parseUri(
                "mycodex://pair?data=" + SecureRemoteProtocol.encodeBase64Url(
                    deeplyNested.toByteArray(Charsets.UTF_8),
                ),
                VECTOR_NOW,
            ),
        )
        assertNull(
            PairingPayloadParser.parseUri(
                pairingUri { root -> root.put("unexpected", true) },
                VECTOR_NOW,
            ),
        )
        assertNull(
            PairingPayloadParser.parseUri(
                pairingUri { root -> root.getJSONObject("relay").put("url", "https://relay.example.com/path") },
                VECTOR_NOW,
            ),
        )
        assertNull(PairingPayloadParser.parseUri(pairingUri() + "#fragment", VECTOR_NOW))
        assertNull(PairingPayloadParser.parseUri(pairingUri().replace("?data=", "?data=%41"), VECTOR_NOW))
    }

    @Test
    fun parserRejectsSameHostSigningAndAgreementKeyEvenWithValidSignature() {
        val uri = pairingUri(
            change = { root ->
                root.put("hostAgreementPublicKey", root.getString("hostSigningPublicKey"))
            },
            resign = true,
        )

        assertNull(PairingPayloadParser.parseUri(uri, VECTOR_NOW))
    }

    @Test
    fun clipboardClearRequiresUnchangedExactOriginalText() {
        val original = pairingUri()

        assertTrue(PairingPayloadParser.shouldClearClipboard(original, original, parsedSuccessfully = true))
        assertFalse(PairingPayloadParser.shouldClearClipboard(original, "$original ", parsedSuccessfully = true))
        assertFalse(PairingPayloadParser.shouldClearClipboard(original, original, parsedSuccessfully = false))
    }

    private fun pairingUri(
        resign: Boolean = false,
        change: (JSONObject) -> Unit = {},
    ): String {
        val vector = vector()
        val inputs = vector.getJSONObject("inputs")
        val strings = inputs.getJSONObject("strings")
        val ids = inputs.getJSONObject("ids")
        val publicKeys = vector.getJSONObject("publicKeys")
        val signatures = vector.getJSONObject("signatures")
        val relay = JSONObject()
            .put("url", strings.getString("relayUrl"))
            .put("signingPublicKey", publicKeys.getString("relaySigning"))
            .put("signingKeyFingerprint", publicKeys.getString("relayFingerprintSha256"))
            .put("embeddedTlsCertificateSha256", "")
        val root = JSONObject()
            .put("version", 1)
            .put("relay", relay)
            .put("tenantId", ids.getString("tenantId"))
            .put("hostId", ids.getString("hostId"))
            .put("hostName", strings.getString("hostName"))
            .put("inviteId", ids.getString("inviteId"))
            .put(
                "inviteSecret",
                SecureRemoteProtocol.encodeBase64Url(hex(inputs.getString("inviteSecretHex"))),
            )
            .put("expiresAt", inputs.getJSONObject("unixMilliseconds").getLong("inviteExpiresAt"))
            .put("hostSigningPublicKey", publicKeys.getString("hostSigning"))
            .put("hostAgreementPublicKey", publicKeys.getString("hostAgreement"))
            .put("hostKeyVersion", 1)
            .put("hostSignature", signatures.getString("hostPairingPackage"))
        change(root)
        if (resign) {
            root.put(
                "hostSignature",
                SecureRemoteProtocol.encodeBase64Url(
                    SecureRemoteCrypto.signRawEcdsa(
                        privateKey(BigInteger.valueOf(2)),
                        SecureRemoteTranscript.pairingPackage(packageFrom(root)),
                    ),
                ),
            )
        }
        val data = SecureRemoteProtocol.encodeBase64Url(root.toString().toByteArray(Charsets.UTF_8))
        return "mycodex://pair?data=$data"
    }

    private fun packageFrom(root: JSONObject): PairingPackage {
        val relay = root.getJSONObject("relay")
        return PairingPackage(
            protocolVersion = root.getLong("version"),
            relayUrl = relay.getString("url"),
            relaySigningPublicKey = SecureRemoteProtocol.decodeBase64Url(
                relay.getString("signingPublicKey"),
            ),
            relaySigningKeyFingerprint = SecureRemoteProtocol.decodeBase64Url(
                relay.getString("signingKeyFingerprint"),
            ),
            embeddedTlsCertificateSha256 = byteArrayOf(),
            tenantId = root.getString("tenantId"),
            hostId = root.getString("hostId"),
            hostName = root.getString("hostName"),
            inviteId = root.getString("inviteId"),
            inviteSecret = SecureRemoteProtocol.decodeBase64Url(root.getString("inviteSecret")),
            expiresAt = root.getLong("expiresAt"),
            hostSigningPublicKey = SecureRemoteProtocol.decodeBase64Url(
                root.getString("hostSigningPublicKey"),
            ),
            hostAgreementPublicKey = SecureRemoteProtocol.decodeBase64Url(
                root.getString("hostAgreementPublicKey"),
            ),
            hostKeyVersion = root.getLong("hostKeyVersion"),
        )
    }

    private fun privateKey(scalar: BigInteger) = KeyFactory.getInstance("EC").generatePrivate(
        ECPrivateKeySpec(
            scalar,
            AlgorithmParameters.getInstance("EC").run {
                init(ECGenParameterSpec("secp256r1"))
                getParameterSpec(ECParameterSpec::class.java)
            },
        ),
    )

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
        const val HOST_ID = "22222222-2222-2222-2222-222222222222"
        const val VECTOR_NOW = 1_893_456_000_000L
        const val VECTOR_EXPIRES_AT = 1_893_456_600_000L
    }
}
