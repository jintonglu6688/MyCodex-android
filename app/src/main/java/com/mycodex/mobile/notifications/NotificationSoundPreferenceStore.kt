package com.mycodex.mobile.notifications

import android.annotation.SuppressLint
import android.content.Context

internal class NotificationSoundPreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun load(): NotificationSoundSettings = NotificationSoundSettings(
        turnCompletedEnabled = preferences.getBoolean(TurnCompletedKey, true),
        subAgentCompletedEnabled = preferences.getBoolean(SubAgentCompletedKey, true),
        approvalRequestedEnabled = preferences.getBoolean(ApprovalRequestedKey, true),
        messageSentEnabled = preferences.getBoolean(MessageSentKey, true),
    )

    @SuppressLint("UseKtx") // The KTX edit extension discards the synchronous commit result used by the UI.
    fun save(settings: NotificationSoundSettings): Boolean = preferences.edit()
        .putBoolean(TurnCompletedKey, settings.turnCompletedEnabled)
        .putBoolean(SubAgentCompletedKey, settings.subAgentCompletedEnabled)
        .putBoolean(ApprovalRequestedKey, settings.approvalRequestedEnabled)
        .putBoolean(MessageSentKey, settings.messageSentEnabled)
        .commit()

    private companion object {
        private const val PreferencesName = "notification_sound_preferences"
        private const val TurnCompletedKey = "turn_completed"
        private const val SubAgentCompletedKey = "subagent_completed"
        private const val ApprovalRequestedKey = "approval_requested"
        private const val MessageSentKey = "message_sent"
    }
}
