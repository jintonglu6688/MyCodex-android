package com.mycodex.mobile.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsSettingsSourceContractTest {
    @Test
    fun appWiresFourScreensProfileEntryAndBackFocusTargets() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val automaticLoadEffect = source
            .substringAfter("LaunchedEffect(activeScreen, currentDevice?.id)")
            .substringBefore("LaunchedEffect(activeScreen, codingSessionId")

        assertTrue(source.contains("AppScreen.WindowsSettings"))
        assertTrue(source.contains("AppScreen.WindowsDefaultsSettings"))
        assertTrue(source.contains("AppScreen.WindowsCustomApprovalSettings"))
        assertTrue(source.contains("AppScreen.WindowsOfficialSettings"))
        assertTrue(source.contains("ProfileWindowsSettings"))
        assertTrue(source.contains("profile_windows_settings_row"))
        assertTrue(source.contains("WindowsSettingsFocusTarget.Defaults"))
        assertTrue(source.contains("WindowsSettingsFocusTarget.CustomApproval"))
        assertTrue(source.contains("WindowsSettingsFocusTarget.Official"))
        assertTrue(source.contains("WindowsSettingsViewModel"))
        assertTrue(source.contains("viewModel("))
        assertTrue(source.contains("WindowsSettingsRepository(codingConnectionManager)"))
        assertTrue(source.contains("if (activeScreen == AppScreen.WindowsSettings.name)"))
        assertTrue(automaticLoadEffect.contains("windowsSettingsController.loadIfNeeded()"))
        assertFalse(automaticLoadEffect.contains("windowsSettingsController.load()"))
        assertTrue(source.contains("onReload = { windowsSettingsController.load() }"))
    }

    @Test
    fun screensUseExplicitAccessibleActionsHeadingsAndQuietProgress() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt")
        val sharedSource = readProjectFile("src/main/java/com/mycodex/mobile/SettingsUi.kt")

        assertTrue(source.contains("NativeAccessibleActionRow("))
        assertTrue(source.contains("AppSettingsHeading("))
        assertTrue(sharedSource.contains(".semantics { heading() }"))
        assertTrue(source.contains("NativeAccessibleButton("))
        assertTrue(source.contains("text = \"保存\""))
        assertTrue(source.contains("Modifier.clearAndSetSemantics {}"))
        assertTrue(source.contains("LiveRegionMode.Polite"))
        assertFalse(source.contains("LiveRegionMode.Assertive"))
        assertTrue(source.contains("\"正在加载当前设备设置。\""))
        assertTrue(source.contains("\"正在保存当前设备设置。\""))
        assertTrue(source.contains("MaterialTheme.colorScheme.errorContainer"))
        assertTrue(source.contains("AppSettingsGroup {"))
        assertTrue(source.contains("restoreFocus"))
        assertFalse(source.contains("onFocusChanged"))
        assertFalse(source.contains("onGloballyPositioned"))
    }

    @Test
    fun riskyAndDestructiveChangesRequireConfirmation() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt")

        assertTrue(source.contains("confirmFullAccess"))
        assertTrue(source.contains("confirmFastMode"))
        assertTrue(source.contains("快速模式可能消耗双倍额度。是否启用？"))
        assertTrue(source.contains("删除命令前缀"))
        assertTrue(source.contains("ConfirmDialog("))
        assertTrue(source.contains("controller.saveDefaults"))
        assertTrue(source.contains("controller.saveCustomApproval"))
        assertTrue(source.contains("controller.saveOfficial"))
    }

    @Test
    fun fastModeUsesSwitchSemanticsInsteadOfChoiceDialog() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt")
        val fastModeRow = source.substringAfter("private fun FastModeSwitchRow(")
            .substringBefore("@Composable", missingDelimiterValue = source)
        val officialDialogs = source.substringAfter("private enum class OfficialDialog")
            .substringBefore("private fun WindowsApprovalMode.displayName")

        assertTrue(source.contains("FastModeSwitchRow("))
        assertTrue(fastModeRow.contains("Switch("))
        assertTrue(fastModeRow.contains("role = Role.Switch"))
        assertTrue(fastModeRow.contains("stateDescription"))
        assertTrue(fastModeRow.contains("Modifier.clearAndSetSemantics {}"))
        assertFalse(officialDialogs.contains("ServiceTier"))
        assertFalse(source.contains("OfficialDialog.ServiceTier -> OptionalStringDialog("))
    }

    @Test
    fun optionalDialogsDismissWithoutSelectingCurrentValue() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt")
        val optionalDialog = source.substringAfter("private fun OptionalStringDialog(")
            .substringBefore("@Composable", missingDelimiterValue = source)
        val longDialog = source.substringAfter("private fun LongChoiceDialog(")
            .substringBefore("@Composable", missingDelimiterValue = source)

        assertTrue(optionalDialog.contains("onDismiss: () -> Unit"))
        assertTrue(optionalDialog.contains("onDismiss = onDismiss"))
        assertFalse(optionalDialog.contains("onDismiss = { onSelect(selected) }"))
        assertTrue(longDialog.contains("onDismiss: () -> Unit"))
        assertTrue(longDialog.contains("onDismiss = onDismiss"))
        assertFalse(longDialog.contains("onDismiss = { onSelect(selected) }"))
    }

    @Test
    fun settingsRowsForwardNativeDisabledStateDuringWorkOrUnavailableChoices() {
        val source = readProjectFile("src/main/java/com/mycodex/mobile/settings/WindowsSettingsScreens.kt")
        val valueRow = source.substringAfter("private fun SettingsValueRow(")
            .substringBefore("@Composable", missingDelimiterValue = source)
        val navigationRow = source.substringAfter("private fun SettingsNavigationRow(")
            .substringBefore("@Composable", missingDelimiterValue = source)

        assertTrue(valueRow.contains("enabled = enabled"))
        assertTrue(navigationRow.contains("enabled: Boolean = true"))
        assertTrue(navigationRow.contains("enabled = enabled"))
        assertTrue(source.contains("enabled = enabled && selectedModel?.reasoningEfforts?.isNotEmpty() == true"))
        assertTrue(source.contains("enabled = !state.loading && !state.saving"))
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?: error("Could not find project file: $relativePath")
    }
}
