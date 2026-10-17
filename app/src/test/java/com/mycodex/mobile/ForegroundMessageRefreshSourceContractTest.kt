package com.mycodex.mobile

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundMessageRefreshSourceContractTest {
    @Test
    fun foregroundResumeReconnectsRelayAndReloadsCurrentConversation() {
        val appSource = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val screensSource = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")
        val relaySource = readProjectFile("src/main/java/com/mycodex/mobile/relay/RelayDeviceConnectionManager.kt")

        assertTrue(appSource.contains("LifecycleEventEffect(Lifecycle.Event.ON_RESUME)"))
        assertTrue(appSource.contains("reconnectForForegroundEvents()"))
        assertTrue(
            Regex("""LaunchedEffect\(foregroundRefreshToken\)\s*\{[\s\S]{0,300}controller\.reload\(\)""")
                .containsMatchIn(screensSource),
        )
        assertTrue(relaySource.contains("suspend fun reconnectForForegroundEvents()"))
        assertTrue(relaySource.contains("Remote websocket replaced"))
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
