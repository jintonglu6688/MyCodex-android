package com.mycodex.mobile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatTransientStatusSourceContractTest {
    @Test
    fun chatScreensDoNotExposeTransientStatusTextOrToasts() {
        val appSource = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val composerSource = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingComposer.kt")
        val screensSource = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")
        val messagePageSource = screensSource
            .substringAfter("fun CodingMessageListPage(")
            .substringBefore("fun CodingSessionSettingsPage(")

        assertFalse(composerSource.contains("statusText"))
        assertFalse(composerSource.contains("LiveRegionMode"))
        assertFalse(messagePageSource.contains("operationStatusText"))
        assertFalse(messagePageSource.contains("externalStatusText"))
        assertFalse(messagePageSource.contains("Toast.makeText"))
        assertFalse(appSource.contains("ChatOperationStatusText"))
        assertFalse(appSource.contains("PlanModeStatusText"))
        assertFalse(appSource.contains("Toast.makeText"))
    }

    @Test
    fun successfulOperationReceiptsDoNotRemainInContentAreas() {
        val appSource = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val screensSource = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")
        val deviceSource = readProjectFile("src/main/java/com/mycodex/mobile/devices/DeviceScreens.kt")

        assertFalse(appSource.contains("已创建。"))
        assertFalse(screensSource.contains("operationResult.statusText"))
        assertFalse(screensSource.contains("updateResult.statusText"))
        assertFalse(screensSource.contains("会话标题未更改。"))
        assertFalse(screensSource.contains("会话设置已更新。"))
        assertFalse(deviceSource.contains("连接测试成功，"))
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not find project file: $relativePath")
        return file.readText(Charsets.UTF_8)
    }
}
