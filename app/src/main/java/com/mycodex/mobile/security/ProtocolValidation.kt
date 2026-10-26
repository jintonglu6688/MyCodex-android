package com.mycodex.mobile.security

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI

/** Validation rules shared by the pairing and Relay parsers, so tightening one tightens all. */
internal object ProtocolValidation {
    val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val SAFE_ERROR_CODE = Regex("[a-z0-9_]{1,64}")

    fun requireRelayUrl(value: String) {
        val uri = URI(value)
        require(uri.scheme == "https")
        require(!uri.host.isNullOrBlank())
        require(uri.userInfo == null)
        require(uri.query == null)
        require(uri.fragment == null)
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        require(uri.port == -1 || uri.port in 1..65535)
    }

    fun requireExactKeys(value: JSONObject, expected: Set<String>) {
        require(value.keys().asSequence().toSet() == expected)
    }

    fun requireLong(value: JSONObject, key: String): Long {
        val number = value.get(key) as? Number
            ?: throw IllegalArgumentException("Expected a number")
        val result = number.toLong()
        require(number.toString() == result.toString())
        return result
    }

    fun readLimited(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            require(output.size() + read <= maxBytes) { "Response exceeded the allowed size" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    fun safeErrorCode(responseText: String): String? {
        return runCatching {
            (JSONObject(responseText).get("code") as? String)?.takeIf(SAFE_ERROR_CODE::matches)
        }.getOrNull()
    }
}
