package com.mycodex.mobile.devices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PairingPageViewModel(
    identityStore: LocalDeviceIdentityStore,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bindingConsumer: (DeviceBinding) -> Unit = {}
    private var operationJob: Job? = null
    private val random = SecureRandom()

    var pairingUri by mutableStateOf("")
    var localStatusText by mutableStateOf("")

    val controller = PairingController(
        identityProvider = identityStore::load,
        sign = identityStore::sign,
        deriveAgreement = identityStore::deriveAgreement,
        relayClient = RelayPairingClient(),
        persistBinding = { bindingConsumer(it) },
        clock = System::currentTimeMillis,
        claimIdFactory = { UUID.randomUUID().toString() },
        randomBytes = { size -> ByteArray(size).also(random::nextBytes) },
        appVersion = "1.0",
        pollDelay = { delay(it) },
    )

    fun updateBindingConsumer(consumer: (DeviceBinding) -> Unit) {
        bindingConsumer = consumer
    }

    fun launch(block: suspend PairingController.() -> Unit) {
        operationJob?.cancel()
        operationJob = scope.launch { controller.block() }
    }

    fun clearTransientState() {
        operationJob?.cancel()
        operationJob = null
        pairingUri = ""
        localStatusText = ""
        controller.clearLocalState()
    }

    override fun onCleared() {
        bindingConsumer = {}
        clearTransientState()
        scope.cancel()
    }
}
