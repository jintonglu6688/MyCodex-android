package com.mycodex.mobile

import android.view.View
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mycodex.mobile.ui.theme.MyCodexTheme
import com.mycodex.mobile.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainScaffoldTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<NativeControlsTestActivity>()

    @Test
    fun bottomNavigationHasExactlyThreeTabsWithRoleAndSelectedState() {
        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                MyCodexApp(
                    themeMode = ThemeMode.System,
                    onThemeModeSelected = { true },
                )
            }
        }

        val tabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        composeRule.onNodeWithTag("bottom_navigation").assertExists().assertHasNoClickAction()
        assertTrue(
            composeRule.onNodeWithTag("bottom_navigation")
                .getUnclippedBoundsInRoot()
                .let { bounds -> bounds.bottom - bounds.top < 92.dp },
        )
        composeRule.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag("bottom_navigation")),
            useUnmergedTree = true,
        ).assertCountEquals(3)
        composeRule.onNodeWithTag("tab_coding")
            .assert(tabRole)
            .assertIsSelected()
            .assertTextEquals("编程")
            .assertHasClickAction()
        composeRule.onNodeWithTag("tab_office")
            .assert(tabRole)
            .assertIsNotSelected()
            .assertTextEquals("办公")
            .assertHasClickAction()
        composeRule.onNodeWithTag("tab_profile")
            .assert(tabRole)
            .assertIsNotSelected()
            .assertTextEquals("我的")
            .assertHasClickAction()

        composeRule.onNodeWithTag("tab_office")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        composeRule.onNodeWithTag("tab_coding").assertIsSelected()
        composeRule.onNodeWithTag("tab_office").assertIsNotSelected()

        composeRule.onNodeWithTag("tab_office").performClick()

        composeRule.onNodeWithTag("tab_coding").assertIsNotSelected()
        composeRule.onNodeWithTag("tab_office").assertIsSelected().assertHasClickAction()
        composeRule.onNodeWithTag("app_top_bar_title").assertTextEquals("办公")
        composeRule.onNodeWithText("尚未选择设备").assertExists()
    }

    @Test
    fun appShellTargetsMeetMinimumSizeAtNormalAndDoubleFontScale() {
        var fontScale by mutableStateOf(1f)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                MyCodexTheme(dynamicColor = false) {
                    MyCodexApp(
                        themeMode = ThemeMode.System,
                        onThemeModeSelected = { true },
                    )
                }
            }
        }

        val normalBottomBarHeight = composeRule.onNodeWithTag("bottom_navigation")
            .getUnclippedBoundsInRoot()
            .let { bounds -> bounds.bottom - bounds.top }
        composeRule.onNodeWithTag("bottom_navigation").assertHeightIsAtLeast(72.dp)
        listOf("tab_coding", "tab_office", "tab_profile").forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithTag("app_top_bar").assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithTag("app_top_bar_title").assertExists()

        composeRule.runOnIdle { fontScale = 2f }
        composeRule.onNodeWithTag("bottom_navigation").assertHeightIsAtLeast(72.dp)
        val doubleFontBottomBarHeight = composeRule.onNodeWithTag("bottom_navigation")
            .getUnclippedBoundsInRoot()
            .let { bounds -> bounds.bottom - bounds.top }
        assertTrue(doubleFontBottomBarHeight > normalBottomBarHeight)
        listOf("tab_coding", "tab_office", "tab_profile").forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun topBarKeepsFullDynamicTitleInOneHeadingAndRequiresActivation() {
        val longTitle = "这是一个需要换行并在两行仍溢出时完整展开的超长会话标题，用于验证标题不会永久省略"
        var backCalls = 0
        var actionCalls = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MyCodexTheme(dynamicColor = false) {
                    MyCodexTopBar(
                        title = longTitle,
                        dynamicTitle = true,
                        onBack = { backCalls += 1 },
                        actions = {
                            TextButton(
                                onClick = { actionCalls += 1 },
                                modifier = Modifier
                                    .testTag("test_top_bar_action")
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            ) {
                                Text("操作")
                            }
                        },
                    )
                }
            }
        }

        composeRule.onAllNodes(isHeading()).assertCountEquals(1)
        val finalTextLayouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("app_top_bar_title")
            .assertTextEquals(longTitle)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(finalTextLayouts)
            }
        assertEquals(1, finalTextLayouts.size)
        assertTrue(finalTextLayouts.single().lineCount > 2)
        assertFalse(finalTextLayouts.single().hasVisualOverflow)
        assertTrue(
            composeRule.onNodeWithTag("app_top_bar")
                .getUnclippedBoundsInRoot()
                .let { bounds -> bounds.bottom - bounds.top > 64.dp },
        )
        composeRule.onNodeWithTag("app_top_bar_back")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        composeRule.onNodeWithTag("test_top_bar_action")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        assertEquals(0, backCalls)
        assertEquals(0, actionCalls)

        composeRule.onNodeWithTag("app_top_bar_back").performClick()
        composeRule.onNodeWithTag("test_top_bar_action").performClick()
        assertEquals(1, backCalls)
        assertEquals(1, actionCalls)
    }

    @Test
    fun systemBackReturnsOneLevelAndRestoresFocus() {
        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                MyCodexApp(
                    themeMode = ThemeMode.System,
                    onThemeModeSelected = { true },
                )
            }
        }

        composeRule.onNodeWithTag("tab_profile").performClick()
        composeRule.onNodeWithTag("profile_device_management_row").performClick()
        composeRule.onNodeWithTag("add_device_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("pairing_import_clipboard")
            .assertExists()
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithTag("pairing_uri_input").assertExists().assertIsEnabled()
        composeRule.onNodeWithTag("pairing_submit")
            .assertExists()
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithTag("pairing_cancel")
            .assertExists()
            .assertIsEnabled()
            .assertHasClickAction()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("add_device_button").assertExists()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_device_management_row").assertExists()
        composeRule.waitUntil(5_000) {
            composeRule.activity.findViewByTag("profile_device_management_row")?.hasFocus() == true
        }
    }

    @Test
    fun profileShowsOnlyRealEntriesInNonInteractiveContainers() {
        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                MyCodexApp(
                    themeMode = ThemeMode.System,
                    onThemeModeSelected = { true },
                )
            }
        }

        composeRule.onNodeWithTag("tab_profile").performClick()
        composeRule.onNodeWithTag("profile_device_card")
            .assertExists()
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        composeRule.onNodeWithTag("profile_action_group")
            .assertExists()
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        composeRule.runOnIdle {
            listOf(
                "profile_device_management_row",
                "profile_user_center_row",
                "profile_windows_settings_row",
                "profile_local_settings_row",
                "profile_share_logs_row",
            ).forEach { tag ->
                assertTrue(composeRule.activity.findViewByTag(tag)?.isClickable == true)
            }
        }
        composeRule.onNodeWithTag("profile_diagnostics_row").assertDoesNotExist()
    }

    @Test
    fun localThemeSettingsUsesThreeExplicitSingleActionRows() {
        var mode by mutableStateOf(ThemeMode.System)
        var selections = 0
        composeRule.setContent {
            MyCodexTheme(darkTheme = mode == ThemeMode.Dark, dynamicColor = false) {
                MyCodexApp(
                    themeMode = mode,
                    onThemeModeSelected = {
                        selections += 1
                        mode = it
                        true
                    },
                )
            }
        }

        composeRule.onNodeWithTag("tab_profile").performClick()
        composeRule.onNodeWithTag("profile_local_settings_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("local_theme_group")
            .assertExists()
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        composeRule.runOnIdle {
            assertEquals(
                "跟随系统，已选中",
                composeRule.activity.findViewByTag("local_theme_system")?.contentDescription,
            )
            assertEquals(
                "浅色，未选中",
                composeRule.activity.findViewByTag("local_theme_light")?.contentDescription,
            )
            listOf("local_theme_system", "local_theme_light", "local_theme_dark").forEach { tag ->
                assertTrue(composeRule.activity.findViewByTag(tag)?.isClickable == true)
            }
        }
        composeRule.onNodeWithTag("local_theme_light").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(
                "浅色，已选中",
                composeRule.activity.findViewByTag("local_theme_light")?.contentDescription,
            )
        }
        assertEquals(1, selections)
        composeRule.onNodeWithTag("local_theme_light").performClick()
        assertEquals(1, selections)

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_local_settings_row").assertExists()
        composeRule.waitUntil(5_000) {
            composeRule.activity.findViewByTag("profile_local_settings_row")?.hasFocus() == true
        }
    }

    @Test
    fun failedThemeSaveKeepsSelectionAndShowsVisibleError() {
        composeRule.setContent {
            MyCodexTheme(dynamicColor = false) {
                MyCodexApp(
                    themeMode = ThemeMode.System,
                    onThemeModeSelected = { false },
                )
            }
        }

        composeRule.onNodeWithTag("tab_profile").performClick()
        composeRule.onNodeWithTag("profile_local_settings_row").performScrollTo().performClick()
        composeRule.onNodeWithTag("local_theme_dark").performClick()
        composeRule.runOnIdle {
            assertEquals(
                "跟随系统，已选中",
                composeRule.activity.findViewByTag("local_theme_system")?.contentDescription,
            )
        }
        composeRule.onNodeWithText("无法保存主题设置，请重试。").assertExists()
    }

    private fun NativeControlsTestActivity.findViewByTag(tag: String): View? {
        return window.decorView.findViewWithTag(tag)
    }
}
