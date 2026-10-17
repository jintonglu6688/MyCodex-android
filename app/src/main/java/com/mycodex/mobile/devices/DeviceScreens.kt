package com.mycodex.mobile.devices

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.os.Build
import com.mycodex.mobile.MyCodexApplication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycodex.mobile.AppFocusTarget
import com.mycodex.mobile.NativeAccessibleActionRow
import com.mycodex.mobile.NativeAccessibleButton

@Composable
fun DeviceManagementPage(
    snapshot: DeviceStoreSnapshot,
    listState: LazyListState,
    restoreFocusTarget: String?,
    onFocusRestored: () -> Unit,
    onAddDevice: () -> Unit,
    onOpenDevice: (String) -> Unit,
    contentPadding: PaddingValues,
    selectionState: DeviceSelectionState,
) {
    var focusReady by remember(restoreFocusTarget) { mutableStateOf(false) }
    val focusTarget = restoreFocusTarget?.takeIf { target ->
        target == AppFocusTarget.DeviceAddButton ||
            snapshot.devices.any { AppFocusTarget.deviceRow(it.id) == target }
    } ?: restoreFocusTarget?.let { AppFocusTarget.DeviceAddButton }
    LaunchedEffect(restoreFocusTarget, snapshot.devices.size) {
        if (focusTarget != null) {
            val index = deviceListFocusIndex(snapshot.devices, focusTarget)
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                listState.scrollToItem(index)
            }
            focusReady = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item(key = "current_device") {
            val currentDevice = snapshot.devices.firstOrNull { it.id == snapshot.currentDeviceId }
            DeviceStatusPanel(
                title = "当前连接",
                body = currentDevice?.let {
                    "${it.displayName}，${deviceConnectionPresentation(it, true, selectionState).summary}"
                } ?: "尚未选择设备",
            )
            selectionState.statusText?.takeIf(String::isNotBlank)?.let { status ->
                Spacer(Modifier.height(12.dp))
                if (selectionState.switching) DeviceLoadingPanel(status) else
                    DeviceStatusPanel("连接结果", status)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (snapshot.devices.isEmpty()) {
            item(key = "empty_devices") {
                DeviceStatusPanel(
                    title = "尚未添加设备",
                    body = "请从电脑端目标生成配对码，然后添加设备。",
                )
                Spacer(Modifier.height(12.dp))
            }
        } else {
            items(snapshot.devices, key = { it.id }) { device ->
                DeviceRow(
                    device = device,
                    current = device.id == snapshot.currentDeviceId,
                    selectionState = selectionState,
                    restoreFocus = focusReady && focusTarget == AppFocusTarget.deviceRow(device.id),
                    onFocusRestored = onFocusRestored,
                    onClick = { onOpenDevice(device.id) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        item(key = "add_device") {
            Spacer(Modifier.height(16.dp))
            NativeAccessibleButton(
                text = "新增设备",
                testTag = "add_device_button",
                restoreFocus = focusReady && focusTarget == AppFocusTarget.DeviceAddButton,
                onFocusRestored = onFocusRestored,
                onClick = onAddDevice,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: ManagedDevice,
    current: Boolean,
    selectionState: DeviceSelectionState,
    restoreFocus: Boolean,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit,
) {
    val presentation = deviceConnectionPresentation(device, current, selectionState)
    NativeAccessibleActionRow(
        title = device.displayName,
        body = listOfNotNull(presentation.summary, presentation.lastCheckText).joinToString("\n"),
        testTag = "device_row_${device.id}",
        restoreFocus = restoreFocus,
        onFocusRestored = onFocusRestored,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        contentDescription = "${device.displayName}，${presentation.summary}",
    )
}

@Composable
fun AddDevicePage(
    onCancel: () -> Unit,
    onAddDevice: (DeviceBinding) -> Unit,
    contentPadding: PaddingValues,
    retainedHolder: PairingPageViewModel? = null,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val identityStore = remember(context) { (context.applicationContext as MyCodexApplication).deviceIdentityStore }
    val holder = retainedHolder ?: viewModel(key = "pairing_page") {
        PairingPageViewModel(identityStore)
    }
    SideEffect { holder.updateBindingConsumer(onAddDevice) }
    val controller = holder.controller
    val pairingState by controller.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPairing = pairingState is PairingUiState.Waiting

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.launch { resumePolling() }
                Lifecycle.Event.ON_STOP -> controller.pausePolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.pausePolling()
        }
    }

    fun submit(value: String, clearClipboardOnImport: Boolean) {
        if (isPairing) {
            return
        }
        if (value.isBlank()) {
            holder.localStatusText = "请输入或从剪贴板导入完整的 MyCodex 配对 URI。"
            return
        }
        holder.localStatusText = ""
        holder.launch {
            importAndSubmit(value) {
                if (clearClipboardOnImport) {
                    val current = clipboardManager.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(context)
                        ?.toString()
                        .orEmpty()
                    if (PairingPayloadParser.shouldClearClipboard(value, current, true)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            clipboardManager.clearPrimaryClip()
                        } else {
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                }
            }
            if (state.value is PairingUiState.Succeeded) {
                holder.clearTransientState()
            }
        }
    }

    AddDevicePageContent(
        pairingState = pairingState,
        pairingUri = holder.pairingUri,
        localStatusText = holder.localStatusText,
        onPairingUriChanged = { holder.pairingUri = it },
        onImportClipboard = {
            val text = clipboardManager.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
            holder.pairingUri = text
            submit(text, clearClipboardOnImport = true)
        },
        onSubmit = { submit(holder.pairingUri, clearClipboardOnImport = false) },
        onCancel = {
            holder.launch {
                cancel()
                holder.clearTransientState()
                onCancel()
            }
        },
        contentPadding = contentPadding,
    )
}

@Composable
internal fun AddDevicePageContent(
    pairingState: PairingUiState,
    pairingUri: String,
    localStatusText: String,
    onPairingUriChanged: (String) -> Unit,
    onImportClipboard: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues,
) {
    val isPairing = pairingState is PairingUiState.Waiting
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(
                onClick = onImportClipboard,
                enabled = !isPairing,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing_import_clipboard"),
            ) {
                Text(if (isPairing) "正在配对" else "从剪贴板导入")
            }
        }

        item {
            OutlinedTextField(
                value = pairingUri,
                onValueChange = onPairingUriChanged,
                label = { Text("MyCodex 配对 URI") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing_uri_input")
                    .semantics { contentDescription = "完整配对 URI" },
                enabled = !isPairing,
            )
        }
        if (pairingState is PairingUiState.Waiting) {
            item {
                Text(
                    text = "目标：${pairingState.target}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag("pairing_target_summary")
                        .semantics { contentDescription = "目标 ${pairingState.target}" },
                )
            }
            item {
                Text(
                    text = pairingState.shortCode.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .testTag("pairing_short_code")
                        .semantics {
                            contentDescription = digitReadableShortCode(pairingState.shortCode)
                        },
                )
            }
        }
        val statusText = when (val state = pairingState) {
            PairingUiState.Idle -> localStatusText
            is PairingUiState.Waiting -> "等待 Windows 允许。请核对上方六位短码。"
            is PairingUiState.Succeeded -> "设备已安全配对。"
            is PairingUiState.Failed -> describePairingError(state.code)
        }
        if (statusText.isNotBlank()) {
            item {
                androidx.compose.foundation.layout.Box(Modifier.testTag("pairing_status")) {
                    when (pairingState) {
                        is PairingUiState.Waiting -> DeviceLoadingPanel(statusText)
                        is PairingUiState.Failed -> DeviceErrorPanel(statusText)
                        PairingUiState.Idle -> DeviceErrorPanel(statusText)
                        is PairingUiState.Succeeded -> DeviceStatusPanel("配对完成", statusText)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pairing_cancel"),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onSubmit,
                    enabled = !isPairing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pairing_submit"),
                ) {
                    Text(if (isPairing) "正在配对" else "添加设备")
                }
            }
        }
    }
}

internal fun digitReadableShortCode(shortCode: String): String {
    require(shortCode.length == 6 && shortCode.all(Char::isDigit))
    return "配对短码 " + shortCode.toCharArray().joinToString(" ")
}

@Composable
fun DeviceDetailPage(
    device: ManagedDevice,
    current: Boolean,
    selectionState: DeviceSelectionState,
    onConnect: () -> Unit,
    onCancelSwitch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPairDevice: () -> Unit,
    onDelete: () -> Unit,
    contentPadding: PaddingValues,
    restoreSettingsFocus: Boolean = false,
    onSettingsFocusRestored: () -> Unit = {},
) {
    var confirmDelete by remember(device.id) { mutableStateOf(false) }
    var deleteFocusGeneration by remember(device.id) { mutableStateOf(0L) }
    val presentation = deviceConnectionPresentation(device, current, selectionState)
    val status = selectionState.statusText?.takeIf { selectionState.targetHostId == device.id }
    val listState = rememberLazyListState()
    var settingsFocusReady by remember(restoreSettingsFocus) { mutableStateOf(false) }
    LaunchedEffect(restoreSettingsFocus) {
        if (restoreSettingsFocus) {
            val connectIndex = 2 + (if (presentation.lastCheckText != null) 1 else 0) +
                (if (!status.isNullOrBlank()) 1 else 0)
            listState.scrollToItem(if (presentation.connected) connectIndex + 1 else connectIndex)
            settingsFocusReady = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "device_summary") {
            DeviceStatusPanel(
                title = device.displayName,
                body = presentation.summary,
            )
        }
        presentation.lastCheckText?.let { text ->
            item(key = "last_check") { Text(text, modifier = Modifier.testTag("device_last_check")) }
        }
        if (!status.isNullOrBlank()) {
            item(key = "connection_status") {
                if (selectionState.switching) {
                    DeviceLoadingPanel(status)
                } else {
                    DeviceStatusPanel("连接结果", status)
                }
            }
        }
        item(key = "switch_explanation") {
            Text("切换后，手机仅接收这个设备的消息；其他目标在电脑上继续运行。")
        }
        item(key = "connect_device") {
            NativeAccessibleButton(
                text = presentation.actionLabel,
                testTag = "device_connect_button",
                restoreFocus = settingsFocusReady && !presentation.connected,
                onFocusRestored = onSettingsFocusRestored,
                onClick = if (presentation.needsPairing) onPairDevice else onConnect,
                enabled = presentation.canConnect || (presentation.needsPairing && !selectionState.switching),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (selectionState.switching && selectionState.targetHostId == device.id) {
            item(key = "cancel_switch") {
                NativeAccessibleButton(
                    text = "取消切换",
                    testTag = "device_cancel_switch_button",
                    restoreFocus = false,
                    onFocusRestored = {},
                    onClick = onCancelSwitch,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (current) {
            item(key = "device_settings") {
                NativeAccessibleButton(
                    text = "当前设备设置",
                    testTag = "device_settings_button",
                    restoreFocus = settingsFocusReady,
                    onFocusRestored = onSettingsFocusRestored,
                    onClick = onOpenSettings,
                    enabled = presentation.connected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "delete_device") {
            NativeAccessibleButton(
                text = "删除设备",
                testTag = "device_delete_button",
                restoreFocus = deleteFocusGeneration > 0,
                restoreFocusGeneration = deleteFocusGeneration,
                onFocusRestored = {},
                onClick = { confirmDelete = true },
                enabled = !selectionState.switching,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                confirmDelete = false
                deleteFocusGeneration += 1
            },
            title = { Text("删除设备") },
            text = { Text("删除设备“${device.displayName}”？删除后，需要重新配对才能再次使用。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleteFocusGeneration += 1
                }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun DeviceStatusPanel(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun DeviceLoadingPanel(text: String) {
    DeviceStatePanel(
        text = text,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        loading = true,
    )
}

@Composable
private fun DeviceErrorPanel(text: String) {
    DeviceStatePanel(
        text = text,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun DeviceStatePanel(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    loading: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.clearAndSetSemantics {})
            }
            Text(
                text = text,
                color = contentColor,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private fun describePairingError(code: String): String {
    return when (code) {
        "network_error" -> "无法连接中继服务。请检查服务器地址、端口、TLS 设置和网络。"
        "invite_not_found" -> "配对码无效。请在 Windows 端重新生成配对码。"
        "invite_consumed" -> "配对码已被使用。请在 Windows 端重新生成配对码。"
        "invite_expired" -> "配对码已过期。请在 Windows 端重新生成配对码。"
        "auth_failed" -> "配对码验证失败。请重新复制 Windows 端生成的配对码。"
        "invalid_response" -> "中继服务返回内容不完整。请稍后重试。"
        DeviceIdentityException.SECURITY_UNAVAILABLE ->
            "无法初始化手机安全密钥。请重启手机后重试。"
        else -> "配对失败：$code。"
    }
}
