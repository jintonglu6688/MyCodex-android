package com.mycodex.mobile.devices

import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

data class DeviceConnectionCheck(val checkedAtEpochMillis: Long, val available: Boolean, val errorText: String? = null)
data class DeviceSelectionState(
    val currentHostId: String? = null,
    val generation: Long = 1L,
    val connected: Boolean = false,
    val switching: Boolean = false,
    val targetHostId: String? = null,
    val statusText: String? = null,
    val checks: Map<String, DeviceConnectionCheck> = emptyMap(),
)

/** Owns the single connection. All replacement and rollback use the same close-before-open path. */
internal class DeviceSelectionCoordinator<C : Closeable>(
    initialDevice: ManagedDevice?,
    private val create: (ManagedDevice) -> C,
    private val verify: suspend (C) -> Boolean,
    private val revokeMonitoring: () -> Unit,
    private val commit: (ManagedDevice) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var device = initialDevice
    private var connection: C? = initialDevice?.let(create)
    private val mutableState = MutableStateFlow(DeviceSelectionState(initialDevice?.hostId, generation = UUID.randomUUID().mostSignificantBits))
    val state = mutableState.asStateFlow()

    fun currentConnection(hostId: String?, generation: Long = state.value.generation): C? =
        connection.takeIf { isCurrent(hostId, generation) }

    fun isCurrent(hostId: String?, generation: Long): Boolean =
        !state.value.switching && hostId != null && state.value.currentHostId == hostId && state.value.generation == generation

    suspend fun select(target: ManagedDevice, saveCurrent: () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        val original = device
        try {
            if (target.pairingState != PairingState.Paired && target.pairingState != PairingState.Connected) {
                reportError(target.hostId, "请先完成此设备的配对。")
                return false
            }
            try { saveCurrent() } catch (_: Exception) {
                mutableState.value = state.value.copy(targetHostId = target.hostId, statusText = "保存当前设备草稿失败，未切换设备。")
                return false
            }
            mutableState.value = state.value.copy(
                generation = state.value.generation + 1, switching = true, connected = false,
                targetHostId = target.hostId, statusText = "正在连接 ${target.displayName}…",
            )
            closeCurrent()
            try {
                connection = create(target)
                check(verify(requireNotNull(connection))) { "connection_unavailable" }
                currentCoroutineContext().ensureActive()
                commit(target)
                device = target
                mutableState.value = state.value.copy(
                    currentHostId = target.hostId, connected = true, switching = false, targetHostId = target.hostId,
                    statusText = "已连接 ${target.displayName}",
                    checks = state.value.checks + (target.hostId to DeviceConnectionCheck(now(), true)),
                )
                return true
            } catch (error: Exception) {
                // Cancellation also waits for target disposal before reconnecting the original host.
                withContext(NonCancellable) {
                    closeCurrent()
                    val restored = if (original == null) false else try {
                        connection = create(original)
                        verify(requireNotNull(connection)).also { if (!it) closeCurrent() }
                    } catch (_: Exception) { closeCurrent(); false }
                    mutableState.value = state.value.copy(
                        currentHostId = original?.hostId, switching = false, targetHostId = target.hostId, connected = restored,
                        statusText = (if (error is CancellationException) "已取消切换。" else "无法连接 ${target.displayName}。请检查电脑端目标、手机连接开关及网络。") +
                            (original?.let { "当前设备：${it.displayName}，" + if (restored) "已连接。" else "未连接。" } ?: ""),
                        checks = if (error is CancellationException) state.value.checks else
                            state.value.checks + (target.hostId to DeviceConnectionCheck(now(), false, "连接未成功，请检查电脑端目标及网络。")),
                    )
                }
                return false
            }
        } finally { mutex.unlock() }
    }

    fun disconnect() {
        device = null
        mutableState.value = state.value.copy(currentHostId = null, generation = state.value.generation + 1, connected = false)
        closeCurrent()
    }

    fun reportError(hostId: String?, message: String) {
        if (!state.value.switching) mutableState.value = state.value.copy(targetHostId = hostId, statusText = message)
    }

    private fun closeCurrent() {
        revokeMonitoring()
        connection?.close()
        connection = null
    }
}
