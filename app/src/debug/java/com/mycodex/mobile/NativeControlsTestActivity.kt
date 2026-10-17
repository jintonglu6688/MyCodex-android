package com.mycodex.mobile

import androidx.activity.ComponentActivity

/**
 * Explicit debug-only host for Compose instrumentation tests.
 *
 * Some Android variants refuse to launch the library ComponentActivity alias used by the
 * generic Compose rule. Keeping the host in the debug APK gives instrumentation a declared,
 * deterministic activity without adding a production component.
 */
class NativeControlsTestActivity : ComponentActivity()
