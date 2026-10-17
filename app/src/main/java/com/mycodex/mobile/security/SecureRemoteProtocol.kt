package com.mycodex.mobile.security

import java.util.Base64

object SecureRemoteProtocol {
    const val VERSION = 1

    fun encodeBase64Url(value: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    }

    fun decodeBase64Url(value: String): ByteArray {
        require(BASE64_URL_PATTERN.matches(value))
        val decoded = Base64.getUrlDecoder().decode(value)
        require(encodeBase64Url(decoded) == value)
        return decoded
    }

    private val BASE64_URL_PATTERN = Regex("[A-Za-z0-9_-]*")
}
