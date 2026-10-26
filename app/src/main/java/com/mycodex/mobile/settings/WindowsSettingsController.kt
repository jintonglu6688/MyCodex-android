package com.mycodex.mobile.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WindowsSettingsUiState(
    val snapshot: WindowsSettingsSnapshot? = null,
    val draft: WindowsSettingsDraft? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val errorText: String? = null,
)

class WindowsSettingsController(
    private val scope: CoroutineScope,
    private val repository: WindowsSettingsDataSource,
) {
    private val mutableState = MutableStateFlow(WindowsSettingsUiState())
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var automaticLoadAttempted = false
    private var completionListener: (() -> Unit)? = null
    var prefixEditor by mutableStateOf<WindowsPrefixEditor?>(null)
        private set

    val state: StateFlow<WindowsSettingsUiState> = mutableState.asStateFlow()

    fun updateCompletionListener(listener: () -> Unit) {
        completionListener = listener
    }

    internal fun clearTransientState() {
        loadJob?.cancel()
        saveJob?.cancel()
        loadJob = null
        saveJob = null
        automaticLoadAttempted = false
        completionListener = null
        prefixEditor = null
        mutableState.value = WindowsSettingsUiState()
    }

    fun openPrefixEditor(index: Int?, value: String) {
        prefixEditor = WindowsPrefixEditor(index, value, value)
    }

    fun updatePrefixEditor(value: String) {
        prefixEditor = prefixEditor?.copy(value = value)
    }

    fun closePrefixEditor() {
        prefixEditor = null
    }

    fun loadIfNeeded() {
        if (!automaticLoadAttempted && mutableState.value.snapshot == null) {
            automaticLoadAttempted = true
            load()
        }
    }

    fun load() {
        if (mutableState.value.loading || mutableState.value.saving) {
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            mutableState.value = mutableState.value.copy(
                loading = true,
                errorText = null,
            )
            val result = repository.load()
            if (result.state == WindowsSettingsLoadState.Loaded && result.settings != null) {
                mutableState.value = WindowsSettingsUiState(
                    snapshot = result.settings,
                    draft = WindowsSettingsDraft.from(result.settings),
                )
                return@launch
            }

            mutableState.value = mutableState.value.copy(
                loading = false,
                errorText = "加载失败：${result.errorCode ?: result.state.name.lowercase()}",
            )
        }
    }

    fun selectModel(modelId: String) {
        updateDraft { draft, snapshot ->
            draft.selectModel(modelId, snapshot.models)
        }
    }

    fun selectReasoningEffort(reasoningEffort: String?) {
        updateDraft { draft, snapshot ->
            val model = snapshot.models.firstOrNull { it.modelId == draft.defaults.modelId }
                ?: return@updateDraft draft
            if (reasoningEffort != null && reasoningEffort !in model.reasoningEfforts) {
                return@updateDraft draft
            }
            draft.copy(
                defaults = draft.defaults.copy(reasoningEffort = reasoningEffort),
            )
        }
    }

    fun selectApprovalMode(approvalMode: WindowsApprovalMode) {
        updateDraft { draft, _ ->
            draft.copy(defaults = draft.defaults.copy(approvalMode = approvalMode))
        }
    }

    fun setEnabledPermission(permissionId: String, enabled: Boolean) {
        updateDraft { draft, snapshot ->
            if (snapshot.customApproval.availablePermissions.none { it.id == permissionId }) {
                return@updateDraft draft
            }
            val next = if (enabled) {
                (draft.customApproval.enabledPermissions + permissionId).distinct()
            } else {
                draft.customApproval.enabledPermissions.filterNot { it == permissionId }
            }
            draft.copy(customApproval = draft.customApproval.copy(enabledPermissions = next))
        }
    }

    fun addCommandPrefix(prefix: String) {
        updateDraft { draft, _ -> draft.withAddedPrefix(prefix) }
    }

    fun replaceCommandPrefix(index: Int, prefix: String) {
        updateDraft { draft, _ -> draft.withReplacedPrefix(index, prefix) }
    }

    fun deleteCommandPrefix(index: Int) {
        updateDraft { draft, _ -> draft.withDeletedPrefix(index) }
    }

    fun updateOfficial(transform: (WindowsOfficialSettings) -> WindowsOfficialSettings) {
        updateDraft { draft, _ ->
            draft.official?.let { draft.copy(official = transform(it)) } ?: draft
        }
    }

    fun saveDefaults(onSaved: () -> Unit) {
        save({ draft, snapshot -> draft.defaultsUpdate(snapshot) }, onSaved)
    }

    fun saveCustomApproval(onSaved: () -> Unit) {
        save({ draft, snapshot -> draft.customApprovalUpdate(snapshot) }, onSaved)
    }

    fun saveOfficial(onSaved: () -> Unit) {
        save({ draft, snapshot -> draft.officialUpdate(snapshot) }, onSaved)
    }

    private fun save(
        buildUpdate: (WindowsSettingsDraft, WindowsSettingsSnapshot) -> WindowsSettingsUpdate?,
        onSaved: () -> Unit,
    ) {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.loading || current.saving) {
            return
        }
        val update = buildUpdate(draft, snapshot)
        if (update == null) {
            (completionListener ?: onSaved).invoke()
            return
        }

        saveJob?.cancel()
        saveJob = scope.launch {
            mutableState.value = mutableState.value.copy(
                saving = true,
                errorText = null,
            )
            val result = repository.update(update)
            if (result.state == WindowsSettingsUpdateState.Completed && result.settings != null) {
                mutableState.value = WindowsSettingsUiState(
                    snapshot = result.settings,
                    draft = WindowsSettingsDraft.from(result.settings),
                )
                (completionListener ?: onSaved).invoke()
                return@launch
            }

            mutableState.value = mutableState.value.copy(
                saving = false,
                errorText = "保存失败：${result.errorCode ?: result.state.name.lowercase()}",
            )
        }
    }

    private fun updateDraft(
        transform: (WindowsSettingsDraft, WindowsSettingsSnapshot) -> WindowsSettingsDraft,
    ) {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.loading || current.saving) {
            return
        }
        mutableState.value = current.copy(
            draft = transform(draft, snapshot),
            errorText = null,
        )
    }
}

data class WindowsPrefixEditor(
    val index: Int?,
    val initialValue: String,
    val value: String,
)
