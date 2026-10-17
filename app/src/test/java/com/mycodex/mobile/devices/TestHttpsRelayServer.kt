package com.mycodex.mobile.devices

import com.mycodex.mobile.security.SecureRemoteProtocol
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Collections
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

internal data class TestRelayRequest(
    val method: String,
    val path: String,
    val query: String?,
    val authorization: String?,
    val body: String,
)

internal class TestHttpsRelayServer : AutoCloseable {
    private val password = "test-only".toCharArray()
    val certificate: X509Certificate = loadCertificate()
    private val certificateSha256Bytes = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
    val certificateSha256: String = certificateSha256Bytes.joinToString("") { "%02x".format(it) }
    val certificateSha256Base64Url: String =
        SecureRemoteProtocol.encodeBase64Url(certificateSha256Bytes)
    val requests: MutableList<TestRelayRequest> = Collections.synchronizedList(mutableListOf())
    val claimStatusResponses: MutableList<String> = Collections.synchronizedList(mutableListOf())
    @Volatile
    var claimCreatedResponse: String =
        """{"claimId":"55555555-5555-5555-5555-555555555555","claimAccessToken":"${SecureRemoteProtocol.encodeBase64Url(ByteArray(32) { 9 })}","status":"pending","expiresAt":1893456600000}"""

    @Volatile
    var authChallengeResponse: String = """{"code":"not_configured"}"""

    @Volatile
    var authTicketResponse: String = """{"code":"not_configured"}"""

    @Volatile
    var requestBodyArrived: Boolean = false
        private set

    private val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        httpsConfigurator = HttpsConfigurator(buildSslContext())
        createContext("/v1/pairing/bind") { exchange ->
            record(exchange)
            requestBodyArrived = true
            respond(exchange, 200, """{"deviceId":"device_a","deviceToken":"device-token"}""")
        }
        createContext("/v1/pairing/claims") { exchange ->
            val request = record(exchange)
            when {
                request.path == "/v1/pairing/claims" && request.method == "POST" -> {
                    respond(
                        exchange,
                        201,
                        claimCreatedResponse,
                    )
                }
                request.path.endsWith("/cancel") && request.method == "POST" -> {
                    respond(exchange, 204, "")
                }
                request.method == "GET" -> {
                    val response = synchronized(claimStatusResponses) {
                        if (claimStatusResponses.isEmpty()) {
                            """{"claimId":"55555555-5555-5555-5555-555555555555","status":"pending","updatedAt":1893456002000,"approvalHeader":null,"approvalNonce":"","approvalCiphertext":""}"""
                        } else {
                            claimStatusResponses.removeAt(0)
                        }
                    }
                    respond(exchange, 200, response)
                }
                else -> respond(exchange, 404, """{"code":"not_found"}""")
            }
        }
        createContext("/v1/auth/challenges") { exchange ->
            record(exchange)
            respond(exchange, 200, authChallengeResponse)
        }
        createContext("/v1/auth/prove") { exchange ->
            record(exchange)
            respond(exchange, 200, authTicketResponse)
        }
        start()
    }

    val port: Int
        get() = server.address.port

    val baseUrl: String
        get() = "https://127.0.0.1:$port"

    override fun close() {
        server.stop(0)
    }

    private fun record(exchange: HttpExchange): TestRelayRequest {
        val body = exchange.requestBody.use {
            it.readBytes().toString(StandardCharsets.UTF_8)
        }
        val request = TestRelayRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            query = exchange.requestURI.rawQuery,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            body = body,
        )
        requests.add(request)
        return request
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val response = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        exchange.close()
    }

    private fun buildSslContext(): SSLContext {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, password)
            setKeyEntry("relay", loadPrivateKey(), password, arrayOf(certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers.keyManagers, null, null)
        }
    }

    private fun loadCertificate(): X509Certificate {
        val stream = requireNotNull(javaClass.getResourceAsStream("/tls/relay-test-cert.pem"))
        return stream.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    }

    private fun loadPrivateKey() = KeyFactory.getInstance("RSA").generatePrivate(
        PKCS8EncodedKeySpec(
            Base64.getDecoder().decode(
                requireNotNull(javaClass.getResource("/tls/relay-test-key.pem"))
                    .readText()
                    .substringAfter("-----BEGIN PRIVATE KEY-----")
                    .substringBefore("-----END PRIVATE KEY-----")
                    .filterNot(Char::isWhitespace),
            ),
        ),
    )
}
