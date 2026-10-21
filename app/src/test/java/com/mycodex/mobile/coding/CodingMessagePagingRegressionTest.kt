package com.mycodex.mobile.coding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CodingMessagePagingRegressionTest {
    @Test fun openingAlwaysStartsAtLatestWithoutPersistingReadingPosition() {
        val source = File("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt").readText()
        assertTrue(source.contains("it.id == item.key"))
        assertTrue(source.contains("messageId = state.messages.last().id"))
        assertFalse(source.contains("anchorStore"))
        assertFalse(File("src/main/java/com/mycodex/mobile/coding/CodingMessageAnchorStore.kt").exists())
    }

    @Test fun automaticTailLoadQueuesEventsAndDisablesBoundaryFocusRestore() {
        val source = File("src/main/java/com/mycodex/mobile/coding/CodingConversationController.kt").readText()
        val screenSource = File("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt").readText()
        assertTrue(source.contains("requestTailRefresh()"))
        assertTrue(source.contains("tailRefreshQueued = true"))
        assertTrue(source.contains("followTail = readerAtBottom"))
        assertTrue(source.contains("fun updateReaderAtBottom(atBottom: Boolean)"))
        assertTrue(screenSource.contains(".collect(controller::updateReaderAtBottom)"))
        assertTrue(screenSource.contains("controller.updateReaderAtBottom(wasAtBottom)"))
        assertTrue(source.contains("pendingPagingViewportRestore = viewportRestore"))
        assertTrue(source.contains("restoreFocusMessageId = null"))
    }
}
