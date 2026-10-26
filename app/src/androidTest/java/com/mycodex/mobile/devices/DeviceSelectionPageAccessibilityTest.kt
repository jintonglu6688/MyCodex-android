package com.mycodex.mobile.devices

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mycodex.mobile.AppFocusTarget
import com.mycodex.mobile.NativeControlsTestActivity
import com.mycodex.mobile.ui.theme.MyCodexTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeviceSelectionPageAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<NativeControlsTestActivity>()

    @Test
    fun connectedDevicePartialReadErrorIsVisibleAndHasExplicitNativeRetry() {
        val status = "已连接，但待处理审批未能刷新，请重试连接。"
        var connects = 0
        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                DeviceDetailPage(
                    device = device("a"), current = true,
                    selectionState = DeviceSelectionState(
                        currentHostId = "a", connected = true, targetHostId = "a", statusText = status,
                    ),
                    onConnect = { connects += 1 }, onCancelSwitch = {}, onOpenSettings = {},
                    onDelete = {}, contentPadding = PaddingValues(),
                )
            }
        }

        composeRule.onNodeWithText(status).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("device_connect_button").performScrollTo()
        composeRule.runOnIdle {
            val connect = view<Button>("device_connect_button")
            assertTrue(connect.isEnabled)
            assertEquals("重新检查连接", connect.contentDescription)
            assertEquals(0, connects)
            connect.performClick()
            assertEquals(1, connects)
        }
        composeRule.onNodeWithTag("device_settings_button").performScrollTo()
        composeRule.runOnIdle { assertTrue(view<Button>("device_settings_button").isEnabled) }
    }

    @Test
    fun detailNeedsExplicitActivationAndExposesDisabledDuplicatesAndReachableCancel() {
        var state by mutableStateOf(DeviceSelectionState(currentHostId = "a", connected = true))
        var connects = 0
        var cancels = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MyCodexTheme(dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        DeviceDetailPage(
                            device = device("b"), current = false, selectionState = state,
                            onConnect = {
                                connects += 1
                                state = state.copy(switching = true, targetHostId = "b", statusText = "正在检查并连接。")
                            },
                            onCancelSwitch = { cancels += 1 }, onOpenSettings = {}, onDelete = {},
                            contentPadding = PaddingValues(),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("切换后，手机仅接收这个设备的消息；其他目标在电脑上继续运行。")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("device_connect_button").performScrollTo()
        composeRule.runOnIdle {
            val connect = view<Button>("device_connect_button")
            connect.requestFocus()
            assertEquals(0, connects)
            assertEquals("检查并连接", connect.contentDescription)
            connect.performClick()
            assertEquals(1, connects)
        }
        composeRule.onNodeWithTag("device_connect_button").performScrollTo()
        composeRule.runOnIdle {
            val connect = view<Button>("device_connect_button")
            assertFalse(connect.isEnabled)
            assertFalse(connect.isClickable)
            connect.performClick()
            assertEquals(1, connects)
        }
        composeRule.onNodeWithTag("device_delete_button").performScrollTo()
        composeRule.runOnIdle { assertFalse(view<Button>("device_delete_button").isEnabled) }
        composeRule.onNodeWithTag("device_cancel_switch_button").performScrollTo()
        composeRule.runOnIdle {
            val cancel = view<Button>("device_cancel_switch_button")
            assertTrue(cancel.isEnabled)
            assertEquals("取消切换", cancel.contentDescription)
            cancel.performClick()
            assertEquals(1, cancels)
            state = state.copy(switching = false, connected = false, statusText = "已取消切换。当前设备：办公电脑 · a，未连接。")
        }
        composeRule.onNodeWithText("已取消切换。当前设备：办公电脑 · a，未连接。")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun browsingAndReturningToNativeHostRowKeepsScrollAndRestoresOnlyThatRowFocus() {
        val devices = (0 until 18).map { device("host_$it") }
        val snapshot = DeviceStoreSnapshot(devices, "host_0")
        val listState = LazyListState(firstVisibleItemIndex = 8, firstVisibleItemScrollOffset = 0)
        var showingList by mutableStateOf(true)
        var restoreTarget by mutableStateOf<String?>(null)
        var openedId: String? = null
        var restores = 0
        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                if (showingList) {
                    DeviceManagementPage(
                        snapshot = snapshot, listState = listState, restoreFocusTarget = restoreTarget,
                        onFocusRestored = { restores += 1; restoreTarget = null }, onAddDevice = {},
                        onOpenDevice = { openedId = it; showingList = false }, contentPadding = PaddingValues(),
                        selectionState = DeviceSelectionState(currentHostId = "host_0", connected = true),
                    )
                }
            }
        }
        var savedIndex = 0
        var savedOffset = 0
        composeRule.runOnIdle {
            savedIndex = listState.firstVisibleItemIndex
            savedOffset = listState.firstVisibleItemScrollOffset
            val row = view<LinearLayout>("device_row_host_7")
            row.requestFocus()
            assertEquals(null, openedId)
            assertTrue(row.contentDescription.toString().contains("待检查"))
            row.performClick()
            assertEquals("host_7", openedId)
        }
        composeRule.runOnIdle {
            restoreTarget = AppFocusTarget.deviceRow(requireNotNull(openedId))
            showingList = true
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { restores == 1 }
        composeRule.runOnIdle {
            assertEquals(savedIndex, listState.firstVisibleItemIndex)
            assertEquals(savedOffset, listState.firstVisibleItemScrollOffset)
            assertTrue(view<LinearLayout>("device_row_host_7").hasFocus())
        }
    }

    private inline fun <reified T : View> view(tag: String): T =
        composeRule.activity.findViewById<View>(android.R.id.content).findViewWithTag<T>(tag)
            ?: error("Missing native view: $tag")

    private fun device(hostId: String) = ManagedDevice(
        binding = DeviceBinding(
            relayUrl = "https://relay.example.com", relaySigningPublicKey = "relay-key",
            relaySigningKeyFingerprint = "relay-pin", certificateSha256 = null,
            tenantId = "tenant", hostId = hostId, hostName = "办公电脑 · $hostId",
            deviceId = "phone", hostSigningPublicKey = "host-signing", hostAgreementPublicKey = "host-agreement",
            deviceSigningPublicKey = "device-signing", deviceAgreementPublicKey = "device-agreement",
            hostKeyVersion = 1, deviceKeyVersion = 1, bindingVersion = 1,
        ),
        createdAtEpochMillis = 1L, lastUsedAtEpochMillis = null,
    )
}
