package com.mycodex.mobile.relay

import com.mycodex.mobile.security.SecureEnvelope
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.SessionHello
import java.lang.StringBuilder

internal sealed interface SecureRelayFrame {
    data class Hello(val hello: SessionHello, val signature: ByteArray) : SecureRelayFrame
    data class Confirmation(
        val sessionId: String,
        val tenantId: String,
        val hostId: String,
        val deviceId: String,
        val direction: String,
        val senderRole: String,
        val confirmation: ByteArray,
    ) : SecureRelayFrame

    data class Envelope(val envelope: SecureEnvelope, val ciphertext: ByteArray) : SecureRelayFrame
}

/** The wire format is deliberately small: all secure frames are flat JSON objects. */
internal object SecureRelayFrames {
    const val ClientHello = "session.client_hello"
    const val ServerHello = "session.server_hello"
    const val Confirmation = "session.confirmation"
    const val Envelope = "session.envelope"
    const val MobileToWindows = "mobile_to_windows"
    const val WindowsToMobile = "windows_to_mobile"
    const val EncryptedJson = "encrypted-json"
    const val RpcRequest = "rpc.request"
    const val RpcResponse = "rpc.response"
    const val Event = "event"
    const val Android = "android"
    const val Windows = "windows"
    const val MaxBusinessPlaintextBytes = 11 * 1024 * 1024
    const val MaxCiphertextBytes = MaxBusinessPlaintextBytes + 16
    const val MaxCiphertextBase64UrlChars = 15_379_136
    const val MaxWireFrameBytes = 15_444_672
    const val MaxSequence = 4_294_967_295L

    fun parse(text: String): SecureRelayFrame {
        require(text.toByteArray(Charsets.UTF_8).size <= MaxWireFrameBytes)
        val fields = StrictFlatJson.parse(text)
        val frameType = fields.string("frameType")
        return when (frameType) {
            ClientHello, ServerHello -> parseHello(fields, frameType)
            Confirmation -> parseConfirmation(fields)
            Envelope -> parseEnvelope(fields)
            else -> throw IllegalArgumentException("Unknown secure frame type")
        }
    }

    fun serializeHello(hello: SessionHello, signature: ByteArray): String {
        require(hello.frameType == ClientHello || hello.frameType == ServerHello)
        require(signature.size == 64)
        return json(
            "protocolVersion" to hello.protocolVersion,
            "frameType" to hello.frameType,
            "sessionId" to hello.sessionId,
            "tenantId" to hello.tenantId,
            "hostId" to hello.hostId,
            "deviceId" to hello.deviceId,
            "direction" to hello.direction,
            "bindingVersion" to hello.bindingVersion,
            "keyVersion" to hello.keyVersion,
            "ephemeralPublicKey" to SecureRemoteProtocol.encodeBase64Url(hello.ephemeralPublicKey),
            "nonce" to SecureRemoteProtocol.encodeBase64Url(hello.nonce),
            "createdAt" to hello.createdAt,
            "signature" to SecureRemoteProtocol.encodeBase64Url(signature),
        )
    }

    fun serializeConfirmation(
        sessionId: String,
        tenantId: String,
        hostId: String,
        deviceId: String,
        direction: String,
        senderRole: String,
        confirmation: ByteArray,
    ): String {
        require(confirmation.size == 32)
        return json(
            "protocolVersion" to SecureRemoteProtocol.VERSION.toLong(),
            "frameType" to Confirmation,
            "sessionId" to sessionId,
            "tenantId" to tenantId,
            "hostId" to hostId,
            "deviceId" to deviceId,
            "direction" to direction,
            "senderRole" to senderRole,
            "confirmation" to SecureRemoteProtocol.encodeBase64Url(confirmation),
        )
    }

