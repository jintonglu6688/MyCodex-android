package com.mycodex.mobile.devices

import okhttp3.OkHttpClient
import okhttp3.Dns
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.io.IOException
import java.net.URL
import java.net.InetAddress
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession

class RelayCertificatePinningTest {
    @Test
    fun sha256Hex_encodesExactly32Bytes() {
        assertEquals(
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            RelayCertificatePinning.sha256Hex(ByteArray(32) { it.toByte() }),
        )
        assertThrows(IllegalArgumentException::class.java) {
            RelayCertificatePinning.sha256Hex(ByteArray(31))
        }
    }

    @Test
    fun normalizeSha256_acceptsHexAndReturnsLowercase() {
        assertEquals("ab".repeat(32), RelayCertificatePinning.normalizeSha256("  ${"AB".repeat(32)}  "))
    }

    @Test
    fun pinnedTrustManager_acceptsMatchingLeaf() {
        TestHttpsRelayServer().use { server ->
            val connection = pairingConnection(server)
            try {
                RelayCertificatePinning.configure(connection, server.certificateSha256)
                connection.outputStream.use { it.write("{}".toByteArray()) }
                assertEquals(200, connection.responseCode)
            } finally {
                connection.disconnect()
            }
        }
    }

    @Test
    fun pinnedTrustManager_rejectsMismatchedLeaf() {
        TestHttpsRelayServer().use { server ->
            val connection = pairingConnection(server)
            try {
                RelayCertificatePinning.configure(connection, changedFingerprint(server.certificateSha256))
                assertThrows(IOException::class.java) {
                    connection.outputStream.use { it.write("{}".toByteArray()) }
                }
                assertFalse(server.requestBodyArrived)
            } finally {
                connection.disconnect()
            }
        }
    }

    @Test
    fun pinnedHostnameVerifier_acceptsOnlyMatchingPeerLeaf() {
        TestHttpsRelayServer().use { server ->
            val matching = URL("https://localhost").openConnection() as HttpsURLConnection
            RelayCertificatePinning.configure(matching, server.certificateSha256)
            assertTrue(matching.hostnameVerifier.verify("changed.example", sslSession(server.certificate)))

            val mismatched = URL("https://localhost").openConnection() as HttpsURLConnection
            RelayCertificatePinning.configure(mismatched, changedFingerprint(server.certificateSha256))
            assertFalse(mismatched.hostnameVerifier.verify("localhost", sslSession(server.certificate)))
        }
    }

    @Test
    fun withCertificatePin_nullReturnsOriginalClient() {
        val client = OkHttpClient()

        assertSame(client, RelayCertificatePinning.withCertificatePin(client, null))
    }

    @Test
    fun withCertificatePin_matchingLeafAllowsOkHttpDespiteHostnameMismatch() {
        TestHttpsRelayServer().use { server ->
            val client = clientForTestHost()
            val pinned = RelayCertificatePinning.withCertificatePin(client, server.certificateSha256)

            pinned.newCall(pairingRequest(server)).execute().use { response ->
                assertEquals(200, response.code)
            }
            assertTrue(server.requestBodyArrived)
        }
    }

    @Test
    fun withCertificatePin_mismatchedLeafRejectsOkHttpBeforeRequestBody() {
        TestHttpsRelayServer().use { server ->
            val client = RelayCertificatePinning.withCertificatePin(
                clientForTestHost(),
                changedFingerprint(server.certificateSha256),
            )

            assertThrows(IOException::class.java) {
                client.newCall(pairingRequest(server)).execute().close()
            }
            assertFalse(server.requestBodyArrived)
        }
    }

    @Test
    fun withCertificatePin_unpinnedOkHttpRejectsSelfSignedLeaf() {
        TestHttpsRelayServer().use { server ->
            val client = clientForTestHost()

            assertThrows(IOException::class.java) {
                client.newCall(pairingRequest(server)).execute().close()
            }
            assertFalse(server.requestBodyArrived)
        }
    }

    private fun clientForTestHost(): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    listOf(InetAddress.getLoopbackAddress())
            })
            .build()
    }

    private fun pairingRequest(server: TestHttpsRelayServer): Request {
        return Request.Builder()
            .url("https://relay.invalid:${server.port}/v1/pairing/bind")
            .post("{}".toRequestBody())
            .build()
    }

    private fun pairingConnection(server: TestHttpsRelayServer): HttpsURLConnection {
        return (URL("https://127.0.0.1:${server.port}/v1/pairing/bind").openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
        }
    }

    private fun sslSession(certificate: X509Certificate): SSLSession {
        return Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPeerCertificates" -> arrayOf(certificate)
                "isValid" -> true
                else -> defaultValue(method.returnType)
            }
        } as SSLSession
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }

    private fun changedFingerprint(value: String): String {
        return (if (value[0] == '0') '1' else '0') + value.substring(1)
    }
}
