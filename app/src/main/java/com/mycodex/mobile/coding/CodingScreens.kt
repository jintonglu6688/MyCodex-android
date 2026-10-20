package com.mycodex.mobile.coding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycodex.mobile.AppFocusTarget
import com.mycodex.mobile.AppSettingsGroup
import com.mycodex.mobile.AppSettingsGroupDivider
import com.mycodex.mobile.AppSettingsHeading
import com.mycodex.mobile.NativeAccessibleAction
import com.mycodex.mobile.NativeAccessibleActionRow
import com.mycodex.mobile.NativeAccessibleButton
import com.mycodex.mobile.R
import com.mycodex.mobile.adjacentItemId
import com.mycodex.mobile.devices.ManagedDevice
import com.mycodex.mobile.relay.RemoteTaskForegroundService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun CodingWorkspaceListPage(
    currentDevice: ManagedDevice?,
    repository: CodingRemoteRepository,
    listState: LazyListState,
    restoreFocusTarget: String?,
    onFocusRestored: () -> Unit,
    onOpenWorkspace: (CodingWorkspace) -> Unit,
    externalStatusText: String? = null,
    reloadToken: Int = 0,
    contentPadding: PaddingValues,
) {
    val mode = repository.mode
    var result by remember(currentDevice?.id) {
        mutableStateOf(CodingListResult<CodingWorkspace>(CodingLoadState.Unavailable))
    }
    var loading by remember(currentDevice?.id) { mutableStateOf(currentDevice != null) }
    var operationStatusText by remember(currentDevice?.id) { mutableStateOf<String?>(null) }
    var operationInProgress by remember(currentDevice?.id) { mutableStateOf(false) }
    var workspacePendingAction by remember(currentDevice?.id) { mutableStateOf<CodingWorkspace?>(null) }
    var workspacePendingDelete by remember(currentDevice?.id) { mutableStateOf<CodingWorkspace?>(null) }
    var deleteLocalHistory by remember(currentDevice?.id) { mutableStateOf(false) }
    var interruptedFocusTarget by rememberSaveable(currentDevice?.id) { mutableStateOf<String?>(null) }
    var focusRestoreGeneration by rememberSaveable(currentDevice?.id) { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(workspacePendingAction?.id, workspacePendingDelete?.id) {
        (workspacePendingAction ?: workspacePendingDelete)?.let {
            interruptedFocusTarget = AppFocusTarget.codingWorkspace(it.id)
        }
    }

    suspend fun reloadWorkspaces(): CodingListResult<CodingWorkspace> {
        if (currentDevice == null) {
            val unavailable = CodingListResult<CodingWorkspace>(CodingLoadState.Unavailable)
            result = unavailable
            loading = false
            return unavailable
        }

        loading = true
        val loaded = repository.loadWorkspaces()
        result = loaded
        loading = false
        return loaded
    }

    LaunchedEffect(currentDevice?.id, repository, reloadToken) {
        operationStatusText = null
        operationInProgress = false
        workspacePendingAction = null
        workspacePendingDelete = null
        deleteLocalHistory = false
        reloadWorkspaces()
    }

    fun runWorkspaceOperation(
        workspace: CodingWorkspace,
        archive: Boolean,
        deleteLocalHistoryFlag: Boolean = false,
    ) {
        val oldIds = result.items.map { it.id }
        scope.launch {
            operationInProgress = true
            operationStatusText = if (archive) {
                "正在归档工作区“${workspace.name}”。"
            } else {
                "正在删除工作区“${workspace.name}”。"
            }

            val operationResult = if (archive) {
                repository.archiveWorkspace(workspace.id)
            } else {
                repository.deleteWorkspace(workspace.id, deleteLocalHistoryFlag)
            }

            operationInProgress = false
            if (operationResult.state == CodingWorkspaceOperationState.Completed) {
                operationStatusText = null
                val refreshed = reloadWorkspaces()
                interruptedFocusTarget = adjacentItemId(
                    oldIds = oldIds,
                    sourceId = workspace.id,
                    newIds = refreshed.items.map { it.id },
                )?.let(AppFocusTarget::codingWorkspace) ?: AppFocusTarget.CodingWorkspaceEmpty
                focusRestoreGeneration += 1
            } else {
                val errorCode = operationResult.errorCode ?: "unknown"
                operationStatusText = if (archive) {
                    "工作区归档失败：$errorCode"
                } else {
                    "工作区删除失败：$errorCode"
                }
                interruptedFocusTarget = AppFocusTarget.codingWorkspace(workspace.id)
                focusRestoreGeneration += 1
            }
        }
    }

    val effectiveFocusTarget = restoreFocusTarget ?: interruptedFocusTarget
    LaunchedEffect(effectiveFocusTarget, result.items.size, focusRestoreGeneration) {
        if (effectiveFocusTarget?.startsWith(AppFocusTarget.CodingWorkspacePrefix) == true) {
            val workspaceId = effectiveFocusTarget.substringAfter(AppFocusTarget.CodingWorkspacePrefix)
            val workspaceIndex = result.items.indexOfFirst { it.id == workspaceId }
            if (workspaceIndex >= 0) {
                listState.scrollToItem(workspaceIndex + 2)
            }
        } else if (effectiveFocusTarget == AppFocusTarget.CodingWorkspaceEmpty) {
            listState.scrollToItem(1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            CodingInfoPanel(
                title = currentDevice?.let { "当前设备：${it.displayName}" } ?: "尚未选择设备",
                body = if (currentDevice == null) {
                    "请先在“我的 - 设备管理”中添加或选择一台 Windows 设备。"
                } else {
                    null
                },
            )
        }
        when {
            loading -> item { CodingLoadingPanel("正在加载${mode.workspaceLabel}。") }
            result.state == CodingLoadState.Error -> item {
                CodingErrorPanel("${mode.workspaceLabel}加载失败：${result.errorCode ?: "unknown"}")
            }
            externalStatusText != null -> item {
                if (externalStatusText.startsWith("正在")) {
                    CodingLoadingPanel(externalStatusText)
                } else {
                    CodingErrorPanel(externalStatusText)
                }
            }
            operationStatusText != null -> item {
                if (operationInProgress) {
                    CodingLoadingPanel(operationStatusText!!)
                } else {
                    CodingErrorPanel(operationStatusText!!)
                }
            }
            result.state == CodingLoadState.Loaded && result.items.isEmpty() -> item {
                CodingEmptyPanel(
                    text = "尚未从 Windows 端返回${mode.workspaceLabel}。",
                    restoreFocus = effectiveFocusTarget == AppFocusTarget.CodingWorkspaceEmpty,
                    restoreFocusGeneration = focusRestoreGeneration,
                    onFocusRestored = {
                        interruptedFocusTarget = null
                        onFocusRestored()
                    },
                )
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
        }

        items(result.items, key = { it.id }) { workspace ->
            val dialogsOpen = workspacePendingAction != null || workspacePendingDelete != null
            CodingWorkspaceRow(
                workspace = workspace,
                restoreFocus = !dialogsOpen && !operationInProgress &&
                    effectiveFocusTarget ==
                    AppFocusTarget.codingWorkspace(workspace.id),
                restoreFocusGeneration = focusRestoreGeneration,
                onFocusRestored = {
                    interruptedFocusTarget = null
                    onFocusRestored()
                },
                onClick = { onOpenWorkspace(workspace) },
                onLongClick = {
                    if (mode.supportsWorkspaceOperations) {
                        workspacePendingAction = workspace
                    }
                },
                enabled = !operationInProgress,
                actionsEnabled = !operationInProgress && mode.supportsWorkspaceOperations,
                onArchive = {
                    runWorkspaceOperation(workspace, archive = true)
                },
                onDelete = {
                    workspacePendingDelete = workspace
                    deleteLocalHistory = false
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }

    workspacePendingAction?.let { workspace ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    workspacePendingAction = null
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            workspacePendingAction = null
                            runWorkspaceOperation(workspace, archive = true)
                        },
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("归档")
                    }
                    TextButton(
                        onClick = {
                            workspacePendingAction = null
                            workspacePendingDelete = workspace
                            deleteLocalHistory = false
                        },
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { workspacePendingAction = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    workspacePendingDelete?.let { workspace ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    workspacePendingDelete = null
                }
            },
            title = { Text("删除工作区") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("删除“${workspace.name}”？")
                    DeleteLocalHistoryRow(
                        checked = deleteLocalHistory,
                        enabled = !operationInProgress,
                        onCheckedChange = { deleteLocalHistory = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetWorkspace = workspace
                        val shouldDeleteLocalHistory = deleteLocalHistory
                        workspacePendingDelete = null
                        runWorkspaceOperation(
                            workspace = targetWorkspace,
                            archive = false,
                            deleteLocalHistoryFlag = shouldDeleteLocalHistory,
                        )
                    },
                    enabled = !operationInProgress,
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { workspacePendingDelete = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
fun OfficeSessionRootPage(
    currentDevice: ManagedDevice?,
    repository: CodingRemoteRepository,
    listState: LazyListState,
    restoreFocusTarget: String?,
    externalStatusText: String? = null,
    reloadToken: Int = 0,
    onWorkspaceResolved: (CodingWorkspace?) -> Unit,
    onFocusRestored: () -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (CodingSession) -> Unit,
    contentPadding: PaddingValues,
) {
    var workspaces by remember(currentDevice?.id) {
        mutableStateOf(CodingListResult<CodingWorkspace>(CodingLoadState.Unavailable))
    }
    var loading by remember(currentDevice?.id) { mutableStateOf(currentDevice != null) }

    LaunchedEffect(currentDevice?.id, repository) {
        onWorkspaceResolved(null)
        if (currentDevice == null) {
            workspaces = CodingListResult(CodingLoadState.Unavailable)
            loading = false
            return@LaunchedEffect
        }

        loading = true
        workspaces = repository.loadWorkspaces()
        if (workspaces.state == CodingLoadState.Loaded) {
            onWorkspaceResolved(workspaces.items.singleOrNull())
        }
        loading = false
    }

    val workspace = workspaces.items.singleOrNull()
        ?.takeIf { workspaces.state == CodingLoadState.Loaded }
    if (workspace != null) {
        CodingSessionListPage(
            workspaceId = workspace.id,
            workspaceTitle = workspace.name,
            repository = repository,
            listState = listState,
            restoreFocusTarget = restoreFocusTarget,
            externalStatusText = externalStatusText,
            reloadToken = reloadToken,
            onFocusRestored = onFocusRestored,
            onCreateSession = onCreateSession,
            onOpenSession = onOpenSession,
            contentPadding = contentPadding,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        CodingInfoPanel(
            title = currentDevice?.let { "当前设备：${it.displayName}" } ?: "尚未选择设备",
            body = if (currentDevice == null) {
                "请先在“我的 - 设备管理”中添加或选择一台 Windows 设备。"
            } else {
                null
            },
        )
        Spacer(Modifier.height(8.dp))
        when {
            loading -> CodingLoadingPanel("正在加载办公会话。")
            workspaces.state == CodingLoadState.Error ->
                CodingErrorPanel("办公工作区加载失败：${workspaces.errorCode ?: "unknown"}")
            workspaces.state == CodingLoadState.Loaded && workspaces.items.isEmpty() ->
                CodingEmptyPanel("Windows 端未返回办公工作区。")
            workspaces.state == CodingLoadState.Loaded ->
                CodingErrorPanel("Windows 端返回了多个办公工作区，请升级 Windows 版。")
            else -> CodingEmptyPanel("办公会话暂不可用。")
        }
    }
}

@Composable
fun CodingSessionListPage(
    workspaceId: String,
    workspaceTitle: String,
    repository: CodingRemoteRepository,
    listState: LazyListState,
    restoreFocusTarget: String?,
    externalStatusText: String? = null,
    reloadToken: Int = 0,
    onFocusRestored: () -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (CodingSession) -> Unit,
    contentPadding: PaddingValues,
) {
    val mode = repository.mode
    var result by remember(workspaceId) {
        mutableStateOf(CodingListResult<CodingSession>(CodingLoadState.Unavailable))
    }
    var loading by remember(workspaceId) { mutableStateOf(true) }
    var operationStatusText by remember(workspaceId) { mutableStateOf<String?>(null) }
    var operationInProgress by remember(workspaceId) { mutableStateOf(false) }
    var sessionPendingAction by remember(workspaceId) { mutableStateOf<CodingSession?>(null) }
    var sessionPendingDelete by remember(workspaceId) { mutableStateOf<CodingSession?>(null) }
    var sessionPendingRename by remember(workspaceId) { mutableStateOf<CodingSession?>(null) }
    var sessionPendingSubAgents by remember(workspaceId) { mutableStateOf<CodingSession?>(null) }
    var subAgentPendingAction by remember(workspaceId) { mutableStateOf<CodingSession?>(null) }
    var subAgentPendingDelete by remember(workspaceId) { mutableStateOf<CodingSession?>(null) }
    var subAgentsPendingDeleteAll by remember(workspaceId) { mutableStateOf<CodingSession?>(null) }
    var deleteLocalHistory by remember(workspaceId) { mutableStateOf(false) }
    var renameTitleText by remember(workspaceId) { mutableStateOf("") }
    var interruptedFocusTarget by rememberSaveable(workspaceId) { mutableStateOf<String?>(null) }
    var focusRestoreGeneration by rememberSaveable(workspaceId) { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(
        sessionPendingAction?.id,
        sessionPendingDelete?.id,
        sessionPendingRename?.id,
        sessionPendingSubAgents?.id,
        subAgentPendingAction?.id,
        subAgentPendingDelete?.id,
        subAgentsPendingDeleteAll?.id,
    ) {
        val source = sessionPendingAction ?: sessionPendingDelete ?: sessionPendingRename ?:
            sessionPendingSubAgents ?: subAgentPendingAction ?: subAgentPendingDelete ?:
            subAgentsPendingDeleteAll
        if (interruptedFocusTarget == null) {
            source?.let { interruptedFocusTarget = AppFocusTarget.codingSession(it.id) }
        }
    }

    suspend fun reloadSessions(): CodingListResult<CodingSession> {
        loading = true
        val loaded = repository.loadSessions(workspaceId)
        result = loaded
        loading = false
        return loaded
    }

    LaunchedEffect(workspaceId, repository, reloadToken) {
        if (reloadToken == 0) {
            operationStatusText = null
            operationInProgress = false
            sessionPendingAction = null
            sessionPendingDelete = null
            sessionPendingRename = null
            sessionPendingSubAgents = null
            subAgentPendingAction = null
            subAgentPendingDelete = null
            subAgentsPendingDeleteAll = null
            deleteLocalHistory = false
            renameTitleText = ""
        }
        reloadSessions()
    }

    fun runSessionOperation(
        session: CodingSession,
        archive: Boolean,
        deleteLocalHistoryFlag: Boolean = false,
    ) {
        val oldIds = result.items.map { it.id }
        scope.launch {
            operationInProgress = true
            operationStatusText = if (archive) {
                "正在归档会话“${session.title}”。"
            } else {
                "正在删除会话“${session.title}”。"
            }

            val operationResult = if (archive) {
                repository.archiveSession(session.id)
            } else {
                repository.deleteSession(session.id, deleteLocalHistoryFlag)
            }

            operationInProgress = false
            if (operationResult.state == CodingSessionOperationState.Completed) {
                operationStatusText = null
                val refreshed = reloadSessions()
                interruptedFocusTarget = adjacentItemId(
                    oldIds = oldIds,
                    sourceId = session.id,
                    newIds = refreshed.items.map { it.id },
                )?.let(AppFocusTarget::codingSession) ?: AppFocusTarget.CodingSessionCreateButton
                focusRestoreGeneration += 1
            } else {
                val errorCode = operationResult.errorCode ?: "unknown"
                operationStatusText = if (archive) {
                    "会话归档失败：$errorCode"
                } else {
                    "会话删除失败：$errorCode"
                }
                interruptedFocusTarget = AppFocusTarget.codingSession(session.id)
                focusRestoreGeneration += 1
            }
        }
    }

    fun showRenameDialog(session: CodingSession) {
        sessionPendingRename = session
        renameTitleText = session.title
    }

    fun runSessionRename(session: CodingSession, titleText: String) {
        val submittedTitle = titleText.trim()
        if (submittedTitle.isBlank()) {
            return
        }

        if (submittedTitle == session.title.trim()) {
            sessionPendingRename = null
            return
        }

        sessionPendingRename = null
        scope.launch {
            operationInProgress = true
            operationStatusText = "正在重命名会话“${session.title}”。"
            val operationResult = repository.renameSession(session.id, submittedTitle)
            operationInProgress = false
            if (operationResult.state == CodingSessionOperationState.Completed) {
                operationStatusText = null
                renameTitleText = ""
                reloadSessions()
                interruptedFocusTarget = AppFocusTarget.codingSession(session.id)
                focusRestoreGeneration += 1
            } else {
                val errorCode = operationResult.errorCode ?: "unknown"
                operationStatusText = "会话重命名失败：$errorCode"
                interruptedFocusTarget = AppFocusTarget.codingSession(session.id)
                focusRestoreGeneration += 1
            }
        }
    }

    fun runSubAgentDelete(subAgent: CodingSession) {
        scope.launch {
            operationInProgress = true
            operationStatusText = "正在删除子代理“${subAgent.title}”。"
            val operationResult = repository.deleteSession(subAgent.id, deleteLocalHistory = false)
            operationInProgress = false
            if (operationResult.state == CodingSessionOperationState.Completed) {
                operationStatusText = null
                reloadSessions()
                focusRestoreGeneration += 1
            } else {
                operationStatusText = "子代理删除失败：${operationResult.errorCode ?: "unknown"}"
                focusRestoreGeneration += 1
            }
        }
    }

    fun runDeleteAllSubAgents(session: CodingSession) {
        val targets = session.subAgents.filter { it.id.isNotBlank() }
        if (targets.isEmpty()) {
            operationStatusText = "没有可删除的子代理。"
            return
        }

        scope.launch {
            operationInProgress = true
            operationStatusText = "正在删除 ${targets.size} 个子代理。"
            var deletedCount = 0
            var firstError: String? = null
            for (subAgent in targets) {
                val operationResult = repository.deleteSession(subAgent.id, deleteLocalHistory = false)
                if (operationResult.state == CodingSessionOperationState.Completed) {
                    deletedCount += 1
                } else if (firstError == null) {
                    firstError = operationResult.errorCode ?: "unknown"
                }
            }
            operationInProgress = false
            operationStatusText = firstError?.let { errorCode ->
                "已删除 $deletedCount 个子代理，失败：$errorCode"
            }
            reloadSessions()
            interruptedFocusTarget = AppFocusTarget.codingSession(session.id)
            focusRestoreGeneration += 1
        }
    }

    val effectiveFocusTarget = restoreFocusTarget ?: interruptedFocusTarget
    LaunchedEffect(effectiveFocusTarget, result.items.size, focusRestoreGeneration) {
        if (effectiveFocusTarget?.startsWith(AppFocusTarget.CodingSessionPrefix) == true) {
            val sessionId = effectiveFocusTarget.substringAfter(AppFocusTarget.CodingSessionPrefix)
            val sessionIndex = result.items.indexOfFirst { it.id == sessionId }
            if (sessionIndex >= 0) {
                listState.scrollToItem(sessionIndex + 2)
            }
        } else if (effectiveFocusTarget == AppFocusTarget.CodingSessionCreateButton) {
            listState.scrollToItem(3)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            CodingInfoPanel(
                title = workspaceTitle,
                body = "${mode.workspaceLabel}中的${mode.sessionLabel}",
            )
        }
        when {
            loading -> item { CodingLoadingPanel("正在加载会话。") }
            result.state == CodingLoadState.Error -> item {
                CodingErrorPanel("会话加载失败：${result.errorCode ?: "unknown"}")
            }
            externalStatusText != null -> item {
                if (externalStatusText.startsWith("正在")) {
                    CodingLoadingPanel(externalStatusText)
                } else {
                    CodingErrorPanel(externalStatusText)
                }
            }
            operationStatusText != null -> item {
                if (operationInProgress) {
                    CodingLoadingPanel(operationStatusText!!)
                } else {
                    CodingErrorPanel(operationStatusText!!)
                }
            }
            result.state == CodingLoadState.Loaded && result.items.isEmpty() -> item {
                CodingEmptyPanel(
                    text = if (mode == CodingRemoteMode.Office) {
                        "当前还没有办公会话。"
                    } else {
                        "这个${mode.workspaceLabel}还没有返回${mode.sessionLabel}。"
                    },
                )
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
        }

        items(result.items, key = { it.id }) { session ->
            val dialogsOpen = sessionPendingAction != null ||
                sessionPendingDelete != null ||
                sessionPendingRename != null ||
                sessionPendingSubAgents != null ||
                subAgentPendingAction != null ||
                subAgentPendingDelete != null ||
                subAgentsPendingDeleteAll != null
            CodingSessionRow(
                session = session,
                restoreFocus = !dialogsOpen && !operationInProgress &&
                    effectiveFocusTarget ==
                    AppFocusTarget.codingSession(session.id),
                restoreFocusGeneration = focusRestoreGeneration,
                onFocusRestored = {
                    interruptedFocusTarget = null
                    onFocusRestored()
                },
                onClick = { onOpenSession(session) },
                onLongClick = { sessionPendingAction = session },
                enabled = !operationInProgress,
                actionsEnabled = !operationInProgress,
                onRename = {
                    showRenameDialog(session)
                },
                onArchive = {
                    runSessionOperation(session, archive = true)
                },
                onDelete = {
                    sessionPendingDelete = session
                    deleteLocalHistory = false
                },
                onOpenSubAgents = {
                    if (mode.supportsSubAgents) {
                        sessionPendingSubAgents = session
                    }
                },
                supportsSubAgents = mode.supportsSubAgents,
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (result.items.isEmpty() && !loading && result.state != CodingLoadState.Error) {
            item {
                NativeAccessibleButton(
                    text = "新建会话",
                    testTag = "coding_session_empty_create",
                    restoreFocus = effectiveFocusTarget == AppFocusTarget.CodingSessionCreateButton,
                    restoreFocusGeneration = focusRestoreGeneration,
                    onFocusRestored = {
                        interruptedFocusTarget = null
                        onFocusRestored()
                    },
                    onClick = onCreateSession,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    sessionPendingAction?.let { session ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    sessionPendingAction = null
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            sessionPendingAction = null
                            showRenameDialog(session)
                        },
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重命名")
                    }
                    if (mode.supportsSubAgents && session.subAgentCount > 0) {
                        TextButton(
                            onClick = {
                                sessionPendingAction = null
                                sessionPendingSubAgents = session
                            },
                            enabled = !operationInProgress,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("子代理")
                        }
                    }
                    TextButton(
                        onClick = {
                            sessionPendingAction = null
                            runSessionOperation(session, archive = true)
                        },
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("归档")
                    }
                    TextButton(
                        onClick = {
                            sessionPendingAction = null
                            sessionPendingDelete = session
                            deleteLocalHistory = false
                        },
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { sessionPendingAction = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    sessionPendingRename?.let { session ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    sessionPendingRename = null
                }
            },
            title = { Text("编辑标题") },
            text = {
                OutlinedTextField(
                    value = renameTitleText,
                    onValueChange = { renameTitleText = it },
                    enabled = !operationInProgress,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetSession = session
                        val submittedTitle = renameTitleText.trim()
                        if (submittedTitle.isNotBlank()) {
                            runSessionRename(targetSession, submittedTitle)
                        }
                    },
                    enabled = !operationInProgress && renameTitleText.isNotBlank(),
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionPendingRename = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    sessionPendingSubAgents?.let { session ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    sessionPendingSubAgents = null
                }
            },
            title = { Text("子代理") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (session.subAgents.isEmpty()) {
                        item { Text("子代理列表未返回。") }
                    } else {
                        items(session.subAgents, key = { it.id }) { subAgent ->
                            NativeAccessibleActionRow(
                                title = subAgent.subAgentDisplayText(),
                                body = "",
                                testTag = "coding_subagent_${subAgent.id}",
                                restoreFocus = false,
                                onFocusRestored = {},
                                onClick = {
                                    sessionPendingSubAgents = null
                                    onOpenSession(subAgent)
                                },
                                onLongClick = {
                                    sessionPendingSubAgents = null
                                    subAgentPendingAction = subAgent
                                },
                                accessibilityActions = if (operationInProgress) {
                                    emptyList()
                                } else {
                                    listOf(
                                        NativeAccessibleAction(
                                            id = R.id.accessibility_action_delete_session,
                                            label = "删除",
                                            onAction = {
                                                sessionPendingSubAgents = null
                                                subAgentPendingDelete = subAgent
                                                true
                                            },
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentDescription = subAgent.subAgentDisplayText(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { sessionPendingSubAgents = null },
                    enabled = !operationInProgress,
                ) {
                    Text("关闭")
                }
            },
            dismissButton = {
                if (session.subAgents.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            sessionPendingSubAgents = null
                            subAgentsPendingDeleteAll = session
                        },
                        enabled = !operationInProgress,
                    ) {
                        Text("全部删除")
                    }
                }
            },
        )
    }

    subAgentPendingAction?.let { subAgent ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    subAgentPendingAction = null
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            subAgentPendingAction = null
                            subAgentPendingDelete = subAgent
                        },
                        enabled = !operationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { subAgentPendingAction = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    subAgentPendingDelete?.let { subAgent ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    subAgentPendingDelete = null
                }
            },
            title = { Text("删除子代理") },
            text = { Text("删除“${subAgent.title}”？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetSubAgent = subAgent
                        subAgentPendingDelete = null
                        runSubAgentDelete(targetSubAgent)
                    },
                    enabled = !operationInProgress,
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { subAgentPendingDelete = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    subAgentsPendingDeleteAll?.let { session ->
        val count = session.subAgents.count { it.id.isNotBlank() }
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    subAgentsPendingDeleteAll = null
                }
            },
            title = { Text("全部删除子代理") },
            text = { Text("删除“${session.title}”的 $count 个子代理？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetSession = session
                        subAgentsPendingDeleteAll = null
                        runDeleteAllSubAgents(targetSession)
                    },
                    enabled = !operationInProgress && count > 0,
                ) {
                    Text("全部删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { subAgentsPendingDeleteAll = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    sessionPendingDelete = null
                }
            },
            title = { Text("删除会话") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("删除“${session.title}”？")
                    DeleteLocalHistoryRow(
                        checked = deleteLocalHistory,
                        enabled = !operationInProgress,
                        onCheckedChange = { deleteLocalHistory = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetSession = session
                        val shouldDeleteLocalHistory = deleteLocalHistory
                        sessionPendingDelete = null
                        runSessionOperation(
                            session = targetSession,
                            archive = false,
                            deleteLocalHistoryFlag = shouldDeleteLocalHistory,
                        )
                    },
                    enabled = !operationInProgress,
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionPendingDelete = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
fun CodingMessageListPage(
    sessionId: String,
    deviceId: String?,
    sessionTitle: String,
    workspaceId: String? = null,
    repository: CodingRemoteRepository,
    anchorStore: CodingMessageAnchorStore,
    contentPadding: PaddingValues,
    onMessagesChanged: (List<CodingMessage>) -> Unit = {},
    onSessionsChanged: () -> Unit = {},
    onOpenCreatedSession: (CodingSession) -> Unit = {},
    onMessageSent: () -> Unit = {},
    foregroundRefreshToken: Int = 0,
    retainedDraft: CodingDraftViewModel? = null,
) {
    val mode = repository.mode
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val draft = retainedDraft ?: viewModel(key = "coding_draft:${repository.mode.name}") {
        CodingDraftViewModel()
    }
    draft.bindConversation("${deviceId.orEmpty()}:${repository.mode.name}:$workspaceId:$sessionId")
    var attachmentSheetVisible by remember(sessionId) { mutableStateOf(false) }
    var didInitialScroll by remember(sessionId) { mutableStateOf(false) }
    var hasNewMessagesAwayFromBottom by remember(sessionId) { mutableStateOf(false) }
    var consumedForegroundRefreshToken by remember(sessionId) { mutableStateOf(foregroundRefreshToken) }
    var consumedScrollToLatestGeneration by remember(sessionId) { mutableStateOf(0L) }
    var foregroundTaskActive by remember(sessionId) { mutableStateOf(false) }
    var operationInProgress by remember(sessionId) { mutableStateOf(false) }
    var messagePendingAction by remember(sessionId) { mutableStateOf<CodingMessage?>(null) }
    var forkMessage by remember(sessionId) { mutableStateOf<CodingMessage?>(null) }
    var forkTitleText by remember(sessionId) { mutableStateOf("") }
    var operationErrorText by remember(sessionId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selectionGeneration = remember(repository) {
        (context.applicationContext as com.mycodex.mobile.MyCodexApplication).deviceSelection.state.value.generation
    }
    val anchorScope = "${deviceId.orEmpty()}:${mode.name}"
    val restoredAnchor = remember(anchorScope, sessionId) { anchorStore.load(anchorScope, sessionId) }
    val controller = remember(sessionId, repository, restoredAnchor) {
        CodingConversationController(sessionId, repository, scope, restoredAnchor)
    }
    val state by controller.state.collectAsState()
    var completeMessage by remember(sessionId) { mutableStateOf<CodingMessage?>(null) }
    completeMessage?.let { message ->
        CodingCompleteMessagePage(
            sessionId = sessionId,
            message = message,
            repository = repository,
            contentPadding = contentPadding,
            onBack = { completeMessage = null },
        )
        return
    }
    val composerState = CodingComposerState(
        text = draft.text,
        attachmentCount = draft.attachments.size,
        sending = state.sending,
        generating = state.generating,
        stopping = state.stopping,
    )

    DisposableEffect(controller) {
        controller.start()
        onDispose {
            controller.stop()
        }
    }

    LaunchedEffect(state.generating) {
        if (state.generating && !foregroundTaskActive) {
            RemoteTaskForegroundService.start(
                context,
                sessionId,
                mode,
                state.latestAssistantMessageId,
                deviceId,
                selectionGeneration,
            )
            foregroundTaskActive = true
        } else if (!state.generating && foregroundTaskActive) {
            RemoteTaskForegroundService.stop(context, sessionId, mode, deviceId, selectionGeneration)
            foregroundTaskActive = false
        }
    }

    LaunchedEffect(foregroundRefreshToken) {
        if (foregroundRefreshToken != consumedForegroundRefreshToken) {
            consumedForegroundRefreshToken = foregroundRefreshToken
            controller.reload()
        }
    }

    LaunchedEffect(state.messages) {
        onMessagesChanged(state.messages)
    }

    LaunchedEffect(state.scrollToLatestGeneration) {
        if (state.scrollToLatestGeneration > consumedScrollToLatestGeneration && state.messages.isNotEmpty()) {
            val latestIndex = CodingMessagePagingViewportRestore.resolveLazyListIndex(
                messages = state.messages,
                pagingSupported = state.pagingSupported,
                hasOlder = state.hasOlder,
                messageId = state.messages.last().id,
            ) ?: return@LaunchedEffect
            listState.animateScrollToItem(latestIndex)
            consumedScrollToLatestGeneration = state.scrollToLatestGeneration
            hasNewMessagesAwayFromBottom = false
            controller.updateReaderAtBottom(true)
        }
    }

    LaunchedEffect(state.pendingPagingViewportRestore) {
        val restore = state.pendingPagingViewportRestore ?: return@LaunchedEffect
        val targetIndex = CodingMessagePagingViewportRestore.resolveLazyListIndex(
            messages = state.messages,
            pagingSupported = state.pagingSupported,
            hasOlder = state.hasOlder,
            messageId = restore.messageId,
        )
        if (targetIndex != null) {
            listState.scrollToItem(targetIndex, restore.scrollOffset)
            withFrameNanos { }
        }
        controller.onPagingViewportRestored(restore, targetIndex != null)
    }

    fun copyMessage(message: CodingMessage) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("MyCodex message", message.content))
    }

    fun insertComposerAttachment(attachment: CodingInputAttachment) {
        if (draft.attachments.any { it.path.equals(attachment.path, ignoreCase = true) }) {
            attachmentSheetVisible = false
            return
        }

        draft.attachments = draft.attachments + attachment
        attachmentSheetVisible = false
    }

    fun removeComposerAttachment(attachment: CodingInputAttachment) {
        draft.attachments = draft.attachments.filterNot { it.path.equals(attachment.path, ignoreCase = true) }
    }

    fun startForkFromTurn(message: CodingMessage) {
        forkMessage = message
        forkTitleText = "${sessionTitle} 分叉"
    }

    fun runForkFromTurn(message: CodingMessage, title: String) {
        val turnId = message.turnId
        val submittedTitle = title.trim()
        if (turnId.isNullOrBlank() || submittedTitle.isBlank()) {
            return
        }

        forkMessage = null
        scope.launch {
            operationInProgress = true
            val result = repository.forkSession(
                sessionId = sessionId,
                title = submittedTitle,
                turnId = turnId,
                switchToCreatedSession = true,
            )
            operationInProgress = false
            if (result.state != CodingSessionOperationState.Completed) {
                operationErrorText = threadOperationFailureText("分叉", result.errorCode ?: result.state.name.lowercase())
                return@launch
            }

            onSessionsChanged()
            if (result.switchToCreatedSession && result.createdSession != null) {
                onOpenCreatedSession(result.createdSession)
            }
        }
    }

    fun saveCurrentAnchor() {
        if (state.loadState != CodingLoadState.Loaded || state.messages.isEmpty()) {
            return
        }

        val visibleMessageItem = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> state.messages.any { it.id == item.key } }
            ?: return
        val message = state.messages.firstOrNull { it.id == visibleMessageItem.key } ?: return
        val lastVisibleMessage = listState.layoutInfo.visibleItemsInfo.lastOrNull { item -> state.messages.any { it.id == item.key } }
        val lastVisibleMessageIndex = state.messages.indexOfFirst { it.id == lastVisibleMessage?.key }.coerceAtLeast(0)
        val wasAtBottom = state.tailConnected && isNearMessageListBottom(lastVisibleMessageIndex, state.messages.size)
        controller.updateReadingAnchor(message.id)
        controller.updateReaderAtBottom(wasAtBottom)
        anchorStore.save(anchorScope,
            CodingMessageAnchor(
                sessionId = sessionId,
                messageId = message.id,
                scrollOffset = visibleMessageItem.offset,
                wasAtBottom = wasAtBottom,
                messageCount = state.messages.size,
                savedAtEpochMillis = System.currentTimeMillis(),
            )
        )
    }

    fun scrollToMessageListBottom() {
        if (state.messages.isEmpty()) {
            return
        }

        scope.launch {
            controller.goToLatest()
        }
    }

    val showScrollToBottomButton by remember(listState) {
        derivedStateOf {
            shouldShowMessageListBottomButton(
                lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                totalItems = listState.layoutInfo.totalItemsCount,
            )
        }
    }

    LaunchedEffect(sessionId, state.loadState, state.messages.size) {
        if (!didInitialScroll && state.loadState == CodingLoadState.Loaded && state.messages.isNotEmpty()) {
            val anchor = restoredAnchor
            val restoreTarget = state.messages.restoreIndexForAnchor(anchor)
            if (restoreTarget != null) {
                val targetIndex = CodingMessagePagingViewportRestore.resolveLazyListIndex(
                    messages = state.messages,
                    pagingSupported = state.pagingSupported,
                    hasOlder = state.hasOlder,
                    messageId = state.messages[restoreTarget.messageIndex].id,
                ) ?: return@LaunchedEffect
                listState.scrollToItem(targetIndex, restoreTarget.scrollOffset)
            } else {
                val latestIndex = CodingMessagePagingViewportRestore.resolveLazyListIndex(
                    messages = state.messages,
                    pagingSupported = state.pagingSupported,
                    hasOlder = state.hasOlder,
                    messageId = state.messages.last().id,
                ) ?: return@LaunchedEffect
                listState.scrollToItem(latestIndex)
            }
            didInitialScroll = true
            hasNewMessagesAwayFromBottom = false
            return@LaunchedEffect
        }
    }

    DisposableEffect(controller) {
        onDispose {
            saveCurrentAnchor()
        }
    }

    LaunchedEffect(sessionId, didInitialScroll) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (!inProgress && didInitialScroll) {
                    saveCurrentAnchor()
                }
            }
    }

    LaunchedEffect(sessionId, didInitialScroll, state.messages, state.tailConnected) {
        if (!didInitialScroll) {
            return@LaunchedEffect
        }

        snapshotFlow {
            val lastVisibleMessage = listState.layoutInfo.visibleItemsInfo.lastOrNull { item ->
                state.messages.any { it.id == item.key }
            }
            val lastVisibleMessageIndex = state.messages.indexOfFirst { it.id == lastVisibleMessage?.key }.coerceAtLeast(0)
            state.tailConnected && isNearMessageListBottom(lastVisibleMessageIndex, state.messages.size)
        }
            .distinctUntilChanged()
            .collect(controller::updateReaderAtBottom)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        CodingMessageList(
            state = state,
            listState = listState,
            actionsEnabled = !operationInProgress,
            supportsThreadOperations = mode.supportsThreadOperations,
            onCopyMessage = { copyMessage(it) },
            onMessageMenu = { messagePendingAction = it },
            onForkFromTurn = { startForkFromTurn(it) },
            onReadFullMessage = { completeMessage = it },
            onPagingFocusRestored = controller::onPagingFocusRestored,
            onLoadOlder = controller::loadOlder,
            onLoadNewer = controller::loadNewer,
            modifier = Modifier.weight(1f),
        )
        if (showScrollToBottomButton) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { scrollToMessageListBottom() },
                    modifier = Modifier.semantics {
                        contentDescription = "转到底部"
                    },
                ) {
                    Text(
                        text = "↓",
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
        if (state.needsRetryLatest) {
            TextButton(onClick = controller::retryGoToLatest, modifier = Modifier.fillMaxWidth()) { Text("重试转到底部") }
        }
        (state.composerStatusText ?: if (draft.uncertainSend) "发送结果待确认，请查看此设备的最新历史后再决定是否发送。" else null)?.let { statusText ->
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        CodingComposer(
            state = composerState,
            attachments = draft.attachments,
            onTextChange = {
                draft.text = it
            },
            onSend = { submittedText, attachments ->
                draft.uncertainSend = true
                controller.send(submittedText, attachments) {
                    draft.clearAccepted(submittedText, attachments)
                    onMessageSent()
                }
            },
            onStop = {
                controller.stopGeneration()
            },
            onAttachmentClick = {
                attachmentSheetVisible = true
            },
            onRemoveAttachment = { removeComposerAttachment(it) },
        )
    }

    if (attachmentSheetVisible) {
        CodingAttachmentSheet(
            sessionId = sessionId,
            repository = repository,
            insertedAttachments = draft.attachments,
            onDismiss = { attachmentSheetVisible = false },
            onInsertAttachment = { insertComposerAttachment(it) },
        )
    }

    messagePendingAction?.let { message ->
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    messagePendingAction = null
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (message.actions.copy) {
                        TextButton(
                            onClick = {
                                messagePendingAction = null
                                copyMessage(message)
                            },
                            enabled = !operationInProgress,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("复制")
                        }
                    }
                    if (mode.supportsThreadOperations && message.actions.forkFromTurn && !message.turnId.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                messagePendingAction = null
                                startForkFromTurn(message)
                            },
                            enabled = !operationInProgress,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("从本轮分叉")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { messagePendingAction = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    forkMessage?.let { message ->
        val titleValid = forkTitleText.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = {
                if (!operationInProgress) {
                    forkMessage = null
                }
            },
            title = { Text("从本轮分叉") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(turnTrimSummaryText(message.turnIndex, message.operationPreview.trimTurnsAfterTurn, "分叉"))
                    OutlinedTextField(
                        value = forkTitleText,
                        onValueChange = { forkTitleText = it },
                        label = { Text("标题") },
                        enabled = !operationInProgress,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { runForkFromTurn(message, forkTitleText) },
                    enabled = !operationInProgress && titleValid,
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { forkMessage = null },
                    enabled = !operationInProgress,
                ) {
                    Text("取消")
                }
            },
        )
    }

    operationErrorText?.let { errorText ->
        ThreadOperationErrorDialog(text = errorText, onDismiss = { operationErrorText = null })
    }
}

@Composable
internal fun ThreadOperationErrorDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("操作失败") },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        },
    )
}

@Composable
fun CodingSessionSettingsPage(
    sessionId: String,
    sessionTitle: String,
    repository: CodingRemoteRepository,
    onSaved: () -> Unit,
    contentPadding: PaddingValues,
) {
    var result by remember(sessionId) {
        mutableStateOf(CodingSessionSettingsResult(CodingSessionSettingsState.Unavailable))
    }
    var loading by remember(sessionId) { mutableStateOf(false) }
    var saving by remember(sessionId) { mutableStateOf(false) }
    var statusText by remember(sessionId) { mutableStateOf<String?>(null) }
    var selectedModelId by remember(sessionId) { mutableStateOf<String?>(null) }
    var selectedReasoningEffort by remember(sessionId) { mutableStateOf<String?>(null) }
    var selectedApprovalMode by remember(sessionId) { mutableStateOf<CodingApprovalMode?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reloadSettings() {
        loading = true
        result = repository.loadSessionSettings(sessionId)
        result.settings?.let { settings ->
            selectedModelId = settings.modelId
            selectedReasoningEffort = settings.reasoningEffort
            selectedApprovalMode = settings.approvalMode
        }
        loading = false
    }

    fun saveSettings() {
        scope.launch {
            saving = true
            statusText = "正在保存会话设置。"
            val updateResult = repository.updateSessionSettings(
                CodingSessionSettingsUpdate(
                    sessionId = sessionId,
                    modelId = selectedModelId,
                    reasoningEffort = selectedReasoningEffort,
                    approvalMode = selectedApprovalMode,
                ),
            )
            saving = false
            if (updateResult.state == CodingSessionSettingsUpdateState.Completed) {
                statusText = null
                onSaved()
            } else {
                statusText = "会话设置保存失败：${updateResult.errorCode ?: "unknown"}"
            }
        }
    }

    LaunchedEffect(sessionId, repository) {
        statusText = null
        reloadSettings()
    }

    val settings = result.settings
    val selectedModel = settings?.availableModels?.firstOrNull { it.modelId == selectedModelId }
    val reasoningOptions = selectedModel?.reasoningEfforts.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CodingInfoPanel(
                title = sessionTitle,
                body = "模型、推理强度和权限仅影响当前会话。",
            )
        }
        when {
            loading -> item { CodingLoadingPanel("正在加载会话设置。") }
            result.state == CodingSessionSettingsState.Error -> item {
                CodingErrorPanel("会话设置加载失败：${result.errorCode ?: "unknown"}")
            }
            statusText != null -> item {
                if (saving) CodingLoadingPanel(statusText!!) else CodingErrorPanel(statusText!!)
            }
            settings == null -> item { CodingEmptyPanel("会话设置尚未返回。") }
        }

        if (settings != null) {
            item {
                AppSettingsHeading("模型")
            }
            item {
                AppSettingsGroup {
                    SettingsChoiceRow(
                        title = "跟随默认",
                        body = "当前：${settings.defaultModelId ?: "未返回"}",
                        selected = selectedModelId == null,
                        enabled = !saving,
                        onClick = {
                            selectedModelId = null
                            selectedReasoningEffort = null
                        },
                    )
                    settings.availableModels.forEach { model ->
                        AppSettingsGroupDivider()
                        SettingsChoiceRow(
                            title = model.displayName,
                            body = "",
                            selected = selectedModelId == model.modelId,
                            enabled = !saving,
                            onClick = {
                                selectedModelId = model.modelId
                                selectedReasoningEffort = selectedReasoningEffort
                                    ?.takeIf { model.reasoningEfforts.contains(it) }
                                    ?: model.defaultReasoningEffort
                                        ?.takeIf { model.reasoningEfforts.contains(it) }
                                    ?: model.reasoningEfforts.firstOrNull()
                            },
                        )
                    }
                }
            }

            item {
                AppSettingsHeading("推理强度")
            }
            if (selectedModelId == null) {
                item {
                    AppSettingsGroup {
                        SettingsChoiceRow(
                            title = "跟随默认",
                            body = "当前：${reasoningEffortDisplayName(settings.defaultReasoningEffort)}",
                            selected = true,
                            enabled = !saving,
                            onClick = { selectedReasoningEffort = null },
                        )
                    }
                }
            } else if (reasoningOptions.isEmpty()) {
                item {
                    CodingInfoPanel(
                        title = "推理强度",
                        body = "当前模型不支持单独设置推理强度。",
                    )
                }
            } else {
                item {
                    AppSettingsGroup {
                        SettingsChoiceRow(
                            title = "跟随模型默认",
                            body = "当前：${reasoningEffortDisplayName(selectedModel?.defaultReasoningEffort)}",
                            selected = selectedReasoningEffort == null,
                            enabled = !saving,
                            onClick = { selectedReasoningEffort = null },
                        )
                        reasoningOptions.forEach { effort ->
                            AppSettingsGroupDivider()
                            SettingsChoiceRow(
                                title = reasoningEffortDisplayName(effort),
                                body = "",
                                selected = selectedReasoningEffort == effort,
                                enabled = !saving,
                                onClick = { selectedReasoningEffort = effort },
                            )
                        }
                    }
                }
            }

            item {
                AppSettingsHeading("权限")
            }
            item {
                AppSettingsGroup {
                    SettingsChoiceRow(
                        title = "跟随默认",
                        body = "当前：${settings.defaultApprovalMode?.displayName() ?: "未返回"}",
                        selected = selectedApprovalMode == null,
                        enabled = !saving,
                        onClick = { selectedApprovalMode = null },
                    )
                    CodingApprovalMode.entries.forEach { mode ->
                        AppSettingsGroupDivider()
                        SettingsChoiceRow(
                            title = mode.displayName(),
                            body = "",
                            selected = selectedApprovalMode == mode,
                            enabled = !saving,
                            onClick = { selectedApprovalMode = mode },
                        )
                    }
                }
            }

            item {
                NativeAccessibleButton(
                    text = "保存",
                    testTag = "coding_session_settings_save",
                    restoreFocus = false,
                    onFocusRestored = {},
                    onClick = { saveSettings() },
                    enabled = !saving && !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun DeleteLocalHistoryRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "同时删除本地历史记录" }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = "同时删除本地历史记录",
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    body: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val selectedText = if (selected) "已选中" else "未选中"
    NativeAccessibleActionRow(
        title = title,
        body = listOf(body, selectedText).filter(String::isNotBlank).joinToString("，"),
        testTag = "coding_session_setting_${title.hashCode()}_${body.hashCode()}",
        restoreFocus = false,
        onFocusRestored = {},
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        accessibilityActions = emptyList(),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = listOf(title, body, selectedText)
            .filter(String::isNotBlank)
            .joinToString("，"),
    )
}

@Composable
private fun CodingMessageList(
    state: CodingConversationState,
    listState: LazyListState,
    actionsEnabled: Boolean,
    supportsThreadOperations: Boolean,
    onCopyMessage: (CodingMessage) -> Unit,
    onMessageMenu: (CodingMessage) -> Unit,
    onForkFromTurn: (CodingMessage) -> Unit,
    onReadFullMessage: (CodingMessage) -> Unit,
    onPagingFocusRestored: (String) -> Unit,
    onLoadOlder: (CodingPagingViewportAnchor?) -> Unit,
    onLoadNewer: (CodingPagingViewportAnchor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.loading && state.messages.isEmpty() -> item(key = "message_loading") {
                CodingLoadingPanel("正在加载消息")
            }

            state.errorCode != null && state.messages.isEmpty() -> item(key = "message_error") {
                CodingErrorPanel(
                    if (state.loadState == CodingLoadState.Error) {
                        "消息加载失败：${state.errorCode}"
                    } else {
                        "消息操作失败：${state.errorCode}"
                    },
                )
            }

            state.loadState == CodingLoadState.Loaded && state.messages.isEmpty() -> item(key = "message_empty") {
                CodingEmptyPanel("还没有消息")
            }

            state.loadState == CodingLoadState.Unavailable -> item(key = "message_unavailable") {
                CodingInfoPanel(
                    title = "消息暂不可用",
                    body = "连接设备后可查看此会话的消息。",
                )
            }
        }

        if (state.pagingSupported && state.hasOlder) {
            item(key = "message_load_older") {
                TextButton(
                    onClick = {
                        onLoadOlder(
                            state.messages.firstOrNull()?.let { message ->
                                CodingPagingViewportAnchor(
                                    messageId = message.id,
                                    scrollOffset = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == message.id }
                                        ?.offset
                                        ?: 0,
                                )
                            },
                        )
                    },
                    enabled = !state.loadingOlder,
                ) { Text("加载更早消息") }
            }
        }

        items(state.messages, key = { it.id }) { message ->
            CodingMessageActionRow(
                message = message,
                actionsEnabled = actionsEnabled,
                supportsThreadOperations = supportsThreadOperations,
                onCopyMessage = onCopyMessage,
                onMessageMenu = onMessageMenu,
                onForkFromTurn = onForkFromTurn,
                onReadFullMessage = onReadFullMessage,
                onPagingFocusRestored = onPagingFocusRestored,
                restoreFocus = state.restoreFocusMessageId == message.id,
                restoreFocusGeneration = state.restoreFocusGeneration,
                onFocusRestored = { onPagingFocusRestored(message.id) },
            )
        }

        if (state.pagingSupported && state.hasNewer) {
            item(key = "message_load_newer") {
                TextButton(
                    onClick = {
                        onLoadNewer(
                            state.messages.lastOrNull()?.let { message ->
                                CodingPagingViewportAnchor(
                                    messageId = message.id,
                                    scrollOffset = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == message.id }
                                        ?.offset
                                        ?: 0,
                                )
                            },
                        )
                    },
                    enabled = !state.loadingNewer,
                ) { Text("加载更新消息") }
            }
        }
    }
}

@Composable
private fun CodingMessageActionRow(
    message: CodingMessage,
    actionsEnabled: Boolean,
    supportsThreadOperations: Boolean,
    onCopyMessage: (CodingMessage) -> Unit,
    onMessageMenu: (CodingMessage) -> Unit,
    onForkFromTurn: (CodingMessage) -> Unit,
    onReadFullMessage: (CodingMessage) -> Unit,
    onPagingFocusRestored: (String) -> Unit,
    restoreFocus: Boolean,
    restoreFocusGeneration: Long,
    onFocusRestored: () -> Unit,
) {
    val collapsible = shouldCollapseMessageContent(message.content)
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val accessibleText = message.accessibilityText()
    val expansionLabel = if (collapsible) messageExpansionLabel(expanded) else null
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (message.role) {
        CodingMessageRole.User -> colorScheme.primaryContainer
        CodingMessageRole.Assistant -> colorScheme.surfaceContainerLow
        CodingMessageRole.System,
        CodingMessageRole.Tool,
        -> colorScheme.secondaryContainer
        CodingMessageRole.Unknown -> colorScheme.surfaceContainer
    }
    val titleColor = when (message.role) {
        CodingMessageRole.User -> colorScheme.onPrimaryContainer
        CodingMessageRole.System,
        CodingMessageRole.Tool,
        -> colorScheme.onSecondaryContainer
        CodingMessageRole.Assistant,
        CodingMessageRole.Unknown,
        -> colorScheme.onSurface
    }
    val bodyColor = when (message.role) {
        CodingMessageRole.User -> colorScheme.onPrimaryContainer
        CodingMessageRole.System,
        CodingMessageRole.Tool,
        -> colorScheme.onSecondaryContainer
        CodingMessageRole.Assistant,
        CodingMessageRole.Unknown,
        -> colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        contentColor = bodyColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        NativeAccessibleActionRow(
            title = message.role.displayName(),
            body = message.content,
            testTag = "coding_message_${message.id}",
            restoreFocus = restoreFocus,
            restoreFocusGeneration = restoreFocusGeneration,
            onFocusRestored = onFocusRestored,
            onClick = if (collapsible) {
                { expanded = !expanded }
            } else {
                null
            },
            onLongClick = if (actionsEnabled) {
                { onMessageMenu(message) }
            } else {
                null
            },
            accessibilityActions = if (actionsEnabled) {
                buildMessageAccessibilityActions(
                    message = message,
                    supportsThreadOperations = supportsThreadOperations,
                    onCopyMessage = onCopyMessage,
                    onForkFromTurn = onForkFromTurn,
                    onReadFullMessage = onReadFullMessage,
                )
            } else {
                emptyList()
            },
            modifier = Modifier.fillMaxWidth(),
            contentDescription = accessibleText,
            bodyMaxLines = if (collapsible && !expanded) CollapsedMessageMaxLines else null,
            footerText = expansionLabel,
            titleTextColor = titleColor,
            bodyTextColor = bodyColor,
            footerTextColor = bodyColor,
        )
    }
}

private fun buildMessageAccessibilityActions(
    message: CodingMessage,
    supportsThreadOperations: Boolean,
    onCopyMessage: (CodingMessage) -> Unit,
    onForkFromTurn: (CodingMessage) -> Unit,
    onReadFullMessage: (CodingMessage) -> Unit,
): List<NativeAccessibleAction> {
    return buildList {
        if (message.actions.copy) {
            add(
                NativeAccessibleAction(
                    id = R.id.accessibility_action_copy_message,
                    label = "复制",
                    onAction = {
                        onCopyMessage(message)
                        true
                    },
                ),
            )
        }
        if (message.contentTruncated && message.contentVersion != null) {
            add(
                NativeAccessibleAction(
                    id = R.id.accessibility_action_read_full_message,
                    label = "查看完整消息",
                    onAction = { onReadFullMessage(message); true },
                ),
            )
        }
        if (supportsThreadOperations && message.actions.forkFromTurn && !message.turnId.isNullOrBlank()) {
            add(
                NativeAccessibleAction(
                    id = R.id.accessibility_action_fork_from_turn,
                    label = "从本轮分叉",
                    onAction = {
                        onForkFromTurn(message)
                        true
                    },
                ),
            )
        }
    }
}

@Composable
private fun CodingWorkspaceRow(
    workspace: CodingWorkspace,
    restoreFocus: Boolean,
    restoreFocusGeneration: Long,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean,
    actionsEnabled: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    NativeAccessibleActionRow(
        title = workspace.name,
        body = workspace.path.ifBlank { "路径未返回" },
        testTag = "coding_workspace_${workspace.id}",
        restoreFocus = restoreFocus,
        restoreFocusGeneration = restoreFocusGeneration,
        onFocusRestored = onFocusRestored,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        accessibilityActions = if (actionsEnabled) {
            listOf(
                NativeAccessibleAction(
                    id = R.id.accessibility_action_archive_workspace,
                    label = "归档",
                    onAction = {
                        onArchive()
                        true
                    },
                ),
                NativeAccessibleAction(
                    id = R.id.accessibility_action_delete_workspace,
                    label = "删除",
                    onAction = {
                        onDelete()
                        true
                    },
                ),
            )
        } else {
            emptyList()
        },
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "${workspace.name}，${workspace.path.ifBlank { "路径未返回" }}",
    )
}

@Composable
private fun CodingSessionRow(
    session: CodingSession,
    restoreFocus: Boolean,
    restoreFocusGeneration: Long,
    onFocusRestored: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean,
    actionsEnabled: Boolean,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onOpenSubAgents: () -> Unit,
    supportsSubAgents: Boolean = true,
) {
    val metadata = session.metadataText()
    NativeAccessibleActionRow(
        title = session.title,
        body = metadata,
        testTag = "coding_session_${session.id}",
        restoreFocus = restoreFocus,
        restoreFocusGeneration = restoreFocusGeneration,
        onFocusRestored = onFocusRestored,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        accessibilityActions = if (actionsEnabled) {
            buildList {
                add(
                    NativeAccessibleAction(
                        id = R.id.accessibility_action_rename_session,
                        label = "重命名",
                        onAction = {
                            onRename()
                            true
                        },
                    ),
                )
                if (supportsSubAgents && session.subAgentCount > 0) {
                    add(
                        NativeAccessibleAction(
                            id = R.id.accessibility_action_open_subagents,
                            label = "子代理",
                            onAction = {
                                onOpenSubAgents()
                                true
                            },
                        ),
                    )
                }
                add(
                    NativeAccessibleAction(
                        id = R.id.accessibility_action_archive_session,
                        label = "归档",
                        onAction = {
                            onArchive()
                            true
                        },
                    ),
                )
                add(
                    NativeAccessibleAction(
                        id = R.id.accessibility_action_delete_session,
                        label = "删除",
                        onAction = {
                            onDelete()
                            true
                        },
                    ),
                )
            }
        } else {
            emptyList()
        },
        modifier = Modifier.fillMaxWidth(),
        contentDescription = listOf(session.title, metadata)
            .filter(String::isNotBlank)
            .joinToString("，"),
    )
}

@Composable
private fun CodingInfoPanel(
    title: String,
    body: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodingLoadingPanel(text: String) {
    CodingStatePanel(
        text = text,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        loading = true,
    )
}

@Composable
private fun CodingErrorPanel(text: String) {
    CodingStatePanel(
        text = text,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun CodingEmptyPanel(
    text: String,
    restoreFocus: Boolean = false,
    restoreFocusGeneration: Long? = null,
    onFocusRestored: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        NativeAccessibleActionRow(
            title = text,
            body = "",
            testTag = "coding_empty_state",
            restoreFocus = restoreFocus,
            restoreFocusGeneration = restoreFocusGeneration,
            onFocusRestored = onFocusRestored,
            onClick = null,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = text,
        )
    }
}

@Composable
private fun CodingStatePanel(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    loading: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