    fun serializeEnvelope(value: SecureEnvelope, ciphertext: ByteArray): String {
        require(value.frameType == Envelope)
        require(value.payloadEncoding == EncryptedJson)
        require(ciphertext.size in 16..MaxCiphertextBytes)
        val encodedCiphertext = SecureRemoteProtocol.encodeBase64Url(ciphertext)
        require(encodedCiphertext.length <= MaxCiphertextBase64UrlChars)
        val result = json(
            "protocolVersion" to value.protocolVersion,
            "frameType" to value.frameType,
            "sessionId" to value.sessionId,
            "tenantId" to value.tenantId,
            "hostId" to value.hostId,
            "deviceId" to value.deviceId,
            "direction" to value.direction,
            "kind" to value.kind,
            "messageId" to value.messageId,
            "sequence" to value.sequence.toLong(),
            "createdAt" to value.createdAt,
            "payloadEncoding" to value.payloadEncoding,
            "nonce" to SecureRemoteProtocol.encodeBase64Url(value.nonce),
            "ciphertext" to encodedCiphertext,
        )
        require(result.toByteArray(Charsets.UTF_8).size <= MaxWireFrameBytes)
        return result
    }

    private fun parseHello(fields: StrictFlatJson.Fields, frameType: String): SecureRelayFrame.Hello {
        fields.requireExactly(HELLO_FIELDS)
        val hello = SessionHello(
            protocolVersion = fields.long("protocolVersion"),
            frameType = frameType,
            sessionId = validSessionId(fields.string("sessionId")),
            tenantId = fields.string("tenantId"),
            hostId = fields.string("hostId"),
            deviceId = fields.string("deviceId"),
            direction = fields.string("direction"),
            bindingVersion = fields.long("bindingVersion"),
            keyVersion = fields.long("keyVersion"),
            ephemeralPublicKey = fields.base64("ephemeralPublicKey", 65),
            nonce = fields.base64("nonce", 32),
            createdAt = fields.long("createdAt"),
        )
        require(hello.protocolVersion == SecureRemoteProtocol.VERSION.toLong())
        require(hello.bindingVersion > 0 && hello.keyVersion > 0)
        require(SecureRemoteCrypto.isValidP256PublicKey(hello.ephemeralPublicKey))
        return SecureRelayFrame.Hello(hello, fields.base64("signature", 64))
    }

    private fun parseConfirmation(fields: StrictFlatJson.Fields): SecureRelayFrame.Confirmation {
        fields.requireExactly(CONFIRMATION_FIELDS)
        require(fields.long("protocolVersion") == SecureRemoteProtocol.VERSION.toLong())
        return SecureRelayFrame.Confirmation(
            sessionId = validSessionId(fields.string("sessionId")),
            tenantId = fields.string("tenantId"),
            hostId = fields.string("hostId"),
            deviceId = fields.string("deviceId"),
            direction = fields.string("direction"),
            senderRole = fields.string("senderRole"),
            confirmation = fields.base64("confirmation", 32),
        )
    }

    private fun parseEnvelope(fields: StrictFlatJson.Fields): SecureRelayFrame.Envelope {
        fields.requireExactly(ENVELOPE_FIELDS)
        val sequence = fields.long("sequence")
        require(sequence in 1..MaxSequence)
        val nonce = fields.base64("nonce", 12)
        val ciphertextText = fields.string("ciphertext")
        require(ciphertextText.length <= MaxCiphertextBase64UrlChars)
        val ciphertext = SecureRemoteProtocol.decodeBase64Url(ciphertextText)
        require(ciphertext.size in 16..MaxCiphertextBytes)
        val envelope = SecureEnvelope(
            protocolVersion = fields.long("protocolVersion"),
            frameType = Envelope,
            sessionId = validSessionId(fields.string("sessionId")),
            tenantId = fields.string("tenantId"),
            hostId = fields.string("hostId"),
            deviceId = fields.string("deviceId"),
            direction = fields.string("direction"),
            kind = fields.string("kind"),
            messageId = fields.string("messageId"),
            sequence = sequence.toULong(),
            createdAt = fields.long("createdAt"),
            payloadEncoding = fields.string("payloadEncoding"),
            nonce = nonce,
        )
        require(envelope.protocolVersion == SecureRemoteProtocol.VERSION.toLong())
        require(envelope.payloadEncoding == EncryptedJson)
        return SecureRelayFrame.Envelope(envelope, ciphertext)
    }

    private fun validSessionId(value: String): String {
        require(value.length == 43)
        require(SecureRemoteProtocol.decodeBase64Url(value).size == 32)
        return value
    }

