package com.mycodex.mobile.updates

import android.content.Context
import android.os.Build
import com.mycodex.mobile.diagnostics.MobileDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI

internal class AndroidUpdateController(
    private val context: Context,
    private val scope: CoroutineScope,
    manifestUrl: String,
    private val store: UpdateStateStore = AndroidUpdateStore(context),
    private val inspector: UpdateApkInspector = AndroidApkInspector(context),
    transport: UpdateHttpTransport = OkHttpUpdateTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val installer: UpdatePackageInstaller = AndroidPackageInstaller(context),
    private val authorizationLauncher: UpdateInstallAuthorizationLauncher = AndroidInstallAuthorizationLauncher(context),
    private val deviceApi: Int = Build.VERSION.SDK_INT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val parser = AndroidUpdateManifestParser()
    private val cacheRoot = File(context.cacheDir, "updates")
    private val repository = AndroidUpdateRepository(URI(manifestUrl), parser, transport, cacheRoot, ioDispatcher)
    private val _state = MutableStateFlow(
        AndroidUpdateUiState(installed = inspector.installedVersion()),
    )
    val state: StateFlow<AndroidUpdateUiState> = _state.asStateFlow()
    private var operation: Job? = null
    private var activeCheckOrigin: UpdateCheckOrigin? = null
    private var foreground = false
    private var promptAllowed = false
    private var restoringVerifiedPackage = false
    private var pendingAutomaticCheck = false
    private var pendingManualPrompt = false
    private var nextToastId = 1L

    init {
        repository.cleanPartialFiles()
        restorePersistedState()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        maybeShowUpdatePrompt()
    }

    fun setPromptAllowed(value: Boolean) {
        promptAllowed = value
        if (value) maybeShowUpdatePrompt()
    }

    fun startAutomaticCheck() {
        val now = clock()
        val last = store.lastAutomaticCheckAttemptAt
        if (!automaticCheckDue(now, last)) return
        if (operation?.isActive == true) {
            if (restoringVerifiedPackage) pendingAutomaticCheck = true
            return
        }
        startCheck(UpdateCheckOrigin.Automatic, now)
    }

    fun checkManually() {
        if (restoringVerifiedPackage) {
            pendingAutomaticCheck = false
            pendingManualPrompt = true
            return
        }
        when (_state.value.phase) {
            AndroidUpdatePhase.CheckingAutomatic -> {
                activeCheckOrigin = UpdateCheckOrigin.Manual
                update { it.copy(phase = AndroidUpdatePhase.CheckingManual) }
                showToast("正在检查更新")
            }
            AndroidUpdatePhase.CheckingManual -> Unit
            AndroidUpdatePhase.Downloading,
            AndroidUpdatePhase.Verifying,
            AndroidUpdatePhase.PreparingInstallationHandoff,
            AndroidUpdatePhase.AwaitingInstaller,
            -> showToast("更新正在处理中")
            AndroidUpdatePhase.UpdateAvailable,
            AndroidUpdatePhase.ReadyToInstall,
            AndroidUpdatePhase.InstallAuthorizationRequired,
            -> showAvailablePrompt()
            else -> {
                showToast("正在检查更新")
                startCheck(UpdateCheckOrigin.Manual, clock())
            }
        }
    }

    fun consumeToastMessage(id: Long) {
        update { current ->
            if (current.toastMessage?.id == id) current.copy(toastMessage = null) else current
        }
    }

    fun deferPrompt() {
        markPromptHandled()
        update { it.copy(updatePromptVisible = false) }
    }

    fun beginUpdate() {
        markPromptHandled()
        update { it.copy(updatePromptVisible = false) }
        if (operation?.isActive == true) return
        val manifest = _state.value.manifest ?: return
        val cached = cachedFileFor(manifest.identity)
        if (cached != null) {
            verifyCachedAndHandOff(cached, manifest)
        } else {
            downloadAndHandOff(manifest)
        }
    }

    fun refreshInstallAuthorization() {
        refreshInstallationHandoff()
        if (_state.value.phase != AndroidUpdatePhase.InstallAuthorizationRequired) return
        if (installer.canRequestPackageInstalls()) {
            val manifest = _state.value.manifest ?: return
            val cached = cachedFileFor(manifest.identity)
            if (cached != null) {
                verifyCachedAndHandOff(cached, manifest)
            } else {
                update { it.copy(phase = AndroidUpdatePhase.UpdateAvailable) }
                showToast("更新包已被清理，请重新更新")
            }
        } else {
            update { it.copy(phase = AndroidUpdatePhase.ReadyToInstall) }
            showToast("未允许安装更新，可以稍后重试")
        }
    }

    private fun downloadAndHandOff(manifest: AndroidUpdateManifest) {
        operation = scope.launch {
            update { it.copy(phase = AndroidUpdatePhase.Downloading) }
            showToast("正在下载更新，完成后将打开安装程序")
            var part: File? = null
            try {
                part = repository.download(manifest) { _, _ -> }
                update { it.copy(phase = AndroidUpdatePhase.Verifying) }
                val verification = try {
                    withContext(ioDispatcher) { inspector.verify(part, manifest) }
                } catch (error: Exception) {
                    ApkVerificationResult.Invalid(error.safeCategory())
                }
                when (val result = verification) {
                    ApkVerificationResult.Valid -> {
                        val final = repository.promote(part, manifest)
                        store.saveVerified(manifest.identity, final.relativeTo(cacheRoot).path)
                        handOffVerifiedPackage(final)
                    }
                    is ApkVerificationResult.Invalid -> verificationFailed(part, result.category)
                }
            } catch (cancelled: CancellationException) {
                repository.deleteCached(part)
                update { it.copy(phase = AndroidUpdatePhase.UpdateAvailable) }
                throw cancelled
            } catch (error: Exception) {
                repository.deleteCached(part)
                MobileDiagnostics.warn("Update", "download_failed category=${error.safeCategory()}")
                update { it.copy(phase = AndroidUpdatePhase.UpdateAvailable) }
                showToast("下载更新失败，请重试")
            } finally {
                operation = null
            }
        }
    }

    private fun verifyCachedAndHandOff(file: File, manifest: AndroidUpdateManifest) {
        if (operation?.isActive == true) return
        operation = scope.launch {
            update { it.copy(phase = AndroidUpdatePhase.Verifying) }
            val verification = try {
                withContext(ioDispatcher) { inspector.verify(file, manifest) }
            } catch (error: Exception) {
                ApkVerificationResult.Invalid(error.safeCategory())
            }
            when (verification) {
                ApkVerificationResult.Valid -> handOffVerifiedPackage(file)
                is ApkVerificationResult.Invalid -> verificationFailed(file, verification.category)
            }
            operation = null
        }
    }

    private fun handOffVerifiedPackage(file: File) {
        if (!installer.canRequestPackageInstalls()) {
            update { it.copy(phase = AndroidUpdatePhase.InstallAuthorizationRequired) }
            openInstallAuthorization()
            return
        }
        update { it.copy(phase = AndroidUpdatePhase.PreparingInstallationHandoff) }
        try {
            installer.install(file)
            update { it.copy(phase = AndroidUpdatePhase.AwaitingInstaller) }
        } catch (error: Exception) {
            MobileDiagnostics.warn("Update", "install_handoff_failed category=${error.safeCategory()}")
            update { it.copy(phase = AndroidUpdatePhase.ReadyToInstall) }
            showToast("无法打开安装程序，请稍后重试")
        }
    }

    private fun openInstallAuthorization() {
        try {
            authorizationLauncher.open()
        } catch (error: Exception) {
            MobileDiagnostics.warn("Update", "install_authorization_unavailable category=${error.safeCategory()}")
            update { it.copy(phase = AndroidUpdatePhase.ReadyToInstall) }
            showToast("无法打开安装授权设置，请稍后重试")
        }
    }

    private fun refreshInstallationHandoff() {
        if (_state.value.phase != AndroidUpdatePhase.AwaitingInstaller) return
        val manifest = _state.value.manifest ?: return
        val installed = inspector.installedVersion()
        if (installed.versionCode >= manifest.app.versionCode) {
            clearPersistedAvailableUpdate()
            update {
                it.copy(
                    phase = AndroidUpdatePhase.UpToDate,
                    installed = installed,
                    manifest = null,
                )
            }
            showToast("更新安装完成")
        } else {
            update { it.copy(phase = AndroidUpdatePhase.ReadyToInstall, installed = installed) }
            showToast("安装未完成，可以稍后重试")
        }
    }

    private fun startCheck(origin: UpdateCheckOrigin, now: Long) {
        if (operation?.isActive == true) return
        activeCheckOrigin = origin
        update {
            it.copy(
                phase = if (origin == UpdateCheckOrigin.Automatic) {
                    AndroidUpdatePhase.CheckingAutomatic
                } else {
                    AndroidUpdatePhase.CheckingManual
                },
                updatePromptVisible = false,
            )
        }
        operation = scope.launch {
            try {
                if (origin == UpdateCheckOrigin.Automatic) store.lastAutomaticCheckAttemptAt = now
                val manifest = repository.check()
                val finalOrigin = activeCheckOrigin ?: origin
                invalidateDifferentCache(manifest.identity)
                when (selectManifest(manifest, _state.value.installed, deviceApi)) {
                    ManifestCheckResult.UpToDate -> {
                        clearPersistedAvailableUpdate()
                        update { it.copy(phase = AndroidUpdatePhase.UpToDate, manifest = null) }
                        if (finalOrigin == UpdateCheckOrigin.Manual) {
                            showToast("当前已是最新版本 ${_state.value.installed.versionName}")
                        }
                    }
                    is ManifestCheckResult.Incompatible -> {
                        clearPersistedAvailableUpdate()
                        update { it.copy(phase = AndroidUpdatePhase.Incompatible, manifest = null) }
                        if (finalOrigin == UpdateCheckOrigin.Manual) showToast("当前设备不支持此版本")
                    }
                    is ManifestCheckResult.Available -> acceptAvailable(manifest, finalOrigin)
                }
            } catch (error: Exception) {
                val finalOrigin = activeCheckOrigin ?: origin
                MobileDiagnostics.warn("Update", "check_failed category=${error.safeCategory()}")
                update { it.copy(phase = AndroidUpdatePhase.Idle, updatePromptVisible = false) }
                if (finalOrigin == UpdateCheckOrigin.Manual) showToast("检查更新失败，请重试")
            } finally {
                activeCheckOrigin = null
                operation = null
            }
        }
    }

    private fun acceptAvailable(manifest: AndroidUpdateManifest, origin: UpdateCheckOrigin) {
        store.lastKnownManifestJson = manifest.sourceJson
        val verified = store.loadVerified()
        val verifiedIsReady = verified?.first == manifest.identity && cachedFile(verified.second)?.isFile == true
        update {
            it.copy(
                phase = if (verifiedIsReady) AndroidUpdatePhase.ReadyToInstall else AndroidUpdatePhase.UpdateAvailable,
                manifest = manifest,
                updatePromptVisible = origin == UpdateCheckOrigin.Manual,
            )
        }
        if (origin == UpdateCheckOrigin.Automatic) maybeShowUpdatePrompt()
    }

    private fun showAvailablePrompt() {
        if (_state.value.manifest == null) {
            startCheck(UpdateCheckOrigin.Manual, clock())
            return
        }
        update { it.copy(updatePromptVisible = true) }
    }

    private fun maybeShowUpdatePrompt() {
        val current = _state.value
        val manifest = current.manifest ?: return
        val attemptWindow = store.lastAutomaticCheckAttemptAt ?: return
        if (current.phase != AndroidUpdatePhase.UpdateAvailable &&
            current.phase != AndroidUpdatePhase.ReadyToInstall
        ) return
        if (!foreground || !promptAllowed) return
        if (store.lastPromptedVersionCode == manifest.app.versionCode &&
            store.lastPromptedAttemptWindow == attemptWindow
        ) return
        update { it.copy(updatePromptVisible = true) }
    }

    private fun markPromptHandled() {
        val manifest = _state.value.manifest ?: return
        val attemptWindow = store.lastAutomaticCheckAttemptAt ?: return
        store.lastPromptedVersionCode = manifest.app.versionCode
        store.lastPromptedAttemptWindow = attemptWindow
    }

    private fun restorePersistedState() {
        val installed = inspector.installedVersion()
        val verified = store.loadVerified()
        if (verified != null && installed.versionCode >= verified.first.versionCode) {
            cachedFile(verified.second)?.delete()
            store.clearVerified()
            store.lastKnownManifestJson = null
            return
        }
        val manifest = store.lastKnownManifestJson?.let { raw ->
            runCatching { parser.parse(raw.toByteArray()) }.getOrNull()
        }
        if (manifest == null || selectManifest(manifest, installed, deviceApi) !is ManifestCheckResult.Available) {
            verified?.let { cachedFile(it.second)?.delete() }
            store.clearVerified()
            store.lastKnownManifestJson = null
            return
        }
        update { it.copy(installed = installed, manifest = manifest, phase = AndroidUpdatePhase.UpdateAvailable) }
        if (verified?.first == manifest.identity) revalidateCached(restoring = true)
    }

    private fun revalidateCached(restoring: Boolean) {
        val manifest = _state.value.manifest ?: return
        if (operation?.isActive == true) return
        val file = cachedFileFor(manifest.identity)
        if (file == null) {
            update { it.copy(phase = AndroidUpdatePhase.UpdateAvailable) }
            return
        }
        restoringVerifiedPackage = restoring
        operation = scope.launch {
            update { it.copy(phase = AndroidUpdatePhase.Verifying) }
            try {
                when (val result = withContext(ioDispatcher) { inspector.verify(file, manifest) }) {
                    ApkVerificationResult.Valid -> update { it.copy(phase = AndroidUpdatePhase.ReadyToInstall) }
                    is ApkVerificationResult.Invalid -> verificationFailed(file, result.category)
                }
            } catch (error: Exception) {
                verificationFailed(file, error.safeCategory())
            } finally {
                if (restoring) {
                    restoringVerifiedPackage = false
                    operation = null
                    if (pendingManualPrompt) {
                        pendingManualPrompt = false
                        showAvailablePrompt()
                    } else if (pendingAutomaticCheck) {
                        pendingAutomaticCheck = false
                        startAutomaticCheck()
                    }
                }
            }
        }
    }

    private fun verificationFailed(file: File?, category: String) {
        repository.deleteCached(file)
        store.clearVerified()
        MobileDiagnostics.warn("Update", "verification_failed category=$category")
        update { it.copy(phase = AndroidUpdatePhase.UpdateAvailable) }
        showToast("更新包验证失败，请重新更新")
    }

    private fun invalidateDifferentCache(identity: UpdatePackageIdentity) {
        val saved = store.loadVerified() ?: return
        if (saved.first != identity) {
            cachedFile(saved.second)?.delete()
            store.clearVerified()
        }
    }

    private fun clearPersistedAvailableUpdate() {
        store.lastKnownManifestJson = null
        store.loadVerified()?.let { cachedFile(it.second)?.delete() }
        store.clearVerified()
    }

    private fun cachedFileFor(identity: UpdatePackageIdentity): File? {
        val saved = store.loadVerified() ?: return null
        if (saved.first != identity) return null
        return cachedFile(saved.second)?.takeIf(File::isFile)
    }

    private fun cachedFile(relativePath: String): File? {
        val file = File(cacheRoot, relativePath)
        return file.takeIf { it.canonicalPath.startsWith(cacheRoot.canonicalPath + File.separator) }
    }

    private fun showToast(message: String) {
        val toast = UpdateToastMessage(nextToastId++, message)
        update { it.copy(toastMessage = toast) }
    }

    private inline fun update(block: (AndroidUpdateUiState) -> AndroidUpdateUiState) {
        _state.value = block(_state.value)
    }

    companion object {
        internal const val AUTOMATIC_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}

internal fun automaticCheckDue(now: Long, lastAttempt: Long?): Boolean =
    lastAttempt == null || now - lastAttempt !in 0 until AndroidUpdateController.AUTOMATIC_INTERVAL_MS

private fun Exception.safeCategory(): String =
    (message ?: javaClass.simpleName).substringBefore(':').take(48).replace(Regex("[^A-Za-z0-9_-]"), "_")
