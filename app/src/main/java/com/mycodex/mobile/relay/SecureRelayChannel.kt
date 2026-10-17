package com.mycodex.mobile.relay

import com.mycodex.mobile.devices.DeviceBinding
import com.mycodex.mobile.security.SecureEnvelope
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.SecureRemoteTranscript
import com.mycodex.mobile.security.SessionHello
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** One ephemeral, in-memory E2E session bound to exactly one WebSocket generation. */
internal class SecureRelayChannel(
    private val binding: DeviceBinding,
    private val signer: (ByteArray) -> ByteArray,
    private val sendText: (String) -> Boolean,
    private val onInvalid: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val keyPairGenerator: () -> KeyPair = {
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
    },
) {
    private val lock = Any()
    private var state = State.Disconnected
    private var sessionId: String? = null
    private var ephemeralKeyPair: KeyPair? = null
    private var clientNonce: ByteArray? = null
    private var clientHello: SessionHello? = null
    private var clientHelloSignature: ByteArray? = null
    private var completeTranscript: ByteArray? = null
    private var confirmationKey: ByteArray? = null
    private var outboundKey: ByteArray? = null
    private var inboundKey: ByteArray? = null
    private var outboundNoncePrefix: ByteArray? = null
    private var inboundNoncePrefix: ByteArray? = null
    private var outboundSequence = 1L
    private var inboundSequence = 0L

    enum class State { Disconnected, AwaitingServerHello, AwaitingServerConfirmation, Established, Closed }
    sealed interface Inbound {
        data class Response(val event: RemoteEventPayload) : Inbound
        data class Event(val event: RemoteEventPayload) : Inbound
        data object Ignored : Inbound
    }

    fun start(): Boolean = synchronized(lock) {
        if (state != State.Disconnected) return false
        val newSessionId = ByteArray(32).also(random::nextBytes).let(SecureRemoteProtocol::encodeBase64Url)
        val ephemeral = keyPairGenerator()
        val nonce = ByteArray(32).also(random::nextBytes)
        val hello = SessionHello(
            protocolVersion = SecureRemoteProtocol.VERSION.toLong(),
            frameType = SecureRelayFrames.ClientHello,
            sessionId = newSessionId,
            tenantId = binding.tenantId,
            hostId = binding.hostId,
            deviceId = binding.deviceId,
            direction = SecureRelayFrames.MobileToWindows,
            bindingVersion = binding.bindingVersion.toLong(),
            keyVersion = binding.deviceKeyVersion.toLong(),
            ephemeralPublicKey = SecureRemoteCrypto.encodeP256PublicKey(ephemeral.public),
            nonce = nonce,
            createdAt = now(),
        )
        val signature = signer(SecureRemoteTranscript.sessionHello(hello))
        sessionId = newSessionId
        ephemeralKeyPair = ephemeral
        clientNonce = nonce
        clientHello = hello
        clientHelloSignature = signature
        if (signature.size != 64 || !sendText(SecureRelayFrames.serializeHello(hello, signature))) {
            invalidateLocked("handshake_send_failed")
            return false
        }
        state = State.AwaitingServerHello
        true
    }

    fun currentState(): State = synchronized(lock) { state }

    fun receive(text: String): Inbound = synchronized(lock) {
        try {
            when (val frame = SecureRelayFrames.parse(text)) {
                is SecureRelayFrame.Hello -> receiveServerHelloLocked(frame)
                is SecureRelayFrame.Confirmation -> receiveServerConfirmationLocked(frame)
                is SecureRelayFrame.Envelope -> receiveEnvelopeLocked(frame)
            }
        } catch (_: RuntimeException) {
            invalidateLocked("invalid_secure_frame")
            Inbound.Ignored
        }
    }

    fun rejectBinary() = synchronized(lock) { invalidateLocked("binary_frame") }

    fun sendCommand(command: RemoteCommandPayload): Boolean = synchronized(lock) {
        if (state != State.Established) return false
        val plaintext = RemotePayloadCodec.commandJson(binding.deviceId, command).toByteArray(Charsets.UTF_8)
        if (plaintext.size > SecureRelayFrames.MaxBusinessPlaintextBytes) return false
        sendEnvelopeLocked(SecureRelayFrames.RpcRequest, plaintext)
    }

    fun close(reason: String = "closed") = synchronized(lock) { invalidateLocked(reason) }

    private fun receiveServerHelloLocked(frame: SecureRelayFrame.Hello): Inbound {
        require(state == State.AwaitingServerHello)
        val hello = frame.hello
        require(hello.frameType == SecureRelayFrames.ServerHello)
        require(hello.sessionId == sessionId)
        require(hello.tenantId == binding.tenantId && hello.hostId == binding.hostId && hello.deviceId == binding.deviceId)
        require(hello.direction == SecureRelayFrames.WindowsToMobile)
        require(hello.bindingVersion == binding.bindingVersion.toLong())
        require(hello.keyVersion == binding.hostKeyVersion.toLong())
        val hostSigningKey = SecureRemoteProtocol.decodeBase64Url(binding.hostSigningPublicKey)
        val hostAgreementKey = SecureRemoteProtocol.decodeBase64Url(binding.hostAgreementPublicKey)
        require(!MessageDigest.isEqual(hello.ephemeralPublicKey, hostAgreementKey))
        val clientHello = checkNotNull(clientHello)
        val clientSignature = checkNotNull(clientHelloSignature)
        val serverTranscript = SecureRemoteTranscript.serverHello(
            SecureRemoteTranscript.sessionHello(clientHello),
            clientSignature,
            hello,
        )
        require(SecureRemoteCrypto.verifyRawEcdsa(hostSigningKey, serverTranscript, frame.signature))
        val complete = SecureRemoteTranscript.sessionComplete(serverTranscript, frame.signature)
        val shared = SecureRemoteCrypto.deriveAgreement(checkNotNull(ephemeralKeyPair).private, hello.ephemeralPublicKey)
        val sessionMaster = SecureRemoteCrypto.hkdfSha256(
            shared,
            SecureRemoteCrypto.sha256(checkNotNull(clientNonce) + hello.nonce),
            complete,
            32,
        )
        completeTranscript = complete
        confirmationKey = SecureRemoteCrypto.hkdfSha256(sessionMaster, byteArrayOf(), "session confirmation".toByteArray(), 32)
        outboundKey = SecureRemoteCrypto.hkdfSha256(sessionMaster, byteArrayOf(), "android to windows key".toByteArray(), 32)
        inboundKey = SecureRemoteCrypto.hkdfSha256(sessionMaster, byteArrayOf(), "windows to android key".toByteArray(), 32)
        outboundNoncePrefix = SecureRemoteCrypto.hkdfSha256(sessionMaster, byteArrayOf(), "android to windows nonce prefix".toByteArray(), 4)
        inboundNoncePrefix = SecureRemoteCrypto.hkdfSha256(sessionMaster, byteArrayOf(), "windows to android nonce prefix".toByteArray(), 4)
        val confirmation = SecureRemoteCrypto.hmacSha256(
            checkNotNull(confirmationKey),
            SecureRemoteTranscript.sessionConfirmation(complete, SecureRelayFrames.Android),
        )
        require(sendText(SecureRelayFrames.serializeConfirmation(
            checkNotNull(sessionId), binding.tenantId, binding.hostId, binding.deviceId,
            SecureRelayFrames.MobileToWindows, SecureRelayFrames.Android, confirmation,
        )))
        state = State.AwaitingServerConfirmation
        return Inbound.Ignored
    }

    private fun receiveServerConfirmationLocked(frame: SecureRelayFrame.Confirmation): Inbound {
        require(state == State.AwaitingServerConfirmation)
        require(frame.sessionId == sessionId)
        require(frame.tenantId == binding.tenantId && frame.hostId == binding.hostId && frame.deviceId == binding.deviceId)
        require(frame.direction == SecureRelayFrames.WindowsToMobile && frame.senderRole == SecureRelayFrames.Windows)
        val expected = SecureRemoteCrypto.hmacSha256(
            checkNotNull(confirmationKey),
            SecureRemoteTranscript.sessionConfirmation(checkNotNull(completeTranscript), SecureRelayFrames.Windows),
        )
        require(MessageDigest.isEqual(expected, frame.confirmation))
        state = State.Established
        return Inbound.Ignored
    }

    private fun receiveEnvelopeLocked(frame: SecureRelayFrame.Envelope): Inbound {
        require(state == State.Established)
        val envelope = frame.envelope
        require(envelope.sessionId == sessionId)
        require(envelope.tenantId == binding.tenantId && envelope.hostId == binding.hostId && envelope.deviceId == binding.deviceId)
        require(envelope.direction == SecureRelayFrames.WindowsToMobile)
        require(envelope.kind == SecureRelayFrames.RpcResponse || envelope.kind == SecureRelayFrames.Event)
        require(envelope.sequence.toLong() == inboundSequence + 1)
        val expectedNonce = SecureRemoteCrypto.envelopeNonce(checkNotNull(inboundNoncePrefix), envelope.sequence)
        require(MessageDigest.isEqual(expectedNonce, envelope.nonce))
        val plaintext = SecureRemoteCrypto.decryptAesGcm(
            checkNotNull(inboundKey), envelope.nonce, frame.ciphertext, SecureRemoteTranscript.envelopeAad(envelope),
        )
        require(plaintext.size <= SecureRelayFrames.MaxBusinessPlaintextBytes)
        inboundSequence = envelope.sequence.toLong()
        return when (envelope.kind) {
            SecureRelayFrames.RpcResponse -> RemotePayloadCodec.response(plaintext)?.let(Inbound::Response)
                ?: throw IllegalArgumentException("Invalid response payload")
            SecureRelayFrames.Event -> RemotePayloadCodec.event(plaintext)?.let(Inbound::Event) ?: throw IllegalArgumentException("Invalid event payload")
            else -> throw IllegalArgumentException("Invalid envelope kind")
        }
    }

    private fun sendEnvelopeLocked(kind: String, plaintext: ByteArray): Boolean {
        if (outboundSequence !in 1..SecureRelayFrames.MaxSequence) {
            invalidateLocked("sequence_exhausted")
            return false
        }
        val sequence = outboundSequence.toULong()
        val nonce = SecureRemoteCrypto.envelopeNonce(checkNotNull(outboundNoncePrefix), sequence)
        val envelope = SecureEnvelope(
            protocolVersion = SecureRemoteProtocol.VERSION.toLong(),
            frameType = SecureRelayFrames.Envelope,
            sessionId = checkNotNull(sessionId),
            tenantId = binding.tenantId,
            hostId = binding.hostId,
            deviceId = binding.deviceId,
            direction = SecureRelayFrames.MobileToWindows,
            kind = kind,
            messageId = "android-message-${UUID.randomUUID().toString().replace("-", "")}",
            sequence = sequence,
            createdAt = now(),
            payloadEncoding = SecureRelayFrames.EncryptedJson,
            nonce = nonce,
        )
        val ciphertext = SecureRemoteCrypto.encryptAesGcm(
            checkNotNull(outboundKey), nonce, plaintext, SecureRemoteTranscript.envelopeAad(envelope),
        )
        val serialized = SecureRelayFrames.serializeEnvelope(envelope, ciphertext)
        if (!sendText(serialized)) {
            invalidateLocked("secure_send_failed")
            return false
        }
        outboundSequence++
        return true
    }

    private fun invalidateLocked(reason: String) {
        if (state == State.Closed) return
        state = State.Closed
        listOf(confirmationKey, outboundKey, inboundKey, outboundNoncePrefix, inboundNoncePrefix, clientNonce).forEach { it?.fill(0) }
        confirmationKey = null; outboundKey = null; inboundKey = null; outboundNoncePrefix = null; inboundNoncePrefix = null
        clientNonce = null; clientHello = null; clientHelloSignature = null; completeTranscript = null; ephemeralKeyPair = null; sessionId = null; outboundSequence = 1; inboundSequence = 0
        onInvalid(reason)
    }
}

