package com.mycodex.mobile.security

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPrivateKeySpec
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureRemoteProtocolTest {
    private val vector = VectorFixture()

    @Test
    fun transcriptVectorUsesExactBytes() {
        val clientHello = SecureRemoteTranscript.sessionHello(vector.clientHello())
        val clientSignature = vector.signature("deviceSessionHello")
        val serverHelloUnsigned = SecureRemoteTranscript.sessionHello(vector.serverHello())
        val serverSignature = vector.signature("hostSessionHello")
        val serverHello = SecureRemoteTranscript.serverHello(
            clientHello = clientHello,
            clientSignature = clientSignature,
            serverHello = vector.serverHello(),
        )
        val sessionComplete = SecureRemoteTranscript.sessionComplete(
            serverHello = serverHello,
            serverSignature = serverSignature,
        )

        assertTranscript("pairingPackage", SecureRemoteTranscript.pairingPackage(vector.pairingPackage()))
        assertTranscript("visibleClaimHeader", SecureRemoteTranscript.visibleClaimHeader(vector.claimHeader()))
        assertTranscript(
            "pairingClaimWithoutProof",
            SecureRemoteTranscript.pairingClaimWithoutProof(vector.pairingClaim()),
        )
        assertTranscript(
            "pairingClaimForSignature",
            SecureRemoteTranscript.pairingClaimForSignature(
                vector.pairingClaim(),
                vector.pairing("inviteProof"),
            ),
        )
        assertArrayEquals(
            vector.pairing("claimPlaintext"),
            SecureRemoteTranscript.pairingClaimPlaintext(
                vector.pairingClaim(),
                vector.pairing("inviteProof"),
                vector.signature("devicePairingClaim"),
            ),
        )
        assertTranscript("pairingApproval", SecureRemoteTranscript.pairingApproval(vector.pairingApproval()))
        assertArrayEquals(
            vector.pairing("approvalPlaintext"),
            SecureRemoteTranscript.pairingApprovalPlaintext(
                vector.pairingApproval(),
                vector.signature("hostPairingApproval"),
            ),
        )
        assertTranscript("approvalHeader", SecureRemoteTranscript.approvalHeader(vector.approvalHeader()))
        val challenge = SecureRemoteTranscript.relayChallenge(vector.relayChallenge())
        assertTranscript("relayChallenge", challenge)
        assertTranscript(
            "relayProof",
            SecureRemoteTranscript.relayProof(
                challengeTranscript = challenge,
                relaySignature = vector.signature("relayChallenge"),
                subjectType = "device",
                subjectId = vector.id("deviceId"),
            ),
        )
        assertTranscript("sessionClientHello", clientHello)
        assertTranscript("sessionServerHelloUnsigned", serverHelloUnsigned)
        assertTranscript("sessionServerHello", serverHello)
        assertTranscript("sessionComplete", sessionComplete)
        assertTranscript(
            "sessionConfirmAndroid",
            SecureRemoteTranscript.sessionConfirmation(sessionComplete, "android"),
        )
        assertTranscript(
            "sessionConfirmWindows",
            SecureRemoteTranscript.sessionConfirmation(sessionComplete, "windows"),
        )
        assertTranscript("envelopeAadAndroidToWindows", SecureRemoteTranscript.envelopeAad(vector.envelope()))
    }

    @Test
    fun rawSignatureVectorVerifies() {
        val pairingPackage = SecureRemoteTranscript.pairingPackage(vector.pairingPackage())
        val pairingClaim = SecureRemoteTranscript.pairingClaimForSignature(
            vector.pairingClaim(),
            vector.pairing("inviteProof"),
        )
        val pairingApproval = SecureRemoteTranscript.pairingApproval(vector.pairingApproval())
        val challenge = SecureRemoteTranscript.relayChallenge(vector.relayChallenge())
        val proof = SecureRemoteTranscript.relayProof(
            challenge,
            vector.signature("relayChallenge"),
            "device",
            vector.id("deviceId"),
        )
        val clientHello = SecureRemoteTranscript.sessionHello(vector.clientHello())
        val serverHello = SecureRemoteTranscript.serverHello(
            clientHello,
            vector.signature("deviceSessionHello"),
            vector.serverHello(),
        )

        assertSignature("hostSigning", pairingPackage, "hostPairingPackage")
        assertSignature("deviceSigning", pairingClaim, "devicePairingClaim")
        assertSignature("hostSigning", pairingApproval, "hostPairingApproval")
        assertSignature("relaySigning", challenge, "relayChallenge")
        assertSignature("deviceSigning", proof, "deviceRelayProof")
        assertSignature("deviceSigning", clientHello, "deviceSessionHello")
        assertSignature("hostSigning", serverHello, "hostSessionHello")
        assertFalse(
            SecureRemoteCrypto.verifyRawEcdsa(
                vector.publicKey("hostSigning"),
                pairingPackage,
                vector.signature("hostPairingPackage").copyOf(63),
            ),
        )
    }

    @Test
    fun ecdhAndHkdfVectorMatches() {
        val pairingShared = SecureRemoteCrypto.deriveAgreement(
            privateKey(vector.privateScalar("deviceAgreement")),
            vector.publicKey("hostAgreement"),
        )
        assertArrayEquals(vector.pairing("ecdhSharedSecret"), pairingShared)

        val inviteSecret = vector.hex("inviteSecretHex")
        val pairingSalt = SecureRemoteCrypto.sha256(inviteSecret)
        assertArrayEquals(vector.pairing("saltSha256"), pairingSalt)
        assertArrayEquals(
            vector.pairing("inviteProof"),
            SecureRemoteCrypto.hmacSha256(
                inviteSecret,
                SecureRemoteTranscript.pairingClaimWithoutProof(vector.pairingClaim()),
            ),
        )
        val pairingPackage = SecureRemoteTranscript.pairingPackage(vector.pairingPackage())
        val visibleClaimHeader = SecureRemoteTranscript.visibleClaimHeader(vector.claimHeader())
        val pairingInfo = SecureRemoteCrypto.sha256(
            pairingPackage +
                field(vector.signature("hostPairingPackage")) +
                visibleClaimHeader,
        )
        assertArrayEquals(vector.pairing("infoSha256"), pairingInfo)
        val pairingBaseKey = SecureRemoteCrypto.hkdfSha256(
            ikm = pairingShared,
            salt = pairingSalt,
            info = pairingInfo,
            length = 32,
        )
        assertArrayEquals(vector.pairing("pairingBaseKey"), pairingBaseKey)
        assertArrayEquals(
            vector.pairing("claimKey"),
            SecureRemoteCrypto.hkdfSha256(
                pairingBaseKey,
                byteArrayOf(),
                "claim encryption".toByteArray(),
                32,
            ),
        )
        assertArrayEquals(
            vector.pairing("approvalKey"),
            SecureRemoteCrypto.hkdfSha256(
                pairingBaseKey,
                byteArrayOf(),
                "approval encryption".toByteArray(),
                32,
            ),
        )

        val sessionShared = SecureRemoteCrypto.deriveAgreement(
            privateKey(vector.privateScalar("sessionClientEphemeral")),
            vector.publicKey("sessionServerEphemeral"),
        )
        assertArrayEquals(vector.session("ecdhSharedSecret"), sessionShared)
        val sessionSalt = SecureRemoteCrypto.sha256(
            vector.hex("sessionClientNonceHex") + vector.hex("sessionServerNonceHex"),
        )
        assertArrayEquals(vector.session("saltSha256"), sessionSalt)
        val sessionComplete = vector.transcript("sessionComplete")
        val sessionMaster = SecureRemoteCrypto.hkdfSha256(
            sessionShared,
            sessionSalt,
            sessionComplete,
            32,
        )
        assertArrayEquals(vector.session("sessionMaster"), sessionMaster)
        assertDerived(sessionMaster, "android to windows key", 32, "androidToWindowsKey")
        assertDerived(sessionMaster, "windows to android key", 32, "windowsToAndroidKey")
        assertDerived(
            sessionMaster,
            "android to windows nonce prefix",
            4,
            "androidToWindowsNoncePrefix",
        )
        assertDerived(
            sessionMaster,
            "windows to android nonce prefix",
            4,
            "windowsToAndroidNoncePrefix",
        )
        assertDerived(sessionMaster, "session confirmation", 32, "confirmationKey")
        val confirmationKey = vector.session("confirmationKey")
        assertArrayEquals(
            vector.session("androidConfirmation"),
            SecureRemoteCrypto.hmacSha256(
                confirmationKey,
                vector.transcript("sessionConfirmAndroid"),
            ),
        )
        assertArrayEquals(
            vector.session("windowsConfirmation"),
            SecureRemoteCrypto.hmacSha256(
                confirmationKey,
                vector.transcript("sessionConfirmWindows"),
            ),
        )
    }

    @Test
    fun aesGcmVectorMatches() {
        assertEncryption(
            key = vector.pairing("claimKey"),
            nonce = vector.hex("claimNonceHex"),
            plaintext = vector.pairing("claimPlaintext"),
            aad = vector.transcript("visibleClaimHeader"),
            ciphertext = vector.pairing("claimCiphertext"),
        )
        assertEncryption(
            key = vector.pairing("approvalKey"),
            nonce = vector.hex("approvalNonceHex"),
            plaintext = vector.pairing("approvalPlaintext"),
            aad = vector.transcript("approvalHeader"),
            ciphertext = vector.pairing("approvalCiphertext"),
        )
        assertArrayEquals(
            vector.envelope("nonce"),
            SecureRemoteCrypto.envelopeNonce(
                vector.session("androidToWindowsNoncePrefix"),
                vector.envelopeSequence(),
            ),
        )
        assertEncryption(
            key = vector.session("androidToWindowsKey"),
            nonce = vector.envelope("nonce"),
            plaintext = vector.envelope("plaintext"),
            aad = vector.transcript("envelopeAadAndroidToWindows"),
            ciphertext = vector.envelope("ciphertext"),
        )
    }

    @Test
    fun leadingZeroEcdhVectorUsesExactly32Bytes() {
        assertArrayEquals(
            vector.leadingZeroEcdh(),
            SecureRemoteCrypto.deriveAgreement(
                privateKey(vector.leadingZeroPrivateScalar()),
                vector.publicKey("sessionServerEphemeral"),
            ),
        )
    }

    @Test
    fun shortCodeVectorMatches() {
        assertEquals(
            vector.pairingText("pairingCode"),
            SecureRemoteCrypto.pairingCode(vector.pairing("claimPlaintext")),
        )
    }

    @Test
    fun base64UrlIsUnpaddedAndStrict() {
        val bytes = byteArrayOf(0x00, 0x01, 0xfe.toByte(), 0xff.toByte())

        val encoded = SecureRemoteProtocol.encodeBase64Url(bytes)

        assertEquals("AAH-_w", encoded)
        assertArrayEquals(bytes, SecureRemoteProtocol.decodeBase64Url(encoded))
        assertThrows(IllegalArgumentException::class.java) {
            SecureRemoteProtocol.decodeBase64Url("AAH-_w==")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecureRemoteProtocol.decodeBase64Url("AAH+/w")
        }
    }

    @Test
    fun rawSignatureVerificationFailsClosed() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = generator.generateKeyPair()
        val publicKey = SecureRemoteCrypto.encodeP256PublicKey(keyPair.public)
        val transcript = "signed transcript".toByteArray(Charsets.UTF_8)
        val signature = SecureRemoteCrypto.signRawEcdsa(keyPair.private, transcript)

        assertEquals(64, signature.size)
        assertTrue(SecureRemoteCrypto.verifyRawEcdsa(publicKey, transcript, signature))
        assertFalse(SecureRemoteCrypto.verifyRawEcdsa(publicKey, transcript, signature.copyOf(63)))
        assertFalse(SecureRemoteCrypto.verifyRawEcdsa(publicKey, transcript, signature.copyOf(65)))
        assertFalse(
            SecureRemoteCrypto.verifyRawEcdsa(
                publicKey,
                transcript,
                signature.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() },
            ),
        )
        assertFalse(
            SecureRemoteCrypto.verifyRawEcdsa(
                ByteArray(65).apply { this[0] = 0x04 },
                transcript,
                signature,
            ),
        )
    }

    @Test
    fun derToRawAcceptsCanonical32And33ByteIntegers() {
        val canonical32 = ByteArray(32).apply {
            this[0] = 0x7f
            this[lastIndex] = 0x01
        }
        val canonical33Magnitude = ByteArray(32).apply {
            this[0] = 0x80.toByte()
            this[lastIndex] = 0x02
        }

        val raw = SecureRemoteCrypto.derToRawEcdsa(
            derSignature(
                byteArrayOf(0) + canonical33Magnitude,
                canonical32,
            ),
        )

        assertArrayEquals(canonical33Magnitude + canonical32, raw)
    }

    @Test
    fun derToRawRejectsRedundantPositivePadding() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureRemoteCrypto.derToRawEcdsa(
                derSignature(
                    byteArrayOf(0, 1),
                    byteArrayOf(1),
                ),
            )
        }
    }

    @Test
    fun derToRawRejectsNegativeAndOversizedIntegers() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureRemoteCrypto.derToRawEcdsa(
                derSignature(
                    byteArrayOf(0x80.toByte()),
                    byteArrayOf(1),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecureRemoteCrypto.derToRawEcdsa(
                derSignature(
                    byteArrayOf(1) + ByteArray(32),
                    byteArrayOf(1),
                ),
            )
        }
    }

    @Test
    fun derToRawRejectsZeroAndOutOfRangeScalars() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureRemoteCrypto.derToRawEcdsa(
                derSignature(
                    byteArrayOf(0),
                    byteArrayOf(1),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecureRemoteCrypto.derToRawEcdsa(
                derSignature(
                    byteArrayOf(0) + P256_ORDER.toUnsignedBytes(),
                    byteArrayOf(1),
                ),
            )
        }
    }

    private fun assertTranscript(name: String, actual: ByteArray) {
        assertArrayEquals(name, vector.transcript(name), actual)
    }

    private fun assertSignature(publicKey: String, transcript: ByteArray, signature: String) {
        assertTrue(
            signature,
            SecureRemoteCrypto.verifyRawEcdsa(
                vector.publicKey(publicKey),
                transcript,
                vector.signature(signature),
            ),
        )
    }

    private fun assertDerived(
        sessionMaster: ByteArray,
        label: String,
        length: Int,
        expectedName: String,
    ) {
        assertArrayEquals(
            vector.session(expectedName),
            SecureRemoteCrypto.hkdfSha256(
                sessionMaster,
                byteArrayOf(),
                label.toByteArray(Charsets.UTF_8),
                length,
            ),
        )
    }

    private fun assertEncryption(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        ciphertext: ByteArray,
    ) {
        assertArrayEquals(
            ciphertext,
            SecureRemoteCrypto.encryptAesGcm(key, nonce, plaintext, aad),
        )
        assertArrayEquals(
            plaintext,
            SecureRemoteCrypto.decryptAesGcm(key, nonce, ciphertext, aad),
        )
        assertThrows(Exception::class.java) {
            SecureRemoteCrypto.decryptAesGcm(
                key,
                nonce,
                ciphertext.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
                aad,
            )
        }
    }

    private fun privateKey(scalar: BigInteger) = KeyFactory.getInstance("EC").generatePrivate(
        ECPrivateKeySpec(scalar, p256Parameters()),
    )

    private fun p256Parameters(): ECParameterSpec {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        return parameters.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun field(value: ByteArray): ByteArray {
        return ByteBuffer.allocate(4 + value.size).putInt(value.size).put(value).array()
    }

    private fun derSignature(r: ByteArray, s: ByteArray): ByteArray {
        val content = byteArrayOf(0x02, r.size.toByte()) + r +
            byteArrayOf(0x02, s.size.toByte()) + s
        return byteArrayOf(0x30, content.size.toByte()) + content
    }

    private fun BigInteger.toUnsignedBytes(): ByteArray {
        return toByteArray().let { encoded ->
            if (encoded.size > 1 && encoded[0] == 0.toByte()) {
                encoded.copyOfRange(1, encoded.size)
            } else {
                encoded
            }
        }
    }

    private class VectorFixture {
        private val root = JSONObject(
            checkNotNull(
                SecureRemoteProtocolTest::class.java.classLoader
                    ?.getResourceAsStream("protocol/secure-remote-v1.json"),
            ).bufferedReader().use { it.readText() },
        )
        private val inputs = root.getJSONObject("inputs")
        private val ids = inputs.getJSONObject("ids")
        private val strings = inputs.getJSONObject("strings")
        private val times = inputs.getJSONObject("unixMilliseconds")
        private val publicKeys = root.getJSONObject("publicKeys")
        private val transcripts = root.getJSONObject("transcripts")
        private val signatures = root.getJSONObject("signatures")
        private val pairing = root.getJSONObject("pairing")
        private val session = root.getJSONObject("session")
        private val envelope = root.getJSONObject("envelopes").getJSONObject("androidToWindows")

        fun id(name: String): String = ids.getString(name)

        fun hex(name: String): ByteArray = inputs.getString(name).hexToBytes()

        fun privateScalar(name: String): BigInteger {
            return BigInteger(inputs.getJSONObject("privateScalars").getString(name))
        }

        fun publicKey(name: String): ByteArray = decode(publicKeys.getString(name))

        fun transcript(name: String): ByteArray = decode(transcripts.getString(name))

        fun signature(name: String): ByteArray = decode(signatures.getString(name))

        fun pairing(name: String): ByteArray = decode(pairing.getString(name))

        fun pairingText(name: String): String = pairing.getString(name)

        fun session(name: String): ByteArray = decode(session.getString(name))

        fun leadingZeroEcdh(): ByteArray = decode(root.getJSONObject("leadingZeroEcdh").getString("sharedSecret"))

        fun leadingZeroPrivateScalar(): BigInteger = BigInteger(root.getJSONObject("leadingZeroEcdh").getString("privateScalar"))

        fun envelope(name: String): ByteArray = decode(envelope.getString(name))

        fun envelopeSequence(): ULong = envelope.getLong("sequence").toULong()

        fun pairingPackage() = PairingPackage(
            protocolVersion = root.getLong("protocolVersion"),
            relayUrl = strings.getString("relayUrl"),
            relaySigningPublicKey = publicKey("relaySigning"),
            relaySigningKeyFingerprint = publicKey("relayFingerprintSha256"),
            embeddedTlsCertificateSha256 = byteArrayOf(),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            hostName = strings.getString("hostName"),
            inviteId = id("inviteId"),
            inviteSecret = hex("inviteSecretHex"),
            expiresAt = times.getLong("inviteExpiresAt"),
            hostSigningPublicKey = publicKey("hostSigning"),
            hostAgreementPublicKey = publicKey("hostAgreement"),
            hostKeyVersion = 1,
        )

        fun pairingClaim() = PairingClaim(
            protocolVersion = root.getLong("protocolVersion"),
            claimId = id("claimId"),
            inviteId = id("inviteId"),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            deviceId = id("deviceId"),
            deviceName = strings.getString("deviceName"),
            platform = strings.getString("platform"),
            appVersion = strings.getString("appVersion"),
            deviceSigningPublicKey = publicKey("deviceSigning"),
            deviceAgreementPublicKey = publicKey("deviceAgreement"),
            deviceKeyVersion = 1,
            clientNonce = hex("clientNonceHex"),
            createdAt = times.getLong("claimCreatedAt"),
        )

        fun claimHeader() = PairingClaimHeader(
            protocolVersion = root.getLong("protocolVersion"),
            inviteId = id("inviteId"),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            claimId = id("claimId"),
            deviceId = id("deviceId"),
            deviceAgreementPublicKey = publicKey("deviceAgreement"),
            deviceKeyVersion = 1,
            clientNonce = hex("clientNonceHex"),
            ciphertextLength = pairing("claimPlaintext").size.toLong() + 16,
        )

        fun pairingApproval() = PairingApproval(
            protocolVersion = root.getLong("protocolVersion"),
            bindingId = id("bindingId"),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            deviceId = id("deviceId"),
            hostSigningPublicKey = publicKey("hostSigning"),
            hostAgreementPublicKey = publicKey("hostAgreement"),
            hostKeyVersion = 1,
            deviceSigningPublicKey = publicKey("deviceSigning"),
            deviceAgreementPublicKey = publicKey("deviceAgreement"),
            deviceKeyVersion = 1,
            relaySigningPublicKey = publicKey("relaySigning"),
            relaySigningKeyFingerprint = publicKey("relayFingerprintSha256"),
            approvedAt = times.getLong("approvedAt"),
            bindingVersion = 1,
        )

        fun approvalHeader() = PairingApprovalHeader(
            protocolVersion = root.getLong("protocolVersion"),
            claimId = id("claimId"),
            inviteId = id("inviteId"),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            deviceId = id("deviceId"),
            status = strings.getString("approvalStatus"),
            bindingVersion = 1,
            nonce = hex("approvalNonceHex"),
            ciphertextLength = pairing("approvalPlaintext").size.toLong() + 16,
        )

        fun relayChallenge() = RelayChallenge(
            protocolVersion = root.getLong("protocolVersion"),
            relaySigningKeyFingerprint = publicKey("relayFingerprintSha256"),
            challengeId = id("challengeId"),
            challengeValue = hex("challengeValueHex"),
            subjectType = "device",
            subjectId = id("deviceId"),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            deviceId = id("deviceId"),
            purpose = strings.getString("challengePurpose"),
            issuedAt = times.getLong("challengeIssuedAt"),
            expiresAt = times.getLong("challengeExpiresAt"),
        )

        fun clientHello() = SessionHello(
            protocolVersion = root.getLong("protocolVersion"),
            frameType = "session.client_hello",
            sessionId = id("sessionId"),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            deviceId = id("deviceId"),
            direction = "mobile_to_windows",
            bindingVersion = 1,
            keyVersion = 1,
            ephemeralPublicKey = publicKey("sessionClientEphemeral"),
            nonce = hex("sessionClientNonceHex"),
            createdAt = times.getLong("clientHelloCreatedAt"),
        )

        fun serverHello() = SessionHello(
            protocolVersion = root.getLong("protocolVersion"),
            frameType = "session.server_hello",
            sessionId = id("sessionId"),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            deviceId = id("deviceId"),
            direction = "windows_to_mobile",
            bindingVersion = 1,
            keyVersion = 1,
            ephemeralPublicKey = publicKey("sessionServerEphemeral"),
            nonce = hex("sessionServerNonceHex"),
            createdAt = times.getLong("serverHelloCreatedAt"),
        )

        fun envelope() = SecureEnvelope(
            protocolVersion = root.getLong("protocolVersion"),
            frameType = envelope.getString("frameType"),
            sessionId = id("sessionId"),
            tenantId = id("tenantId"),
            hostId = id("hostId"),
            deviceId = id("deviceId"),
            direction = envelope.getString("direction"),
            kind = envelope.getString("kind"),
            messageId = envelope.getString("messageId"),
            sequence = envelopeSequence(),
            createdAt = envelope.getLong("createdAt"),
            payloadEncoding = envelope.getString("payloadEncoding"),
            nonce = envelope("nonce"),
        )

        private fun decode(value: String): ByteArray = SecureRemoteProtocol.decodeBase64Url(value)

        private fun String.hexToBytes(): ByteArray {
            require(length % 2 == 0)
            return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }

    private companion object {
        val P256_ORDER = BigInteger(
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",
            16,
        )
    }
}
