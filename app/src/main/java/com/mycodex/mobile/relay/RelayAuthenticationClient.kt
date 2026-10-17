package com.mycodex.mobile.relay

import com.mycodex.mobile.devices.DeviceBinding
import com.mycodex.mobile.devices.LocalDeviceIdentityStore
import com.mycodex.mobile.devices.RelayCertificatePinning
import com.mycodex.mobile.security.RelayChallenge
import com.mycodex.mobile.security.SecureRemoteCrypto
import com.mycodex.mobile.security.SecureRemoteProtocol
import com.mycodex.mobile.security.SecureRemoteTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class RelayAuthenticationClient internal constructor(
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val signer: (ByteArray) -> ByteArray,
    private val nowMilliseconds: () -> Long = System::currentTimeMillis,
) {
    constructor(identityStore: LocalDeviceIdentityStore) : this(signer = identityStore::sign)

    suspend fun ticket(binding: DeviceBinding): String {
        return withTimeout(AUTHENTICATION_DEADLINE_MILLIS) {
            ticketWithinDeadline(binding)
        }
    }

    private suspend fun ticketWithinDeadline(binding: DeviceBinding): String {
        val challengeObject = post(
            binding,
            "/v1/auth/challenges",
            JSONObject()
                .put("subjectType", SUBJECT_DEVICE)
                .put("subjectId", binding.deviceId)
                .put("tenantId", binding.tenantId)
                .put("hostId", binding.hostId)
                .put("deviceId", binding.deviceId)
                .put("purpose", PURPOSE_WEBSOCKET_DEVICE),
        )
        val challenge = parseAndVerifyChallenge(binding, challengeObject)
        val challengeTranscript = SecureRemoteTranscript.relayChallenge(challenge)
        val relaySignature = decodeExact(
            challengeObject.getString("relaySignature"),
            RAW_SIGNATURE_SIZE,
        )
        val proofTranscript = SecureRemoteTranscript.relayProof(
            challengeTranscript,
            relaySignature,
            SUBJECT_DEVICE,
            binding.deviceId,
        )
        val subjectSignature = signer(proofTranscript)
        require(subjectSignature.size == RAW_SIGNATURE_SIZE)
        val ticketObject = post(
            binding,
            "/v1/auth/prove",
            JSONObject()
                .put("challenge", challengeObject)
                .put(
                    "subjectSignature",
                    SecureRemoteProtocol.encodeBase64Url(subjectSignature),
                ),
        )
        return parseTicket(ticketObject)
    }

    private fun parseAndVerifyChallenge(
        binding: DeviceBinding,
        value: JSONObject,
    ): RelayChallenge {
        return try {
            require(value.keys().asSequence().toSet() == CHALLENGE_KEYS)
            val challenge = RelayChallenge(
                protocolVersion = requireLong(value, "protocolVersion"),
                relaySigningKeyFingerprint = decodeExact(
                    value.getString("relayFingerprint"),
                    SHA256_SIZE,
                ),
                challengeId = requireString(value, "challengeId"),
                challengeValue = decodeExact(value.getString("challenge"), CHALLENGE_SIZE),
                subjectType = requireString(value, "subjectType"),
                subjectId = requireString(value, "subjectId"),
                tenantId = requireString(value, "tenantId"),
                hostId = requireString(value, "hostId"),
                deviceId = requireString(value, "deviceId"),
                purpose = requireString(value, "purpose"),
                issuedAt = requireLong(value, "issuedAt"),
                expiresAt = requireLong(value, "expiresAt"),
            )
            val relayPublicKey = decodeExact(
                binding.relaySigningPublicKey,
                P256_PUBLIC_KEY_SIZE,
            )
            val configuredFingerprint = decodeExact(
                binding.relaySigningKeyFingerprint,
                SHA256_SIZE,
            )
            val signature = decodeExact(
                value.getString("relaySignature"),
                RAW_SIGNATURE_SIZE,
            )
            val now = nowMilliseconds()
            require(challenge.protocolVersion == SecureRemoteProtocol.VERSION.toLong())
            require(challenge.subjectType == SUBJECT_DEVICE)
            require(challenge.subjectId == binding.deviceId)
            require(challenge.tenantId == binding.tenantId)
            require(challenge.hostId == binding.hostId)
            require(challenge.deviceId == binding.deviceId)
            require(challenge.purpose == PURPOSE_WEBSOCKET_DEVICE)
            require(challenge.issuedAt <= now + CLOCK_SKEW_TOLERANCE_MILLIS)
            require(challenge.expiresAt > now)
            require(challenge.expiresAt - challenge.issuedAt in 1..MAX_LIFETIME_MILLIS)
            require(
                MessageDigest.isEqual(
                    challenge.relaySigningKeyFingerprint,
                    configuredFingerprint,
                ),
            )
            require(
                MessageDigest.isEqual(
                    SecureRemoteCrypto.sha256(relayPublicKey),
                    configuredFingerprint,
                ),
            )
            require(
                SecureRemoteCrypto.verifyRawEcdsa(
                    relayPublicKey,
                    SecureRemoteTranscript.relayChallenge(challenge),
                    signature,
                ),
            )
            challenge
        } catch (error: RuntimeException) {
            throw RelayAuthenticationException("invalid_challenge", error)
        }
    }

    private fun parseTicket(value: JSONObject): String {
        return try {
            require(value.keys().asSequence().toSet() == TICKET_KEYS)
            val ticket = requireString(value, "ticket")
            decodeExact(ticket, TICKET_SIZE)
            require(value.getString("purpose") == PURPOSE_WEBSOCKET_DEVICE)
            val expiresAt = requireLong(value, "expiresAt")
            val now = nowMilliseconds()
            require(expiresAt > now)
            require(
                expiresAt <= now + MAX_LIFETIME_MILLIS + CLOCK_SKEW_TOLERANCE_MILLIS,
            )
            ticket
        } catch (error: RuntimeException) {
            throw RelayAuthenticationException("invalid_ticket", error)
        }
    }

    private suspend fun post(
        binding: DeviceBinding,
        path: String,
        body: JSONObject,
    ): JSONObject {
        val request = Request.Builder()
            .url(binding.relayUrl.removeSuffix("/") + path)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val client = RelayCertificatePinning.withCertificatePin(
            baseClient,
            binding.certificateSha256,
        )
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.byteStream()?.let(::readLimited).orEmpty()
                    if (!response.isSuccessful) {
                        throw RelayAuthenticationException(
                            safeErrorCode(responseText) ?: "http_${response.code}",
                        )
                    }
                    JSONObject(responseText)
                }
            } catch (error: RelayAuthenticationException) {
                throw error
            } catch (error: IOException) {
                throw RelayAuthenticationException("network_error", error)
            } catch (error: RuntimeException) {
                throw RelayAuthenticationException("invalid_response", error)
            }
        }
    }

    private fun requireString(value: JSONObject, key: String): String {
        return (value.get(key) as? String)
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing protocol string")
    }

    private fun requireLong(value: JSONObject, key: String): Long {
        val number = value.get(key) as? Number
            ?: throw IllegalArgumentException("Missing protocol integer")
        val result = number.toLong()
        require(number.toString() == result.toString())
        return result
    }

    private fun decodeExact(value: String, length: Int): ByteArray {
        return SecureRemoteProtocol.decodeBase64Url(value).also {
            require(it.size == length)
        }
    }

    private fun safeErrorCode(responseText: String): String? {
        return runCatching {
            (JSONObject(responseText).get("code") as? String)
                ?.takeIf(SAFE_ERROR_CODE::matches)
        }.getOrNull()
    }

    private fun readLimited(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            require(output.size() + read <= MAX_RESPONSE_BYTES)
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SUBJECT_DEVICE = "device"
        const val PURPOSE_WEBSOCKET_DEVICE = "websocket_device"
        const val SHA256_SIZE = 32
        const val CHALLENGE_SIZE = 32
        const val TICKET_SIZE = 32
        const val RAW_SIGNATURE_SIZE = 64
        const val P256_PUBLIC_KEY_SIZE = 65
        const val MAX_LIFETIME_MILLIS = 60_000L
        const val CLOCK_SKEW_TOLERANCE_MILLIS = 5_000L
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val AUTHENTICATION_DEADLINE_MILLIS = 8_000L
        val SAFE_ERROR_CODE = Regex("[a-z0-9_]{1,64}")
        val CHALLENGE_KEYS = setOf(
            "protocolVersion",
            "relayFingerprint",
            "challengeId",
            "challenge",
            "subjectType",
            "subjectId",
            "tenantId",
            "hostId",
            "deviceId",
            "purpose",
            "issuedAt",
            "expiresAt",
            "relaySignature",
        )
        val TICKET_KEYS = setOf("ticket", "purpose", "expiresAt")
    }
}

class RelayAuthenticationException(
    val code: String,
    cause: Throwable? = null,
) : Exception("Relay authentication failed.", cause)
