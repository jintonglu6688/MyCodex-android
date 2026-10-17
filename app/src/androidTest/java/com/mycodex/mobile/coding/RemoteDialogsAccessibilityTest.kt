package com.mycodex.mobile.coding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import com.mycodex.mobile.NativeControlsTestActivity
import com.mycodex.mobile.ui.theme.MyCodexTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RemoteDialogsAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<NativeControlsTestActivity>()

    @Test
    fun approvalDialogAtDoubleFontScrollsAndOnlyExplicitActionResponds() {
        val decisions = mutableListOf<RemoteApprovalDecision>()

        setDoubleFontContent {
            RemoteApprovalDialog(
                state = RemoteApprovalUiState(activeRequest = approvalRequest),
                onDecision = decisions::add,
            )
        }

        scrollContentContaining("审批内容结尾")
        composeRule.onNodeWithText("允许一次")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        composeRule.onNodeWithText("允许本会话").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("拒绝").assertIsDisplayed().assertIsEnabled()
        assertTrue(decisions.isEmpty())

        composeRule.onNodeWithText("允许一次").performClick()
        assertEquals(listOf(RemoteApprovalDecision.AllowOnce), decisions)
    }

    @Test
    fun submittingApprovalDialogDisablesEveryDecision() {
        var decisionCount = 0

        setDoubleFontContent {
            RemoteApprovalDialog(
                state = RemoteApprovalUiState(
                    activeRequest = approvalRequest,
                    submitting = true,
                ),
                onDecision = { decisionCount += 1 },
            )
        }

        listOf("允许一次", "允许本会话", "拒绝").forEach { label ->
            composeRule.onNodeWithText(label).assertIsNotEnabled()
        }
        assertEquals(0, decisionCount)
    }

    @Test
    fun planDialogAtDoubleFontScrollsAndOnlyExplicitActionsRespond() {
        val actions = mutableListOf<RemotePlanExecutionAction>()

        setDoubleFontContent {
            RemotePlanExecutionDialog(
                state = RemotePlanExecutionUiState(activeRequest = planRequest),
                onAction = actions::add,
            )
        }

        scrollContentContaining("计划内容结尾")
        composeRule.onNodeWithText("执行计划")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
        composeRule.onNodeWithText("继续讨论")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        assertTrue(actions.isEmpty())

        composeRule.onNodeWithText("执行计划").performClick()
        composeRule.onNodeWithText("继续讨论").performClick()
        assertEquals(
            listOf(
                RemotePlanExecutionAction.Execute,
                RemotePlanExecutionAction.Continue,
            ),
            actions,
        )
    }

    @Test
    fun toolInputAtDoubleFontKeepsQuestionsAndOptionsAccessibleWithoutLeakingSecret() {
        val answerChanges = mutableListOf<Pair<String, List<String>>>()
        var submitCount = 0
        var cancelCount = 0

        setDoubleFontContent {
            RemoteToolInputDialog(
                state = RemoteToolInputUiState(
                    activeRequest = toolInputRequest,
                    answers = mapOf(
                        "mode" to listOf("safe"),
                        "token" to listOf(secretAnswer),
                    ),
                ),
                onAnswerChanged = { id, values -> answerChanges += id to values },
                onSubmit = { submitCount += 1 },
                onCancel = { cancelCount += 1 },
            )
        }

        composeRule.onAllNodes(isHeading()).assertCountEquals(3)
        listOf("选择执行模式", "输入访问令牌", "补充说明").forEach { question ->
            composeRule.onNode(hasText(question) and isHeading()).assertExists()
        }
        composeRule.onAllNodes(
            hasContentDescription("标准流程"),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("标准流程")
            .assertHasClickAction()
            .assertIsEnabled()
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }

        scrollContentContaining("补充说明")
        assertTrue(answerChanges.isEmpty())
        assertEquals(0, submitCount)
        assertEquals(0, cancelCount)
        composeRule.onNode(passwordField).assertIsEnabled()

        scrollContentContaining("补充说明", distance = -600f)
        composeRule.onNodeWithContentDescription("标准流程").performClick()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.onNodeWithText("取消").performClick()

        assertEquals(listOf("mode" to listOf("standard")), answerChanges)
        assertEquals(1, submitCount)
        assertEquals(1, cancelCount)
    }

    @Test
    fun submittingOrCancelingToolInputDisablesEveryAction() {
        var state by mutableStateOf(
            RemoteToolInputUiState(
                activeRequest = toolInputRequest,
                submitting = true,
            ),
        )
        var callbackCount = 0

        setDoubleFontContent {
            RemoteToolInputDialog(
                state = state,
                onAnswerChanged = { _, _ -> callbackCount += 1 },
                onSubmit = { callbackCount += 1 },
                onCancel = { callbackCount += 1 },
            )
        }

        assertToolInputActionsDisabled()
        composeRule.runOnIdle {
            state = state.copy(submitting = false, canceling = true)
        }
        assertToolInputActionsDisabled()
        assertEquals(0, callbackCount)
    }

    private fun setDoubleFontContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MyCodexTheme(dynamicColor = false, content = content)
            }
        }
    }

    private fun scrollContentContaining(text: String, distance: Float = 600f) {
        composeRule.onNode(
            hasScrollAction() and hasAnyDescendant(hasText(text, substring = true)),
        ).performSemanticsAction(SemanticsActions.ScrollBy) { action ->
            action(0f, distance)
        }
    }

    private fun assertToolInputActionsDisabled() {
        composeRule.onNodeWithContentDescription("标准流程").assertIsNotEnabled()
        composeRule.onNode(passwordField).assertIsNotEnabled()
        composeRule.onNodeWithText("确定").assertIsNotEnabled()
        composeRule.onNodeWithText("取消").assertIsNotEnabled()
    }

    private val approvalRequest = RemoteApprovalRequest(
        requestId = "approval-test",
        sessionId = null,
        title = "审批请求",
        questionText = "请确认这一项不会因为字体放大而截断。\n".repeat(30) + "审批内容结尾",
        summaryText = null,
        detailsText = null,
        canApproveForSession = true,
        decisions = listOf(
            RemoteApprovalDecisionOption(RemoteApprovalDecision.AllowOnce, "允许一次", true),
            RemoteApprovalDecisionOption(RemoteApprovalDecision.AllowForSession, "允许本会话", true),
            RemoteApprovalDecisionOption(RemoteApprovalDecision.Decline, "拒绝", true),
        ),
    )

    private val planRequest = RemotePlanExecutionRequest(
        requestId = "plan-test",
        threadId = null,
        title = "计划已生成",
        questionText = "是否执行以下计划？",
        planText = "逐项检查界面、语义和回调。\n".repeat(40) + "计划内容结尾",
        actions = listOf(
            RemotePlanExecutionActionOption(RemotePlanExecutionAction.Execute, "执行计划", true),
            RemotePlanExecutionActionOption(RemotePlanExecutionAction.Continue, "继续讨论", true),
        ),
    )

    private val toolInputRequest = RemoteToolInputRequest(
        requestId = "tool-input-test",
        sessionId = null,
        title = "工具输入",
        headerText = null,
        detailsText = null,
        questions = listOf(
            RemoteToolInputQuestion(
                id = "mode",
                header = null,
                question = "选择执行模式",
                description = null,
                isOther = false,
                isSecret = false,
                options = listOf(
                    RemoteToolInputOption("标准流程", "standard", null),
                    RemoteToolInputOption("安全流程", "safe", null),
                ),
            ),
            RemoteToolInputQuestion(
                id = "token",
                header = null,
                question = "输入访问令牌",
                description = null,
                isOther = false,
                isSecret = true,
                options = emptyList(),
            ),
            RemoteToolInputQuestion(
                id = "notes",
                header = null,
                question = "补充说明",
                description = null,
                isOther = false,
                isSecret = false,
                options = emptyList(),
            ),
        ),
    )

    private companion object {
        const val secretAnswer = "never-expose-this-secret"
        val passwordField = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
    }
}
