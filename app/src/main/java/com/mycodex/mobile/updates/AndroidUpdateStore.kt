package com.mycodex.mobile.updates

import android.content.Context

internal interface UpdateStateStore {
    var lastAutomaticCheckAttemptAt: Long?
    var lastPromptedVersionCode: Long?
    var lastPromptedAttemptWindow: Long?
    var lastKnownManifestJson: String?
    fun saveVerified(identity: UpdatePackageIdentity, relativePath: String)
    fun loadVerified(): Pair<UpdatePackageIdentity, String>?
    fun clearVerified()
}

internal class AndroidUpdateStore(context: Context) : UpdateStateStore {
    private val preferences = context.getSharedPreferences("android_updates", Context.MODE_PRIVATE)

    override var lastAutomaticCheckAttemptAt: Long?
        get() = preferences.getLong(KEY_LAST_ATTEMPT, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        set(value) = editLong(KEY_LAST_ATTEMPT, value)

    override var lastPromptedVersionCode: Long?
        get() = preferences.getLong(KEY_LAST_PROMPT_VERSION, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        set(value) = editLong(KEY_LAST_PROMPT_VERSION, value)

    override var lastPromptedAttemptWindow: Long?
        get() = preferences.getLong(KEY_LAST_PROMPT_WINDOW, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        set(value) = editLong(KEY_LAST_PROMPT_WINDOW, value)

    override var lastKnownManifestJson: String?
        get() = preferences.getString(KEY_MANIFEST, null)
        set(value) = preferences.edit().apply { if (value == null) remove(KEY_MANIFEST) else putString(KEY_MANIFEST, value) }.apply()

    override fun saveVerified(identity: UpdatePackageIdentity, relativePath: String) {
        preferences.edit()
            .putLong(KEY_VERIFIED_VERSION, identity.versionCode)
            .putString(KEY_VERIFIED_NAME, identity.fileName)
            .putLong(KEY_VERIFIED_SIZE, identity.size)
            .putString(KEY_VERIFIED_SHA, identity.sha256)
            .putString(KEY_VERIFIED_PATH, relativePath)
            .apply()
    }

    override fun loadVerified(): Pair<UpdatePackageIdentity, String>? {
        val version = preferences.getLong(KEY_VERIFIED_VERSION, Long.MIN_VALUE)
        val name = preferences.getString(KEY_VERIFIED_NAME, null)
        val size = preferences.getLong(KEY_VERIFIED_SIZE, Long.MIN_VALUE)
        val sha = preferences.getString(KEY_VERIFIED_SHA, null)
        val path = preferences.getString(KEY_VERIFIED_PATH, null)
        if (version <= 0 || name.isNullOrBlank() || size <= 0 || sha.isNullOrBlank() || path.isNullOrBlank()) return null
        return UpdatePackageIdentity(version, name, size, sha) to path
    }

    override fun clearVerified() {
        preferences.edit()
            .remove(KEY_VERIFIED_VERSION)
            .remove(KEY_VERIFIED_NAME)
            .remove(KEY_VERIFIED_SIZE)
            .remove(KEY_VERIFIED_SHA)
            .remove(KEY_VERIFIED_PATH)
            .apply()
    }

    private fun editLong(key: String, value: Long?) {
        preferences.edit().apply { if (value == null) remove(key) else putLong(key, value) }.apply()
    }

    companion object {
        private const val KEY_LAST_ATTEMPT = "lastAutomaticCheckAttemptAt"
        private const val KEY_LAST_PROMPT_VERSION = "lastPromptedVersionCode"
        private const val KEY_LAST_PROMPT_WINDOW = "lastPromptedAttemptWindow"
        private const val KEY_MANIFEST = "lastKnownAvailableManifest"
        private const val KEY_VERIFIED_VERSION = "verifiedVersionCode"
        private const val KEY_VERIFIED_NAME = "verifiedFileName"
        private const val KEY_VERIFIED_SIZE = "verifiedSize"
        private const val KEY_VERIFIED_SHA = "verifiedSha256"
        private const val KEY_VERIFIED_PATH = "verifiedPackageRelativePath"
    }
}
