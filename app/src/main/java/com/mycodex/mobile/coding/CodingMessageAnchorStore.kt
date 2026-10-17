package com.mycodex.mobile.coding

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

class CodingMessageAnchorStore(context: Context) {
    private val preferences = context.getSharedPreferences("coding_message_anchors", Context.MODE_PRIVATE)

    init {
        if (!preferences.contains("legacy_owner")) {
            val original = com.mycodex.mobile.devices.DevicePreferencesStore(context).load().currentDeviceId.orEmpty()
            check(preferences.edit().putString("legacy_owner", original).commit())
        }
    }

    fun load(sessionId: String): CodingMessageAnchor? {
        return load("legacy", sessionId)
    }

    fun load(scope: String, sessionId: String): CodingMessageAnchor? {
        val line = preferences.getString(key(scope, sessionId), null)
            ?: (if (scope == "legacy" || scope == preferences.getString("legacy_owner", "") + ":Coding") {
                preferences.getString(legacyKey(sessionId), null)
            } else null)
            ?: return null
        val parts = line.split('\t')
        if (parts.size != 6) {
            return null
        }

        return try {
            CodingMessageAnchor(
                sessionId = decode(parts[0]) ?: return null,
                messageId = decode(parts[1]) ?: return null,
                scrollOffset = parts[2].toInt(),
                wasAtBottom = parts[3].toBooleanStrict(),
                messageCount = parts[4].toInt(),
                savedAtEpochMillis = parts[5].toLong(),
            )
        } catch (_: RuntimeException) {
            null
        }?.takeIf { it.sessionId == sessionId }
    }

    fun save(anchor: CodingMessageAnchor) {
        save("legacy", anchor)
    }

    fun save(scope: String, anchor: CodingMessageAnchor) {
        preferences.edit()
            .putString(key(scope, anchor.sessionId), serialize(anchor))
            .commit().also { check(it) { "message_anchor_save_failed" } }
    }

    private fun serialize(anchor: CodingMessageAnchor): String {
        return listOf(
            encode(anchor.sessionId),
            encode(anchor.messageId),
            anchor.scrollOffset.toString(),
            anchor.wasAtBottom.toString(),
            anchor.messageCount.toString(),
            anchor.savedAtEpochMillis.toString(),
        ).joinToString("\t")
    }

    private fun key(scope: String, sessionId: String): String = "anchor_" + encode(scope) + "_" + encode(sessionId)

    private fun legacyKey(sessionId: String): String = "anchor_" + encode(sessionId)

    private fun encode(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decode(value: String): String? {
        return try {
            String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
        } catch (_: RuntimeException) {
            null
        }
    }
}
