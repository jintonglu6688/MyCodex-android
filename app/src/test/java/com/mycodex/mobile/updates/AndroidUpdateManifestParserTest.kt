package com.mycodex.mobile.updates

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUpdateManifestParserTest {
    @Test
    fun parsesValidStableManifest() {
        val manifest = AndroidUpdateManifestParser().parse(json().toString().toByteArray())
        assertEquals(2, manifest.app.versionCode)
        assertEquals("MyCodex-Android-v1.1.0-c2.apk", manifest.app.asset.fileName)
    }

    @Test
    fun exampleManifestsUseSameSchema() {
        val root = java.io.File("../updates/android-stable.example.json")
            .takeIf { it.isFile } ?: java.io.File("updates/android-stable.example.json")
        assertEquals(2, AndroidUpdateManifestParser().parse(root.readBytes()).app.versionCode)
    }

    @Test
    fun rejectsInvalidTrustBoundaryFields() {
        val invalid = listOf<(JSONObject) -> Unit>(
            { it.put("schemaVersion", "2") },
            { it.put("channel", "test") },
            { it.put("generatedAt", "not-utc") },
            { it.put("packageBaseUrl", "http://api.gitcode.com/file/") },
            { it.getJSONObject("app").put("applicationId", "evil.app") },
            { it.getJSONObject("app").put("versionCode", 0) },
            { it.getJSONObject("app").put("minimumAndroidApi", 0) },
            { it.getJSONObject("app").getJSONObject("releaseNotes").put("zh-CN", "") },
            { it.getJSONObject("app").getJSONObject("asset").put("url", "https://evil.example/app.apk") },
            { it.getJSONObject("app").getJSONObject("asset").put("url", "../app.apk") },
            { it.getJSONObject("app").getJSONObject("asset").put("fileName", "../app.apk") },
            { root ->
                root.getJSONObject("app").getJSONObject("asset")
                    .put("fileName", "app%2fescape.apk")
                    .put("url", "app%2fescape.apk/download")
            },
            { root ->
                root.getJSONObject("app").getJSONObject("asset")
                    .put("fileName", "更新.apk")
                    .put("url", "更新.apk/download")
            },
            { it.getJSONObject("app").getJSONObject("asset").put("sha256", "bad") },
            { it.getJSONObject("app").getJSONObject("asset").put("size", MAX_APK_BYTES + 1) },
        )
        invalid.forEach { mutate ->
            val value = json().also(mutate)
            assertTrue(runCatching { AndroidUpdateManifestParser().parse(value.toString().toByteArray()) }.isFailure)
        }
    }

    @Test
    fun rejectsEmptyAndOversizedResponses() {
        val parser = AndroidUpdateManifestParser()
        assertTrue(runCatching { parser.parse(ByteArray(0)) }.isFailure)
        assertTrue(runCatching { parser.parse(ByteArray(MAX_MANIFEST_BYTES + 1)) }.isFailure)
    }

    @Test
    fun selectionUsesOnlyVersionCodeAndDeviceApi() {
        val manifest = AndroidUpdateManifestParser().parse(json().toString().toByteArray())
        assertTrue(selectManifest(manifest, InstalledAppVersion(1, "99.0", 26), 26) is ManifestCheckResult.Available)
        assertTrue(selectManifest(manifest, InstalledAppVersion(2, "1.0", 26), 26) is ManifestCheckResult.UpToDate)
        assertTrue(selectManifest(manifest.copy(app = manifest.app.copy(minimumAndroidApi = 35)), InstalledAppVersion(1, "1.0", 26), 34) is ManifestCheckResult.Incompatible)
    }

    @Test
    fun releaseNotesPreferChineseAndFallBackToEnglish() {
        assertEquals("中文", displayReleaseNotes(LocalizedReleaseNotes("中文", "English")))
        assertEquals("English", displayReleaseNotes(LocalizedReleaseNotes("", "English")))
        assertEquals("暂无更新说明", displayReleaseNotes(LocalizedReleaseNotes("", null)))
    }

    private fun json(): JSONObject = JSONObject()
        .put("schemaVersion", "1")
        .put("channel", "stable")
        .put("generatedAt", "2026-08-07T00:00:00Z")
        .put("packageBaseUrl", "https://api.gitcode.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/android-v1.1.0-c2/attach_files/")
        .put(
            "app",
            JSONObject()
                .put("applicationId", UPDATE_APPLICATION_ID)
                .put("versionCode", 2)
                .put("versionName", "1.1.0")
                .put("minimumAndroidApi", 26)
                .put("releaseDate", "2026-08-07")
                .put("releaseNotes", JSONObject().put("zh-CN", "更新说明").put("en-US", "Notes"))
                .put(
                    "asset",
                    JSONObject()
                        .put("url", "MyCodex-Android-v1.1.0-c2.apk/download")
                        .put("fileName", "MyCodex-Android-v1.1.0-c2.apk")
                        .put("sha256", "a".repeat(64))
                        .put("size", 10),
                ),
        )
}
