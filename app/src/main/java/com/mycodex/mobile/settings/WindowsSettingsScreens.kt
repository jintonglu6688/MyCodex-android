package com.mycodex.mobile.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mycodex.mobile.NativeAccessibleActionRow
import com.mycodex.mobile.NativeAccessibleButton
import com.mycodex.mobile.AppSettingsGroup
import com.mycodex.mobile.AppSettingsGroupDivider
import com.mycodex.mobile.AppSettingsHeading

object WindowsSettingsFocusTarget {
    const val Defaults = "windows_settings_defaults"
    const val CustomApproval = "windows_settings_custom_approval"
    const val Official = "windows_settings_official"
}

@Composable
fun WindowsSettingsRootPage(
    state: WindowsSettingsUiState,
    restoreFocusTarget: String?,
    onFocusRestored: () -> Unit,
    onOpenDefaults: () -> Unit,
    onOpenCustomApproval: () -> Unit,
    onOpenOfficial: () -> Unit,
    onReload: () -> Unit,
    contentPadding: PaddingValues,
    deviceName: String? = null,
) {
    val enabled = !state.loading && !state.saving
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { AppSettingsHeading("当前设备") }
        state.snapshot?.scope?.let { scope ->
            item {
                WindowsSettingsSummary(
                    title = deviceName ?: scope.hostName,
                    body = listOfNotNull(
                        "${scope.runtimeDisplayName}（${scope.runtimeSource}）",
                        scope.authenticationDisplayName?.takeIf(String::isNotBlank),
                    ).joinToString("\n"),
                )
            }
        }
        if (state.loading) {
            item { QuietProgress("正在加载当前设备设置。") }
        }
        state.errorText?.let { error ->
            item { SettingsError(error) }
            item {
                NativeAccessibleButton(
                    text = "重新加载",
                    testTag = "windows_settings_reload",
                    restoreFocus = false,
                    onFocusRestored = {},
                    onClick = onReload,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.snapshot != null) {
            item {
                AppSettingsGroup {
                    SettingsNavigationRow(
                        title = "默认模型与权限",
                        body = "当前：${state.snapshot.defaults.modelId}，${state.snapshot.defaults.approvalMode.displayName()}",
                        testTag = WindowsSettingsFocusTarget.Defaults,
                        enabled = enabled,
                        restoreFocus = restoreFocusTarget == WindowsSettingsFocusTarget.Defaults,
                        onFocusRestored = onFocusRestored,
                        onClick = onOpenDefaults,
                    )
                    AppSettingsGroupDivider()
                    SettingsNavigationRow(
                        title = "自定义权限",
                        body = "已启用 ${state.snapshot.customApproval.enabledPermissions.size} 类权限，${state.snapshot.customApproval.commandPrefixes.size} 条命令前缀",
                        testTag = WindowsSettingsFocusTarget.CustomApproval,
                        enabled = enabled,
                        restoreFocus = restoreFocusTarget == WindowsSettingsFocusTarget.CustomApproval,
                        onFocusRestored = onFocusRestored,
                        onClick = onOpenCustomApproval,
                    )
                    AppSettingsGroupDivider()
                    SettingsNavigationRow(
                        title = "Codex 高级设置",
                        body = if (state.snapshot.official != null) "搜索、工具和项目文档限制"
                            else "此电脑版本暂不支持远程高级设置，请更新电脑端。",
                        testTag = WindowsSettingsFocusTarget.Official,
                        enabled = enabled && state.snapshot.official != null,
                        restoreFocus = restoreFocusTarget == WindowsSettingsFocusTarget.Official,
                        onFocusRestored = onFocusRestored,
                        onClick = onOpenOfficial,
                    )
                }
            }
        }
    }
}

@Composable
fun WindowsDefaultsSettingsPage(
    state: WindowsSettingsUiState,
    controller: WindowsSettingsController,
    onSaved: () -> Unit,
    contentPadding: PaddingValues,
) {
    val snapshot = state.snapshot
    val draft = state.draft
    var dialog by remember { mutableStateOf<DefaultsDialog?>(null) }
    var confirmFullAccess by remember { mutableStateOf(false) }
    var focusReturnTag by remember { mutableStateOf<String?>(null) }
    val enabled = !state.loading && !state.saving
    val selectedModel = snapshot?.models?.firstOrNull {
        it.modelId == draft?.defaults?.modelId
    }

    SettingsPage(
        contentPadding = contentPadding,
        state = state,
    ) {
        item { AppSettingsHeading("默认模型与权限") }
        if (snapshot != null && draft != null) {
            item {
                AppSettingsGroup {
                    SettingsValueRow(
                        title = "默认模型",
                        value = snapshot.models.firstOrNull { it.modelId == draft.defaults.modelId }?.displayName
                            ?: draft.defaults.modelId,
                        testTag = "windows_defaults_model",
                        enabled = enabled,
                        restoreFocus = focusReturnTag == "windows_defaults_model",
                        onFocusRestored = { focusReturnTag = null },
                        onClick = { dialog = DefaultsDialog.Model },
                    )
                    AppSettingsGroupDivider()
                    SettingsValueRow(
                        title = "推理强度",
                        value = draft.defaults.reasoningEffort.displayReasoning(),
                        testTag = "windows_defaults_reasoning",
                        enabled = enabled && selectedModel?.reasoningEfforts?.isNotEmpty() == true,
                        restoreFocus = focusReturnTag == "windows_defaults_reasoning",
                        onFocusRestored = { focusReturnTag = null },
                        onClick = { dialog = DefaultsDialog.Reasoning },
                    )
                    AppSettingsGroupDivider()
                    SettingsValueRow(
                        title = "默认权限",
                        value = draft.defaults.approvalMode.displayName(),
                        testTag = "windows_defaults_approval",
                        enabled = enabled,
                        restoreFocus = focusReturnTag == "windows_defaults_approval",
                        onFocusRestored = { focusReturnTag = null },
                        onClick = { dialog = DefaultsDialog.Approval },
                    )
                }
            }
            item {
                SaveButton(
                    enabled = enabled,
                    testTag = "windows_defaults_save",
                    onClick = { controller.saveDefaults(onSaved) },
                )
            }
        }
    }

    when (dialog) {
        DefaultsDialog.Model -> SettingsChoiceDialog(
            title = "选择默认模型",
            choices = snapshot?.models.orEmpty().map { Choice(it.displayName, it.modelId) },
            selected = draft?.defaults?.modelId,
            onDismiss = {
                dialog = null
                focusReturnTag = "windows_defaults_model"
            },
            onSelect = {
                controller.selectModel(it)
                dialog = null
                focusReturnTag = "windows_defaults_model"
            },
        )
        DefaultsDialog.Reasoning -> {
            val model = snapshot?.models?.firstOrNull { it.modelId == draft?.defaults?.modelId }
            SettingsChoiceDialog(
                title = "选择推理强度",
                choices = model?.reasoningEfforts.orEmpty().map { Choice(it.displayReasoning(), it) },
                selected = draft?.defaults?.reasoningEffort,
                onDismiss = {
                    dialog = null
                    focusReturnTag = "windows_defaults_reasoning"
                },
                onSelect = {
                    controller.selectReasoningEffort(it)
                    dialog = null
                    focusReturnTag = "windows_defaults_reasoning"
                },
            )
        }
        DefaultsDialog.Approval -> SettingsChoiceDialog(
            title = "选择默认权限",
            choices = WindowsApprovalMode.entries.map { Choice(it.displayName(), it) },
            selected = draft?.defaults?.approvalMode,
            onDismiss = {
                dialog = null
                focusReturnTag = "windows_defaults_approval"
            },
            onSelect = { mode ->
                dialog = null
                if (mode == WindowsApprovalMode.FullAccess) {
                    confirmFullAccess = true
                } else {
                    controller.selectApprovalMode(mode)
                    focusReturnTag = "windows_defaults_approval"
                }
            },
        )
        null -> Unit
    }

    if (confirmFullAccess) {
        ConfirmDialog(
            title = "启用完全访问",
            body = "完全访问允许 Codex 不经逐项确认执行操作。是否继续？",
            onCancel = {
                confirmFullAccess = false
                focusReturnTag = "windows_defaults_approval"
            },
            onConfirm = {
                controller.selectApprovalMode(WindowsApprovalMode.FullAccess)
                confirmFullAccess = false
                focusReturnTag = "windows_defaults_approval"
            },
        )
    }
}

@Composable
fun WindowsCustomApprovalSettingsPage(
    state: WindowsSettingsUiState,
    controller: WindowsSettingsController,
    onSaved: () -> Unit,
    contentPadding: PaddingValues,
) {
    val snapshot = state.snapshot
    val draft = state.draft
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var focusReturnTag by remember { mutableStateOf<String?>(null) }
    val enabled = !state.loading && !state.saving

    SettingsPage(contentPadding = contentPadding, state = state) {
        item { AppSettingsHeading("权限类别") }
        if (snapshot != null && draft != null) {
            item {
                AppSettingsGroup {
                    snapshot.customApproval.availablePermissions.forEachIndexed { index, permission ->
                        if (index > 0) AppSettingsGroupDivider()
                        val checked = permission.id in draft.customApproval.enabledPermissions
                        PermissionCheckboxRow(
                            label = permission.displayName,
                            checked = checked,
                            enabled = enabled,
                            onCheckedChange = {
                                controller.setEnabledPermission(permission.id, it)
                            },
                        )
                    }
                }
            }
            item { AppSettingsHeading("命令前缀") }
            item {
                AppSettingsGroup {
                    SettingsNavigationRow(
                        title = "添加命令前缀",
                        body = "只匹配明确配置的命令开头",
                        testTag = "windows_prefix_add",
                        enabled = enabled,
                        restoreFocus = focusReturnTag == "windows_prefix_add",
                        onFocusRestored = { focusReturnTag = null },
                        onClick = { controller.openPrefixEditor(null, "") },
                    )
                    draft.customApproval.commandPrefixes.forEachIndexed { index, prefix ->
                        AppSettingsGroupDivider()
                        SettingsNavigationRow(
                            title = prefix,
                            body = "点击编辑",
                            testTag = "windows_prefix_edit_$index",
                            enabled = enabled,
                            restoreFocus = focusReturnTag == "windows_prefix_edit_$index",
                            onFocusRestored = { focusReturnTag = null },
                            onClick = { controller.openPrefixEditor(index, prefix) },
                        )
                        AppSettingsGroupDivider()
                        SettingsNavigationRow(
                            title = "删除 $prefix",
                            body = "删除前需要确认",
                            testTag = "windows_prefix_delete_$index",
                            enabled = enabled,
                            restoreFocus = focusReturnTag == "windows_prefix_delete_$index",
                            onFocusRestored = { focusReturnTag = null },
                            onClick = { deleteIndex = index },
                        )
                    }
                }
            }
            item {
                SaveButton(
                    enabled = enabled,
                    testTag = "windows_custom_approval_save",
                    onClick = { controller.saveCustomApproval(onSaved) },
                )
            }
        }
    }

    controller.prefixEditor?.let { edit ->
        PrefixEditDialog(
            initialValue = edit.initialValue,
            value = edit.value,
            onValueChange = controller::updatePrefixEditor,
            onCancel = {
                focusReturnTag = edit.index?.let { "windows_prefix_edit_$it" } ?: "windows_prefix_add"
                controller.closePrefixEditor()
            },
            onConfirm = { value ->
                if (edit.index == null) {
                    controller.addCommandPrefix(value)
                    focusReturnTag = "windows_prefix_add"
                } else {
                    controller.replaceCommandPrefix(edit.index, value)
                    focusReturnTag = "windows_prefix_edit_${edit.index}"
                }
                controller.closePrefixEditor()
            },
        )
    }
    deleteIndex?.let { index ->
        val prefix = draft?.customApproval?.commandPrefixes?.getOrNull(index).orEmpty()
        ConfirmDialog(
            title = "删除命令前缀",
            body = "确定删除“$prefix”吗？",
            onCancel = {
                deleteIndex = null
                focusReturnTag = "windows_prefix_delete_$index"
            },
            onConfirm = {
                val remainingCount =
                    (draft?.customApproval?.commandPrefixes?.size ?: 0) - 1
                controller.deleteCommandPrefix(index)
                deleteIndex = null
                focusReturnTag = if (remainingCount <= 0) {
                    "windows_prefix_add"
                } else {
                    "windows_prefix_edit_${index.coerceAtMost(remainingCount - 1)}"
                }
            },
        )
    }
}

@Composable
fun WindowsOfficialSettingsPage(
    state: WindowsSettingsUiState,
    controller: WindowsSettingsController,
    onSaved: () -> Unit,
    contentPadding: PaddingValues,
) {
    val official = state.draft?.official
    var dialog by remember { mutableStateOf<OfficialDialog?>(null) }
    var confirmFastMode by remember { mutableStateOf(false) }
    var focusReturnTag by remember { mutableStateOf<String?>(null) }
    val enabled = !state.loading && !state.saving

    SettingsPage(contentPadding = contentPadding, state = state) {
        item { AppSettingsHeading("输出与搜索") }
        if (official != null) {
            item {
                AppSettingsGroup {
                    OfficialRow(
                        title = "推理摘要",
                        value = displayOfficialReasoningSummary(official.modelReasoningSummary),
                        description = "控制支持模型是否返回推理摘要。",
                        id = "reasoning_summary",
                        enabled = enabled,
                        focusReturnTag = focusReturnTag,
                        onFocusRestored = { focusReturnTag = null },
                    ) { dialog = OfficialDialog.ReasoningSummary }
                    AppSettingsGroupDivider()
                    OfficialRow(
                        title = "回答详细程度",
                        value = displayOfficialLevel(official.modelVerbosity),
                        description = "控制回答的详细程度；更高可能使用更多输出额度。",
                        id = "verbosity",
                        enabled = enabled,
                        focusReturnTag = focusReturnTag,
                        onFocusRestored = { focusReturnTag = null },
                    ) { dialog = OfficialDialog.Verbosity }
                    AppSettingsGroupDivider()
                    FastModeSwitchRow(
                        checked = official.serviceTier == "priority",
                        enabled = enabled,
                        restoreFocus = focusReturnTag == "windows_official_service_tier",
                        onFocusRestored = { focusReturnTag = null },
                        onCheckedChange = { checked ->
                            if (checked) {
                                confirmFastMode = true
                            } else {
                                controller.updateOfficial { value -> value.copy(serviceTier = null) }
                                focusReturnTag = "windows_official_service_tier"
                            }
                        },
                    )
                    AppSettingsGroupDivider()
                    OfficialRow(
                        title = "网页搜索",
                        value = displayOfficialWebSearch(official.webSearchMode),
                        description = "控制 Codex 使用缓存、索引或实时网页搜索。",
                        id = "web_search",
                        enabled = enabled,
                        focusReturnTag = focusReturnTag,
                        onFocusRestored = { focusReturnTag = null },
                    ) { dialog = OfficialDialog.WebSearch }
                    AppSettingsGroupDivider()
                    OfficialRow(
                        title = "搜索上下文",
                        value = displayOfficialLevel(official.webSearchContextSize),
                        description = "更高可提高搜索召回，但会占用更多上下文。",
                        id = "search_context",
                        enabled = enabled,
                        focusReturnTag = focusReturnTag,
                        onFocusRestored = { focusReturnTag = null },
                    ) { dialog = OfficialDialog.SearchContext }
                    AppSettingsGroupDivider()
                    OfficialRow(
                        title = "查看图片",
                        value = displayOfficialBoolean(official.viewImageToolEnabled),
                        description = "控制本地图片查看工具；跟随默认会保留 Codex 内置行为。",
                        id = "view_image",
                        enabled = enabled,
                        focusReturnTag = focusReturnTag,
                        onFocusRestored = { focusReturnTag = null },
                    ) { dialog = OfficialDialog.ViewImage }
                }
            }
            item { AppSettingsHeading("工具与限制") }
            item {
                AppSettingsGroup {
                    OfficialRow(
                        title = "工具输出限制",
                        value = displayToolOutputLimit(official.toolOutputTokenLimit),
                        description = "限制保留在上下文中的工具输出；过低可能截断日志或差异。",
                        id = "tool_output",
                        enabled = enabled,
                        focusReturnTag = focusReturnTag,
                        onFocusRestored = { focusReturnTag = null },
                    ) { dialog = OfficialDialog.ToolOutput }
                    AppSettingsGroupDivider()
                    OfficialRow(
                        title = "项目文档限制",
                        value = displayProjectDocLimit(official.projectDocMaxBytes),
                        description = "限制加载的项目说明；过低可能遗漏重要指令。",
                        id = "project_docs",
                        enabled = enabled,
                        focusReturnTag = focusReturnTag,
                        onFocusRestored = { focusReturnTag = null },
                    ) { dialog = OfficialDialog.ProjectDocs }
                }
            }
            item {
                SaveButton(
                    enabled = enabled,
                    testTag = "windows_official_save",
                    onClick = { controller.saveOfficial(onSaved) },
                )
            }
        }
    }

    if (official != null) {
        when (dialog) {
            OfficialDialog.ReasoningSummary -> OptionalStringDialog(
                title = "推理摘要",
                values = WindowsOfficialChoices.reasoningSummaries,
                selected = official.modelReasoningSummary,
                displayValue = ::displayOfficialReasoningSummary,
                onDismiss = {
                    dialog = null
                    focusReturnTag = "windows_official_reasoning_summary"
                },
                onSelect = {
                    controller.updateOfficial { value -> value.copy(modelReasoningSummary = it) }
                    dialog = null
                    focusReturnTag = "windows_official_reasoning_summary"
                },
            )
            OfficialDialog.Verbosity -> OptionalStringDialog(
                title = "回答详细程度",
                values = WindowsOfficialChoices.verbosities,
                selected = official.modelVerbosity,
                displayValue = ::displayOfficialLevel,
                onDismiss = {
                    dialog = null
                    focusReturnTag = "windows_official_verbosity"
                },
                onSelect = {
                    controller.updateOfficial { value -> value.copy(modelVerbosity = it) }
                    dialog = null
                    focusReturnTag = "windows_official_verbosity"
                },
            )
            OfficialDialog.WebSearch -> OptionalStringDialog(
                title = "网页搜索",
                values = WindowsOfficialChoices.webSearchModes,
                selected = official.webSearchMode,
                displayValue = ::displayOfficialWebSearch,
                onDismiss = {
                    dialog = null
                    focusReturnTag = "windows_official_web_search"
                },
                onSelect = {
                    controller.updateOfficial { value -> value.copy(webSearchMode = it) }
                    dialog = null
                    focusReturnTag = "windows_official_web_search"
                },
            )
            OfficialDialog.SearchContext -> OptionalStringDialog(
                title = "搜索上下文",
                values = WindowsOfficialChoices.searchContextSizes,
                selected = official.webSearchContextSize,
                displayValue = ::displayOfficialLevel,
                onDismiss = {
                    dialog = null
                    focusReturnTag = "windows_official_search_context"
                },
                onSelect = {
                    controller.updateOfficial { value -> value.copy(webSearchContextSize = it) }
                    dialog = null
                    focusReturnTag = "windows_official_search_context"
                },
            )
            OfficialDialog.ViewImage -> SettingsChoiceDialog(
                title = "查看图片",
                choices = WindowsOfficialChoices.viewImageValues.map { Choice(displayOfficialBoolean(it), it) },
                selected = official.viewImageToolEnabled,
                onDismiss = {
                    dialog = null
                    focusReturnTag = "windows_official_view_image"
                },
                onSelect = {
                    controller.updateOfficial { value -> value.copy(viewImageToolEnabled = it) }
                    dialog = null
                    focusReturnTag = "windows_official_view_image"
                },
            )
            OfficialDialog.ToolOutput -> LongChoiceDialog(
                title = "工具输出限制",
                values = WindowsOfficialChoices.toolOutputLimits,
                selected = official.toolOutputTokenLimit,
                displayValue = ::displayToolOutputLimit,
                onDismiss = {
                    dialog = null
                    focusReturnTag = "windows_official_tool_output"
                },
                onSelect = {
                    controller.updateOfficial { value -> value.copy(toolOutputTokenLimit = it) }
                    dialog = null
                    focusReturnTag = "windows_official_tool_output"
                },
            )
            OfficialDialog.ProjectDocs -> LongChoiceDialog(
                title = "项目文档限制",
                values = WindowsOfficialChoices.projectDocLimits,
                selected = official.projectDocMaxBytes,
                displayValue = ::displayProjectDocLimit,
                onDismiss = {
                    dialog = null
                    focusReturnTag = "windows_official_project_docs"
                },
                onSelect = {
                    controller.updateOfficial { value -> value.copy(projectDocMaxBytes = it) }
                    dialog = null
                    focusReturnTag = "windows_official_project_docs"
                },
            )
            null -> Unit
        }
    }

    if (confirmFastMode && official != null) {
        ConfirmDialog(
            title = "启用快速模式",
            body = "快速模式可能消耗双倍额度。是否启用？",
            onCancel = {
                confirmFastMode = false
                focusReturnTag = "windows_official_service_tier"
            },
            onConfirm = {
                controller.updateOfficial { value -> value.copy(serviceTier = "priority") }
                confirmFastMode = false
                focusReturnTag = "windows_official_service_tier"
            },
        )
    }
}

@Composable
private fun SettingsPage(
    contentPadding: PaddingValues,
    state: WindowsSettingsUiState,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = {
            if (state.loading || state.saving) {
                item {
                    QuietProgress(
                        if (state.saving) "正在保存当前设备设置。" else "正在加载当前设备设置。",
                    )
                }
            }
            state.errorText?.let { error ->
                item { SettingsError(error) }
            }
            content()
        },
    )
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    body: String,
    testTag: String,
    enabled: Boolean = true,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        NativeAccessibleActionRow(
            title = title,
            body = body,
            testTag = testTag,
            restoreFocus = restoreFocus,
            onFocusRestored = onFocusRestored,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "$title，$body",
        )
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    description: String? = null,
    testTag: String,
    enabled: Boolean,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit,
) {
    val body = buildString {
        append("当前：")
        append(value)
        if (!description.isNullOrBlank()) {
            append('\n')
            append(description)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        NativeAccessibleActionRow(
            title = title,
            body = body,
            testTag = testTag,
            restoreFocus = restoreFocus,
            onFocusRestored = onFocusRestored,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "$title，${body.replace('\n', '，')}",
        )
    }
}

@Composable
private fun OfficialRow(
    title: String,
    value: String,
    description: String,
    id: String,
    enabled: Boolean,
    focusReturnTag: String?,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit,
) {
    val tag = "windows_official_$id"
    SettingsValueRow(
        title = title,
        value = value,
        description = description,
        testTag = tag,
        enabled = enabled,
        restoreFocus = focusReturnTag == tag,
        onFocusRestored = onFocusRestored,
        onClick = onClick,
    )
}

@Composable
private fun FastModeSwitchRow(
    checked: Boolean,
    enabled: Boolean,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val state = if (checked) "已启用" else "已关闭，跟随默认"
    LaunchedEffect(restoreFocus) {
        if (restoreFocus) {
            focusRequester.requestFocus()
            onFocusRestored()
        }
    }
    ListItem(
        headlineContent = { Text("快速模式") },
        supportingContent = {
            Text("$state\n启用后响应更快，但可能消耗双倍额度。")
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("windows_official_service_tier")
            .focusRequester(focusRequester)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = state
            },
    )
}

@Composable
private fun PermissionCheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
    )
}

