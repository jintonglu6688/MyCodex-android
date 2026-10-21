package com.mycodex.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycodex.mobile.account.UserCenterPage
import com.mycodex.mobile.account.UserCenterRepository
import com.mycodex.mobile.coding.CodingDraftViewModel
import com.mycodex.mobile.coding.CodingMessageListPage
import com.mycodex.mobile.coding.CodingMessage
import com.mycodex.mobile.coding.CodingRemoteMode
import com.mycodex.mobile.coding.CodingRemoteRepository
import com.mycodex.mobile.coding.CodingSession
import com.mycodex.mobile.coding.CodingSessionCreateState
import com.mycodex.mobile.coding.CodingSessionOperationState
import com.mycodex.mobile.coding.CodingSessionSettingsPage
import com.mycodex.mobile.coding.CodingSessionSettingsState
import com.mycodex.mobile.coding.CodingSessionSettingsUpdate
import com.mycodex.mobile.coding.CodingSessionSettingsUpdateState
import com.mycodex.mobile.coding.CodingSessionListPage
import com.mycodex.mobile.coding.CodingWorkspace
import com.mycodex.mobile.coding.CodingWorkspaceListPage
import com.mycodex.mobile.coding.OfficeSessionRootPage
import com.mycodex.mobile.coding.RemoteApprovalController
import com.mycodex.mobile.coding.RemoteApprovalDialog
import com.mycodex.mobile.coding.RemoteApprovalUiState
import com.mycodex.mobile.coding.RemotePlanExecutionController
import com.mycodex.mobile.coding.RemotePlanExecutionDialog
import com.mycodex.mobile.coding.RemotePlanExecutionUiState
import com.mycodex.mobile.coding.RemoteToolInputController
import com.mycodex.mobile.coding.RemoteToolInputDialog
import com.mycodex.mobile.coding.RemoteToolInputUiState
import com.mycodex.mobile.coding.ThreadOperationErrorDialog
import com.mycodex.mobile.coding.copyAllMessagesText
import com.mycodex.mobile.coding.threadOperationFailureText
import com.mycodex.mobile.devices.AddDevicePage
import com.mycodex.mobile.devices.DeviceBinding
import com.mycodex.mobile.devices.DeviceDetailPage
import com.mycodex.mobile.devices.DeviceManagementPage
import com.mycodex.mobile.devices.DevicePreferencesStore
import com.mycodex.mobile.devices.DeviceRegistry
import com.mycodex.mobile.devices.DeviceStoreSnapshot
import com.mycodex.mobile.devices.ManagedDevice
import com.mycodex.mobile.diagnostics.MobileDiagnostics
import com.mycodex.mobile.devices.PairingPageViewModel
import com.mycodex.mobile.settings.WindowsCustomApprovalSettingsPage
import com.mycodex.mobile.settings.WindowsDefaultsSettingsPage
import com.mycodex.mobile.settings.WindowsOfficialSettingsPage
import com.mycodex.mobile.settings.WindowsSettingsController
import com.mycodex.mobile.settings.WindowsSettingsFocusTarget
import com.mycodex.mobile.settings.WindowsSettingsRepository
import com.mycodex.mobile.settings.WindowsSettingsRootPage
import com.mycodex.mobile.settings.WindowsSettingsViewModel
import com.mycodex.mobile.ui.theme.MyCodexTheme
import com.mycodex.mobile.ui.theme.ThemeMode
import com.mycodex.mobile.updates.AndroidUpdateUiState
import com.mycodex.mobile.updates.UpdatePromptDialog
import com.mycodex.mobile.updates.UpdateToastHost
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MyCodexApp(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Boolean,
    themeFocusRequest: ThemeFocusRequest? = null,
    onThemeFocusRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as MyCodexApplication
    val notificationSounds = remember(application) { application.notificationSounds }
    val updateController = remember(application) { application.androidUpdates }
    val updateState by updateController.state.collectAsState()
    var notificationSoundSettings by remember {
        mutableStateOf(notificationSounds.currentSettings())
    }
    val codingTransient: CodingDraftViewModel = viewModel(key = "coding_transient:coding") {
        CodingDraftViewModel()
    }
    val officeTransient: CodingDraftViewModel = viewModel(key = "coding_transient:office") {
        CodingDraftViewModel()
    }
    val pairingTransient: PairingPageViewModel = viewModel(key = "pairing_transient") {
        PairingPageViewModel(application.deviceIdentityStore)
    }
    val navigation: AppNavigationViewModel = viewModel(key = "app_navigation")
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val deviceStore = remember(context) { DevicePreferencesStore(context) }
    var deviceSnapshot by remember { mutableStateOf(deviceStore.load()) }
    var selectedRoute by navigation::selectedRoute
    var activeScreen by navigation::activeScreen
    var diagnosticsShareStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var detailDeviceId by navigation::detailDeviceId
    var codingWorkspaceId by navigation::codingWorkspaceId
    var codingWorkspaceTitle by navigation::codingWorkspaceTitle
    var codingSessionId by navigation::codingSessionId
    var codingSessionTitle by navigation::codingSessionTitle
    var codingChatMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var codingSessionCreateInProgress by remember { mutableStateOf(false) }
    var codingSessionCreateStatusText by rememberSaveable { mutableStateOf<String?>(null) }
    var codingSessionCreateDialogVisible by rememberSaveable { mutableStateOf(false) }
    var codingSessionListReloadToken by rememberSaveable { mutableStateOf(0) }
    var codingChatMessages by remember { mutableStateOf<List<CodingMessage>>(emptyList()) }
    var codingThreadOperationInProgress by remember { mutableStateOf(false) }
    var codingPlanModeEnabled by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var codingPlanModeToggleInProgress by remember { mutableStateOf(false) }
    var codingThreadOperationErrorText by remember { mutableStateOf<String?>(null) }
    var codingFullForkDialogVisible by rememberSaveable { mutableStateOf(false) }
    var codingCompactDialogVisible by rememberSaveable { mutableStateOf(false) }
    var officeWorkspaceId by navigation::officeWorkspaceId
    var officeWorkspaceTitle by navigation::officeWorkspaceTitle
    var officeSessionId by navigation::officeSessionId
    var officeSessionTitle by navigation::officeSessionTitle
    var officeChatMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var officeSessionCreateInProgress by remember { mutableStateOf(false) }
    var officeSessionCreateStatusText by rememberSaveable { mutableStateOf<String?>(null) }
    var officeSessionCreateDialogVisible by rememberSaveable { mutableStateOf(false) }
    var officeSessionListReloadToken by rememberSaveable { mutableStateOf(0) }
    var officeChatMessages by remember { mutableStateOf<List<CodingMessage>>(emptyList()) }
    var officeOperationInProgress by remember { mutableStateOf(false) }
    var officePlanModeEnabled by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var officePlanModeToggleInProgress by remember { mutableStateOf(false) }
    var mainRestoreFocusTarget by navigation::mainRestoreFocusTarget
    var deviceRestoreFocusTarget by navigation::deviceRestoreFocusTarget
    var codingRestoreFocusTarget by navigation::codingRestoreFocusTarget
    var officeRestoreFocusTarget by navigation::officeRestoreFocusTarget
    var windowsSettingsRestoreFocusTarget by navigation::windowsSettingsRestoreFocusTarget
    var settingsOriginDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreDeviceSettingsFocus by rememberSaveable { mutableStateOf(false) }
    var pairingOriginDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val deviceManagementListState = rememberLazyListState()
    val codingWorkspaceListState = rememberLazyListState()
    val codingSessionListState = rememberLazyListState()
    val officeSessionListState = rememberLazyListState()
    var foregroundResumeCount by remember { mutableStateOf(0) }
    var foregroundRefreshToken by remember { mutableStateOf(0) }
    val selectedDestination = MainTabDestination.entries.firstOrNull { it.route == selectedRoute }
        ?: MainTabDestination.Coding
    val selectionState by application.deviceSelection.state.collectAsState()
    val workspaceStateStore = remember { DeviceWorkspaceStateStore(context) }
    val currentDevice = deviceSnapshot.currentDevice()
    val codingConnectionManager = remember(currentDevice, selectionState.generation, selectionState.switching) {
        application.relayConnection(currentDevice)
    }
    val secureConnected by codingConnectionManager?.connectionEstablished?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val deviceUiState = selectionState.copy(connected = selectionState.connected && secureConnected)
    val codingRepository = remember(codingConnectionManager) {
        CodingRemoteRepository(codingConnectionManager)
    }
    val officeRepository = remember(codingConnectionManager) {
        CodingRemoteRepository(codingConnectionManager, mode = CodingRemoteMode.Office)
    }
    val userCenterRepository = remember(codingConnectionManager) {
        UserCenterRepository(codingConnectionManager)
    }
    val appScope = rememberCoroutineScope()

    fun saveCurrentWorkspace() {
        check(codingTransient.workspaceRestored && officeTransient.workspaceRestored) { "workspace_state_unavailable" }
        val hostId = navigation.boundHostId ?: run {
            check(currentDevice == null) { "workspace_state_unavailable" }
            return
        }
        workspaceStateStore.save(hostId, JSONObject()
            .put("coding", codingTransient.snapshot()).put("office", officeTransient.snapshot())
            .put("navigation", navigation.workspaceSnapshot())
            .put("codingWorkspaceIndex", codingWorkspaceListState.firstVisibleItemIndex)
            .put("codingWorkspaceOffset", codingWorkspaceListState.firstVisibleItemScrollOffset)
            .put("codingSessionIndex", codingSessionListState.firstVisibleItemIndex)
            .put("codingSessionOffset", codingSessionListState.firstVisibleItemScrollOffset)
            .put("officeSessionIndex", officeSessionListState.firstVisibleItemIndex)
            .put("officeSessionOffset", officeSessionListState.firstVisibleItemScrollOffset))
    }

    fun restoreWorkspace(hostId: String?, switching: Boolean) {
        val saved = hostId?.let(workspaceStateStore::load)
        codingTransient.restore(saved?.optJSONObject("coding"))
        officeTransient.restore(saved?.optJSONObject("office"))
        codingTransient.workspaceRestored = true
        officeTransient.workspaceRestored = true
        navigation.restoreWorkspace(saved?.optJSONObject("navigation"))
        navigation.boundHostId = hostId
        codingWorkspaceListState.requestScrollToItem(saved?.optInt("codingWorkspaceIndex") ?: 0, saved?.optInt("codingWorkspaceOffset") ?: 0)
        codingSessionListState.requestScrollToItem(saved?.optInt("codingSessionIndex") ?: 0, saved?.optInt("codingSessionOffset") ?: 0)
        officeSessionListState.requestScrollToItem(saved?.optInt("officeSessionIndex") ?: 0, saved?.optInt("officeSessionOffset") ?: 0)
        if (switching) activeScreen = AppScreen.Main.name
        codingChatMessages = emptyList()
        officeChatMessages = emptyList()
        codingThreadOperationErrorText = null
        codingPlanModeEnabled = null
        officePlanModeEnabled = null
        codingPlanModeToggleInProgress = false
        officePlanModeToggleInProgress = false
        codingFullForkDialogVisible = false
        codingCompactDialogVisible = false
        codingSessionCreateDialogVisible = false
        officeSessionCreateDialogVisible = false
        codingSessionCreateInProgress = false
        officeSessionCreateInProgress = false
        codingThreadOperationInProgress = false
        officeOperationInProgress = false
        codingSessionCreateStatusText = null
        officeSessionCreateStatusText = null
    }

    LaunchedEffect(Unit) {
        try {
            if (!codingTransient.workspaceRestored || !officeTransient.workspaceRestored) {
                // SavedStateHandle data from an older version belongs only to its original selected host.
                val hostId = currentDevice?.hostId
                if (hostId != null && workspaceStateStore.load(hostId) != null) restoreWorkspace(hostId, false)
                else {
                    if (navigation.boundHostId != null && navigation.boundHostId != hostId) navigation.restoreWorkspace(null)
                    navigation.boundHostId = hostId
                    codingTransient.workspaceRestored = true
                    officeTransient.workspaceRestored = true
                }
            } else if (navigation.boundHostId != currentDevice?.hostId) restoreWorkspace(currentDevice?.hostId, true)
        } catch (_: Exception) {
            application.deviceSelection.reportError(currentDevice?.hostId, "无法读取此设备的草稿，原数据已保留。")
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        try { saveCurrentWorkspace() } catch (_: Exception) { diagnosticsShareStatus = "草稿保存失败，切换前请重试。" }
    }

    LaunchedEffect(currentDevice?.hostId) {
        snapshotFlow { codingTransient.snapshot().toString() + officeTransient.snapshot().toString() + navigation.workspaceSnapshot().toString() }
            .collectLatest {
                delay(300)
                if (!application.deviceSelection.state.value.switching && navigation.boundHostId == currentDevice?.hostId) {
                    try { saveCurrentWorkspace() } catch (_: Exception) { diagnosticsShareStatus = "草稿保存失败，切换前请重试。" }
                }
            }
    }

    fun selectDevice(device: ManagedDevice) {
        if (selectionState.switching) return
        appScope.launch {
            // Validate retained data before committing a new selection; malformed data stays intact.
            try { workspaceStateStore.load(device.hostId) } catch (_: Exception) {
                application.deviceSelection.reportError(device.hostId, "无法读取此设备的草稿，原数据已保留。")
                return@launch
            }
            if (application.selectDevice(device, ::saveCurrentWorkspace)) {
                deviceSnapshot = deviceStore.load()
                restoreWorkspace(device.hostId, true)
                deviceRestoreFocusTarget = AppFocusTarget.deviceRow(device.id)
                foregroundRefreshToken += 1
            }
        }
    }

    LaunchedEffect(Unit) {
        updateController.startAutomaticCheck()
    }

    val settingsBindingKey = "${currentDevice?.id.orEmpty()}:${selectionState.generation}:${selectionState.switching}"
    val windowsSettingsViewModel: WindowsSettingsViewModel = viewModel(
        key = "windows_settings",
    ) {
        WindowsSettingsViewModel(
            settingsBindingKey,
            WindowsSettingsRepository(codingConnectionManager),
        )
    }
    windowsSettingsViewModel.bindDevice(
        settingsBindingKey,
        WindowsSettingsRepository(codingConnectionManager),
    )
    val windowsSettingsController = windowsSettingsViewModel.controller
    val windowsSettingsState by windowsSettingsController.state.collectAsState()
    SideEffect {
        windowsSettingsController.updateCompletionListener {
            when (activeScreen) {
                AppScreen.WindowsDefaultsSettings.name -> {
                    windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.Defaults
                    activeScreen = AppScreen.WindowsSettings.name
                }
                AppScreen.WindowsCustomApprovalSettings.name -> {
                    windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.CustomApproval
                    activeScreen = AppScreen.WindowsSettings.name
                }
                AppScreen.WindowsOfficialSettings.name -> {
                    windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.Official
                    activeScreen = AppScreen.WindowsSettings.name
                }
            }
        }
    }
    val remoteApprovalController = remember(codingConnectionManager) {
        codingConnectionManager?.let { manager ->
            RemoteApprovalController(
                scope = appScope,
                remoteEvents = manager.remoteEvents,
                responder = { requestId, decision ->
                    codingRepository.respondApproval(requestId, decision)
                },
            )
        }
    }
    val remoteApprovalState by remoteApprovalController?.state?.collectAsState()
        ?: remember { mutableStateOf(RemoteApprovalUiState()) }
    val remotePlanExecutionController = remember(codingConnectionManager) {
        codingConnectionManager?.let { manager ->
            RemotePlanExecutionController(
                scope = appScope,
                remoteEvents = manager.remoteEvents,
                responder = { request, action -> codingRepository.respondPlanExecution(request, action) },
            )
        }
    }
    val remotePlanExecutionState by remotePlanExecutionController?.state?.collectAsState()
        ?: remember { mutableStateOf(RemotePlanExecutionUiState()) }
    val remoteToolInputController = remember(codingConnectionManager) {
        codingConnectionManager?.let { manager ->
            RemoteToolInputController(
                scope = appScope,
                remoteEvents = manager.remoteEvents,
                responder = { requestId, answers -> codingRepository.respondToolInput(requestId, answers) },
                canceler = { requestId -> codingRepository.cancelToolInput(requestId) },
            )
        }
    }
    val remoteToolInputState by remoteToolInputController?.state?.collectAsState()
        ?: remember { mutableStateOf(RemoteToolInputUiState()) }

    val updatePromptAllowed = remoteApprovalState.activeRequest == null &&
        remotePlanExecutionState.activeRequest == null &&
        remoteToolInputState.activeRequest == null &&
        !codingSessionCreateDialogVisible &&
        !officeSessionCreateDialogVisible &&
        !codingFullForkDialogVisible &&
        !codingCompactDialogVisible
    SideEffect {
        updateController.setPromptAllowed(updatePromptAllowed)
    }

    LaunchedEffect(Unit) {
        if (!selectionState.switching && !selectionState.connected && currentDevice != null && codingConnectionManager != null) {
            appScope.launch { application.selectDevice(currentDevice, ::saveCurrentWorkspace) }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        foregroundResumeCount += 1
        if (foregroundResumeCount > 1) {
            codingConnectionManager?.let { manager ->
                appScope.launch {
                    manager.reconnectForForegroundEvents()
                    foregroundRefreshToken += 1
                }
            }
        }
    }

    DisposableEffect(remoteApprovalController) {
        remoteApprovalController?.start()
        onDispose {
            remoteApprovalController?.stop()
        }
    }

    DisposableEffect(remoteToolInputController) {
        remoteToolInputController?.start()
        onDispose {
            remoteToolInputController?.stop()
        }
    }

    DisposableEffect(remotePlanExecutionController) {
        remotePlanExecutionController?.start()
        onDispose {
            remotePlanExecutionController?.stop()
        }
    }

    LaunchedEffect(codingConnectionManager, secureConnected, selectionState.connected, foregroundRefreshToken) {
        val manager = codingConnectionManager ?: return@LaunchedEffect
        val hostId = currentDevice?.hostId ?: return@LaunchedEffect
        val generation = selectionState.generation
        if (!secureConnected || !selectionState.connected || selectionState.switching) return@LaunchedEffect
        // A reconnect replaces cached interaction queues with the target's current pending requests.
        remoteApprovalController?.stop()
        remoteApprovalController?.start()
        remoteToolInputController?.stop()
        remoteToolInputController?.start()
        remotePlanExecutionController?.stop()
        remotePlanExecutionController?.start()
        if (!manager.refreshPendingInteractions()) {
            application.deviceSelection.reportError(hostId, "已连接，但待处理审批未能刷新，请重试连接。")
        }
        for ((repository, sessionId) in listOf(codingRepository to codingSessionId, officeRepository to officeSessionId)) {
            if (sessionId.isNullOrBlank()) continue
            val refreshed = repository.loadMessages(sessionId)
            if (!application.deviceSelection.isCurrent(hostId, generation)) return@LaunchedEffect
            if (refreshed.state == com.mycodex.mobile.coding.CodingLoadState.Loaded) {
                if (repository.mode == CodingRemoteMode.Coding) codingChatMessages = refreshed.items else officeChatMessages = refreshed.items
                if (refreshed.generating == true) {
                    com.mycodex.mobile.relay.RemoteTaskForegroundService.start(
                        context, sessionId, repository.mode,
                        refreshed.items.lastOrNull { it.role == com.mycodex.mobile.coding.CodingMessageRole.Assistant }?.id,
                        hostId, generation,
                    )
                }
            }
        }
    }

    LaunchedEffect(activeScreen, currentDevice?.id, windowsSettingsController) {
        if (activeScreen == AppScreen.WindowsSettings.name) {
            windowsSettingsController.loadIfNeeded()
        } else if (
            activeScreen != AppScreen.WindowsDefaultsSettings.name &&
            activeScreen != AppScreen.WindowsCustomApprovalSettings.name &&
            activeScreen != AppScreen.WindowsOfficialSettings.name
        ) {
            windowsSettingsViewModel.clearTransientState()
        }
    }

    LaunchedEffect(activeScreen) {
        if (activeScreen != AppScreen.AddDevice.name) {
            pairingTransient.clearTransientState()
        }
    }

    LaunchedEffect(activeScreen, codingSessionId, currentDevice?.id, codingRepository) {
        if (activeScreen != AppScreen.CodingChat.name || codingSessionId.isNullOrBlank()) {
            codingPlanModeEnabled = null
            codingPlanModeToggleInProgress = false
            return@LaunchedEffect
        }

        val result = codingRepository.loadSessionSettings(codingSessionId!!)
        if (result.state == CodingSessionSettingsState.Loaded && result.settings != null) {
            codingPlanModeEnabled = result.settings.isPlanModeEnabled
        }
    }

    LaunchedEffect(activeScreen, officeSessionId, currentDevice?.id, officeRepository) {
        if (activeScreen != AppScreen.OfficeChat.name || officeSessionId.isNullOrBlank()) {
            officePlanModeEnabled = null
            officePlanModeToggleInProgress = false
            return@LaunchedEffect
        }

        val result = officeRepository.loadSessionSettings(officeSessionId!!)
        if (result.state == CodingSessionSettingsState.Loaded && result.settings != null) {
            officePlanModeEnabled = result.settings.isPlanModeEnabled
        }
    }

    fun saveDeviceSnapshot(snapshot: DeviceStoreSnapshot) {
        deviceStore.save(snapshot)
        deviceSnapshot = snapshot
    }

    fun goBack() {
        if (application.deviceSelection.state.value.switching) {
            application.cancelDeviceSelection()
            return
        }
        when (activeScreen) {
            AppScreen.AddDevice.name -> {
                pairingTransient.clearTransientState()
                if (pairingOriginDeviceId != null) {
                    detailDeviceId = pairingOriginDeviceId
                    pairingOriginDeviceId = null
                    restoreDeviceSettingsFocus = true
                    activeScreen = AppScreen.DeviceDetail.name
                } else {
                    deviceRestoreFocusTarget = AppFocusTarget.DeviceAddButton
                    activeScreen = AppScreen.DeviceManagement.name
                }
            }
            AppScreen.DeviceDetail.name -> {
                detailDeviceId?.let { deviceRestoreFocusTarget = AppFocusTarget.deviceRow(it) }
                activeScreen = AppScreen.DeviceManagement.name
            }
            AppScreen.DeviceManagement.name -> {
                mainRestoreFocusTarget = AppFocusTarget.ProfileDeviceManagement
                selectedRoute = MainTabDestination.Profile.route
                activeScreen = AppScreen.Main.name
            }
            AppScreen.UserCenter.name -> {
                mainRestoreFocusTarget = AppFocusTarget.ProfileUserCenter
                selectedRoute = MainTabDestination.Profile.route
                activeScreen = AppScreen.Main.name
            }
            AppScreen.WindowsSettings.name -> {
                if (settingsOriginDeviceId != null) {
                    detailDeviceId = settingsOriginDeviceId
                    settingsOriginDeviceId = null
                    restoreDeviceSettingsFocus = true
                    activeScreen = AppScreen.DeviceDetail.name
                } else {
                    mainRestoreFocusTarget = AppFocusTarget.ProfileWindowsSettings
                    selectedRoute = MainTabDestination.Profile.route
                    activeScreen = AppScreen.Main.name
                }
            }
            AppScreen.LocalSettings.name -> {
                mainRestoreFocusTarget = AppFocusTarget.ProfileLocalSettings
                selectedRoute = MainTabDestination.Profile.route
                activeScreen = AppScreen.Main.name
            }
            AppScreen.WindowsDefaultsSettings.name -> {
                windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.Defaults
                activeScreen = AppScreen.WindowsSettings.name
            }
            AppScreen.WindowsCustomApprovalSettings.name -> {
                windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.CustomApproval
                activeScreen = AppScreen.WindowsSettings.name
            }
            AppScreen.WindowsOfficialSettings.name -> {
                windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.Official
                activeScreen = AppScreen.WindowsSettings.name
            }
            AppScreen.CodingChat.name -> {
                codingChatMenuExpanded = false
                codingSessionId?.let { codingRestoreFocusTarget = AppFocusTarget.codingSession(it) }
                activeScreen = AppScreen.CodingSessions.name
            }
            AppScreen.CodingSessionSettings.name -> {
                activeScreen = AppScreen.CodingChat.name
            }
            AppScreen.CodingSessions.name -> {
                codingWorkspaceId?.let { codingRestoreFocusTarget = AppFocusTarget.codingWorkspace(it) }
                codingSessionCreateStatusText = null
                codingSessionCreateInProgress = false
                codingSessionCreateDialogVisible = false
                selectedRoute = MainTabDestination.Coding.route
                activeScreen = AppScreen.Main.name
            }
            AppScreen.OfficeChat.name -> {
                officeChatMenuExpanded = false
                officeSessionId?.let { officeRestoreFocusTarget = AppFocusTarget.codingSession(it) }
                selectedRoute = MainTabDestination.Office.route
                activeScreen = AppScreen.Main.name
            }
            AppScreen.OfficeSessionSettings.name -> {
                activeScreen = AppScreen.OfficeChat.name
            }
            else -> activeScreen = AppScreen.Main.name
        }
    }

    BackHandler(enabled = activeScreen != AppScreen.Main.name) {
        goBack()
    }

    val title = when (activeScreen) {
        AppScreen.DeviceManagement.name -> "设备管理"
        AppScreen.AddDevice.name -> "新增设备"
        AppScreen.DeviceDetail.name -> "设备详情"
        AppScreen.UserCenter.name -> "用户中心"
        AppScreen.CodingSessions.name -> "会话"
        AppScreen.CodingChat.name -> codingSessionTitle ?: "聊天"
        AppScreen.CodingSessionSettings.name -> "模型与权限"
        AppScreen.OfficeChat.name -> officeSessionTitle ?: "办公"
        AppScreen.OfficeSessionSettings.name -> "模型与权限"
        AppScreen.WindowsSettings.name -> "当前设备设置 · ${currentDevice?.displayName.orEmpty()}"
        AppScreen.WindowsDefaultsSettings.name -> "默认模型与权限 · ${currentDevice?.displayName.orEmpty()}"
        AppScreen.WindowsCustomApprovalSettings.name -> "自定义权限 · ${currentDevice?.displayName.orEmpty()}"
        AppScreen.WindowsOfficialSettings.name -> "Codex 高级设置 · ${currentDevice?.displayName.orEmpty()}"
        AppScreen.LocalSettings.name -> "本机设置"
        else -> selectedDestination.label
    }

    fun defaultCodingSessionTitle(): String {
        return codingWorkspaceTitle
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: codingWorkspaceId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: "新会话"
    }

    fun showCreateCodingSessionDialog() {
        if (codingWorkspaceId.isNullOrBlank() || codingSessionCreateInProgress) {
            return
        }

        codingTransient.sessionCreateTitle = defaultCodingSessionTitle()
        codingSessionCreateStatusText = null
        codingSessionCreateDialogVisible = true
    }

    fun createCodingSession(titleText: String) {
        val workspaceId = codingWorkspaceId
        val submittedTitle = titleText.trim()
        if (workspaceId.isNullOrBlank() || submittedTitle.isBlank() || codingSessionCreateInProgress) {
            return
        }

        codingTransient.clearSessionCreateTitle()
        appScope.launch {
            codingSessionCreateInProgress = true
            codingSessionCreateStatusText = "正在新建会话。"
            val result = codingRepository.createSession(workspaceId, submittedTitle)
            codingSessionCreateInProgress = false
            if (result.state == CodingSessionCreateState.Created && result.session != null) {
                codingRestoreFocusTarget = AppFocusTarget.codingSession(result.session.id)
                codingSessionCreateStatusText = null
                codingSessionListReloadToken += 1
                codingTransient.clearSessionCreateTitle()
                activeScreen = AppScreen.CodingSessions.name
            } else {
                codingSessionCreateStatusText = "新建会话失败：${result.errorCode ?: "unknown"}"
            }
        }
    }

    fun defaultOfficeSessionTitle(): String {
        return officeWorkspaceTitle
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: officeWorkspaceId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: "新会话"
    }

    fun showCreateOfficeSessionDialog() {
        if (officeWorkspaceId.isNullOrBlank() || officeSessionCreateInProgress) {
            return
        }

        officeTransient.sessionCreateTitle = defaultOfficeSessionTitle()
        officeSessionCreateStatusText = null
        officeSessionCreateDialogVisible = true
    }

    fun createOfficeSession(titleText: String) {
        val workspaceId = officeWorkspaceId
        val submittedTitle = titleText.trim()
        if (workspaceId.isNullOrBlank() || submittedTitle.isBlank() || officeSessionCreateInProgress) {
            return
        }

        officeTransient.clearSessionCreateTitle()
        appScope.launch {
            officeSessionCreateInProgress = true
            officeSessionCreateStatusText = "正在新建会话。"
            val result = officeRepository.createSession(workspaceId, submittedTitle)
            officeSessionCreateInProgress = false
            if (result.state == CodingSessionCreateState.Created && result.session != null) {
                officeRestoreFocusTarget = AppFocusTarget.codingSession(result.session.id)
                officeSessionCreateStatusText = null
                officeSessionListReloadToken += 1
                officeTransient.clearSessionCreateTitle()
                selectedRoute = MainTabDestination.Office.route
                activeScreen = AppScreen.Main.name
            } else {
                officeSessionCreateStatusText = "新建会话失败：${result.errorCode ?: "unknown"}"
            }
        }
    }

    fun openCreatedCodingSession(session: CodingSession) {
        codingWorkspaceId = session.workspaceId
        codingSessionId = session.id
        codingSessionTitle = session.title
        codingSessionListReloadToken += 1
        activeScreen = AppScreen.CodingChat.name
    }

    fun openCreatedOfficeSession(session: CodingSession) {
        officeWorkspaceId = session.workspaceId
        officeSessionId = session.id
        officeSessionTitle = session.title
        officeSessionListReloadToken += 1
        activeScreen = AppScreen.OfficeChat.name
    }

    fun copyAllCodingMessages() {
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText(
                "MyCodex messages",
                copyAllMessagesText(codingChatMessages),
            ),
        )
    }

    fun copyAllOfficeMessages() {
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText(
                "MyCodex office messages",
                copyAllMessagesText(officeChatMessages),
            ),
        )
    }

    fun showFullForkDialog() {
        codingTransient.forkTitle = "${codingSessionTitle ?: codingSessionId ?: "会话"} 分叉"
        codingFullForkDialogVisible = true
    }

    fun runFullFork(titleText: String) {
        val sessionId = codingSessionId
        val submittedTitle = titleText.trim()
        if (sessionId.isNullOrBlank() || submittedTitle.isBlank() || codingThreadOperationInProgress) {
            return
        }

        codingFullForkDialogVisible = false
        codingTransient.forkTitle = ""
        codingThreadOperationErrorText = null
        appScope.launch {
            codingThreadOperationInProgress = true
            val result = codingRepository.forkSession(
                sessionId = sessionId,
                title = submittedTitle,
                turnId = null,
                switchToCreatedSession = true,
            )
            codingThreadOperationInProgress = false
            if (result.state != CodingSessionOperationState.Completed) {
                codingThreadOperationErrorText =
                    threadOperationFailureText("完整分叉", result.errorCode ?: result.state.name.lowercase())
                return@launch
            }

            codingSessionListReloadToken += 1
            if (result.switchToCreatedSession && result.createdSession != null) {
                openCreatedCodingSession(result.createdSession)
            }
        }
    }

    fun runCompactContext() {
        val sessionId = codingSessionId
        if (sessionId.isNullOrBlank() || codingThreadOperationInProgress) {
            return
        }

        codingCompactDialogVisible = false
        codingThreadOperationErrorText = null
        appScope.launch {
            codingThreadOperationInProgress = true
            val result = codingRepository.compactSession(sessionId)
            codingThreadOperationInProgress = false
            if (result.state != CodingSessionOperationState.Completed) {
                codingThreadOperationErrorText =
                    threadOperationFailureText("压缩上下文", result.errorCode ?: result.state.name.lowercase())
            }
        }
    }

    fun resetCodingSessionDefaults() {
        val sessionId = codingSessionId
        if (sessionId.isNullOrBlank() || codingThreadOperationInProgress) {
            return
        }

        codingThreadOperationErrorText = null
        appScope.launch {
            codingThreadOperationInProgress = true
            val result = codingRepository.updateSessionSettings(
                CodingSessionSettingsUpdate(
                    sessionId = sessionId,
                    modelId = null,
                    reasoningEffort = null,
                    approvalMode = null,
                ),
            )
            codingThreadOperationInProgress = false
            if (result.state != CodingSessionSettingsUpdateState.Completed) {
                codingThreadOperationErrorText =
                    threadOperationFailureText("恢复默认", result.errorCode ?: result.state.name.lowercase())
            }
        }
    }

    fun toggleCodingPlanMode() {
        val sessionId = codingSessionId
        val current = codingPlanModeEnabled
        if (sessionId.isNullOrBlank() || current == null || codingPlanModeToggleInProgress || codingThreadOperationInProgress) {
            return
        }

        val next = !current
        appScope.launch {
            codingPlanModeToggleInProgress = true
            val result = codingRepository.updateSessionSettings(
                CodingSessionSettingsUpdate(
                    sessionId = sessionId,
                    updateModelSettings = false,
                    isPlanModeEnabled = next,
                ),
            )
            codingPlanModeToggleInProgress = false
            if (result.state == CodingSessionSettingsUpdateState.Completed) {
                codingPlanModeEnabled = next
                codingSessionListReloadToken += 1
            }
        }
    }

    fun resetOfficeSessionDefaults() {
        val sessionId = officeSessionId
        if (sessionId.isNullOrBlank() || officeOperationInProgress) {
            return
        }

        appScope.launch {
            officeOperationInProgress = true
            officeRepository.updateSessionSettings(
                CodingSessionSettingsUpdate(
                    sessionId = sessionId,
                    modelId = null,
                    reasoningEffort = null,
                    approvalMode = null,
                ),
            )
            officeOperationInProgress = false
        }
    }

    fun toggleOfficePlanMode() {
        val sessionId = officeSessionId
        val current = officePlanModeEnabled
        if (sessionId.isNullOrBlank() || current == null || officePlanModeToggleInProgress || officeOperationInProgress) {
            return
        }

        val next = !current
        appScope.launch {
            officePlanModeToggleInProgress = true
            val result = officeRepository.updateSessionSettings(
                CodingSessionSettingsUpdate(
                    sessionId = sessionId,
                    updateModelSettings = false,
                    isPlanModeEnabled = next,
                ),
            )
            officePlanModeToggleInProgress = false
            if (result.state == CodingSessionSettingsUpdateState.Completed) {
                officePlanModeEnabled = next
                officeSessionListReloadToken += 1
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            MyCodexTopBar(
                title = title,
                dynamicTitle = activeScreen != AppScreen.Main.name,
                onBack = if (activeScreen != AppScreen.Main.name) {
                    { goBack() }
                } else {
                    null
                },
                actions = {
                    if (activeScreen == AppScreen.Main.name && selectedDestination == MainTabDestination.Office) {
                        TopBarTextAction(
                            text = "新建",
                            testTag = "app_top_bar_create",
                            onClick = { showCreateOfficeSessionDialog() },
                            enabled = officeWorkspaceId != null && !officeSessionCreateInProgress,
                        )
                    }
                    if (activeScreen == AppScreen.CodingSessions.name && codingWorkspaceId != null) {
                        TopBarTextAction(
                            text = "新建",
                            testTag = "app_top_bar_create",
                            onClick = { showCreateCodingSessionDialog() },
                            enabled = !codingSessionCreateInProgress,
                        )
                    }
                    if (activeScreen == AppScreen.CodingChat.name && codingSessionId != null) {
                        Box {
                            TopBarTextAction(
                                text = "更多",
                                testTag = "app_top_bar_more",
                                onClick = { codingChatMenuExpanded = true },
                            )
                            DropdownMenu(
                                expanded = codingChatMenuExpanded,
                                onDismissRequest = { codingChatMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("模型与权限") },
                                    onClick = {
                                        codingChatMenuExpanded = false
                                        activeScreen = AppScreen.CodingSessionSettings.name
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("恢复默认") },
                                    onClick = {
                                        codingChatMenuExpanded = false
                                        resetCodingSessionDefaults()
                                    },
                                    enabled = !codingThreadOperationInProgress,
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when {
                                                codingPlanModeEnabled == true -> "关闭计划模式"
                                                codingPlanModeEnabled == false -> "开启计划模式"
                                                else -> "计划模式"
                                            },
                                        )
                                    },
                                    onClick = {
                                        codingChatMenuExpanded = false
                                        toggleCodingPlanMode()
                                    },
                                    enabled = !codingThreadOperationInProgress &&
                                        !codingPlanModeToggleInProgress &&
                                        codingPlanModeEnabled != null,
                                )
                                DropdownMenuItem(
                                    text = { Text("复制全部消息") },
                                    onClick = {
                                        codingChatMenuExpanded = false
                                        copyAllCodingMessages()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("完整分叉") },
                                    onClick = {
                                        codingChatMenuExpanded = false
                                        showFullForkDialog()
                                    },
                                    enabled = !codingThreadOperationInProgress,
                                )
                                DropdownMenuItem(
                                    text = { Text("压缩上下文") },
                                    onClick = {
                                        codingChatMenuExpanded = false
                                        codingCompactDialogVisible = true
                                    },
                                    enabled = !codingThreadOperationInProgress,
                                )
                            }
                        }
                    }
                    if (activeScreen == AppScreen.OfficeChat.name && officeSessionId != null) {
                        Box {
                            TopBarTextAction(
                                text = "更多",
                                testTag = "app_top_bar_more",
                                onClick = { officeChatMenuExpanded = true },
                            )
                            DropdownMenu(
                                expanded = officeChatMenuExpanded,
                                onDismissRequest = { officeChatMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("模型与权限") },
                                    onClick = {
                                        officeChatMenuExpanded = false
                                        activeScreen = AppScreen.OfficeSessionSettings.name
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("恢复默认") },
                                    onClick = {
                                        officeChatMenuExpanded = false
                                        resetOfficeSessionDefaults()
                                    },
                                    enabled = !officeOperationInProgress,
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when {
                                                officePlanModeEnabled == true -> "关闭计划模式"
                                                officePlanModeEnabled == false -> "开启计划模式"
                                                else -> "计划模式"
                                            },
                                        )
                                    },
                                    onClick = {
                                        officeChatMenuExpanded = false
                                        toggleOfficePlanMode()
                                    },
                                    enabled = !officeOperationInProgress &&
                                        !officePlanModeToggleInProgress &&
                                        officePlanModeEnabled != null,
                                )
                                DropdownMenuItem(
                                    text = { Text("复制全部消息") },
                                    onClick = {
                                        officeChatMenuExpanded = false
                                        copyAllOfficeMessages()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (activeScreen == AppScreen.Main.name) {
                MyCodexBottomNavigation(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = { selectedRoute = it.route },
                )
            }
        },
    ) { innerPadding ->
        when (activeScreen) {
            AppScreen.DeviceManagement.name -> DeviceManagementPage(
                snapshot = deviceSnapshot,
                selectionState = deviceUiState,
                listState = deviceManagementListState,
                restoreFocusTarget = deviceRestoreFocusTarget,
                onFocusRestored = { deviceRestoreFocusTarget = null },
                onAddDevice = { pairingOriginDeviceId = null; activeScreen = AppScreen.AddDevice.name },
                onOpenDevice = {
                    detailDeviceId = it
                    activeScreen = AppScreen.DeviceDetail.name
                },
                contentPadding = innerPadding,
            )
            AppScreen.AddDevice.name -> AddDevicePage(
                onCancel = { goBack() },
                onAddDevice = { binding: DeviceBinding ->
                    val nextSnapshot = DeviceRegistry(deviceSnapshot).addBinding(binding, System.currentTimeMillis())
                        .copy(currentDeviceId = deviceSnapshot.currentDeviceId)
                    saveDeviceSnapshot(nextSnapshot)
                    pairingOriginDeviceId = null
                    deviceRestoreFocusTarget = AppFocusTarget.deviceRow(binding.hostId)
                    activeScreen = AppScreen.DeviceManagement.name
                },
                contentPadding = innerPadding,
                retainedHolder = pairingTransient,
            )
            AppScreen.DeviceDetail.name -> {
                val device = deviceSnapshot.devices.firstOrNull { it.id == detailDeviceId }
                if (device == null) {
                    DeviceManagementPage(
                        snapshot = deviceSnapshot,
                        selectionState = deviceUiState,
                        listState = deviceManagementListState,
                        restoreFocusTarget = deviceRestoreFocusTarget,
                        onFocusRestored = { deviceRestoreFocusTarget = null },
                        onAddDevice = { pairingOriginDeviceId = null; activeScreen = AppScreen.AddDevice.name },
                        onOpenDevice = {
                            detailDeviceId = it
                            activeScreen = AppScreen.DeviceDetail.name
                        },
                        contentPadding = innerPadding,
                    )
                } else {
                    DeviceDetailPage(
                        device = device,
                        current = device.id == deviceSnapshot.currentDeviceId,
                        selectionState = deviceUiState,
                        onConnect = { selectDevice(device) },
                        onCancelSwitch = application::cancelDeviceSelection,
                        onOpenSettings = {
                            settingsOriginDeviceId = device.id
                            activeScreen = AppScreen.WindowsSettings.name
                        },
                        restoreSettingsFocus = restoreDeviceSettingsFocus,
                        onSettingsFocusRestored = { restoreDeviceSettingsFocus = false },
                        onPairDevice = { pairingOriginDeviceId = device.id; activeScreen = AppScreen.AddDevice.name },
                        onDelete = {
                            try {
                                val oldIds = deviceSnapshot.devices.map { it.id }
                                val wasCurrent = device.id == deviceSnapshot.currentDeviceId
                                val nextSnapshot = DeviceRegistry(deviceSnapshot).removeDevice(device.id)
                                if (wasCurrent) { saveCurrentWorkspace(); application.deviceSelection.disconnect() }
                                saveDeviceSnapshot(nextSnapshot)
                                MobileDiagnostics.info(
                                    "Devices",
                                    "host_removed host=${MobileDiagnostics.shortId(device.hostId)} wasCurrent=$wasCurrent",
                                )
                                deviceRestoreFocusTarget = adjacentItemId(
                                    oldIds = oldIds,
                                    sourceId = device.id,
                                    newIds = nextSnapshot.devices.map { it.id },
                                )?.let(AppFocusTarget::deviceRow) ?: AppFocusTarget.DeviceAddButton
                                activeScreen = AppScreen.DeviceManagement.name
                            } catch (_: Exception) {
                                application.deviceSelection.reportError(device.hostId, "无法移除设备，草稿和设备信息已保留，请重试。")
                            }
                        },
                        contentPadding = innerPadding,
                    )
                }
            }
            AppScreen.UserCenter.name -> UserCenterPage(
                deviceId = currentDevice?.id,
                repository = userCenterRepository,
                contentPadding = innerPadding,
            )
            AppScreen.CodingSessions.name -> {
                val workspaceId = codingWorkspaceId
                if (workspaceId == null) {
                    activeScreen = AppScreen.Main.name
                } else {
                    CodingSessionListPage(
                        workspaceId = workspaceId,
                        workspaceTitle = codingWorkspaceTitle ?: workspaceId,
                        repository = codingRepository,
                        listState = codingSessionListState,
                        restoreFocusTarget = codingRestoreFocusTarget,
                        onFocusRestored = { codingRestoreFocusTarget = null },
                        externalStatusText = codingSessionCreateStatusText,
                        reloadToken = codingSessionListReloadToken,
                        onCreateSession = { showCreateCodingSessionDialog() },
                        onOpenSession = { session: CodingSession ->
                            codingSessionId = session.id
                            codingSessionTitle = session.title
                            codingSessionCreateStatusText = null
                            activeScreen = AppScreen.CodingChat.name
                        },
                        contentPadding = innerPadding,
                    )
                }
            }
            AppScreen.CodingChat.name -> {
                val sessionId = codingSessionId
                if (sessionId == null) {
                    activeScreen = AppScreen.CodingSessions.name
                } else {
                    CodingMessageListPage(
                        sessionId = sessionId,
                        deviceId = currentDevice?.id,
                        sessionTitle = codingSessionTitle ?: sessionId,
                        workspaceId = codingWorkspaceId,
                        repository = codingRepository,
                        contentPadding = innerPadding,
                        onMessagesChanged = { codingChatMessages = it },
                        onSessionsChanged = { codingSessionListReloadToken += 1 },
                        onOpenCreatedSession = { openCreatedCodingSession(it) },
                        onMessageSent = notificationSounds::playMessageSent,
                        foregroundRefreshToken = foregroundRefreshToken,
                        retainedDraft = codingTransient,
                    )
                }
            }
            AppScreen.CodingSessionSettings.name -> {
                val sessionId = codingSessionId
                if (sessionId == null) {
                    activeScreen = AppScreen.CodingSessions.name
                } else {
                    CodingSessionSettingsPage(
                        sessionId = sessionId,
                        sessionTitle = codingSessionTitle ?: sessionId,
                        repository = codingRepository,
                        onSaved = {
                            activeScreen = AppScreen.CodingChat.name
                        },
                        contentPadding = innerPadding,
                    )
                }
            }
            AppScreen.OfficeChat.name -> {
                val sessionId = officeSessionId
                if (sessionId == null) {
                    selectedRoute = MainTabDestination.Office.route
                    activeScreen = AppScreen.Main.name
                } else {
                    CodingMessageListPage(
                        sessionId = sessionId,
                        deviceId = currentDevice?.id,
                        sessionTitle = officeSessionTitle ?: sessionId,
                        workspaceId = officeWorkspaceId,
                        repository = officeRepository,
                        contentPadding = innerPadding,
                        onMessagesChanged = { officeChatMessages = it },
                        onSessionsChanged = { officeSessionListReloadToken += 1 },
                        onOpenCreatedSession = { openCreatedOfficeSession(it) },
                        onMessageSent = notificationSounds::playMessageSent,
                        foregroundRefreshToken = foregroundRefreshToken,
                        retainedDraft = officeTransient,
                    )
                }
            }
            AppScreen.OfficeSessionSettings.name -> {
                val sessionId = officeSessionId
                if (sessionId == null) {
                    selectedRoute = MainTabDestination.Office.route
                    activeScreen = AppScreen.Main.name
                } else {
                    CodingSessionSettingsPage(
                        sessionId = sessionId,
                        sessionTitle = officeSessionTitle ?: sessionId,
                        repository = officeRepository,
                        onSaved = {
                            activeScreen = AppScreen.OfficeChat.name
                        },
                        contentPadding = innerPadding,
                    )
                }
            }
            AppScreen.WindowsSettings.name -> WindowsSettingsRootPage(
                deviceName = currentDevice?.displayName,
                state = windowsSettingsState,
                restoreFocusTarget = windowsSettingsRestoreFocusTarget,
                onFocusRestored = { windowsSettingsRestoreFocusTarget = null },
                onOpenDefaults = {
                    activeScreen = AppScreen.WindowsDefaultsSettings.name
                },
                onOpenCustomApproval = {
                    activeScreen = AppScreen.WindowsCustomApprovalSettings.name
                },
                onOpenOfficial = {
                    activeScreen = AppScreen.WindowsOfficialSettings.name
                },
                onReload = { windowsSettingsController.load() },
                contentPadding = innerPadding,
            )
            AppScreen.WindowsDefaultsSettings.name -> WindowsDefaultsSettingsPage(
                state = windowsSettingsState,
                controller = windowsSettingsController,
                onSaved = {
                    windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.Defaults
                    activeScreen = AppScreen.WindowsSettings.name
                },
                contentPadding = innerPadding,
            )
            AppScreen.WindowsCustomApprovalSettings.name -> WindowsCustomApprovalSettingsPage(
                state = windowsSettingsState,
                controller = windowsSettingsController,
                onSaved = {
                    windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.CustomApproval
                    activeScreen = AppScreen.WindowsSettings.name
                },
                contentPadding = innerPadding,
            )
            AppScreen.WindowsOfficialSettings.name -> WindowsOfficialSettingsPage(
                state = windowsSettingsState,
                controller = windowsSettingsController,
                onSaved = {
                    windowsSettingsRestoreFocusTarget = WindowsSettingsFocusTarget.Official
                    activeScreen = AppScreen.WindowsSettings.name
                },
                contentPadding = innerPadding,
            )
            AppScreen.LocalSettings.name -> LocalSettingsPage(
                themeMode = themeMode,
                focusRequest = themeFocusRequest,
                onFocusRestored = onThemeFocusRestored,
                onThemeModeSelected = onThemeModeSelected,
                notificationSoundSettings = notificationSoundSettings,
                onNotificationSoundSettingChanged = { cue, enabled ->
                    if (notificationSounds.updateSetting(cue, enabled)) {
                        notificationSoundSettings = notificationSounds.currentSettings()
                        true
                    } else {
                        false
                    }
                },
                contentPadding = innerPadding,
            )
            else -> MainTabContent(
                destination = selectedDestination,
                currentDevice = currentDevice,
                codingRepository = codingRepository,
                officeRepository = officeRepository,
                codingWorkspaceListState = codingWorkspaceListState,
                officeSessionListState = officeSessionListState,
                codingRestoreFocusTarget = codingRestoreFocusTarget,
                officeRestoreFocusTarget = officeRestoreFocusTarget,
                restoreFocusTarget = mainRestoreFocusTarget,
                onFocusRestored = {
                    mainRestoreFocusTarget = null
                    codingRestoreFocusTarget = null
                    officeRestoreFocusTarget = null
                },
                onOpenCodingWorkspace = { workspace: CodingWorkspace ->
                    if (codingWorkspaceId != workspace.id) codingTransient.clearAll()
                    codingWorkspaceId = workspace.id
                    codingWorkspaceTitle = workspace.name
                    codingSessionCreateStatusText = null
                    codingSessionCreateInProgress = false
                    codingSessionCreateDialogVisible = false
                    codingSessionListReloadToken = 0
                    activeScreen = AppScreen.CodingSessions.name
                },
                onOfficeWorkspaceResolved = { workspace: CodingWorkspace? ->
                    if (workspace != null && officeWorkspaceId != workspace.id) {
                        officeTransient.clearAll()
                    }
                    if (workspace != null) {
                        officeWorkspaceId = workspace.id
                        officeWorkspaceTitle = workspace.name
                    }
                },
                onOpenOfficeSession = { session: CodingSession ->
                    officeSessionId = session.id
                    officeSessionTitle = session.title
                    officeSessionCreateStatusText = null
                    activeScreen = AppScreen.OfficeChat.name
                },
                onCreateOfficeSession = { showCreateOfficeSessionDialog() },
                officeSessionCreateStatusText = officeSessionCreateStatusText,
                officeSessionListReloadToken = officeSessionListReloadToken,
                onDeviceManagementClick = { activeScreen = AppScreen.DeviceManagement.name },
                onUserCenterClick = { activeScreen = AppScreen.UserCenter.name },
                onWindowsSettingsClick = {
                    settingsOriginDeviceId = null
                    activeScreen = AppScreen.WindowsSettings.name
                },
                onLocalSettingsClick = {
                    activeScreen = AppScreen.LocalSettings.name
                },
                updateState = updateState,
                onCheckUpdatesClick = updateController::checkManually,
                onShareDiagnosticsClick = {
                    appScope.launch {
                        diagnosticsShareStatus = "正在整理诊断日志…"
                        try {
                            val shareIntent = withContext(Dispatchers.IO) {
                                MobileDiagnostics.prepareShareIntent(context)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享 MyCodex 诊断日志"))
                            diagnosticsShareStatus = "已打开系统分享，请选择 QQ、微信或其他应用发送日志。"
                        } catch (error: Exception) {
                            MobileDiagnostics.warn("Diagnostics", "share_report_failed", error)
                            diagnosticsShareStatus = "无法分享诊断日志，请稍后重试。"
                        }
                    }
                },
                diagnosticsShareStatus = diagnosticsShareStatus,
                contentPadding = innerPadding,
            )
        }
    }

    if (codingSessionCreateDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!codingSessionCreateInProgress) {
                    codingSessionCreateDialogVisible = false
                    codingTransient.clearSessionCreateTitle()
                }
            },
            title = { Text("新建会话") },
            text = {
                OutlinedTextField(
                    value = codingTransient.sessionCreateTitle,
                    onValueChange = { codingTransient.sessionCreateTitle = it },
                    enabled = !codingSessionCreateInProgress,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val submittedTitle = codingTransient.sessionCreateTitle.trim()
                        if (submittedTitle.isNotBlank()) {
                            codingSessionCreateDialogVisible = false
                            createCodingSession(submittedTitle)
                        }
                    },
                    enabled = !codingSessionCreateInProgress &&
                        codingTransient.sessionCreateTitle.isNotBlank(),
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        codingSessionCreateDialogVisible = false
                        codingTransient.clearSessionCreateTitle()
                    },
                    enabled = !codingSessionCreateInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (officeSessionCreateDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!officeSessionCreateInProgress) {
                    officeSessionCreateDialogVisible = false
                    officeTransient.clearSessionCreateTitle()
                }
            },
            title = { Text("新建会话") },
            text = {
                OutlinedTextField(
                    value = officeTransient.sessionCreateTitle,
                    onValueChange = { officeTransient.sessionCreateTitle = it },
                    enabled = !officeSessionCreateInProgress,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val submittedTitle = officeTransient.sessionCreateTitle.trim()
                        if (submittedTitle.isNotBlank()) {
                            officeSessionCreateDialogVisible = false
                            createOfficeSession(submittedTitle)
                        }
                    },
                    enabled = !officeSessionCreateInProgress &&
                        officeTransient.sessionCreateTitle.isNotBlank(),
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        officeSessionCreateDialogVisible = false
                        officeTransient.clearSessionCreateTitle()
                    },
                    enabled = !officeSessionCreateInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (codingFullForkDialogVisible) {
        val titleValid = codingTransient.forkTitle.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = {
                if (!codingThreadOperationInProgress) {
                    codingFullForkDialogVisible = false
                    codingTransient.forkTitle = ""
                }
            },
            title = { Text("完整分叉") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("将从当前完整会话历史创建分叉。")
                    OutlinedTextField(
                        value = codingTransient.forkTitle,
                        onValueChange = { codingTransient.forkTitle = it },
                        enabled = !codingThreadOperationInProgress,
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { runFullFork(codingTransient.forkTitle) },
                    enabled = !codingThreadOperationInProgress && titleValid,
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        codingFullForkDialogVisible = false
                        codingTransient.forkTitle = ""
                    },
                    enabled = !codingThreadOperationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (codingCompactDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!codingThreadOperationInProgress) {
                    codingCompactDialogVisible = false
                }
            },
            title = { Text("压缩上下文") },
            text = { Text("将请求 Windows 端压缩当前会话上下文。") },
            confirmButton = {
                TextButton(
                    onClick = { runCompactContext() },
                    enabled = !codingThreadOperationInProgress,
                ) {
                    Text("压缩")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { codingCompactDialogVisible = false },
                    enabled = !codingThreadOperationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    codingThreadOperationErrorText?.let { errorText ->
        ThreadOperationErrorDialog(text = errorText, onDismiss = { codingThreadOperationErrorText = null })
    }

    UpdateToastHost(
        message = updateState.toastMessage,
        onConsumed = updateController::consumeToastMessage,
    )

    if (updateState.updatePromptVisible && updatePromptAllowed) {
        val release = updateState.manifest?.app
        if (release != null) {
            UpdatePromptDialog(
                release = release,
                onLater = updateController::deferPrompt,
                onUpdateNow = updateController::beginUpdate,
            )
        }
    }

    RemoteApprovalDialog(
        state = remoteApprovalState,
        onDecision = { decision ->
            remoteApprovalController?.respond(decision)
        },
    )

    if (remoteApprovalState.activeRequest == null) {
        RemotePlanExecutionDialog(
            state = remotePlanExecutionState,
            onAction = { action -> remotePlanExecutionController?.respond(action) },
        )
    }

    if (remoteApprovalState.activeRequest == null && remotePlanExecutionState.activeRequest == null) {
        RemoteToolInputDialog(
            state = remoteToolInputState,
            onAnswerChanged = { questionId, values -> remoteToolInputController?.updateAnswer(questionId, values) },
            onSubmit = { remoteToolInputController?.submit() },
            onCancel = { remoteToolInputController?.cancel() },
        )
    }
}

@Composable
internal fun MyCodexTopBar(
    title: String,
    dynamicTitle: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    var showFullDynamicTitle by remember(title, dynamicTitle) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_top_bar"),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (onBack != null) {
                        TopBarTextAction(
                            text = "返回",
                            testTag = "app_top_bar_back",
                            onClick = onBack,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier
                            .testTag("app_top_bar_title")
                            .semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = when {
                            !dynamicTitle -> 1
                            showFullDynamicTitle -> Int.MAX_VALUE
                            else -> 2
                        },
                        overflow = if (dynamicTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (dynamicTitle && !showFullDynamicTitle && result.hasVisualOverflow) {
                                showFullDynamicTitle = true
                            }
                        },
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    actions()
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun TopBarTextAction(
    text: String,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .testTag(testTag)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        Text(text)
    }
}

@Composable
private fun MyCodexBottomNavigation(
    selectedDestination: MainTabDestination,
    onDestinationSelected: (MainTabDestination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom_navigation"),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(vertical = 4.dp),
            ) {
                MainTabDestination.entries.forEach { destination ->
                    BottomNavigationButton(
                        destination = destination,
                        selected = destination == selectedDestination,
                        onClick = { onDestinationSelected(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomNavigationButton(
    destination: MainTabDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .navigationTabTag(destination)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f)
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                )
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(destination.iconRes),
                contentDescription = null,
                tint = contentColor,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = destination.label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            maxLines = 1,
        )
    }
}

private fun Modifier.navigationTabTag(destination: MainTabDestination): Modifier {
    return when (destination) {
        MainTabDestination.Coding -> testTag("tab_coding")
        MainTabDestination.Office -> testTag("tab_office")
        MainTabDestination.Profile -> testTag("tab_profile")
    }
}

internal enum class AppScreen {
    Main,
    DeviceManagement,
    AddDevice,
    DeviceDetail,
    UserCenter,
    CodingSessions,
    CodingChat,
    CodingSessionSettings,
    OfficeChat,
    OfficeSessionSettings,
    WindowsSettings,
    WindowsDefaultsSettings,
    WindowsCustomApprovalSettings,
    WindowsOfficialSettings,
    LocalSettings,
}

internal object AppFocusTarget {
    const val ProfileDeviceManagement = "profile_device_management"
    const val ProfileUserCenter = "profile_user_center"
    const val ProfileWindowsSettings = "profile_windows_settings"
    const val ProfileLocalSettings = "profile_local_settings"
    const val ProfileCheckUpdates = "profile_check_updates"
    const val DeviceAddButton = "device_add_button"
    const val CodingWorkspaceEmpty = "coding_workspace_empty"
    const val CodingSessionCreateButton = "coding_session_create_button"
    const val CodingWorkspacePrefix = "coding_workspace:"
    const val CodingSessionPrefix = "coding_session:"

    fun deviceRow(deviceId: String): String {
        return "device_row:$deviceId"
    }

    fun codingWorkspace(workspaceId: String): String {
        return CodingWorkspacePrefix + workspaceId
    }

    fun codingSession(sessionId: String): String {
        return CodingSessionPrefix + sessionId
    }
}

internal fun adjacentItemId(
    oldIds: List<String>,
    sourceId: String,
    newIds: List<String>,
): String? {
    if (newIds.isEmpty()) return null
    val sourceIndex = oldIds.indexOf(sourceId)
    if (sourceIndex < 0) return newIds.first()

    oldIds.drop(sourceIndex + 1).firstOrNull(newIds::contains)?.let { return it }
    oldIds.take(sourceIndex).asReversed().firstOrNull(newIds::contains)?.let { return it }
    return newIds[sourceIndex.coerceAtMost(newIds.lastIndex)]
}

@Composable
private fun MainTabContent(
    destination: MainTabDestination,
    currentDevice: ManagedDevice?,
    codingRepository: CodingRemoteRepository,
    officeRepository: CodingRemoteRepository,
    codingWorkspaceListState: LazyListState,
    officeSessionListState: LazyListState,
    codingRestoreFocusTarget: String?,
    officeRestoreFocusTarget: String?,
    restoreFocusTarget: String?,
    onFocusRestored: () -> Unit,
    onOpenCodingWorkspace: (CodingWorkspace) -> Unit,
    onOfficeWorkspaceResolved: (CodingWorkspace?) -> Unit,
    onOpenOfficeSession: (CodingSession) -> Unit,
    onCreateOfficeSession: () -> Unit,
    officeSessionCreateStatusText: String? = null,
    officeSessionListReloadToken: Int = 0,
    onDeviceManagementClick: () -> Unit,
    onUserCenterClick: () -> Unit,
    onWindowsSettingsClick: () -> Unit,
    onLocalSettingsClick: () -> Unit,
    updateState: AndroidUpdateUiState,
    onCheckUpdatesClick: () -> Unit,
    onShareDiagnosticsClick: () -> Unit,
    diagnosticsShareStatus: String?,
    contentPadding: PaddingValues,
) {
    when (destination) {
        MainTabDestination.Coding -> CodingWorkspaceListPage(
            currentDevice = currentDevice,
            repository = codingRepository,
            listState = codingWorkspaceListState,
            restoreFocusTarget = codingRestoreFocusTarget,
            onFocusRestored = onFocusRestored,
            onOpenWorkspace = onOpenCodingWorkspace,
            contentPadding = contentPadding,
        )
        MainTabDestination.Office -> OfficeSessionRootPage(
            currentDevice = currentDevice,
            repository = officeRepository,
            listState = officeSessionListState,
            restoreFocusTarget = officeRestoreFocusTarget,
            externalStatusText = officeSessionCreateStatusText,
            reloadToken = officeSessionListReloadToken,
            onWorkspaceResolved = onOfficeWorkspaceResolved,
            onFocusRestored = onFocusRestored,
            onOpenSession = onOpenOfficeSession,
            onCreateSession = onCreateOfficeSession,
            contentPadding = contentPadding,
        )
        MainTabDestination.Profile -> ProfilePage(
            currentDevice = currentDevice,
            restoreFocusTarget = restoreFocusTarget,
            onFocusRestored = onFocusRestored,
            onDeviceManagementClick = onDeviceManagementClick,
            onUserCenterClick = onUserCenterClick,
            onWindowsSettingsClick = onWindowsSettingsClick,
            onLocalSettingsClick = onLocalSettingsClick,
            updateState = updateState,
            onCheckUpdatesClick = onCheckUpdatesClick,
            onShareDiagnosticsClick = onShareDiagnosticsClick,
            diagnosticsShareStatus = diagnosticsShareStatus,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun ProfilePage(
    currentDevice: ManagedDevice?,
    restoreFocusTarget: String?,
    onFocusRestored: () -> Unit,
    onDeviceManagementClick: () -> Unit,
    onUserCenterClick: () -> Unit,
    onWindowsSettingsClick: () -> Unit,
    onLocalSettingsClick: () -> Unit,
    updateState: AndroidUpdateUiState,
    onCheckUpdatesClick: () -> Unit,
    onShareDiagnosticsClick: () -> Unit,
    diagnosticsShareStatus: String?,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            StatusPanel(
                title = currentDevice?.let { "当前设备" } ?: "尚未选择设备",
                body = currentDevice?.displayName ?: "请先进入设备管理添加或选择一台 Windows 设备。",
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_action_group"),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column {
                    ProfileActionRow(
                        title = "设备管理",
                        body = "管理已配对的 Windows 设备",
                        testTag = "profile_device_management_row",
                        restoreFocus = restoreFocusTarget == AppFocusTarget.ProfileDeviceManagement,
                        onFocusRestored = onFocusRestored,
                        onClick = onDeviceManagementClick,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProfileActionRow(
                        title = "用户中心",
                        body = if (currentDevice == null) {
                            "请先选择 Windows 设备"
                        } else {
                            "查看当前 Windows 端 ChatGPT 账号的额度"
                        },
                        testTag = "profile_user_center_row",
                        restoreFocus = restoreFocusTarget == AppFocusTarget.ProfileUserCenter,
                        onFocusRestored = onFocusRestored,
                        onClick = onUserCenterClick,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProfileActionRow(
                        title = "当前设备设置",
                        body = if (currentDevice == null) {
                            "请先选择 Windows 设备"
                        } else {
                            "${currentDevice.displayName}：默认模型、权限和 Codex 高级设置"
                        },
                        testTag = "profile_windows_settings_row",
                        restoreFocus = restoreFocusTarget == AppFocusTarget.ProfileWindowsSettings,
                        onFocusRestored = onFocusRestored,
                        onClick = onWindowsSettingsClick,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProfileActionRow(
                        title = "本机设置",
                        body = "管理手机端偏好和无障碍相关设置",
                        testTag = "profile_local_settings_row",
                        restoreFocus = restoreFocusTarget == AppFocusTarget.ProfileLocalSettings,
                        onFocusRestored = onFocusRestored,
                        onClick = onLocalSettingsClick,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProfileActionRow(
                        title = "检查更新",
                        body = "当前版本 ${updateState.installed.versionName}",
                        testTag = "profile_check_updates_row",
                        restoreFocus = restoreFocusTarget == AppFocusTarget.ProfileCheckUpdates,
                        onFocusRestored = onFocusRestored,
                        onClick = onCheckUpdatesClick,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProfileActionRow(
                        title = "分享诊断日志",
                        body = "整理最近的应用与远程连接日志，并通过系统分享发送",
                        testTag = "profile_share_logs_row",
                        onClick = onShareDiagnosticsClick,
                    )
                }
            }
        }
        diagnosticsShareStatus?.let { message ->
            item {
                Text(
                    text = message,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("profile_share_logs_status"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    body: String,
    testTag: String,
    restoreFocus: Boolean = false,
    onFocusRestored: () -> Unit = {},
    onClick: () -> Unit,
) {
    NativeAccessibleActionRow(
        title = title,
        body = body,
        testTag = testTag,
        restoreFocus = restoreFocus,
        onFocusRestored = onFocusRestored,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusPanel(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_device_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun DeviceStoreSnapshot.currentDevice(): ManagedDevice? {
    return devices.firstOrNull { it.id == currentDeviceId }
}

@Preview(showBackground = true)
@Composable
private fun MyCodexAppPreview() {
    MyCodexTheme(dynamicColor = false) {
        MyCodexApp(
            themeMode = ThemeMode.System,
            onThemeModeSelected = { true },
        )
    }
}
