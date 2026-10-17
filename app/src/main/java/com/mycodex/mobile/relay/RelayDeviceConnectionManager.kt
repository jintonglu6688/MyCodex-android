package com.mycodex.mobile.relay

import com.mycodex.mobile.devices.ManagedDevice
import com.mycodex.mobile.devices.RelayCertificatePinning
import com.mycodex.mobile.diagnostics.MobileDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class RelayDeviceConnectionManager(
    private val device: ManagedDevice,
    private val authenticationClient: RelayAuthenticationClient,
    private val sessionSigner: (ByteArray) -> ByteArray,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
    private val commandTimeoutMillis: Long = 12_000L,
) : RemoteCommandGateway, RemoteEventSource, Closeable {
    private companion object {
        private const val TransportTimeoutMillis = 8_000L
        private const val HandshakeTimeoutMillis = 8_000L
    }

    private val relayClient = RelayCertificatePinning.withCertificatePin(client, device.certificateSha256)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<RemoteEventPayload?>>()
    private val eventFlow = MutableSharedFlow<RemoteEventPayload>(replay = 0, extraBufferCapacity = 64)
    private val messageListSingleFlight = RemoteCommandSingleFlight<RemoteEventPayload?>()
    private val connectionMutex = Mutex()
    private val generations = AtomicLong()
    private val generationState = RelayGenerationState()
    private val established = MutableStateFlow(false)
    val connectionEstablished = established.asStateFlow()
    @Volatile private var closed = false
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var channel: SecureRelayChannel? = null
    @Volatile private var opening: CompletableDeferred<Boolean>? = null
    private val pendingReplay = PendingInteractionReplay()
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // ponytail: unbounded ordered queue; apply transport backpressure if sustained events outpace the UI.
    private val orderedEvents = Channel<RemoteEventPayload>(Channel.UNLIMITED)

    init {
        eventScope.launch {
            for (event in orderedEvents) {
                if (!closed) eventFlow.emit(event)
            }
        }
    }

    override val remoteEvents: SharedFlow<RemoteEventPayload> = eventFlow.asSharedFlow()

    suspend fun refreshPendingInteractions(): Boolean {
        val token = pendingReplay.begin()
        try {
            val result = sendCommand(RemoteCommandPayload(commandType = "interaction.pending.list"))
            val events = try { pendingInteractionEvents(result) } catch (_: RuntimeException) { null } ?: return false
            for (event in events) {
                requireActiveSelection()
                pendingReplay.replay(token, event) { orderedEvents.trySend(it) }
            }
            return true
        } finally {
            pendingReplay.end(token)
        }
    }

    suspend fun connectForForegroundEvents(): Boolean = withContext(Dispatchers.IO) { ensureConnected() != null }

    suspend fun reconnectForForegroundEvents(): Boolean = withContext(Dispatchers.IO) {
        invalidateCurrent("reconnect")
        ensureConnected() != null
    }

    override suspend fun sendCommand(command: RemoteCommandPayload): RemoteEventPayload? {
        requireActiveSelection()
        val singleFlightKey = command.messageListSingleFlightKey()
        return (if (singleFlightKey == null) sendCommandOnce(command) else messageListSingleFlight.run(singleFlightKey) { sendCommandOnce(command) })
            .also { requireActiveSelection() }
    }

    private suspend fun sendCommandOnce(command: RemoteCommandPayload): RemoteEventPayload? = withContext(Dispatchers.IO) {
        MobileDiagnostics.info(
            "Remote",
            "command_start type=${command.commandType} request=${MobileDiagnostics.shortId(command.requestId)}",
        )
        val secureChannel = ensureConnected() ?: return@withContext localError(command, "connection_unavailable")
        val result = CompletableDeferred<RemoteEventPayload?>()
        if (pending.putIfAbsent(command.requestId, result) != null) return@withContext localError(command, "duplicate_request")
        val startedAt = System.currentTimeMillis()
        if (!secureChannel.sendCommand(command)) {
            pending.remove(command.requestId)
            return@withContext localError(command, "send_failed")
        }
        try {
            withTimeout(command.timeoutMillis ?: commandTimeoutMillis) { result.await() }.also { event ->
                requireActiveSelection()
                MobileDiagnostics.info(
                    "Remote",
                    "command_complete type=${command.commandType} request=${MobileDiagnostics.shortId(command.requestId)} " +
                        "event=${event?.eventType ?: "none"} elapsedMs=${System.currentTimeMillis() - startedAt}",
                )
            }
        } catch (_: TimeoutCancellationException) {
            if (!command.keepConnectionOnTimeout) {
                invalidateCurrent("request_timeout")
            }
            localError(command, "request_timeout", System.currentTimeMillis() - startedAt)
        } finally {
            pending.remove(command.requestId)
        }
    }

    override fun close() {
        synchronized(generationState) {
            closed = true
            invalidateCurrent("closed")
            orderedEvents.close()
            eventScope.cancel()
        }
    }

    private fun requireActiveSelection() {
        if (closed) throw CancellationException("device_selection_revoked")
    }

    private suspend fun ensureConnected(): SecureRelayChannel? {
        requireActiveSelection()
        establishedChannel()?.let { return it }
        return connectionMutex.withLock {
            requireActiveSelection()
            establishedChannel()?.let { return@withLock it }
            if (generationState.current() != 0L) {
                MobileDiagnostics.info("Remote", "Remote websocket replaced device=${MobileDiagnostics.shortId(device.deviceId)}")
                invalidateCurrent("replaced")
            }
            val generation = generations.incrementAndGet()
            MobileDiagnostics.info(
                "Remote",
                "connection_attempt device=${MobileDiagnostics.shortId(device.deviceId)} generation=$generation",
            )
            val transportDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TransportTimeoutMillis)
            val opened = CompletableDeferred<Boolean>()
            synchronized(generationState) {
                requireActiveSelection()
                generationState.activate(generation) { opening = opened }
            }
            val created = try {
                withTimeout(remainingMillis(transportDeadlineNanos)) {
                    val ticket = authenticationClient.ticket(device.binding)
                    MobileDiagnostics.info("Remote", "authentication_succeeded generation=$generation")
                    val request = Request.Builder()
                        .url(RelayRemoteEnvelopeFactory.buildWebSocketUrl(device))
                        .addHeader("Authorization", "Bearer $ticket")
                        .build()
                    synchronized(generationState) {
                        requireActiveSelection()
                        relayClient.newWebSocket(request, listener(generation, opened)).also { webSocket = it }
                    }
                }
            } catch (error: CancellationException) {
                invalidateGeneration(generation, "cancelled")
                if (error is TimeoutCancellationException) return@withLock null
                throw error
            } catch (error: RelayAuthenticationException) {
                MobileDiagnostics.warn("Remote", "authentication_failed generation=$generation code=${error.code}")
                invalidateGeneration(generation, "authentication_failed")
                return@withLock null
            }
            val established = try {
                withTimeout(remainingMillis(transportDeadlineNanos)) { opened.await() } &&
                    withTimeout(HandshakeTimeoutMillis) {
                        while (generationState.isCurrent(generation) && channel?.currentState() != SecureRelayChannel.State.Established) {
                            kotlinx.coroutines.delay(10)
                        }
                        generationState.isCurrent(generation)
                    }
            } catch (error: CancellationException) {
                created.cancel()
                invalidateGeneration(generation, "cancelled")
                if (error is TimeoutCancellationException) false else throw error
            }
            if (!established || !generationState.isCurrent(generation)) {
                created.cancel()
                invalidateGeneration(generation, "connection_timeout")
                null
            } else {
                MobileDiagnostics.info("Remote", "handshake_established generation=$generation")
                generationState.ifCurrent(generation) {
                    this.established.value = true
                    channel
                }
            }
        }
    }

    private fun listener(generation: Long, opened: CompletableDeferred<Boolean>): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            MobileDiagnostics.info("Remote", "websocket_open generation=$generation http=${response.code}")
            val createdChannel = SecureRelayChannel(
                binding = device.binding,
                signer = sessionSigner,
                sendText = { text -> generationState.isCurrent(generation) && webSocket.send(text) },
                onInvalid = { reason -> invalidateGeneration(generation, reason) },
            )
            val accepted = generationState.ifCurrent(generation) {
                this@RelayDeviceConnectionManager.webSocket = webSocket
                channel = createdChannel
                true
            } ?: false
            if (!accepted) { webSocket.cancel(); return }
            if (!createdChannel.start()) {
                invalidateGeneration(generation, "hello_send_failed")
                return
            }
            opened.complete(true)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val secureChannel = generationState.ifCurrent(generation) {
                if (this@RelayDeviceConnectionManager.webSocket === webSocket) channel else null
            } ?: return
            if (text.toByteArray(Charsets.UTF_8).size > SecureRelayFrames.MaxWireFrameBytes) {
                invalidateGeneration(generation, "frame_too_large")
                return
            }
            when (val inbound = secureChannel.receive(text)) {
                is SecureRelayChannel.Inbound.Response -> {
                    MobileDiagnostics.info(
                        "Remote",
                        "response_received generation=$generation type=${inbound.event.eventType} " +
                            "request=${MobileDiagnostics.shortId(inbound.event.requestId)}",
                    )
                    if (inbound.event.requestId != null && generationState.isCurrent(generation) &&
                        this@RelayDeviceConnectionManager.webSocket === webSocket
                    ) {
                        pending[inbound.event.requestId]?.complete(inbound.event)
                    }
                }
                is SecureRelayChannel.Inbound.Event -> {
                    MobileDiagnostics.info("Remote", "event_received generation=$generation type=${inbound.event.eventType}")
                    if (generationState.isCurrent(generation) && this@RelayDeviceConnectionManager.webSocket === webSocket) {
                        pendingReplay.live(inbound.event) { orderedEvents.trySend(it) }
                    }
                }
                else -> Unit
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val secureChannel = generationState.ifCurrent(generation) {
                if (this@RelayDeviceConnectionManager.webSocket === webSocket) channel else null
            } ?: return
            secureChannel.rejectBinary()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            MobileDiagnostics.warn(
                "Remote",
                "websocket_failure generation=$generation http=${response?.code ?: 0}",
                t,
            )
            opened.complete(false)
            invalidateGeneration(generation, if (t is IOException) "network_error" else "connection_failed")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            MobileDiagnostics.info("Remote", "websocket_closed generation=$generation code=$code")
            opened.complete(false)
            invalidateGeneration(generation, "connection_closed")
        }
    }

    private fun invalidateCurrent(reason: String) = invalidateGeneration(generationState.current(), reason)

    private fun establishedChannel(): SecureRelayChannel? {
        val current = generationState.ifCurrent(generationState.current()) { channel } ?: return null
        return current.takeIf { it.currentState() == SecureRelayChannel.State.Established }
    }

    private fun remainingMillis(deadlineNanos: Long): Long {
        val remaining = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())
        return remaining.coerceAtLeast(1L)
    }

    private fun invalidateGeneration(generation: Long, reason: String) {
        if (generation == 0L) return
        val invalidated = generationState.invalidateIfCurrent(generation) {
            established.value = false
            InvalidatedConnection(webSocket, channel).also {
                webSocket = null
                opening?.complete(false)
                opening = null
                channel = null
                completePending(null)
            }
        } ?: return
        MobileDiagnostics.info("Remote", "connection_invalidated generation=$generation reason=$reason")
        invalidated.channel?.close(reason)
        invalidated.webSocket?.cancel()
    }

    private fun localError(command: RemoteCommandPayload, errorCode: String, elapsedMillis: Long? = null): RemoteEventPayload {
        MobileDiagnostics.warn(
            "Remote",
            "command_failed type=${command.commandType} request=${MobileDiagnostics.shortId(command.requestId)} " +
                "code=$errorCode elapsedMs=${elapsedMillis ?: 0}",
        )
        val payload = JSONObject().put("code", errorCode).put("requestId", command.requestId).put("commandType", command.commandType)
        if (elapsedMillis != null) payload.put("elapsedMs", elapsedMillis)
        return RemoteEventPayload(command.requestId, command.operationId, "system.error", payload, errorCode)
    }

    private fun completePending(event: RemoteEventPayload?) { pending.values.forEach { it.complete(event) }; pending.clear() }

    private fun RemoteCommandPayload.messageListSingleFlightKey(): String? =
        if (commandType.endsWith(".messages.list")) "$commandType:$payload" else null

    private data class InvalidatedConnection(
        val webSocket: WebSocket?,
        val channel: SecureRelayChannel?,
    )
}

