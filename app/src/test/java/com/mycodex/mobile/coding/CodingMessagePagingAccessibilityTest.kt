package com.mycodex.mobile.coding

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CodingMessagePagingAccessibilityTest {
    @Test
    fun paging_focus_restore_isBoundToStableMessageAndNativeRow() {
        val controller = File("src/main/java/com/mycodex/mobile/coding/CodingConversationController.kt").readText()
        val screen = File("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt").readText()
        val native = File("src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt").readText()
        assertTrue(controller.contains("fun onPagingViewportRestored"))
        assertTrue(controller.contains("pendingPagingViewportRestore = viewportRestore"))
        assertTrue(screen.contains("LaunchedEffect(state.pendingPagingViewportRestore)"))
        assertTrue(screen.contains("listState.scrollToItem(targetIndex, restore.scrollOffset)"))
        assertTrue(screen.indexOf("listState.scrollToItem(targetIndex, restore.scrollOffset)") < screen.indexOf("controller.onPagingViewportRestored"))
        assertTrue(screen.contains("state.restoreFocusMessageId == message.id"))
        assertTrue(screen.contains("controller::onPagingFocusRestored"))
        assertTrue(native.contains("AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS"))
        assertTrue(!screen.contains("onFocusChanged = { controller.load"))
    }
}
