package com.mycodex.mobile.updates

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mycodex.mobile.ui.theme.MyCodexTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.net.URI

class UpdatePromptDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dialogShowsCompleteReleaseNotesAndOnlyTwoDecisions() {
        var later = 0
        var updateNow = 0
        compose.setContent {
            MyCodexTheme(darkTheme = false, dynamicColor = false) {
                UpdatePromptDialog(
                    release = release(),
                    onLater = { later += 1 },
                    onUpdateNow = { updateNow += 1 },
                )
            }
        }

        compose.onNodeWithText("发现新版本 1.2.0").assertExists()
        compose.onNodeWithTag("update_release_notes").assertExists()
        compose.onNodeWithText("第一项\n第二项\n第三项").assertExists()
        compose.onNodeWithText("安装包大小：1.0 KB").assertExists()
        compose.onNodeWithText("稍后").assertExists().assertHasClickAction().performClick()
        compose.onNodeWithText("立即更新").assertExists().assertHasClickAction().performClick()
        assertEquals(1, later)
        assertEquals(1, updateNow)
    }

    private fun release(): AndroidUpdateAppRelease = AndroidUpdateAppRelease(
        applicationId = UPDATE_APPLICATION_ID,
        versionCode = 6,
        versionName = "1.2.0",
        minimumAndroidApi = 26,
        releaseDate = "2026-08-08",
        releaseNotes = LocalizedReleaseNotes("第一项\n第二项\n第三项", null),
        asset = AndroidUpdateAsset(
            url = URI("https://api.gitcode.com/app.apk"),
            fileName = "MyCodex-Android-v1.2.0-c6.apk",
            sha256 = "a".repeat(64),
            size = 1024,
        ),
    )
}