internal class RelayGenerationState {
    private val lock = Any()
    private var activeGeneration = 0L

    fun current(): Long = synchronized(lock) { activeGeneration }

    fun isCurrent(generation: Long): Boolean = synchronized(lock) { generation != 0L && generation == activeGeneration }

    fun activate(generation: Long, action: () -> Unit) = synchronized(lock) {
        activeGeneration = generation
        action()
    }

    fun <T> ifCurrent(generation: Long, action: () -> T): T? = synchronized(lock) {
        if (generation == 0L || generation != activeGeneration) null else action()
    }

    fun <T> invalidateIfCurrent(generation: Long, action: () -> T): T? = synchronized(lock) {
        if (generation == 0L || generation != activeGeneration) null else {
            activeGeneration = 0L
            action()
        }
    }
}

internal class RemoteCommandSingleFlight<T> {
    private val operations = ConcurrentHashMap<String, CompletableDeferred<T>>()
    suspend fun run(key: String, operation: suspend () -> T): T {
        val own = CompletableDeferred<T>()
        val shared = operations.putIfAbsent(key, own)
        if (shared != null) return shared.await()
        try { return operation().also(own::complete) } catch (error: Throwable) { own.completeExceptionally(error); throw error } finally { operations.remove(key, own) }
    }
}
