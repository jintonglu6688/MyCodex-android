package com.mycodex.mobile.relay

import com.mycodex.mobile.devices.DeviceBinding
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.SecureRemoteTranscript
import com.mycodex.mobile.security.SessionHello
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class SecureRelayChannelTest {
    @Test
    fun handshakeEstablishesAndCommandIsEncrypted() {
        val deviceSigning = keyPair()
        val deviceAgreement = keyPair()
        val hostSigning = keyPair()
        val hostAgreement = keyPair()
        val hostEphemeral = keyPair()
        val binding = binding(deviceSigning, deviceAgreement, hostSigning, hostAgreement)
        val sent = mutableListOf<String>()
        val invalidReasons = mutableListOf<String>()
        val channel = SecureRelayChannel(
            binding = binding,
            signer = { SecureRemoteCrypto.signRawEcdsa(deviceSigning.private, it) },
            sendText = { sent += it; true },
            onInvalid = { invalidReasons += it },
        )

        assertTrue(channel.start())
        val client = SecureRelayFrames.parse(sent.single()) as SecureRelayFrame.Hello
        val serverNonce = ByteArray(32) { 7 }
        val serverHello = SessionHello(
            protocolVersion = 1,
            frameType = SecureRelayFrames.ServerHello,
            sessionId = client.hello.sessionId,
            tenantId = binding.tenantId,
            hostId = binding.hostId,
            deviceId = binding.deviceId,
            direction = SecureRelayFrames.WindowsToMobile,
            bindingVersion = binding.bindingVersion.toLong(),
            keyVersion = binding.hostKeyVersion.toLong(),
            ephemeralPublicKey = SecureRemoteCrypto.encodeP256PublicKey(hostEphemeral.public),
            nonce = serverNonce,
            createdAt = 2,
        )
        val clientTranscript = SecureRemoteTranscript.sessionHello(client.hello)
        val serverTranscript = SecureRemoteTranscript.serverHello(clientTranscript, client.signature, serverHello)
        val serverSignature = SecureRemoteCrypto.signRawEcdsa(hostSigning.private, serverTranscript)
        channel.receive(SecureRelayFrames.serializeHello(serverHello, serverSignature))
        assertEquals(invalidReasons.toString(), SecureRelayChannel.State.AwaitingServerConfirmation, channel.currentState())

        val complete = SecureRemoteTranscript.sessionComplete(serverTranscript, serverSignature)
        val master = SecureRemoteCrypto.hkdfSha256(
            SecureRemoteCrypto.deriveAgreement(hostEphemeral.private, client.hello.ephemeralPublicKey),
            SecureRemoteCrypto.sha256(client.hello.nonce + serverNonce),
            complete,
            32,
        )
        val confirmationKey = SecureRemoteCrypto.hkdfSha256(master, byteArrayOf(), "session confirmation".toByteArray(), 32)
        channel.receive(
            SecureRelayFrames.serializeConfirmation(
                client.hello.sessionId,
                binding.tenantId,
                binding.hostId,
                binding.deviceId,
                SecureRelayFrames.WindowsToMobile,
                SecureRelayFrames.Windows,
                SecureRemoteCrypto.hmacSha256(
                    confirmationKey,
                    SecureRemoteTranscript.sessionConfirmation(complete, SecureRelayFrames.Windows),
                ),
            ),
        )

        assertEquals(invalidReasons.toString(), SecureRelayChannel.State.Established, channel.currentState())
        assertTrue(channel.sendCommand(RemoteCommandPayload(commandType = "host.settings.get", payload = JSONObject().put("x", "secret"))))
        val encrypted = sent.last()
        assertTrue(encrypted.contains("\"payloadEncoding\":\"encrypted-json\""))
        assertFalse(encrypted.contains("host.settings.get"))
        assertFalse(encrypted.contains("secret"))
        assertEquals(SecureRelayFrames.Envelope, (SecureRelayFrames.parse(encrypted) as SecureRelayFrame.Envelope).envelope.frameType)
    }

    @Test
    fun duplicateAndPlaintextFramesFailClosed() {
        val duplicate = "{\"frameType\":\"session.confirmation\",\"frameType\":\"session.confirmation\"}"
        assertFalse(runCatching { SecureRelayFrames.parse(duplicate) }.isSuccess)
        assertFalse(runCatching { SecureRelayFrames.parse("{\"payloadEncoding\":\"plain-json\"}") }.isSuccess)
    }

    @Test
    fun encryptedRpcResponseUsesRemoteResponsePayload() {
        val response = RemotePayloadCodec.response(
            """{"schemaVersion":1,"payloadType":"remote.response","requestId":"request_demo","result":{"ok":true}}""".toByteArray(),
        )

        assertEquals("request_demo", response?.requestId)
        assertEquals("rpc.response", response?.eventType)
        assertTrue(response?.payload?.optBoolean("ok") == true)
    }

    @Test
    fun windowsErrorResponseUsesPayloadCode() {
        val response = RemotePayloadCodec.response(
            """{"schemaVersion":1,"payloadType":"remote.response","requestId":"request_demo","eventType":"system.error","payload":{"code":"write_lock_denied","message":"Write lock denied."}}""".toByteArray(),
        )

        assertEquals("write_lock_denied", response?.errorCode)
    }

    private fun binding(deviceSigning: KeyPair, deviceAgreement: KeyPair, hostSigning: KeyPair, hostAgreement: KeyPair) = DeviceBinding(
        relayUrl = "https://relay.example",
        relaySigningPublicKey = encoded(keyPair().public),
        relaySigningKeyFingerprint = encoded(ByteArray(32)),
        certificateSha256 = null,
        tenantId = "tenant",
        hostId = "host",
        hostName = "Host",
        deviceId = "device",
        hostSigningPublicKey = encoded(hostSigning.public),
        hostAgreementPublicKey = encoded(hostAgreement.public),
        deviceSigningPublicKey = encoded(deviceSigning.public),
        deviceAgreementPublicKey = encoded(deviceAgreement.public),
        hostKeyVersion = 1,
        deviceKeyVersion = 1,
        bindingVersion = 1,
    )

    private fun encoded(value: java.security.PublicKey): String = SecureRemoteProtocol.encodeBase64Url(SecureRemoteCrypto.encodeP256PublicKey(value))
    private fun encoded(value: ByteArray): String = SecureRemoteProtocol.encodeBase64Url(value)
    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
}
