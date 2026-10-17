package com.mycodex.mobile

import android.widget.LinearLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityThemeFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pendingGenerationSurvivesRepeatedRecreateUntilTargetConsumesExactlyOnce() {
        val generation = 991_337L
        composeRule.onNodeWithTag("tab_profile").performClick()
        composeRule.onNodeWithTag("profile_local_settings_row").assertExists()

        composeRule.runOnIdle {
            composeRule.activity.pendingFocusRequest = ThemeFocusRequest(
                targetTag = "local_theme_dark",
                generation = generation,
                sourceActivityId = 1L,
            )
        }

        repeat(2) {
            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals(generation, composeRule.activity.pendingFocusRequest?.generation)
                assertNull(composeRule.activity.lastConsumedThemeFocusGeneration)
            }
        }

        composeRule.onNodeWithTag("profile_local_settings_row").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.lastConsumedThemeFocusGeneration == generation
        }
        composeRule.runOnIdle {
            assertNull(composeRule.activity.pendingFocusRequest)
            val row = composeRule.activity.window.decorView
                .findViewWithTag<LinearLayout>("local_theme_dark")
            assertTrue(row.hasFocus())
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNull(composeRule.activity.pendingFocusRequest)
            assertNull(composeRule.activity.lastConsumedThemeFocusGeneration)
        }
    }
}
