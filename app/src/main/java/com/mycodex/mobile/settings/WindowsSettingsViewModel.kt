package com.mycodex.mobile.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class WindowsSettingsViewModel(
    deviceId: String,
    repository: WindowsSettingsDataSource,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var boundDeviceId = deviceId
    var controller by mutableStateOf(WindowsSettingsController(scope, repository))
        private set

    fun bindDevice(deviceId: String, repository: WindowsSettingsDataSource) {
        if (deviceId == boundDeviceId) return
        controller.clearTransientState()
        boundDeviceId = deviceId
        controller = WindowsSettingsController(scope, repository)
    }

    fun clearTransientState() {
        controller.clearTransientState()
    }

    override fun onCleared() {
        controller.clearTransientState()
        scope.cancel()
    }
}
