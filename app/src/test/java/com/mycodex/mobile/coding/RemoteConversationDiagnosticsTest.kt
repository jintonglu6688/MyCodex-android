package com.mycodex.mobile.coding

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class RemoteConversationDiagnosticsTest {
    @Test fun pagingTransport_doesNotEmbedSensitiveSentinelsInDiagnostics() {
        val text = File("src/main/java/com/mycodex/mobile/coding/CodingRemoteRepository.kt").readText() +
            File("src/main/java/com/mycodex/mobile/relay/RelayDeviceConnectionManager.kt").readText()
        listOf("BODY_SENTINEL_DO_NOT_LOG", "CURSOR_SENTINEL_DO_NOT_LOG", "SECRET_SENTINEL_DO_NOT_LOG").forEach {
            assertFalse(text.contains(it))
        }
    }
}