    private fun json(vararg entries: Pair<String, Any>): String {
        return buildString {
            append('{')
            entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                appendString(key)
                append(':')
                when (value) {
                    is String -> appendString(value)
                    is Long -> append(value)
                    else -> throw IllegalArgumentException("Unsupported wire value")
                }
            }
            append('}')
        }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    require(character >= ' ')
                    append(character)
                }
            }
        }
        append('"')
    }

    private val HELLO_FIELDS = setOf(
        "protocolVersion", "frameType", "sessionId", "tenantId", "hostId", "deviceId", "direction",
        "bindingVersion", "keyVersion", "ephemeralPublicKey", "nonce", "createdAt", "signature",
    )
    private val CONFIRMATION_FIELDS = setOf(
        "protocolVersion", "frameType", "sessionId", "tenantId", "hostId", "deviceId", "direction",
        "senderRole", "confirmation",
    )
    private val ENVELOPE_FIELDS = setOf(
        "protocolVersion", "frameType", "sessionId", "tenantId", "hostId", "deviceId", "direction", "kind",
        "messageId", "sequence", "createdAt", "payloadEncoding", "nonce", "ciphertext",
    )
}

/** Rejects duplicate fields, non-canonical integers, nested values and trailing input. */
private object StrictFlatJson {
    fun parse(text: String): Fields {
        val parser = Parser(text)
        return parser.readObject().also { parser.finish() }
    }

    class Fields internal constructor(private val values: Map<String, Any>) {
        fun requireExactly(expected: Set<String>) = require(values.keys == expected)
        fun string(key: String): String = values[key] as? String ?: throw IllegalArgumentException("Expected string")
        fun long(key: String): Long = values[key] as? Long ?: throw IllegalArgumentException("Expected integer")
        fun base64(key: String, size: Int): ByteArray = SecureRemoteProtocol.decodeBase64Url(string(key)).also { require(it.size == size) }
    }

    private class Parser(private val input: String) {
        private var offset = 0

        fun readObject(): Fields {
            skipWhitespace(); require(read() == '{'); skipWhitespace()
            val values = LinkedHashMap<String, Any>()
            if (peek() == '}') { offset++; return Fields(values) }
            while (true) {
                skipWhitespace(); val key = readString(); skipWhitespace(); require(read() == ':'); skipWhitespace()
                val value = when (peek()) {
                    '"' -> readString()
                    '-', in '0'..'9' -> readLong()
                    else -> throw IllegalArgumentException("Expected scalar")
                }
                require(values.put(key, value) == null)
                skipWhitespace()
                when (read()) {
                    ',' -> continue
                    '}' -> return Fields(values)
                    else -> throw IllegalArgumentException("Expected object delimiter")
                }
            }
        }

        fun finish() { skipWhitespace(); require(offset == input.length) }

        private fun readString(): String {
            require(read() == '"')
            val output = StringBuilder()
            while (true) {
                val character = read()
                when (character) {
                    '"' -> return output.toString()
                    '\\' -> output.append(readEscape())
                    else -> { require(character >= ' '); output.append(character) }
                }
            }
        }

        private fun readEscape(): Char = when (val escape = read()) {
            '"', '\\', '/' -> escape
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readUnicode()
            else -> throw IllegalArgumentException("Invalid escape")
        }

        private fun readUnicode(): Char {
            val digits = input.substring(offset, offset + 4)
            require(digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
            offset += 4
            return digits.toInt(16).toChar()
        }

        private fun readLong(): Long {
            val start = offset
            if (peek() == '-') offset++
            require(offset < input.length)
            if (input[offset] == '0') offset++ else {
                require(input[offset] in '1'..'9')
                while (offset < input.length && input[offset].isDigit()) offset++
            }
            val raw = input.substring(start, offset)
            val value = raw.toLongOrNull() ?: throw IllegalArgumentException("Invalid integer")
            require(value.toString() == raw)
            return value
        }

        private fun skipWhitespace() { while (offset < input.length && input[offset] in " \t\r\n") offset++ }
        private fun peek(): Char { require(offset < input.length); return input[offset] }
        private fun read(): Char { val value = peek(); offset++; return value }
    }
}
