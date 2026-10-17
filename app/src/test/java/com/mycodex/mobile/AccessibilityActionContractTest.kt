package com.mycodex.mobile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityActionContractTest {
    @Test
    fun nativeActionRowExposesCustomAccessibilityActionContract() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt")

        assertTrue(source.contains("data class NativeAccessibleAction"))
        assertTrue(source.contains("accessibilityActions: List<NativeAccessibleAction> = emptyList()"))
        assertTrue(source.contains("AccessibilityNodeInfo.AccessibilityAction"))
        assertTrue(source.contains("performAccessibilityAction"))
    }

    @Test
    fun workspaceRowsExposeArchiveAndDeleteAsCustomAccessibilityActions() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")

        assertTrue(source.contains("NativeAccessibleAction"))
        assertTrue(source.contains("R.id.accessibility_action_archive_workspace"))
        assertTrue(source.contains("R.id.accessibility_action_delete_workspace"))
        assertTrue(source.contains("label = \"归档\""))
        assertTrue(source.contains("label = \"删除\""))
    }

    @Test
    fun sessionRowsExposeArchiveAndDeleteAsCustomAccessibilityActions() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")

        assertTrue(source.contains("R.id.accessibility_action_archive_session"))
        assertTrue(source.contains("R.id.accessibility_action_delete_session"))
        assertTrue(source.contains("R.id.accessibility_action_open_subagents"))
        assertTrue(source.contains("label = \"子代理\""))
        assertTrue(source.contains("archiveSession("))
        assertTrue(source.contains("deleteSession("))
    }

    @Test
    fun myCodexApp_mountsApprovalBeforePlanAndToolInputDialogs() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")

        val approvalIndex = source.indexOf("RemoteApprovalDialog(")
        val planIndex = source.indexOf("RemotePlanExecutionDialog(")
        val toolInputIndex = source.indexOf("RemoteToolInputDialog(")

        assertTrue(approvalIndex >= 0)
        assertTrue(planIndex > approvalIndex)
        assertTrue(toolInputIndex > planIndex)
        assertTrue(toolInputIndex > approvalIndex)
        assertTrue(source.contains("remoteApprovalState.activeRequest == null"))
        assertTrue(source.contains("remotePlanExecutionState.activeRequest == null"))
    }

    @Test
    fun codingChatMoreMenuPlacesPlanModeAfterRestoreDefaults() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")

        val modelIndex = source.indexOf("Text(\"模型与权限\")")
        val restoreIndex = source.indexOf("Text(\"恢复默认\")")
        val planEnabledIndex = source.indexOf("codingPlanModeEnabled == true -> \"关闭计划模式\"")
        val copyIndex = source.indexOf("Text(\"复制全部消息\")")

        assertTrue(modelIndex >= 0)
        assertTrue(restoreIndex > modelIndex)
        assertTrue(planEnabledIndex > restoreIndex)
        assertTrue(copyIndex > planEnabledIndex)
        assertTrue(source.contains("codingPlanModeEnabled == false -> \"开启计划模式\""))
        assertTrue(source.contains("else -> \"计划模式\""))
        assertTrue(source.contains("toggleCodingPlanMode()"))
        assertTrue(source.contains("updateModelSettings = false"))
    }

    @Test
    fun codingComposerPlacesAttachmentSendAndStopInActionOrderAndExposesChipRemoval() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingComposer.kt")

        val attachmentIndex = source.indexOf("coding_composer_attachment")
        val sendIndex = source.indexOf("coding_composer_send")
        val stopIndex = source.indexOf("coding_composer_stop")

        assertTrue(attachmentIndex >= 0)
        assertTrue(sendIndex > attachmentIndex)
        assertTrue(stopIndex > sendIndex)
        assertFalse(source.contains("contentDescription = \"附件\""))
        assertTrue(source.contains("Text(\"附件\")"))
        assertTrue(source.contains("onAttachmentClick"))
        assertTrue(source.contains("onRemoveAttachment"))
        assertTrue(source.contains("移除插件"))
        assertTrue(source.contains("移除技能"))
    }

    @Test
    fun codingComposerRestoresInputAccessibilityFocusAfterKeyboardCloses() {
        val composerSource = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingComposer.kt")
        val nativeSource = readProjectFile("src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt")

        assertTrue(composerSource.contains("NativeAccessibleTextInput("))
        assertTrue(composerSource.contains("restoreInputAccessibilityFocus"))
        assertTrue(composerSource.contains("previousImeVisible && !imeVisible"))
        assertTrue(nativeSource.contains("fun NativeAccessibleTextInput("))
        assertTrue(nativeSource.contains("EditText"))
        assertTrue(nativeSource.contains("AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS"))
        assertTrue(nativeSource.contains("EditorInfo.IME_FLAG_NO_ENTER_ACTION"))
        assertTrue(nativeSource.contains("KeyEvent.KEYCODE_ENTER"))
    }

    @Test
    fun profileExposesReadOnlyUserCenterWithAnnouncedStatus() {
        val appSource = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val userCenterSource = readProjectFile("src/main/java/com/mycodex/mobile/account/UserCenter.kt")

        assertTrue(appSource.contains("profile_user_center_row"))
        assertTrue(appSource.contains("AppFocusTarget.ProfileUserCenter"))
        assertTrue(userCenterSource.contains("host.account.get"))
        assertTrue(userCenterSource.contains("LiveRegionMode.Polite"))
        assertTrue(userCenterSource.contains("user_center_refresh"))
    }

    @Test
    fun codingAttachmentSheetExposesGridAndPickerAccessibilityText() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingAttachmentSheet.kt")

        assertTrue(source.contains("dragHandle = null"))
        assertTrue(source.contains("插入附件"))
        assertTrue(source.contains("Text(\"关闭\")"))
        assertTrue(source.contains("Text(\"返回\")"))
        assertTrue(source.contains("Text(\"没有匹配结果\")"))
        assertTrue(source.contains("MaterialTheme.colorScheme.errorContainer"))
        assertTrue(source.contains("Modifier.clearAndSetSemantics {}"))
        assertTrue(source.contains("插件"))
        assertFalse(source.contains("插入已安装插件"))
        assertTrue(source.contains("技能"))
        assertFalse(source.contains("插入可用技能"))
        assertTrue(source.contains("已插入"))
        assertTrue(source.contains("正在加载"))
        assertTrue(source.contains("重试"))
        assertTrue(source.contains("contentDescription"))
        assertTrue(source.contains("clearAndSetSemantics"))
        assertTrue(source.contains("onClick(label = \"插入"))
    }

    @Test
    fun codingMessageListPageOwnsAttachmentStateAndClearsOnAcceptedSend() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")
        val draftSource = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingDraftViewModel.kt")

        assertTrue(source.contains("CodingDraftViewModel"))
        assertTrue(source.contains("CodingAttachmentSheet("))
        assertTrue(source.contains("onAttachmentClick"))
        assertTrue(source.contains("onRemoveAttachment"))
        assertTrue(source.contains("draft.clearAccepted"))
        assertTrue(draftSource.contains("attachments = emptyList()"))
    }

    @Test
    fun codingMessageRowsExposeFullAccessibilityTextAndVisualExpansion() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")

        assertTrue(source.contains("val accessibleText = message.accessibilityText()"))
        assertFalse(source.contains("message.content.take(120)"))
        assertTrue(source.contains("shouldCollapseMessageContent(message.content)"))
        assertTrue(source.contains("rememberSaveable(message.id)"))
        assertTrue(source.contains("bodyMaxLines = if (collapsible && !expanded) CollapsedMessageMaxLines else null"))
        assertTrue(source.contains("footerText = expansionLabel"))
        assertTrue(source.contains("CodingMessageRole.User -> colorScheme.primaryContainer"))
        assertTrue(source.contains("CodingMessageRole.Assistant -> colorScheme.surfaceContainerLow"))
        assertTrue(source.contains("colorScheme.secondaryContainer"))
        assertTrue(source.contains("onClick = if (collapsible)"))
        assertTrue(source.contains("onLongClick = if (actionsEnabled)"))
        assertTrue(source.contains("state.loadState == CodingLoadState.Loaded && state.messages.isEmpty()"))
        assertTrue(source.contains("state.loadState == CodingLoadState.Unavailable"))
    }

    @Test
    fun profileExposesWindowsSettingsAsAccessibleExplicitAction() {
        val appSource = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val settingsSource = readProjectFile("src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt")

        assertTrue(appSource.contains("profile_windows_settings_row"))
        assertTrue(appSource.contains("AppFocusTarget.ProfileWindowsSettings"))
        assertTrue(appSource.contains("onWindowsSettingsClick"))
        assertTrue(settingsSource.contains("NativeAccessibleActionRow("))
        assertTrue(settingsSource.contains("NativeAccessibleButton("))
        assertTrue(settingsSource.contains("LiveRegionMode.Polite"))
        assertFalse(settingsSource.contains("LiveRegionMode.Assertive"))
    }

    @Test
    fun profileExposesOneClickDiagnosticLogSharing() {
        val appSource = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val diagnosticsSource = readProjectFile("src/main/java/com/mycodex/mobile/diagnostics/MobileDiagnostics.kt")
        val manifest = readProjectFile("src/main/AndroidManifest.xml")

        assertTrue(appSource.contains("profile_share_logs_row"))
        assertTrue(appSource.contains("LiveRegionMode.Polite"))
        assertTrue(diagnosticsSource.contains("Intent.ACTION_SEND"))
        assertTrue(diagnosticsSource.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue(diagnosticsSource.contains("FileProvider.getUriForFile"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
    }

    @Test
    fun nativeActionRowsExposeDisabledStateAndRemoveDisabledActions() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt")
        val actionRow = source.substringAfter("internal fun NativeAccessibleActionRow(")
            .substringBefore("@Composable", missingDelimiterValue = source)

        assertTrue(actionRow.contains("enabled: Boolean = true"))
        assertTrue(actionRow.contains("row.isEnabled = enabled"))
        assertTrue(actionRow.contains("row.isClickable = enabled && onClick != null"))
        assertTrue(actionRow.contains("if (enabled && onClick != null) View.OnClickListener"))
        assertTrue(actionRow.contains("if (enabled) accessibilityActions"))
        assertTrue(actionRow.contains("if (enabled) {"))
    }

    @Test
    fun nativeButtonRemovesItsListenerAndClickActionWhenDisabled() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/AccessibleNativeControls.kt")
        val button = source.substringAfter("internal fun NativeAccessibleButton(")

        assertTrue(button.contains("button.isEnabled = enabled"))
        assertTrue(button.contains("if (enabled) View.OnClickListener"))
        assertTrue(button.contains("button.isClickable = enabled"))
    }

    @Test
    fun stageThreeStatesAndEmptyListTargetsKeepAnnouncementsAndActionsSeparate() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/coding/CodingScreens.kt")
        val infoPanel = source.substringAfter("private fun CodingInfoPanel(")
            .substringBefore("@Composable", missingDelimiterValue = source)

        assertFalse(infoPanel.contains("liveRegion"))
        assertTrue(source.contains("CircularProgressIndicator("))
        assertTrue(source.contains("Modifier.clearAndSetSemantics {}"))
        assertTrue(source.contains("color = MaterialTheme.colorScheme.errorContainer"))
        assertTrue(source.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(source.contains("onClick = null"))
        assertTrue(source.contains("testTag = \"coding_session_empty_create\""))
        assertTrue(source.contains("restoreFocusGeneration = focusRestoreGeneration"))
        assertTrue(source.contains("result.state == CodingLoadState.Loaded && result.items.isEmpty()"))
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Could not find project file: $relativePath")
        return file.readText(Charsets.UTF_8)
    }
}
