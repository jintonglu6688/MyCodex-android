package com.mycodex.mobile.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.mycodex.mobile.R
import com.mycodex.mobile.relay.RemoteEventSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal class NotificationSoundCoordinator(context: Context) {
    private val preferences = NotificationSoundPreferenceStore(context)
    private val player: NotificationSoundPlayer = AndroidNotificationSoundPlayer(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val deduplicator = NotificationSoundEventDeduplicator()
    @Volatile private var settings = preferences.load()
    private var boundSource: RemoteEventSource? = null
    private var eventJob: Job? = null

    @Synchronized
    fun bind(source: RemoteEventSource?) {
        if (boundSource === source) {
            return
        }
        boundSource = source
        eventJob?.cancel()
        eventJob = null
        deduplicator.clear()
        player.clearPending()
        if (source == null) {
            return
        }
        eventJob = scope.launch {
            source.remoteEvents.collect { event ->
                synchronized(this@NotificationSoundCoordinator) {
                    if (boundSource !== source) return@collect
                    val cue = NotificationSoundEventPolicy.cueFor(event, settings) ?: return@collect
                    if (deduplicator.shouldPlay(event)) {
                        player.play(cue)
                    }
                }
            }
        }
    }

    fun currentSettings(): NotificationSoundSettings = settings

    @Synchronized
    fun updateSetting(cue: NotificationSoundCue, enabled: Boolean): Boolean {
        val updated = settings.withCueEnabled(cue, enabled)
        if (!preferences.save(updated)) {
            return false
        }
        settings = updated
        return true
    }

    fun playMessageSent() {
        if (settings.messageSentEnabled) {
            player.play(NotificationSoundCue.MessageSent)
        }
    }
}

internal interface NotificationSoundPlayer {
    fun play(cue: NotificationSoundCue)
    fun clearPending() {}
}

private class AndroidNotificationSoundPlayer(context: Context) : NotificationSoundPlayer {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundIds = ConcurrentHashMap<NotificationSoundCue, Int>()
    private val cuesBySoundId = ConcurrentHashMap<Int, NotificationSoundCue>()
    private val loadedSoundIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingCues = ConcurrentHashMap.newKeySet<NotificationSoundCue>()

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (status != 0) {
                return@setOnLoadCompleteListener
            }
            loadedSoundIds.add(soundId)
            val cue = cuesBySoundId[soundId] ?: return@setOnLoadCompleteListener
            if (pendingCues.remove(cue)) {
                playLoaded(soundId)
            }
        }
        load(context, NotificationSoundCue.ApprovalRequested, R.raw.approval)
        load(context, NotificationSoundCue.TurnCompleted, R.raw.end_of_session)
        load(context, NotificationSoundCue.SubAgentCompleted, R.raw.subagent_completed)
        load(context, NotificationSoundCue.MessageSent, R.raw.msg_sent)
    }

    override fun play(cue: NotificationSoundCue) {
        val soundId = soundIds[cue] ?: return
        if (loadedSoundIds.contains(soundId)) {
            playLoaded(soundId)
        } else {
            pendingCues.add(cue)
        }
    }

    override fun clearPending() { pendingCues.clear() }

    private fun load(context: Context, cue: NotificationSoundCue, resourceId: Int) {
        val soundId = soundPool.load(context, resourceId, 1)
        soundIds[cue] = soundId
        cuesBySoundId[soundId] = cue
    }

    private fun playLoaded(soundId: Int) {
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}
