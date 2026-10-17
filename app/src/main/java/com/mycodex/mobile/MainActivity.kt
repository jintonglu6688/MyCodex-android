package com.mycodex.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mycodex.mobile.ui.theme.MyCodexTheme
import com.mycodex.mobile.ui.theme.ThemeMode
import com.mycodex.mobile.ui.theme.ThemePreferenceStore
import com.mycodex.mobile.ui.theme.selectThemeMode
import com.mycodex.mobile.relay.RemoteTaskForegroundService

internal data class ThemeFocusRequest(
    val targetTag: String,
    val generation: Long,
    val sourceActivityId: Long,
)

internal data class ThemeApplicationDecision(
    val queueFocus: Boolean,
    val manuallyRecreate: Boolean,
)

internal fun decideThemeApplication(
    oldDark: Boolean,
    newDark: Boolean,
): ThemeApplicationDecision {
    val effectiveAppearanceChanged = oldDark != newDark
    return ThemeApplicationDecision(
        queueFocus = effectiveAppearanceChanged,
        manuallyRecreate = effectiveAppearanceChanged,
    )
}

class MainActivity : ComponentActivity() {
    private val activityId = SystemClock.elapsedRealtimeNanos()
    internal var pendingFocusRequest: ThemeFocusRequest? = null
    internal var lastConsumedThemeFocusGeneration: Long? = null
    private var currentThemeMode = ThemeMode.System
    private var systemDark by mutableStateOf(false)
    private var systemThemeReceiverRegistered = false
    private val systemThemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncSystemTheme()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val store = ThemePreferenceStore(this)
        val initialMode = store.load()
        currentThemeMode = initialMode
        systemDark = systemIsDark()
        setTheme(initialMode.activityTheme(systemDark))

        val initialDark = initialMode.resolveDark(systemDark)
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = if (initialDark) {
                SystemBarStyle.dark(transparent)
            } else {
                SystemBarStyle.light(transparent, transparent)
            },
            navigationBarStyle = if (initialDark) {
                SystemBarStyle.dark(transparent)
            } else {
                SystemBarStyle.light(transparent, transparent)
            },
        )
        super.onCreate(savedInstanceState)
        if (!isCurrentNotification(intent)) setIntent(Intent(this, MainActivity::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.registerReceiver(
                this,
                systemThemeReceiver,
                IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            systemThemeReceiverRegistered = true
        }

        val restoredFocusRequest = savedInstanceState?.themeFocusRequest()
            ?.takeIf { it.sourceActivityId != activityId }
        pendingFocusRequest = restoredFocusRequest

        setContent {
            var themeMode by remember { mutableStateOf(initialMode) }
            var focusRequest by remember { mutableStateOf(restoredFocusRequest) }
            val darkTheme = themeMode.resolveDark(systemDark)

            MyCodexTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MyCodexApp(
                        themeMode = themeMode,
                        themeFocusRequest = focusRequest,
                        onThemeFocusRestored = {
                            lastConsumedThemeFocusGeneration = focusRequest?.generation
                            focusRequest = null
                            pendingFocusRequest = null
                        },
                        onThemeModeSelected = { requested ->
                            val oldDark = themeMode.resolveDark(systemDark)
                            selectThemeMode(
                                current = themeMode,
                                requested = requested,
                                save = store::save,
                            ) { savedMode ->
                                pendingFocusRequest = null
                                val newDark = savedMode.resolveDark(systemDark)
                                val decision = decideThemeApplication(
                                    oldDark = oldDark,
                                    newDark = newDark,
                                )
                                if (decision.queueFocus) {
                                    pendingFocusRequest = ThemeFocusRequest(
                                        targetTag = savedMode.focusTag(),
                                        generation = SystemClock.elapsedRealtimeNanos(),
                                        sourceActivityId = activityId,
                                    )
                                }
                                currentThemeMode = savedMode
                                if (decision.manuallyRecreate) {
                                    recreate()
                                } else {
                                    themeMode = savedMode
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncSystemTheme()
        (application as MyCodexApplication).androidUpdates.apply {
            setForeground(true)
            refreshInstallAuthorization()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isCurrentNotification(intent)) setIntent(intent)
    }

    private fun isCurrentNotification(intent: Intent?): Boolean {
        if (intent?.hasExtra(RemoteTaskForegroundService.EXTRA_HOST_ID) != true) return true
        return (application as MyCodexApplication).deviceSelection.isCurrent(
            intent.getStringExtra(RemoteTaskForegroundService.EXTRA_HOST_ID),
            intent.getLongExtra(RemoteTaskForegroundService.EXTRA_GENERATION, 0),
        )
    }

    override fun onPause() {
        (application as MyCodexApplication).androidUpdates.setForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (systemThemeReceiverRegistered) {
            unregisterReceiver(systemThemeReceiver)
            systemThemeReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingFocusRequest?.let {
            outState.putString(FocusTargetKey, it.targetTag)
            outState.putLong(FocusGenerationKey, it.generation)
            outState.putLong(FocusSourceActivityKey, it.sourceActivityId)
        }
        super.onSaveInstanceState(outState)
    }

    private fun Bundle.themeFocusRequest(): ThemeFocusRequest? {
        val target = getString(FocusTargetKey) ?: return null
        val generation = getLong(FocusGenerationKey, Long.MIN_VALUE)
        val source = getLong(FocusSourceActivityKey, Long.MIN_VALUE)
        if (generation == Long.MIN_VALUE || source == Long.MIN_VALUE) return null
        return ThemeFocusRequest(target, generation, source)
    }

    private fun ThemeMode.focusTag(): String = "local_theme_$persistedValue"

    private fun syncSystemTheme() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val updatedSystemDark = systemIsDark()
        if (updatedSystemDark == systemDark) return
        systemDark = updatedSystemDark
        if (currentThemeMode == ThemeMode.System) recreate()
    }

    companion object {
        private const val FocusTargetKey = "theme_focus_target"
        private const val FocusGenerationKey = "theme_focus_generation"
        private const val FocusSourceActivityKey = "theme_focus_source_activity"
    }
}

internal fun systemIsDark(): Boolean =
    Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
