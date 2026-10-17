package com.mycodex.mobile.devices

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class DeviceConnectionPresentation(
    val summary: String,
    val actionLabel: String,
    val canConnect: Boolean,
    val needsPairing: Boolean,
    val connected: Boolean,
    val lastCheckText: String?,
)

internal fun deviceConnectionPresentation(
    device: ManagedDevice,
    current: Boolean,
    selectionState: DeviceSelectionState,
): DeviceConnectionPresentation {
    val paired = device.pairingState == PairingState.Paired || device.pairingState == PairingState.Connected
    val connected = paired && current && selectionState.currentHostId == device.id &&
        selectionState.connected && !selectionState.switching
    val checking = selectionState.switching && selectionState.targetHostId == device.id
    val check = selectionState.checks[device.id]
    val pairingText = if (paired) "已配对" else "配对未完成"
    val availability = when {
        checking -> "正在检查并连接"
        connected -> "已连接"
        current -> "未连接"
        check?.available == false -> "不可用，上次连接未成功"
        else -> "待检查"
    }
    val actionLabel = when {
        checking -> "正在检查并连接"
        connected -> "重新检查连接"
        !paired && device.pairingState == PairingState.Draft -> "完成配对"
        !paired -> "重新配对"
        check?.available == false -> "重试连接"
        else -> "检查并连接"
    }
    val lastCheckText = check?.let {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(it.checkedAtEpochMillis))
        val result = if (it.available) "检查成功" else
            it.errorText?.takeIf(String::isNotBlank) ?: "连接未成功，请检查电脑端目标和网络。"
        "最近检查：$time，$result"
    }
    return DeviceConnectionPresentation(
        summary = "${if (current) "当前设备" else "非当前设备"}，$pairingText，$availability",
        actionLabel = actionLabel,
        canConnect = paired && !selectionState.switching,
        needsPairing = !paired,
        connected = connected,
        lastCheckText = lastCheckText,
    )
}

internal fun deviceListFocusIndex(devices: List<ManagedDevice>, restoreFocusTarget: String): Int {
    val deviceId = restoreFocusTarget.takeIf { it.startsWith("device_row:") }
        ?.substringAfter("device_row:")
    val index = devices.indexOfFirst { it.id == deviceId }
    return if (index >= 0) index + 1 else devices.size.coerceAtLeast(1) + 1
}
