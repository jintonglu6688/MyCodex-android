package com.mycodex.mobile.devices

import com.mycodex.mobile.security.PairingClaimHeader
import com.mycodex.mobile.diagnostics.MobileDiagnostics
import com.mycodex.mobile.security.SecureRemoteProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

open class RelayPairingClient(
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    open suspend fun submitClaim(
        relayUrl: String,
        certificateSha256: String?,
        submission: PairingClaimSubmission,
    ): PairingClaimCreated {
        val validRelayUrl = validatedRelayUrl(relayUrl)
        val body = JSONObject()
            .put("header", submission.header.toWireJson())
            .put("nonce", SecureRemoteProtocol.encodeBase64Url(submission.nonce))
            .put("ciphertext", SecureRemoteProtocol.encodeBase64Url(submission.ciphertext))
            .toString()
        val response = execute(
            certificateSha256 = certificateSha256,
            request = Request.Builder()
                .url(endpoint(validRelayUrl, "/v1/pairing/claims"))
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        return parseCreated(response)
    }

    open suspend fun getClaim(
        relayUrl: String,
        certificateSha256: String?,
        claimId: String,
        claimAccessToken: String,
    ): PairingClaimStatus {
        val validRelayUrl = validatedRelayUrl(relayUrl)
        requireSafeId(claimId)
        validateClaimAccessToken(claimAccessToken)
        val response = execute(
            certificateSha256 = certificateSha256,
            request = Request.Builder()
                .url(endpoint(validRelayUrl, "/v1/pairing/claims/$claimId"))
                .header("Authorization", "Bearer $claimAccessToken")
                .get()
                .build(),
        )
        return parseStatus(response)
    }

    open suspend fun cancelClaim(
        relayUrl: String,
        certificateSha256: String?,
        claimId: String,
        claimAccessToken: String,
    ) {
        val validRelayUrl = validatedRelayUrl(relayUrl)
        requireSafeId(claimId)
        validateClaimAccessToken(claimAccessToken)
        execute(
            certificateSha256 = certificateSha256,
            request = Request.Builder()
                .url(endpoint(validRelayUrl, "/v1/pairing/claims/$claimId/cancel"))
                .header("Authorization", "Bearer $claimAccessToken")
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
    }

    private suspend fun execute(
        certificateSha256: String?,
        request: Request,
    ): String {
        val certificateHex = certificateSha256?.let {
            val bytes = SecureRemoteProtocol.decodeBase64Url(it)
            try {
                require(bytes.size == SHA256_SIZE)
                bytes.joinToString("") { byte -> "%02x".format(byte) }
            } finally {
                bytes.fill(0)
            }
        }
        val client = RelayCertificatePinning.withCertificatePin(baseClient, certificateHex)
        return withContext(Dispatchers.IO) {
            val path = request.url.encodedPath
            val operation = when {
                path.endsWith("/claims") -> "claim_create"
                path.endsWith("/cancel") -> "claim_cancel"
                else -> "claim_status"
            }
            MobileDiagnostics.info("Pairing", "relay_request_start method=${request.method} operation=$operation")
            try {
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.byteStream()?.let(::readLimited).orEmpty()
                    if (!response.isSuccessful) {
                        MobileDiagnostics.warn("Pairing", "relay_request_failed operation=$operation http=${response.code}")
                        throw RelayPairingException(
                            safeErrorCode(responseText) ?: "http_${response.code}",
                            "Relay pairing request failed.",
                        )
                    }
                    MobileDiagnostics.info("Pairing", "relay_request_complete operation=$operation http=${response.code}")
                    responseText
                }
            } catch (ex: RelayPairingException) {
                MobileDiagnostics.warn("Pairing", "relay_request_error operation=$operation code=${ex.code}")
                throw ex
            } catch (ex: IOException) {
                MobileDiagnostics.warn("Pairing", "relay_request_error operation=$operation code=network_error", ex)
                throw RelayPairingException("network_error", "Relay pairing network request failed.", ex)
            } catch (ex: RuntimeException) {
                MobileDiagnostics.warn("Pairing", "relay_request_error operation=$operation code=invalid_response", ex)
                throw RelayPairingException("invalid_response", "Relay pairing response was invalid.", ex)
            }
        }
    }

    private fun parseCreated(responseText: String): PairingClaimCreated {
        return try {
            val response = JSONObject(responseText)
            requireExactKeys(response, CREATED_KEYS)
            PairingClaimCreated(
                claimId = requireSafeId(requireString(response, "claimId")),
                claimAccessToken = requireString(response, "claimAccessToken")
                    .also(::validateClaimAccessToken),
                status = requireStatus(response, "status").also { require(it == STATUS_PENDING) },
                expiresAt = requireLong(response, "expiresAt"),
            )
        } catch (ex: RuntimeException) {
            throw RelayPairingException("invalid_response", "Relay pairing response was invalid.", ex)
        }
    }

    private fun parseStatus(responseText: String): PairingClaimStatus {
        return try {
            val response = JSONObject(responseText)
            requireExactKeys(response, STATUS_KEYS)
            val status = PairingClaimStatus(
                claimId = requireSafeId(requireString(response, "claimId")),
                status = requireStatus(response, "status"),
                updatedAt = requireLong(response, "updatedAt"),
                approvalHeader = optionalBytes(response, "approvalHeader", MAX_APPROVAL_HEADER_BYTES),
                approvalNonce = optionalBytes(response, "approvalNonce", GCM_NONCE_SIZE),
                approvalCiphertext = optionalBytes(
                    response,
                    "approvalCiphertext",
                    MAX_APPROVAL_CIPHERTEXT_BYTES,
                ),
            )
            val approvalValues = listOf(
                status.approvalHeader,
                status.approvalNonce,
                status.approvalCiphertext,
            )
            if (status.status == STATUS_APPROVED) {
                require(approvalValues.all { it != null })
                require(status.approvalHeader!!.isNotEmpty())
                require(status.approvalNonce!!.size == GCM_NONCE_SIZE)
                require(status.approvalCiphertext!!.size >= GCM_TAG_SIZE)
            } else {
                require(approvalValues.all { it == null })
            }
            status
        } catch (ex: RuntimeException) {
            throw RelayPairingException("invalid_response", "Relay pairing response was invalid.", ex)
        }
    }

    private fun PairingClaimHeader.toWireJson(): JSONObject {
        return JSONObject()
            .put("inviteId", inviteId)
            .put("tenantId", tenantId)
            .put("hostId", hostId)
            .put("claimId", claimId)
            .put("deviceId", deviceId)
            .put(
                "deviceAgreementPublicKey",
                SecureRemoteProtocol.encodeBase64Url(deviceAgreementPublicKey),
            )
            .put("deviceKeyVersion", deviceKeyVersion)
            .put("clientNonce", SecureRemoteProtocol.encodeBase64Url(clientNonce))
            .put("ciphertextLength", ciphertextLength)
    }

    private fun endpoint(relayUrl: String, path: String): String {
        return relayUrl.removeSuffix("/") + path
    }

    private fun validatedRelayUrl(relayUrl: String): String {
        return try {
            val uri = URI(relayUrl)
            require(uri.scheme == "https")
            require(!uri.host.isNullOrBlank())
            require(uri.userInfo == null)
            require(uri.query == null)
            require(uri.fragment == null)
            require(uri.path.isNullOrEmpty() || uri.path == "/")
            require(uri.port == -1 || uri.port in 1..65535)
            relayUrl
        } catch (ex: RuntimeException) {
            throw RelayPairingException(
                "invalid_relay_url",
                "Relay URL is invalid.",
                ex,
            )
        }
    }

    private fun requireExactKeys(value: JSONObject, expected: Set<String>) {
        require(value.keys().asSequence().toSet() == expected)
    }

    private fun requireString(value: JSONObject, key: String): String {
        return (value.get(key) as? String)
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Expected a non-empty string")
    }

    private fun requireLong(value: JSONObject, key: String): Long {
        val number = value.get(key) as? Number
            ?: throw IllegalArgumentException("Expected a number")
        val result = number.toLong()
        require(number.toString() == result.toString())
        return result
    }

    private fun requireStatus(value: JSONObject, key: String): String {
        return requireString(value, key).also { require(it in CLAIM_STATUSES) }
    }

    private fun requireSafeId(value: String): String {
        require(UUID_PATTERN.matches(value))
        return value
    }

    private fun optionalBytes(value: JSONObject, key: String, maxSize: Int): ByteArray? {
        if (value.isNull(key)) {
            return null
        }
        val encoded = value.get(key) as? String
            ?: throw IllegalArgumentException("Expected Base64Url text")
        if (encoded.isEmpty()) {
            return null
        }
        return SecureRemoteProtocol.decodeBase64Url(encoded).also { require(it.size <= maxSize) }
    }

    private fun validateClaimAccessToken(value: String) {
        val decoded = try {
            SecureRemoteProtocol.decodeBase64Url(value)
        } catch (ex: RuntimeException) {
            throw IllegalArgumentException("Invalid claim access token", ex)
        }
        try {
            require(decoded.size == CLAIM_ACCESS_TOKEN_SIZE)
        } finally {
            decoded.fill(0)
        }
    }

    private fun safeErrorCode(responseText: String): String? {
        return runCatching {
            val code = JSONObject(responseText).get("code") as? String
            code?.takeIf { SAFE_ERROR_CODE.matches(it) }
        }.getOrNull()
    }

    private fun readLimited(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            if (output.size() + read > MAX_RESPONSE_BYTES) {
                throw RelayPairingException(
                    "invalid_response",
                    "Relay pairing response exceeded the allowed size.",
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SHA256_SIZE = 32
        const val GCM_NONCE_SIZE = 12
        const val GCM_TAG_SIZE = 16
        const val CLAIM_ACCESS_TOKEN_SIZE = 32
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val MAX_APPROVAL_HEADER_BYTES = 4 * 1024
        const val MAX_APPROVAL_CIPHERTEXT_BYTES = 32 * 1024
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        val CLAIM_STATUSES = setOf(
            STATUS_PENDING,
            STATUS_APPROVED,
            "rejected",
            "expired",
            "cancelled",
            "consumed",
        )
        val UUID_PATTERN = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )
        val SAFE_ERROR_CODE = Regex("[a-z0-9_]{1,64}")
        val CREATED_KEYS = setOf("claimId", "claimAccessToken", "status", "expiresAt")
        val STATUS_KEYS = setOf(
            "claimId",
            "status",
            "updatedAt",
            "approvalHeader",
            "approvalNonce",
            "approvalCiphertext",
        )
    }
}

class RelayPairingException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
