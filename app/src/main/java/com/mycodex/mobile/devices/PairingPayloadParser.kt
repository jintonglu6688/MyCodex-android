package com.mycodex.mobile.devices

import com.mycodex.mobile.security.PairingPackage
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.SecureRemoteTranscript
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

object PairingPayloadParser {
    fun parseUri(text: String, nowEpochMillis: Long): ImportedPairingPackage? {
        return runCatching {
            require(text.length <= MAX_URI_LENGTH)
            require(text == text.trim())
            val uri = URI(text)
            require(uri.scheme == "mycodex")
            require(uri.host == "pair")
            require(uri.userInfo == null)
            require(uri.port == -1)
            require(uri.path.isNullOrEmpty())
            require(uri.fragment == null)
            val rawQuery = requireNotNull(uri.rawQuery)
            require(rawQuery.startsWith("data="))
            require(!rawQuery.contains('&'))
            val encodedData = rawQuery.substring(DATA_PREFIX.length)
            require(encodedData.isNotEmpty())
            require(encodedData.length <= MAX_ENCODED_DATA_LENGTH)
            require(encodedData.all { it.isLetterOrDigit() || it == '-' || it == '_' })
            val jsonBytes = SecureRemoteProtocol.decodeBase64Url(encodedData)
            try {
                parsePackageJson(jsonBytes, text, nowEpochMillis)
            } finally {
                jsonBytes.fill(0)
            }
        }.getOrNull()
    }

    fun shouldClearClipboard(
        originalText: String,
        currentText: String,
        parsedSuccessfully: Boolean,
    ): Boolean {
        return parsedSuccessfully && originalText == currentText
    }

    private fun parsePackageJson(
        jsonBytes: ByteArray,
        originalUri: String,
        nowEpochMillis: Long,
    ): ImportedPairingPackage {
        require(jsonBytes.size <= MAX_DECODED_JSON_BYTES)
        val jsonText = jsonBytes.toString(Charsets.UTF_8)
        require(jsonText.toByteArray(Charsets.UTF_8).contentEquals(jsonBytes))
        requireJsonDepth(jsonText)
        val root = JSONObject(jsonText)
        requireExactKeys(root, ROOT_KEYS)
        val relay = requireObject(root, "relay")
        requireExactKeys(relay, RELAY_KEYS)
        val relayUrl = requireString(relay, "url", MAX_URL_LENGTH)
        requireValidRelayUrl(relayUrl)
        val packageValue = PairingPackage(
            protocolVersion = requireLong(root, "version"),
            relayUrl = relayUrl,
            relaySigningPublicKey = requireBytes(relay, "signingPublicKey", PUBLIC_KEY_SIZE),
            relaySigningKeyFingerprint = requireBytes(
                relay,
                "signingKeyFingerprint",
                SHA256_SIZE,
            ),
            embeddedTlsCertificateSha256 = requireOptionalBytes(
                relay,
                "embeddedTlsCertificateSha256",
                SHA256_SIZE,
            ),
            tenantId = requireUuid(root, "tenantId"),
            hostId = requireUuid(root, "hostId"),
            hostName = requireString(root, "hostName", MAX_DISPLAY_NAME_LENGTH),
            inviteId = requireUuid(root, "inviteId"),
            inviteSecret = requireBytes(root, "inviteSecret", INVITE_SECRET_SIZE),
            expiresAt = requireLong(root, "expiresAt"),
            hostSigningPublicKey = requireBytes(root, "hostSigningPublicKey", PUBLIC_KEY_SIZE),
            hostAgreementPublicKey = requireBytes(root, "hostAgreementPublicKey", PUBLIC_KEY_SIZE),
            hostKeyVersion = requireLong(root, "hostKeyVersion"),
        )
        val hostSignature = requireBytes(root, "hostSignature", RAW_SIGNATURE_SIZE)
        require(packageValue.protocolVersion == SecureRemoteProtocol.VERSION.toLong())
        require(packageValue.hostKeyVersion > 0)
        require(packageValue.expiresAt > nowEpochMillis)
        require(packageValue.expiresAt - nowEpochMillis <= MAX_REMAINING_MILLIS)
        require(SecureRemoteCrypto.isValidP256PublicKey(packageValue.relaySigningPublicKey))
        require(SecureRemoteCrypto.isValidP256PublicKey(packageValue.hostSigningPublicKey))
        require(SecureRemoteCrypto.isValidP256PublicKey(packageValue.hostAgreementPublicKey))
        require(
            !MessageDigest.isEqual(
                packageValue.hostSigningPublicKey,
                packageValue.hostAgreementPublicKey,
            ),
        )
        require(
            MessageDigest.isEqual(
                SecureRemoteCrypto.sha256(packageValue.relaySigningPublicKey),
                packageValue.relaySigningKeyFingerprint,
            ),
        )
        require(
            SecureRemoteCrypto.verifyRawEcdsa(
                packageValue.hostSigningPublicKey,
                SecureRemoteTranscript.pairingPackage(packageValue),
                hostSignature,
            ),
        )
        return ImportedPairingPackage(packageValue, hostSignature, originalUri)
    }

