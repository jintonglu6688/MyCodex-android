package com.mycodex.mobile

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTaskForegroundServiceSourceContractTest {
    @Test
    fun acceptedRemoteTaskUsesSharedForegroundConnectionAndVibration() {
        val manifest = readProjectFile("src/main/AndroidManifest.xml")
        val appSource = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val screenSource = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")
        val serviceSource = readProjectFile("src/main/java/com/mycodex/mobile/relay/RemoteTaskForegroundService.kt")

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"))
        assertTrue(manifest.contains("android.permission.VIBRATE"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"remoteMessaging\""))
        assertTrue(appSource.contains("relayConnection(currentDevice)"))
        assertTrue(
            Regex(
                """LaunchedEffect\(state\.generating\)\s*\{[\s\S]{0,500}RemoteTaskForegroundService\.start[\s\S]{0,500}RemoteTaskForegroundService\.stop""",
            ).containsMatchIn(screenSource),
        )
        assertTrue(serviceSource.contains("startForeground"))
        assertTrue(serviceSource.contains("VibrationEffect.createOneShot"))
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)
            ?: error("Could not find project file: $relativePath")
    }
}
