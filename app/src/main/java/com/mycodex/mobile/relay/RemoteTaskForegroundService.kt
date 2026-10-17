package com.mycodex.mobile.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.mycodex.mobile.MainActivity
import com.mycodex.mobile.MyCodexApplication
import com.mycodex.mobile.R
import com.mycodex.mobile.coding.CodingRemoteMode
import com.mycodex.mobile.coding.CodingLoadState
import com.mycodex.mobile.coding.CodingMessageRole
import com.mycodex.mobile.coding.CodingRemoteRepository
import com.mycodex.mobile.diagnostics.MobileDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RemoteTaskForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracker = RemoteTaskTracker()
    private var hostId: String? = null
    private var generation: Long = 0
    private var connection: RelayDeviceConnectionManager? = null
    private var eventJob: Job? = null
    private var reconnectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedHost = intent?.getStringExtra(EXTRA_HOST_ID)
        val requestedGeneration = intent?.getLongExtra(EXTRA_GENERATION, 0) ?: 0
        val selection = (application as MyCodexApplication).deviceSelection
        if (!selection.isCurrent(requestedHost, requestedGeneration)) {
            if (!isCurrentSelection()) stopSelf()
            return START_NOT_STICKY
        }
        if (hostId != requestedHost || generation != requestedGeneration) {
            tracker.clear()
            eventJob?.cancel()
            reconnectJob?.cancel()
        }
        hostId = requestedHost
        generation = requestedGeneration
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val mode = intent?.getStringExtra(EXTRA_MODE)?.let { value ->
            CodingRemoteMode.entries.firstOrNull { it.name == value }
        }
        if (sessionId.isBlank() || mode == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_STOP) {
            MobileDiagnostics.info("RemoteTask", "monitoring_stopped mode=${mode.name}")
            tracker.remove(mode, sessionId)
            if (!tracker.hasTasks) {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        startAsForeground()
        tracker.add(mode, sessionId, intent.getStringExtra(EXTRA_ASSISTANT_MESSAGE_ID))
        MobileDiagnostics.info("RemoteTask", "monitoring_started mode=${mode.name}")
        connectToCurrentDevice()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        eventJob?.cancel()
        reconnectJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun connectToCurrentDevice() {
        val manager = (application as MyCodexApplication).deviceSelection.currentConnection(hostId, generation) ?: run {
            stopSelf()
            return
        }
        if (connection === manager && eventJob?.isActive == true) {
            return
        }

        connection = manager
        eventJob?.cancel()
        reconnectJob?.cancel()
        eventJob = serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            manager.remoteEvents.collect(::handleEvent)
        }
        reconnectJob = serviceScope.launch {
            while (isActive && tracker.hasTasks && isCurrentSelection()) {
                manager.connectForForegroundEvents()
                delay(RECONNECT_INTERVAL_MILLIS)
            }
        }
    }

    private fun isCurrentSelection(): Boolean = (application as MyCodexApplication).deviceSelection.isCurrent(hostId, generation)

    private fun handleEvent(event: RemoteEventPayload) {
        if (!isCurrentSelection()) return
        val action = tracker.handle(event)
        MobileDiagnostics.info("RemoteTask", "event type=${event.eventType} action=${action.name}")
        if (action == RemoteTaskEventAction.Refresh) {
            refreshTask(event)
            return
        }
        finishAction(action)
    }

    private fun refreshTask(event: RemoteEventPayload) {
        val mode = CodingRemoteMode.entries.firstOrNull { event.eventType == it.event("session.updated") } ?: return
        val sessionId = event.payload.optString("sessionId").takeIf { it.isNotBlank() } ?: return
        val manager = connection ?: return
        serviceScope.launch {
            val result = CodingRemoteRepository(manager, mode = mode).loadMessages(sessionId)
            if (!isCurrentSelection() || connection !== manager || result.state != CodingLoadState.Loaded) {
                return@launch
            }
            val latestAssistantMessageId = result.items.lastOrNull { it.role == CodingMessageRole.Assistant }?.id
            val action = tracker.handleSnapshot(mode, sessionId, latestAssistantMessageId, result.generating)
            MobileDiagnostics.info("RemoteTask", "snapshot generating=${result.generating} action=${action.name}")
            finishAction(action)
        }
    }

    private fun finishAction(action: RemoteTaskEventAction) {
        if (!isCurrentSelection()) return
        when (action) {
            RemoteTaskEventAction.Vibrate -> vibrateForNewMessage()
            RemoteTaskEventAction.Stop,
            RemoteTaskEventAction.Ignore,
            RemoteTaskEventAction.Refresh,
            -> Unit
        }
        if (!tracker.hasTasks) {
            stopSelf()
        }
    }

    private fun startAsForeground() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "远程任务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持远程任务连接"
                enableVibration(false)
                setSound(null, null)
            },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction("com.mycodex.mobile.OPEN_TASK.$generation")
                .putExtra(EXTRA_HOST_ID, hostId)
                .putExtra(EXTRA_GENERATION, generation),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MyCodex 远程任务")
            .setContentText("正在等待新消息")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun vibrateForNewMessage() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            MobileDiagnostics.info("RemoteTask", "completion_vibration")
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        const val EXTRA_HOST_ID = "host_id"
        const val EXTRA_GENERATION = "selection_generation"
        fun revoke(context: Context) {
            context.stopService(Intent(context, RemoteTaskForegroundService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }

        private const val ACTION_START = "com.mycodex.mobile.remote.START"
        private const val ACTION_STOP = "com.mycodex.mobile.remote.STOP"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_ASSISTANT_MESSAGE_ID = "assistant_message_id"
        private const val CHANNEL_ID = "remote_task_connection"
        private const val NOTIFICATION_ID = 1001
        private const val VIBRATION_MILLIS = 250L
        private const val RECONNECT_INTERVAL_MILLIS = 5_000L

        fun start(
            context: Context,
            sessionId: String,
            mode: CodingRemoteMode,
            latestAssistantMessageId: String?,
            hostId: String?,
            generation: Long,
        ) {
            val selection = (context.applicationContext as MyCodexApplication).deviceSelection
            if (!selection.isCurrent(hostId, generation)) return
            ContextCompat.startForegroundService(
                context,
                intent(context, ACTION_START, sessionId, mode, hostId, generation)
                    .putExtra(EXTRA_ASSISTANT_MESSAGE_ID, latestAssistantMessageId),
            )
        }

        fun stop(context: Context, sessionId: String, mode: CodingRemoteMode, hostId: String?, generation: Long) {
            if (!(context.applicationContext as MyCodexApplication).deviceSelection.isCurrent(hostId, generation)) return
            context.startService(intent(context, ACTION_STOP, sessionId, mode, hostId, generation))
        }

        private fun intent(context: Context, action: String, sessionId: String, mode: CodingRemoteMode, hostId: String?, generation: Long): Intent {
            return Intent(context, RemoteTaskForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_HOST_ID, hostId)
                .putExtra(EXTRA_GENERATION, generation)
        }
    }
}
