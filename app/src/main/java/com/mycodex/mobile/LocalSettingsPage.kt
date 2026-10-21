package com.mycodex.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mycodex.mobile.notifications.NotificationSoundCue
import com.mycodex.mobile.notifications.NotificationSoundSettings
import com.mycodex.mobile.ui.theme.ThemeMode

@Composable
internal fun LocalSettingsPage(
    themeMode: ThemeMode,
    focusRequest: ThemeFocusRequest?,
    onFocusRestored: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Boolean,
    notificationSoundSettings: NotificationSoundSettings,
    onNotificationSoundSettingChanged: (NotificationSoundCue, Boolean) -> Boolean,
    contentPadding: PaddingValues,
) {
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var failureGeneration by rememberSaveable { mutableIntStateOf(0) }

    fun updateSoundSetting(cue: NotificationSoundCue, enabled: Boolean) {
        if (onNotificationSoundSettingChanged(cue, enabled)) {
            errorText = null
        } else {
            failureGeneration += 1
            errorText = "无法保存音效设置，请重试。"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            ThemeSettingsGroup(
                themeMode = themeMode,
                focusRequest = focusRequest,
                onFocusRestored = onFocusRestored,
                onThemeModeSelected = { mode ->
                    if (onThemeModeSelected(mode)) {
                        errorText = null
                    } else {
                        failureGeneration += 1
                        errorText = "无法保存主题设置，请重试。"
                    }
                },
            )
        }
        item { AppSettingsHeading("音效提示") }
        item {
            AppSettingsGroup {
                NotificationSoundSettingRow(
                    title = "对话结束时播放音效",
                    enabled = notificationSoundSettings.turnCompletedEnabled,
                    testTag = "local_sound_turn_completed",
                    onClick = {
                        updateSoundSetting(
                            NotificationSoundCue.TurnCompleted,
                            !notificationSoundSettings.turnCompletedEnabled,
                        )
                    },
                )
                AppSettingsGroupDivider()
                NotificationSoundSettingRow(
                    title = "子代理结束时播放音效",
                    enabled = notificationSoundSettings.subAgentCompletedEnabled,
                    testTag = "local_sound_subagent_completed",
                    onClick = {
                        updateSoundSetting(
                            NotificationSoundCue.SubAgentCompleted,
                            !notificationSoundSettings.subAgentCompletedEnabled,
                        )
                    },
                )
                AppSettingsGroupDivider()
                NotificationSoundSettingRow(
                    title = "收到审批或提问时播放音效",
                    enabled = notificationSoundSettings.approvalRequestedEnabled,
                    testTag = "local_sound_approval_requested",
                    onClick = {
                        updateSoundSetting(
                            NotificationSoundCue.ApprovalRequested,
                            !notificationSoundSettings.approvalRequestedEnabled,
                        )
                    },
                )
                AppSettingsGroupDivider()
                NotificationSoundSettingRow(
                    title = "消息发送成功后播放音效",
                    enabled = notificationSoundSettings.messageSentEnabled,
                    testTag = "local_sound_message_sent",
                    onClick = {
                        updateSoundSetting(
                            NotificationSoundCue.MessageSent,
                            !notificationSoundSettings.messageSentEnabled,
                        )
                    },
                )
            }
        }
        errorText?.let { message ->
            item {
                key(failureGeneration) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsGroup(
    themeMode: ThemeMode,
    focusRequest: ThemeFocusRequest?,
    onFocusRestored: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local_theme_group"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            ThemeMode.entries.forEachIndexed { index, mode ->
                val selected = mode == themeMode
                val title = when (mode) {
                    ThemeMode.System -> "跟随系统"
                    ThemeMode.Light -> "浅色"
                    ThemeMode.Dark -> "深色"
                }
                val body = if (selected) "已选中" else "未选中"
                val tag = "local_theme_${mode.persistedValue}"
                key(tag) {
                    NativeAccessibleActionRow(
                        title = title,
                        body = body,
                        testTag = tag,
                        restoreFocus = focusRequest?.targetTag == tag,
                        restoreFocusGeneration = focusRequest?.generation,
                        onFocusRestored = onFocusRestored,
                        onClick = {
                            if (mode != themeMode) {
                                onThemeModeSelected(mode)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentDescription = "$title，$body",
                    )
                }
                if (index != ThemeMode.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSoundSettingRow(
    title: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val stateText = if (enabled) "已开启" else "已关闭"
    NativeAccessibleActionRow(
        title = title,
        body = stateText,
        testTag = testTag,
        restoreFocus = false,
        onFocusRestored = {},
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "$title，$stateText",
    )
}
