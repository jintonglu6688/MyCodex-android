package com.mycodex.mobile

import android.graphics.Typeface
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.mycodex.mobile.coding.DeleteLocalHistoryRow
import com.mycodex.mobile.ui.theme.DarkColorScheme
import com.mycodex.mobile.ui.theme.LightColorScheme
import com.mycodex.mobile.ui.theme.MyCodexTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccessibleNativeControlsThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<NativeControlsTestActivity>()

    @Test
    fun deleteLocalHistoryChoiceIsOneNodeAndDisabledChoiceHasNoAction() {
        var checked by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            MyCodexTheme(darkTheme = false, dynamicColor = false) {
                DeleteLocalHistoryRow(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { checked = it },
                )
            }
        }

        composeRule.onAllNodesWithContentDescription(
            "同时删除本地历史记录",
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("同时删除本地历史记录")
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(checked)
            enabled = false
        }
        composeRule.onNodeWithContentDescription("同时删除本地历史记录")
            .assertIsNotEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(checked)
        }
    }

    @Test
    fun nativeControlsKeepIdentityThemeEveryRoleAndExposeOnlyEnabledActions() {
        var dark by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var text by mutableStateOf("draft")
        var restoreFocus by mutableStateOf(false)
        var focusGeneration by mutableStateOf(0L)
        var clicks = 0
        var longClicks = 0
        var customActions = 0
        var textChanges = 0
        var imeSends = 0
        var focusRestores = 0
        val customActionId = R.id.accessibility_action_archive_workspace

        composeRule.setContent {
            MyCodexTheme(darkTheme = dark, dynamicColor = false) {
                Column {
                    NativeAccessibleActionRow(
                        title = "Title",
                        body = "Body",
                        footerText = "Footer",
                        testTag = RowTag,
                        restoreFocus = restoreFocus,
                        restoreFocusGeneration = focusGeneration,
                        onFocusRestored = { focusRestores += 1 },
                        onClick = { clicks += 1 },
                        onLongClick = { longClicks += 1 },
                        accessibilityActions = listOf(
                            NativeAccessibleAction(customActionId, "Archive") {
                                customActions += 1
                                true
                            },
                        ),
                        enabled = enabled,
                    )
                    NativeAccessibleTextInput(
                        text = text,
                        testTag = InputTag,
                        enabled = enabled,
                        label = "Message",
                        hint = "Type",
                        restoreFocus = false,
                        onFocusRestored = { focusRestores += 1 },
                        onTextChange = {
                            textChanges += 1
                            text = it
                        },
                        onImeSend = { imeSends += 1 },
                        onFocusChanged = {},
                    )
                    NativeAccessibleButton(
                        text = "Save",
                        testTag = ButtonTag,
                        restoreFocus = false,
                        onFocusRestored = { focusRestores += 1 },
                        onClick = { clicks += 1 },
                        enabled = enabled,
                    )
                }
            }
        }

        lateinit var initialRow: LinearLayout
        lateinit var initialInput: EditText
        lateinit var initialButton: Button
        composeRule.runOnIdle {
            initialRow = view(RowTag)
            initialInput = view(InputTag)
            initialButton = view(ButtonTag)
            assertRowTheme(initialRow, light = true)
            assertInputTheme(initialInput, light = true, enabled = true)
            assertButtonTheme(initialButton, light = true, enabled = true)
            initialInput.requestFocus()
            initialInput.setSelection(2)
            assertTrue(initialInput.hasFocus())
        }

        composeRule.runOnIdle { dark = true }
        composeRule.runOnIdle {
            val row = view<LinearLayout>(RowTag)
            val input = view<EditText>(InputTag)
            val button = view<Button>(ButtonTag)
            assertSame(initialRow, row)
            assertSame(initialInput, input)
            assertSame(initialButton, button)
            assertRowTheme(row, light = false)
            assertInputTheme(input, light = false, enabled = true)
            assertButtonTheme(button, light = false, enabled = true)
            assertEquals("draft", input.text.toString())
            assertEquals(2, input.selectionStart)
            assertTrue(input.hasFocus())
            assertEquals(0, textChanges)
            assertTrue(row.performAccessibilityAction(customActionId, null))
            assertEquals(1, customActions)
        }

        composeRule.runOnIdle {
            restoreFocus = true
            focusGeneration = 41L
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { focusRestores == 1 }
        composeRule.runOnIdle { dark = false }
        composeRule.runOnIdle {
            assertEquals(1, focusRestores)
            focusGeneration = 42L
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { focusRestores == 2 }

        composeRule.runOnIdle {
            initialInput.onEditorAction(EditorInfo.IME_ACTION_SEND)
            initialInput.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            initialInput.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            initialInput.dispatchKeyEvent(
                KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_SHIFT_ON),
            )
            assertEquals(2, imeSends)
        }

        composeRule.runOnIdle { enabled = false }
        composeRule.runOnIdle { focusGeneration = 43L }
        composeRule.waitUntil(timeoutMillis = 5_000) { focusRestores == 3 }
        composeRule.runOnIdle {
            val row = view<LinearLayout>(RowTag)
            val input = view<EditText>(InputTag)
            val button = view<Button>(ButtonTag)
            val actionIds = row.createAccessibilityNodeInfo().actionList.map { it.id }
            assertFalse(row.isEnabled)
            assertFalse(row.isClickable)
            assertFalse(row.isLongClickable)
            assertFalse(actionIds.contains(AccessibilityNodeInfo.ACTION_CLICK))
            assertFalse(actionIds.contains(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            assertFalse(actionIds.contains(customActionId))
            assertFalse(row.performLongClick())
            assertFalse(row.performAccessibilityAction(customActionId, null))
            dispatchActivationKeys(row)

            assertFalse(input.isEnabled)
            input.onEditorAction(EditorInfo.IME_ACTION_SEND)
            dispatchActivationKeys(input)
            assertInputTheme(input, light = true, enabled = false)

            assertFalse(button.isEnabled)
            assertFalse(button.isClickable)
            assertFalse(
                button.createAccessibilityNodeInfo().actionList
                    .map { it.id }
                    .contains(AccessibilityNodeInfo.ACTION_CLICK),
            )
            button.performClick()
            dispatchActivationKeys(button)
            assertButtonTheme(button, light = true, enabled = false)

            assertEquals(0, clicks)
            assertEquals(0, longClicks)
            assertEquals(1, customActions)
            assertEquals(2, imeSends)
            assertEquals(0, textChanges)
            assertEquals(3, focusRestores)
        }
    }

    private fun assertRowTheme(row: LinearLayout, light: Boolean) {
        val scheme = if (light) LightColorScheme else DarkColorScheme
        val title = row.getChildAt(0) as TextView
        val body = row.getChildAt(1) as TextView
        val footer = row.getChildAt(2) as TextView
        assertEquals(scheme.onSurface.toArgb(), title.currentTextColor)
        assertEquals(scheme.onSurfaceVariant.toArgb(), body.currentTextColor)
        assertEquals(scheme.primary.toArgb(), footer.currentTextColor)
        assertEquals(16f, title.textSize / scaledDensity(title), 0.01f)
        assertEquals(14f, body.textSize / scaledDensity(body), 0.01f)
        assertEquals(12f, footer.textSize / scaledDensity(footer), 0.01f)
        assertEquals(sp(title, 24), title.lineHeight)
        assertEquals(sp(body, 20), body.lineHeight)
        assertEquals(sp(footer, 16), footer.lineHeight)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertEquals(600, title.typeface.weight)
        } else {
            assertTrue(title.typeface.style == Typeface.NORMAL || title.typeface.isBold)
        }
        listOf(title, body, footer).forEach {
            assertFalse(it.isFocusable)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, it.importantForAccessibility)
        }
        assertTrue(row.minimumHeight >= dp(row, 48))
    }

    private fun assertInputTheme(input: EditText, light: Boolean, enabled: Boolean) {
        val scheme = if (light) LightColorScheme else DarkColorScheme
        val expectedText = if (enabled) scheme.onSurface else scheme.onSurfaceVariant
        assertEquals(expectedText.toArgb(), input.currentTextColor)
        assertEquals(scheme.onSurfaceVariant.toArgb(), input.currentHintTextColor)
        assertEquals("Type", input.hint.toString())
        assertEquals(
            scheme.primary.toArgb(),
            input.backgroundTintList!!.getColorForState(
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
                0,
            ),
        )
        assertEquals(
            scheme.outline.toArgb(),
            input.backgroundTintList!!.getColorForState(
                intArrayOf(android.R.attr.state_enabled),
                0,
            ),
        )
        assertEquals(
            scheme.outlineVariant.toArgb(),
            input.backgroundTintList!!.getColorForState(
                intArrayOf(-android.R.attr.state_enabled),
                0,
            ),
        )
        assertEquals(16f, input.textSize / scaledDensity(input), 0.01f)
        assertEquals(sp(input, 24), input.lineHeight)
        assertTrue(input.minimumHeight >= dp(input, 48))
    }

    private fun assertButtonTheme(button: Button, light: Boolean, enabled: Boolean) {
        val scheme = if (light) LightColorScheme else DarkColorScheme
        val state = if (enabled) {
            intArrayOf(android.R.attr.state_enabled)
        } else {
            intArrayOf(-android.R.attr.state_enabled)
        }
        val expectedBackground = if (enabled) scheme.primary else scheme.surfaceContainerHighest
        val expectedText = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant
        assertEquals(
            expectedBackground.toArgb(),
            button.backgroundTintList!!.getColorForState(state, 0),
        )
        assertEquals(expectedText.toArgb(), button.textColors.getColorForState(state, 0))
        assertFalse(button.isAllCaps)
        assertEquals(14f, button.textSize / scaledDensity(button), 0.01f)
        assertEquals(sp(button, 20), button.lineHeight)
        assertTrue(button.minimumWidth >= dp(button, 48))
        assertTrue(button.minimumHeight >= dp(button, 48))
    }

    private fun dispatchActivationKeys(view: View) {
        listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE).forEach { keyCode ->
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private inline fun <reified T : View> view(tag: String): T {
        val result = composeRule.activity.window.decorView.findViewWithTag<View>(tag)
        assertTrue("Could not find native view with tag $tag", result is T)
        return result as T
    }

    private fun scaledDensity(view: View): Float =
        view.resources.displayMetrics.density * view.resources.configuration.fontScale

    private fun sp(view: View, value: Int): Int = (value * scaledDensity(view)).roundToInt()

    private fun dp(view: View, value: Int): Int =
        (value * view.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val RowTag = "native_test_row"
        const val InputTag = "native_test_input"
        const val ButtonTag = "native_test_button"
    }
}
