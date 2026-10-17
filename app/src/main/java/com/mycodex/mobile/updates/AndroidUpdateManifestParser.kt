package com.mycodex.mobile.updates

import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException

internal class AndroidUpdateManifestParser {
    fun parse(bytes: ByteArray): AndroidUpdateManifest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MANIFEST_BYTES) { "manifest_size" }
        val text = bytes.toString(Charsets.UTF_8)
        try {
            val root = JSONObject(text)
            require(root.typedString("schemaVersion") == "1") { "schema" }
            require(root.typedString("channel") == STABLE_CHANNEL) { "channel" }
            val generatedAt = root.typedString("generatedAt")
            requireUtc(generatedAt)
            val base = validateBaseUrl(root.typedString("packageBaseUrl"))
            val appJson = root.typedObject("app")
            require(appJson.typedString("applicationId") == UPDATE_APPLICATION_ID) { "application_id" }
            val versionCode = appJson.typedLong("versionCode")
            require(versionCode in 1..Int.MAX_VALUE.toLong()) { "version_code" }
            val versionName = appJson.typedString("versionName").trim()
            require(versionName.isNotEmpty() && versionName.length <= 64) { "version_name" }
            val minimumApi = appJson.typedInt("minimumAndroidApi")
            require(minimumApi > 0) { "minimum_api" }
            val releaseDate = appJson.typedString("releaseDate")
            require(RELEASE_DATE.matches(releaseDate)) { "release_date" }
            val notesJson = appJson.typedObject("releaseNotes")
            val zhCn = notesJson.typedString("zh-CN").trim()
            require(zhCn.isNotEmpty() && zhCn.length <= 65_536) { "release_notes_zh_cn" }
            val enUs = notesJson.optionalString("en-US")?.trim()?.takeIf { it.isNotEmpty() }
            require(enUs == null || enUs.length <= 65_536) { "release_notes_en_us" }
            val assetJson = appJson.typedObject("asset")
            val fileName = assetJson.typedString("fileName")
            require(isSafeApkFileName(fileName)) { "asset_file_name" }
            val relativeUrl = assetJson.typedString("url")
            val assetUrl = validateAssetUrl(base, relativeUrl, fileName)
            val sha256 = assetJson.typedString("sha256")
            require(SHA_256.matches(sha256)) { "asset_sha256" }
            val size = assetJson.typedLong("size")
            require(size in 1..MAX_APK_BYTES) { "asset_size" }
            val release = AndroidUpdateAppRelease(
                applicationId = UPDATE_APPLICATION_ID,
                versionCode = versionCode,
                versionName = versionName,
                minimumAndroidApi = minimumApi,
                releaseDate = releaseDate,
                releaseNotes = LocalizedReleaseNotes(zhCn, enUs),
                asset = AndroidUpdateAsset(assetUrl, fileName, sha256, size),
            )
            return AndroidUpdateManifest(STABLE_CHANNEL, generatedAt, base, release, text)
        } catch (error: JSONException) {
            throw IllegalArgumentException("manifest_json", error)
        }
    }

    private fun requireUtc(value: String) {
        try {
            require(value.endsWith("Z") && Instant.parse(value).toString().endsWith("Z")) { "generated_at" }
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException("generated_at", error)
        }
    }

    private fun validateBaseUrl(value: String): URI {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host == "api.gitcode.com") { "package_base_url" }
        require(uri.rawQuery == null && uri.rawFragment == null && uri.userInfo == null) { "package_base_url" }
        require(BASE_PATH.matches(uri.path)) { "package_base_url" }
        return uri
    }

    private fun validateAssetUrl(base: URI, relative: String, fileName: String): URI {
        val candidate = URI(relative)
        require(!candidate.isAbsolute && candidate.rawQuery == null && candidate.rawFragment == null) { "asset_url" }
        require(relative == "$fileName/download") { "asset_url" }
        val resolved = base.resolve(candidate).normalize()
        require(resolved.scheme == "https" && resolved.host == base.host && resolved.path.startsWith(base.path)) { "asset_url" }
        return resolved
    }

    companion object {
        private val SHA_256 = Regex("^[0-9a-f]{64}$")
        private val RELEASE_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val BASE_PATH = Regex("^/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/android-v[^/]+-c[1-9][0-9]*/attach_files/$")

        internal fun isSafeApkFileName(value: String): Boolean =
            value.isNotBlank() &&
                value.length <= 128 &&
                SAFE_APK_FILE_NAME.matches(value) &&
                value == java.io.File(value).name &&
                !value.contains('/') &&
                !value.contains('\\')

        private val SAFE_APK_FILE_NAME = Regex("^[A-Za-z0-9._-]+\\.apk$")
        private const val STABLE_CHANNEL = "stable"
    }
}

private fun JSONObject.typedString(name: String): String =
    get(name).let { require(it is String) { name }; it }

private fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return typedString(name)
}

private fun JSONObject.typedObject(name: String): JSONObject =
    get(name).let { require(it is JSONObject) { name }; it }

private fun JSONObject.typedLong(name: String): Long =
    get(name).let {
        require(it is Number && it.toDouble().isFinite() && it.toDouble() == it.toLong().toDouble()) { name }
        it.toLong()
    }

private fun JSONObject.typedInt(name: String): Int {
    val value = typedLong(name)
    require(value in Int.MIN_VALUE..Int.MAX_VALUE) { name }
    return value.toInt()
}
