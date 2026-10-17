package com.mycodex.mobile.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.coroutineContext

internal interface UpdateHttpTransport {
    suspend fun get(url: URI): UpdateHttpResponse
    suspend fun downloadTo(url: URI, destination: File, maximumBytes: Long, onProgress: (Long) -> Unit): UpdateDownloadResponse
}

internal data class UpdateHttpResponse(
    val code: Int,
    val finalUrl: URI,
    val body: ByteArray,
)

internal data class UpdateDownloadResponse(val code: Int, val finalUrl: URI, val bytesWritten: Long)

internal class OkHttpUpdateTransport : UpdateHttpTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    override suspend fun get(url: URI): UpdateHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url.toString()).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val finalUrl = URI(response.request.url.toString())
                        requireAllowedResponseChain(response)
                        val body = response.body?.byteStream()?.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(16 * 1024)
                            while (output.size() <= MAX_MANIFEST_BYTES) {
                                val read = input.read(buffer, 0, minOf(buffer.size, MAX_MANIFEST_BYTES + 1 - output.size()))
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                            require(output.size() <= MAX_MANIFEST_BYTES) { "manifest_size" }
                            output.toByteArray()
                        } ?: ByteArray(0)
                        continuation.resume(UpdateHttpResponse(response.code, finalUrl, body))
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    override suspend fun downloadTo(
        url: URI,
        destination: File,
        maximumBytes: Long,
        onProgress: (Long) -> Unit,
    ): UpdateDownloadResponse = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url.toString()).get().build())
        call.execute().use { response ->
            val finalUrl = URI(response.request.url.toString())
            requireAllowedResponseChain(response)
            var written = 0L
            destination.outputStream().buffered().use { output ->
                val input = response.body?.byteStream() ?: throw IOException("empty_body")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    require(written <= maximumBytes) { "download_size" }
                    output.write(buffer, 0, read)
                    onProgress(written)
                }
            }
            UpdateDownloadResponse(response.code, finalUrl, written)
        }
    }
}

private fun requireAllowedResponseChain(response: Response) {
    var current: Response? = response
    while (current != null) {
        require(isAllowedGitCodeHttps(URI(current.request.url.toString()))) { "redirect_host" }
        current = current.priorResponse
    }
}

internal class AndroidUpdateRepository(
    private val manifestUrl: URI,
    private val parser: AndroidUpdateManifestParser,
    private val transport: UpdateHttpTransport,
    private val cacheRoot: File,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun check(): AndroidUpdateManifest {
        require(isAllowedGitCodeHttps(manifestUrl)) { "manifest_url" }
        val response = transport.get(manifestUrl)
        require(response.code in 200..299) { "manifest_http_${response.code / 100}xx" }
        require(response.body.size <= MAX_MANIFEST_BYTES) { "manifest_size" }
        return parser.parse(response.body)
    }

    suspend fun download(
        manifest: AndroidUpdateManifest,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(ioDispatcher) {
        val releaseDir = File(cacheRoot, manifest.app.versionCode.toString()).apply { mkdirs() }
        require(releaseDir.canonicalPath.startsWith(cacheRoot.canonicalPath + File.separator)) { "cache_path" }
        val part = File(releaseDir, manifest.app.asset.fileName + ".part")
        val final = File(releaseDir, manifest.app.asset.fileName)
        part.delete()
        final.delete()
        try {
            val response = transport.downloadTo(
                url = manifest.app.asset.url,
                destination = part,
                maximumBytes = minOf(manifest.app.asset.size, MAX_APK_BYTES),
            ) { written -> onProgress(written, manifest.app.asset.size) }
            require(response.code in 200..299) { "download_http_${response.code / 100}xx" }
            require(part.length() == manifest.app.asset.size) { "download_size" }
            part
        } catch (error: Exception) {
            part.delete()
            throw error
        }
    }

    fun finalFile(manifest: AndroidUpdateManifest): File =
        File(File(cacheRoot, manifest.app.versionCode.toString()), manifest.app.asset.fileName)

    fun promote(part: File, manifest: AndroidUpdateManifest): File {
        val final = finalFile(manifest)
        final.delete()
        require(part.renameTo(final)) { "cache_promote" }
        return final
    }

    fun cleanPartialFiles() {
        cacheRoot.mkdirs()
        cacheRoot.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.forEach(File::delete)
    }

    fun deleteCached(file: File?) {
        file?.takeIf { it.canonicalPath.startsWith(cacheRoot.canonicalPath + File.separator) }?.delete()
    }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

internal fun isAllowedGitCodeHttps(uri: URI): Boolean =
    uri.scheme == "https" && uri.host?.let { it == "gitcode.com" || it.endsWith(".gitcode.com") } == true
