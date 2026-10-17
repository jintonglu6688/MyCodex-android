# MyCodex Android

Android remote client for MyCodex. It connects to a paired MyCodex Windows host through the MyCodex Relay service.

This repository contains only the Android client. A compatible MyCodex Windows host and Relay deployment are required for end-to-end operation.

| Repository | Description |
|---|---|
| [MyCodex](https://github.com/jintonglu6688/MyCodex) | Windows desktop client and remote host |
| [MyCodex-relay](https://github.com/jintonglu6688/MyCodex-relay) | Relay service for mobile remote connections |
| [MyCodex-auth-host](https://github.com/jintonglu6688/MyCodex-auth-host) | Authentication backend used by the Windows client |
| [MyCodex-android](https://github.com/jintonglu6688/MyCodex-android) | Android remote client (this repository) |

## Current status

The client currently implements:

- Secure device pairing from a `mycodex://pair?...` URI, including short-code confirmation
- Android Keystore-backed device identity and persisted Windows device bindings
- Relay certificate pinning, signed challenge/proof authentication, and short-lived WebSocket tickets
- An end-to-end encrypted mobile-to-Windows channel over Relay
- Multiple paired-device management, selection, deletion, and connection testing
- Coding workspace and session browsing
- Remote chat, message refresh, generation stop, and foreground completion monitoring
- Session creation, rename, archive, delete, fork, derive, rollback, and context compaction
- Plugin and skill references as chat input attachments
- Per-session model, reasoning, approval, and plan-mode settings
- Office sessions and chat through the same remote protocol with a reduced feature set
- Remote approval, plan-execution confirmation, and tool-input dialogs
- Remote Windows defaults, custom permissions, command prefixes, and Codex advanced settings
- Accessibility-focused navigation, custom screen-reader actions, and focus restoration
- System-following and explicit light/dark Material 3 themes across Compose, native views, and system bars

The following areas are still placeholders:

- Diagnostics

The foreground-task notification opens the app but does not yet deep-link to the originating session.

## Platform requirements

- The app has `minSdk 26` and `targetSdk 36`.
- Secure pairing and remote control support Android 8.0 / API 26 or newer. Android 12+ keeps agreement keys directly in Android Keystore; Android 8–11 encrypts agreement keys with an Android Keystore AES-GCM key.
- Building requires JDK 17 and Android SDK 36.
- Runtime use requires a compatible Windows host capable of generating a MyCodex pairing URI and a reachable Relay service.

Pairing is currently performed by pasting or importing the complete pairing URI. Camera-based QR scanning is not implemented in this repository.

## Architecture

The project is a single Android application module using Jetpack Compose, Kotlin coroutines, OkHttp, `org.json`, Android Keystore, and SharedPreferences.

```text
Compose UI
  -> feature controllers and page coroutines
  -> CodingRemoteRepository / WindowsSettingsRepository
  -> RelayDeviceConnectionManager
  -> authenticated secure WebSocket
  -> MyCodex Relay
  -> MyCodex Windows host
```

Main source packages:

- `com.mycodex.mobile`: application entry point, app shell, navigation, and shared accessible native controls
- `com.mycodex.mobile.devices`: pairing, device identity, device bindings, and device management
- `com.mycodex.mobile.relay`: Relay authentication, WebSocket lifecycle, request/response routing, events, and foreground monitoring
- `com.mycodex.mobile.security`: protocol transcripts and cryptographic primitives
- `com.mycodex.mobile.coding`: Coding and Office models, repositories, controllers, screens, attachments, and remote interaction dialogs
- `com.mycodex.mobile.settings`: remote Windows settings models, repository, controller, and screens
- `com.mycodex.mobile.ui.theme`: Material 3 color and theme definitions

Navigation is intentionally implemented as a small app-level state machine rather than with Navigation Compose. Coding and Office share the same repository and UI flow; their wire command prefixes and supported operations differ through `CodingRemoteMode`.

## Security model

The pairing URI is treated as a short-lived trust handoff. The client validates its exact schema, expiry, Relay signing-key fingerprint, HTTPS endpoint, and Windows host signature before submitting a pairing claim.

The Android device keeps separate P-256 signing and key-agreement private keys in Android Keystore. After pairing:

1. The client verifies the signed and encrypted Windows approval before saving a binding.
2. Relay authentication uses a signed challenge/proof exchange to obtain a short-lived WebSocket ticket.
3. The WebSocket performs an additional signed ephemeral ECDH handshake with the paired Windows host.
4. Business messages use per-direction AES-GCM keys, nonce prefixes, and strictly increasing sequence numbers.

Relay tickets, pairing claim tokens, session keys, and conversation contents are not persisted. SharedPreferences store device binding metadata and message scroll anchors; Windows remains the source of truth for workspaces, sessions, messages, and host settings.

## Accessibility

Accessibility behavior is part of the application structure, not a later styling pass. The app uses native Android views where Compose accessibility focus restoration is unreliable, exposes secondary row operations as custom accessibility actions, preserves focus targets across child screens, and keeps destructive actions behind the same confirmation flows used by the visible UI.

The staged UI theme and accessibility specification is documented in [`docs/ui-theme-accessibility-spec.md`](docs/ui-theme-accessibility-spec.md).

The reviewed implementation plan for UI phases 0 and 1 is documented in [`docs/ui-theme-phase-0-1-implementation-plan.md`](docs/ui-theme-phase-0-1-implementation-plan.md).

The reviewed implementation plan for UI phase 2 is documented in [`docs/ui-theme-phase-2-implementation-plan.md`](docs/ui-theme-phase-2-implementation-plan.md).

The completed implementation and validation record for UI phase 3 is documented in [`docs/ui-theme-phase-3-implementation-plan.md`](docs/ui-theme-phase-3-implementation-plan.md).

The completed implementation and core validation record for UI phase 4 is documented in [`docs/ui-theme-phase-4-implementation-plan.md`](docs/ui-theme-phase-4-implementation-plan.md).

The completed implementation and core validation record for UI phase 5 is documented in [`docs/ui-theme-phase-5-implementation-plan.md`](docs/ui-theme-phase-5-implementation-plan.md).

## Build

From PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

Build the unsigned release APK:

```powershell
.\gradlew.bat assembleRelease
```

Without signing configuration the release APK is unsigned. To sign it, create `app/signing/release-signing.properties` (the `app/signing/` directory is ignored by Git):

```properties
storeFile=signing/release.jks
storePassword=<store password>
keyAlias=<key alias>
keyPassword=<key password>
```

`storeFile` is resolved relative to the `app` module. Keep the keystore and its passwords out of the repository.

## Tests

Run JVM unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Compile the instrumentation-test APK:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

Run instrumentation tests on a connected emulator or device:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The JVM suite covers protocol parsing, cryptography, pairing, Relay authentication and secure channels, repositories, controllers, persistence helpers, message behavior, settings, and source-level accessibility contracts. Instrumentation tests cover selected Compose behavior, navigation, pairing accessibility, Keystore identity, and the secure Relay channel.

## License

MyCodex Android is released under the [MIT License](LICENSE). Dependencies are used under their own licenses.

## Security

Please do not report vulnerabilities in public issues. See [SECURITY.md](SECURITY.md).
