package com.mycodex.mobile.devices

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

internal object RelayCertificatePinning {
    fun sha256Hex(value: ByteArray): String {
        require(value.size == 32)
        return value.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun normalizeSha256(value: String): String? {
        val normalized = value.trim().lowercase()
        return normalized.takeIf {
            it.length == 64 && it.all { character -> character in '0'..'9' || character in 'a'..'f' }
        }
    }

    fun configure(connection: HttpsURLConnection, certificateSha256: String) {
        val expected = decodeSha256(certificateSha256)
        val trustManager = PinnedTrustManager(expected)
        connection.sslSocketFactory = sslContext(trustManager).socketFactory
        connection.hostnameVerifier = { _, session -> peerLeafMatches(session, expected) }
    }

    fun withCertificatePin(client: OkHttpClient, certificateSha256: String?): OkHttpClient {
        if (certificateSha256 == null) return client
        val expected = decodeSha256(certificateSha256)
        val trustManager = PinnedTrustManager(expected)
        return client.newBuilder()
            .sslSocketFactory(sslContext(trustManager).socketFactory, trustManager)
            .hostnameVerifier { _, session -> peerLeafMatches(session, expected) }
            .build()
    }

    private fun sslContext(trustManager: X509TrustManager): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }
    }

    private fun peerLeafMatches(session: SSLSession, expected: ByteArray): Boolean {
        return try {
            val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
            leaf.checkValidity()
            MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(leaf.encoded),
                expected,
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeSha256(value: String): ByteArray {
        val normalized = normalizeSha256(value)
            ?: throw IllegalArgumentException("Certificate SHA-256 must contain exactly 64 hexadecimal characters")
        return ByteArray(32) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private class PinnedTrustManager(private val expected: ByteArray) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("Relay did not present a certificate")
            leaf.checkValidity()
            val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            if (!MessageDigest.isEqual(actual, expected)) {
                throw CertificateException("Embedded Relay certificate fingerprint mismatch")
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("Client certificate trust is not supported")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
