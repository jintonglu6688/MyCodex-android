package com.mycodex.mobile.coding

import com.mycodex.mobile.NativeControlsTestActivity
import android.widget.EditText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mycodex.mobile.ui.theme.MyCodexTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CodingComposerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<NativeControlsTestActivity>()

    @Test
    fun sendButtonEnablesOnlyWhenDraftHasTextAndSubmitsTrimmedText() {
        var text by mutableStateOf("")
        var submittedText: String? = null

        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                CodingComposer(
                    state = CodingComposerState(text = text),
                    onTextChange = { text = it },
                    onSend = { submitted, _ -> submittedText = submitted },
                    onStop = {},
                    onAttachmentClick = {},
                    onRemoveAttachment = {},
                )
            }
        }

        composeRule.onNodeWithTag("coding_composer_send").assertIsNotEnabled()

        composeRule.runOnIdle {
            composeRule.activity.window.decorView
                .findViewWithTag<EditText>("coding_composer_input")
                .setText("继续推进")
        }

        composeRule.onNodeWithTag("coding_composer_send").assertIsEnabled()
        composeRule.onNodeWithTag("coding_composer_send").performClick()

        assertEquals("继续推进", submittedText)
    }

    @Test
    fun sendingDisablesEditorAttachmentAndChipRemoval() {
        val attachment = CodingInputAttachment(
            kind = CodingAttachmentKind.Skill,
            type = "skill",
            name = "test-skill",
            displayName = "测试技能",
            path = "skills/test-skill",
        )

        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                CodingComposer(
                    state = CodingComposerState(text = "发送中", attachmentCount = 1, sending = true),
                    attachments = listOf(attachment),
                    onTextChange = {},
                    onSend = { _, _ -> },
                    onStop = {},
                    onAttachmentClick = {},
                    onRemoveAttachment = {},
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(
                false,
                composeRule.activity.window.decorView
                    .findViewWithTag<EditText>("coding_composer_input")
                    .isEnabled,
            )
        }
        composeRule.onNodeWithTag("coding_composer_attachment").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("移除技能 测试技能").assertIsNotEnabled()
    }

    @Test
    fun generatingShowsIndependentSendAndStopActions() {
        var sendCount = 0
        var stopCount = 0

        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                CodingComposer(
                    state = CodingComposerState(text = "继续", generating = true),
                    onTextChange = {},
                    onSend = { _, _ -> sendCount += 1 },
                    onStop = { stopCount += 1 },
                    onAttachmentClick = {},
                    onRemoveAttachment = {},
                )
            }
        }

        composeRule.onNodeWithTag("coding_composer_send").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("coding_composer_stop").assertIsEnabled().performClick()

        assertEquals(1, sendCount)
        assertEquals(1, stopCount)
    }

}
