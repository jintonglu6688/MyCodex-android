package com.mycodex.mobile.updates

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

class AndroidUpdateRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun downloadUsesPartFileReportsProgressAndPromotes() = runTest {
        val body = "0123456789".toByteArray()
        val transport = FakeTransport(body)
        val parser = AndroidUpdateManifestParser()
        val manifest = parser.parse(validManifest(body.size.toLong()).toByteArray())
        val repository = AndroidUpdateRepository(URI("https://api.gitcode.com/manifest"), parser, transport, temporary.root)
        var progress = 0L
        val part = repository.download(manifest) { bytes, _ -> progress = bytes }
        assertTrue(part.name.endsWith(".part"))
        assertEqualsLong(body.size.toLong(), progress)
        val final = repository.promote(part, manifest)
        assertArrayEquals(body, final.readBytes())
        assertFalse(part.exists())
    }

    @Test
    fun failureDeletesPartialFile() = runTest {
        val transport = FakeTransport("too large".toByteArray())
        val parser = AndroidUpdateManifestParser()
        val manifest = parser.parse(validManifest(1).toByteArray())
        val repository = AndroidUpdateRepository(URI("https://api.gitcode.com/manifest"), parser, transport, temporary.root)
        assertTrue(runCatching { repository.download(manifest) { _, _ -> } }.isFailure)
        assertFalse(temporary.root.walkTopDown().any { it.name.endsWith(".part") })
    }

    private fun validManifest(size: Long): String = """{"schemaVersion":"1","channel":"stable","generatedAt":"2026-08-07T00:00:00Z","packageBaseUrl":"https://api.gitcode.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/android-v1.1.0-c2/attach_files/","app":{"applicationId":"com.mycodex.mobile","versionCode":2,"versionName":"1.1.0","minimumAndroidApi":26,"releaseDate":"2026-08-07","releaseNotes":{"zh-CN":"说明"},"asset":{"url":"MyCodex-Android-v1.1.0-c2.apk/download","fileName":"MyCodex-Android-v1.1.0-c2.apk","sha256":"${"a".repeat(64)}","size":$size}}}"""

    private fun assertEqualsLong(expected: Long, actual: Long) = org.junit.Assert.assertEquals(expected, actual)

    private class FakeTransport(private val body: ByteArray) : UpdateHttpTransport {
        override suspend fun get(url: URI) = UpdateHttpResponse(200, url, ByteArray(0))
        override suspend fun downloadTo(url: URI, destination: File, maximumBytes: Long, onProgress: (Long) -> Unit): UpdateDownloadResponse {
            if (body.size > maximumBytes) {
                destination.writeBytes(body.copyOf(maximumBytes.toInt().coerceAtLeast(1)))
                throw IllegalArgumentException("download_size")
            }
            destination.writeBytes(body)
            onProgress(body.size.toLong())
            return UpdateDownloadResponse(200, url, body.size.toLong())
        }
    }
}
