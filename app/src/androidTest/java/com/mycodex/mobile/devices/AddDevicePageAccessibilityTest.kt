package com.mycodex.mobile.devices

import com.mycodex.mobile.NativeControlsTestActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.mycodex.mobile.ui.theme.MyCodexTheme
import org.junit.Rule
import org.junit.Test

class AddDevicePageAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<NativeControlsTestActivity>()

    @Test
    fun compatiblePairingFlowKeepsEveryActionReachable() {
        setContent(PairingUiState.Idle)

        composeRule.onNodeWithTag("pairing_import_clipboard")
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithTag("pairing_uri_input").assertIsEnabled()
        composeRule.onNodeWithTag("pairing_submit")
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithTag("pairing_cancel")
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    fun waitingStateShowsTargetAndDigitReadableShortCodeAndReachableCancel() {
        setContent(
            PairingUiState.Waiting(
                target = "Office Windows",
                shortCode = "012345",
            ),
        )

        composeRule.onNodeWithTag("pairing_target_summary")
            .assertTextContains("Office Windows", substring = true)
        composeRule.onNodeWithTag("pairing_short_code")
            .assertContentDescriptionEquals("配对短码 0 1 2 3 4 5")
        composeRule.onNodeWithTag("pairing_import_clipboard").assertIsNotEnabled()
        composeRule.onNodeWithTag("pairing_uri_input").assertIsNotEnabled()
        composeRule.onNodeWithTag("pairing_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("pairing_cancel")
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    fun talkBackSemanticsNameEveryAction() {
        setContent(PairingUiState.Idle)

        composeRule.onNodeWithTag("pairing_import_clipboard")
            .assertTextContains("从剪贴板导入")
            .assertHasClickAction()
        composeRule.onNodeWithTag("pairing_uri_input")
            .assertContentDescriptionEquals("完整配对 URI")
        composeRule.onNodeWithTag("pairing_cancel")
            .assertTextContains("取消")
            .assertHasClickAction()
        composeRule.onNodeWithTag("pairing_submit")
            .assertTextContains("添加设备")
            .assertHasClickAction()
        composeRule.onNodeWithText("从剪贴板导入").assertExists()
    }

    @Test
    fun securityInitializationFailureIsVisibleAndActionable() {
        setContent(PairingUiState.Failed(DeviceIdentityException.SECURITY_UNAVAILABLE))

        composeRule.onNodeWithText("无法初始化手机安全密钥", substring = true)
            .assertTextContains("无法初始化手机安全密钥", substring = true)
        composeRule.onNodeWithTag("pairing_submit")
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithTag("pairing_cancel")
            .assertIsEnabled()
            .assertHasClickAction()
    }

    private fun setContent(pairingState: PairingUiState) {
        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                AddDevicePageContent(
                    pairingState = pairingState,
                    pairingUri = "",
                    localStatusText = "",
                    onPairingUriChanged = {},
                    onImportClipboard = {},
                    onSubmit = {},
                    onCancel = {},
                    contentPadding = PaddingValues(),
                )
            }
        }
    }
}
