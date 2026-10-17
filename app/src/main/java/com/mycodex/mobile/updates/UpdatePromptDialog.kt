package com.mycodex.mobile.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun UpdatePromptDialog(
    release: AndroidUpdateAppRelease,
    onLater: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("发现新版本 ${release.versionName}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("安装包大小：${formatBytes(release.asset.size)}")
                Text(
                    text = "更新说明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = displayReleaseNotes(release.releaseNotes),
                    modifier = Modifier.testTag("update_release_notes"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdateNow, modifier = Modifier.testTag("update_now")) {
                Text("立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, modifier = Modifier.testTag("update_later")) {
                Text("稍后")
            }
        },
    )
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

internal fun displayReleaseNotes(notes: LocalizedReleaseNotes): String =
    notes.zhCn.takeIf { it.isNotBlank() }
        ?: notes.enUs?.takeIf { it.isNotBlank() }
        ?: "暂无更新说明"
