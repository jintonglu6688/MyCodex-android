package com.mycodex.mobile

import android.app.Application
import com.mycodex.mobile.devices.LocalDeviceIdentityStore
import com.mycodex.mobile.devices.ManagedDevice
import com.mycodex.mobile.devices.DevicePreferencesStore
import com.mycodex.mobile.devices.DeviceRegistry
import com.mycodex.mobile.devices.DeviceSelectionCoordinator
import com.mycodex.mobile.coding.CodingLoadState
import com.mycodex.mobile.coding.CodingRemoteRepository
import com.mycodex.mobile.relay.RemoteTaskForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import com.mycodex.mobile.diagnostics.MobileDiagnostics
import com.mycodex.mobile.notifications.NotificationSoundCoordinator
import com.mycodex.mobile.relay.RelayAuthenticationClient
import com.mycodex.mobile.relay.RelayDeviceConnectionManager
import com.mycodex.mobile.updates.AndroidUpdateController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MyCodexApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val deviceIdentityStore by lazy { LocalDeviceIdentityStore(applicationContext) }
    internal val notificationSounds by lazy { NotificationSoundCoordinator(applicationContext) }
    internal val androidUpdates by lazy {
        AndroidUpdateController(
            context = applicationContext,
            scope = applicationScope,
            manifestUrl = getString(R.string.update_manifest_url),
        )
    }
    private val relayAuthenticationClient by lazy { RelayAuthenticationClient(deviceIdentityStore) }

    override fun onCreate() {
        super.onCreate()
        MobileDiagnostics.initialize(this)
        // Message reading positions are no longer remembered; drop what earlier versions saved.
        deleteSharedPreferences("coding_message_anchors")
    }

    internal val deviceSelection by lazy {
        val store = DevicePreferencesStore(this)
        val snapshot = store.load()
        DeviceSelectionCoordinator(
            initialDevice = snapshot.devices.firstOrNull { it.id == snapshot.currentDeviceId },
            create = { device -> RelayDeviceConnectionManager(device, relayAuthenticationClient, deviceIdentityStore::sign) },
            verify = { manager ->
                manager.connectForForegroundEvents() &&
                    CodingRemoteRepository(manager).loadWorkspaces().state == CodingLoadState.Loaded
            },
            revokeMonitoring = {
                notificationSounds.bind(null)
                RemoteTaskForegroundService.revoke(this)
            },
            commit = { device ->
                store.save(DeviceRegistry(store.load()).selectDevice(device.id, System.currentTimeMillis()))
            },
        )
    }
    private var selectionJob: Job? = null

    internal suspend fun selectDevice(device: ManagedDevice, saveCurrent: () -> Unit): Boolean {
        if (deviceSelection.state.value.switching) return false
        selectionJob = currentCoroutineContext()[Job]
        return try {
            deviceSelection.select(device, saveCurrent).also {
                notificationSounds.bind(deviceSelection.currentConnection(deviceSelection.state.value.currentHostId))
            }
        } finally { selectionJob = null }
    }

    internal fun cancelDeviceSelection() { selectionJob?.cancel() }

    fun relayConnection(device: ManagedDevice?): RelayDeviceConnectionManager? =
        deviceSelection.currentConnection(device?.hostId).also { notificationSounds.bind(it) }
}
