package com.mycodex.mobile.devices

class DeviceRegistry(private val snapshot: DeviceStoreSnapshot) {
    fun addBinding(binding: DeviceBinding, nowEpochMillis: Long): DeviceStoreSnapshot {
        val existing = snapshot.devices.firstOrNull { it.id == binding.hostId }
        val device = ManagedDevice(
            binding = binding,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: nowEpochMillis,
            lastUsedAtEpochMillis = nowEpochMillis,
            pairingState = PairingState.Paired,
        )
        val devices = snapshot.devices
            .filterNot { it.id == device.id }
            .plus(device)
            .sortedByDescending { it.lastUsedAtEpochMillis ?: it.createdAtEpochMillis }
        return DeviceStoreSnapshot(
            devices = devices,
            currentDeviceId = currentDeviceIdIn(devices)
                ?: device.id.takeIf { snapshot.devices.isEmpty() },
        )
    }

    fun selectDevice(deviceId: String, nowEpochMillis: Long): DeviceStoreSnapshot {
        val devices = snapshot.devices.map { device ->
            if (device.id == deviceId) {
                device.copy(lastUsedAtEpochMillis = nowEpochMillis)
            } else {
                device
            }
        }
        return snapshot.copy(
            devices = devices.sortedByDescending { it.lastUsedAtEpochMillis ?: it.createdAtEpochMillis },
            currentDeviceId = if (devices.any { it.id == deviceId }) deviceId else currentDeviceIdIn(devices),
        )
    }

    fun removeDevice(deviceId: String): DeviceStoreSnapshot {
        val devices = snapshot.devices.filterNot { it.id == deviceId }
        return DeviceStoreSnapshot(devices, currentDeviceIdIn(devices))
    }

    fun markConnected(deviceId: String, nowEpochMillis: Long): DeviceStoreSnapshot {
        val devices = snapshot.devices.map { device ->
            if (device.id == deviceId) {
                device.copy(
                    pairingState = PairingState.Connected,
                    lastUsedAtEpochMillis = nowEpochMillis,
                )
            } else {
                device
            }
        }
        return snapshot.copy(
            devices = devices.sortedByDescending { it.lastUsedAtEpochMillis ?: it.createdAtEpochMillis },
            currentDeviceId = currentDeviceIdIn(devices),
        )
    }

    private fun currentDeviceIdIn(devices: List<ManagedDevice>): String? {
        return snapshot.currentDeviceId?.takeIf { currentId -> devices.any { it.id == currentId } }
    }
}
