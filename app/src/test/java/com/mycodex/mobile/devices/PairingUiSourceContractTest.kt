package com.mycodex.mobile.devices

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PairingUiSourceContractTest {
    @Test
    fun cancelRemainsEnabledWhilePairingIsPending() {
        val source = readProjectFile(
            "src/main/java/com/mycodex/mobile/devices/DeviceScreens.kt",
        )
        val cancelControl = source.substringBefore(".testTag(\"pairing_cancel\")")
            .substringAfterLast("OutlinedButton(")

        assertTrue(cancelControl.contains("enabled = true"))
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
        )
        return candidates.firstOrNull { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?: error("Unable to locate $relativePath")
    }
}