internal object RemotePayloadCodec {
    fun commandJson(deviceId: String, command: RemoteCommandPayload): String = JSONObject()
        .put("schemaVersion", 1)
        .put("payloadType", "remote.command")
        .put("requestId", command.requestId)
        .put("commandType", command.commandType)
        .put("deviceId", deviceId)
        .apply { if (!command.operationId.isNullOrBlank()) put("operationId", command.operationId) }
        .put("payload", command.payload)
        .toString()

    fun response(plaintext: ByteArray): RemoteEventPayload? = runCatching {
        val value = JSONObject(plaintext.toString(Charsets.UTF_8))
        require(value.getLong("schemaVersion") == 1L)
        require(value.getString("payloadType") == "remote.response")
        val requestId = (value.get("requestId") as? String)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing response request id")
        val eventType = (value.opt("eventType") as? String)?.takeIf { it.isNotBlank() } ?: "rpc.response"
        val payload = value.optJSONObject("payload") ?: value.optJSONObject("result") ?: JSONObject()
        RemoteEventPayload(
            requestId = requestId,
            operationId = (value.opt("operationId") as? String)?.takeIf { it.isNotBlank() },
            eventType = eventType,
            payload = payload,
            errorCode = value.optString("errorCode").takeIf { it.isNotBlank() }
                ?: payload.optString("code").takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    fun event(plaintext: ByteArray): RemoteEventPayload? = parseEvent(plaintext, requireRequestId = false)

    private fun parseEvent(plaintext: ByteArray, requireRequestId: Boolean): RemoteEventPayload? = runCatching {
        val value = JSONObject(plaintext.toString(Charsets.UTF_8))
        require(value.keys().asSequence().toSet() in EVENT_FIELD_SETS)
        require(value.getLong("schemaVersion") == 1L)
        require(value.getString("payloadType") == "remote.event")
        val requestId = (value.opt("requestId") as? String)?.takeIf { it.isNotBlank() }
        if (requireRequestId) require(requestId != null)
        val eventType = value.get("eventType") as? String ?: throw IllegalArgumentException("Missing event type")
        require(eventType.isNotBlank())
        RemoteEventPayload(
            requestId = requestId,
            operationId = (value.opt("operationId") as? String)?.takeIf { it.isNotBlank() },
            eventType = eventType,
            payload = value.optJSONObject("payload") ?: JSONObject(),
            errorCode = value.optString("errorCode").takeIf { it.isNotBlank() },
        )
    }.getOrNull()

    private val EVENT_FIELD_SETS = setOf(
        setOf("schemaVersion", "payloadType", "requestId", "eventType", "payload"),
        setOf("schemaVersion", "payloadType", "requestId", "operationId", "eventType", "payload"),
        setOf("schemaVersion", "payloadType", "eventType", "payload"),
        setOf("schemaVersion", "payloadType", "operationId", "eventType", "payload"),
        setOf("schemaVersion", "payloadType", "requestId", "eventType", "payload", "errorCode"),
        setOf("schemaVersion", "payloadType", "requestId", "operationId", "eventType", "payload", "errorCode"),
    )
}