    private fun requireExactKeys(value: JSONObject, expected: Set<String>) {
        val actual = value.keys().asSequence().toSet()
        require(actual == expected)
    }

    private fun requireObject(value: JSONObject, key: String): JSONObject {
        return value.get(key) as? JSONObject
            ?: throw IllegalArgumentException("Expected an object")
    }

    private fun requireString(value: JSONObject, key: String, maxLength: Int): String {
        val result = value.get(key) as? String
            ?: throw IllegalArgumentException("Expected a string")
        require(result.isNotEmpty())
        require(result.length <= maxLength)
        return result
    }

    private fun requireLong(value: JSONObject, key: String): Long {
        val number = value.get(key) as? Number
            ?: throw IllegalArgumentException("Expected a number")
        val result = number.toLong()
        require(number.toString() == result.toString())
        return result
    }

    private fun requireUuid(value: JSONObject, key: String): String {
        val result = requireString(value, key, UUID_LENGTH)
        require(UUID_PATTERN.matches(result))
        return result
    }

    private fun requireBytes(value: JSONObject, key: String, size: Int): ByteArray {
        val encoded = requireString(value, key, MAX_BINARY_TEXT_LENGTH)
        return SecureRemoteProtocol.decodeBase64Url(encoded).also { require(it.size == size) }
    }

    private fun requireOptionalBytes(value: JSONObject, key: String, size: Int): ByteArray {
        val encoded = value.get(key) as? String
            ?: throw IllegalArgumentException("Expected a string")
        require(encoded.length <= MAX_BINARY_TEXT_LENGTH)
        if (encoded.isEmpty()) {
            return byteArrayOf()
        }
        return SecureRemoteProtocol.decodeBase64Url(encoded).also { require(it.size == size) }
    }

    private fun requireValidRelayUrl(value: String) {
        val uri = URI(value)
        require(uri.scheme == "https")
        require(!uri.host.isNullOrBlank())
        require(uri.userInfo == null)
        require(uri.query == null)
        require(uri.fragment == null)
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        require(uri.port == -1 || uri.port in 1..65535)
    }

    private fun requireJsonDepth(value: String) {
        var depth = 0
        var inString = false
        var escaped = false
        value.forEach { character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        require(depth <= MAX_JSON_DEPTH)
                    }
                    '}', ']' -> {
                        depth--
                        require(depth >= 0)
                    }
                }
            }
        }
        require(!inString && !escaped && depth == 0)
    }

    private const val DATA_PREFIX = "data="
    private const val MAX_URI_LENGTH = 16_384
    private const val MAX_ENCODED_DATA_LENGTH = 16_000
    private const val MAX_DECODED_JSON_BYTES = 12_000
    private const val MAX_URL_LENGTH = 2_048
    private const val MAX_DISPLAY_NAME_LENGTH = 256
    private const val MAX_BINARY_TEXT_LENGTH = 512
    private const val UUID_LENGTH = 36
    private const val PUBLIC_KEY_SIZE = 65
    private const val SHA256_SIZE = 32
    private const val INVITE_SECRET_SIZE = 32
    private const val RAW_SIGNATURE_SIZE = 64
    private const val MAX_REMAINING_MILLIS = 600_000L
    private const val MAX_JSON_DEPTH = 4
    private val UUID_PATTERN = Regex(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    )
    private val ROOT_KEYS = setOf(
        "version",
        "relay",
        "tenantId",
        "hostId",
        "hostName",
        "inviteId",
        "inviteSecret",
        "expiresAt",
        "hostSigningPublicKey",
        "hostAgreementPublicKey",
        "hostKeyVersion",
        "hostSignature",
    )
    private val RELAY_KEYS = setOf(
        "url",
        "signingPublicKey",
        "signingKeyFingerprint",
        "embeddedTlsCertificateSha256",
    )
}