@Composable
private fun SaveButton(
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    NativeAccessibleButton(
        text = "保存",
        testTag = testTag,
        restoreFocus = false,
        onFocusRestored = {},
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsError(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun QuietProgress(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun WindowsSettingsSummary(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class Choice<T>(val label: String, val value: T)

@Composable
private fun <T> SettingsChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T?,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                itemsIndexed(choices) { index, choice ->
                    NativeAccessibleActionRow(
                        title = choice.label,
                        body = if (choice.value == selected) "已选中" else "未选中",
                        testTag = "windows_choice_$index",
                        restoreFocus = false,
                        onFocusRestored = {},
                        onClick = { onSelect(choice.value) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun OptionalStringDialog(
    title: String,
    values: List<String?>,
    selected: String?,
    displayValue: (String?) -> String,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    SettingsChoiceDialog(
        title = title,
        choices = values.map { Choice(displayValue(it), it) },
        selected = selected,
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun LongChoiceDialog(
    title: String,
    values: List<Long?>,
    selected: Long?,
    displayValue: (Long?) -> String,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    SettingsChoiceDialog(
        title = title,
        choices = values.map { Choice(displayValue(it), it) },
        selected = selected,
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun PrefixEditDialog(
    initialValue: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (initialValue.isBlank()) "添加命令前缀" else "编辑命令前缀") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("命令前缀") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.trim().isNotEmpty(),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}


private enum class DefaultsDialog {
    Model,
    Reasoning,
    Approval,
}

private enum class OfficialDialog {
    ReasoningSummary,
    Verbosity,
    WebSearch,
    SearchContext,
    ViewImage,
    ToolOutput,
    ProjectDocs,
}

private fun WindowsApprovalMode.displayName(): String {
    return when (this) {
        WindowsApprovalMode.ReadOnly -> "只读"
        WindowsApprovalMode.Auto -> "自动"
        WindowsApprovalMode.Custom -> "自定义"
        WindowsApprovalMode.FullAccess -> "完全访问"
    }
}

private fun String?.displayReasoning(): String {
    return when (this) {
        "minimal" -> "最少"
        "low" -> "低"
        "medium" -> "中"
        "high" -> "高"
        "xhigh" -> "超高"
        null -> "不适用"
        else -> this
    }
}
