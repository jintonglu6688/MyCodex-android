package com.mycodex.mobile.updates

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidUpdateControllerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun manualCheckTakesOverAutomaticRequestAndOpensTheCompletePrompt() {
        val scope = TestScope(StandardTestDispatcher())
        val transport = DelayedTransport(validManifest())
        val controller = controller(scope, transport)
        controller.startAutomaticCheck()
        scope.runCurrent()
        assertEquals(1, transport.requestCount)

        controller.checkManually()
        assertEquals(AndroidUpdatePhase.CheckingManual, controller.state.value.phase)
        transport.release.complete(Unit)
        scope.advanceUntilIdle()

        assertEquals(1, transport.requestCount)
        assertEquals(AndroidUpdatePhase.UpdateAvailable, controller.state.value.phase)
        assertTrue(controller.state.value.updatePromptVisible)
    }

    @Test
    fun automaticFailureIsSilentWhileManualFailureUsesATransientMessage() {
        var now = 100L
        val scope = TestScope(StandardTestDispatcher())
        val transport = FailingTransport()
        val controller = controller(scope, transport, clock = { now })
        controller.startAutomaticCheck()
        scope.advanceUntilIdle()
        assertEquals(AndroidUpdatePhase.Idle, controller.state.value.phase)
        assertNull(controller.state.value.toastMessage)

        now += AndroidUpdateController.AUTOMATIC_INTERVAL_MS
        controller.checkManually()
        scope.advanceUntilIdle()

        assertEquals(AndroidUpdatePhase.Idle, controller.state.value.phase)
        assertEquals("检查更新失败，请重试", controller.state.value.toastMessage?.text)
    }

    @Test
    fun upToDateManualCheckClearsStaleStateAndUsesATransientMessage() {
        val scope = TestScope(StandardTestDispatcher())
        val store = MemoryStore().apply {
            lastKnownManifestJson = String(validManifest())
            saveVerified(
                UpdatePackageIdentity(1, "MyCodex-Android-v1.0.0-c1.apk", 10, "a".repeat(64)),
                "1/MyCodex-Android-v1.0.0-c1.apk",
            )
        }
        val controller = controller(scope, ImmediateTransport(manifest(versionCode = 1)), store = store)

        controller.checkManually()
        scope.advanceUntilIdle()

        assertEquals(AndroidUpdatePhase.UpToDate, controller.state.value.phase)
        assertEquals("当前已是最新版本 1.0", controller.state.value.toastMessage?.text)
        assertNull(store.lastKnownManifestJson)
        assertNull(store.loadVerified())
    }

    @Test
    fun incompatibleManualCheckUsesATransientMessage() {
        val scope = TestScope(StandardTestDispatcher())
        val controller = controller(scope, ImmediateTransport(manifest(versionCode = 2, minimumApi = 99)))

        controller.checkManually()
        scope.advanceUntilIdle()

        assertEquals(AndroidUpdatePhase.Incompatible, controller.state.value.phase)
        assertEquals("当前设备不支持此版本", controller.state.value.toastMessage?.text)
    }

    @Test
    fun dueAutomaticCheckRunsAfterStartupVerifiedPackageRevalidation() {
        val scope = TestScope(StandardTestDispatcher())
        val store = verifiedStore()
        cachedApk()
        val transport = ImmediateTransport(validManifest())
        val controller = controller(scope, transport, store = store)

        controller.startAutomaticCheck()
        assertEquals(0, transport.requestCount)
        scope.advanceUntilIdle()

        assertEquals(1, transport.requestCount)
        assertEquals(AndroidUpdatePhase.ReadyToInstall, controller.state.value.phase)
    }

    @Test
    fun availableUpdatePromptsOncePerAutomaticCheckWindowUntilInstalled() {
        var now = 1_000L
        val scope = TestScope(StandardTestDispatcher())
        val store = verifiedStore()
        cachedApk()
        val transport = ImmediateTransport(validManifest())
        val controller = controller(scope, transport, clock = { now }, store = store)
        controller.setForeground(true)
        controller.setPromptAllowed(true)

        controller.startAutomaticCheck()
        scope.advanceUntilIdle()
        assertTrue(controller.state.value.updatePromptVisible)

        controller.deferPrompt()
        assertFalse(controller.state.value.updatePromptVisible)
        controller.setForeground(false)
        controller.setForeground(true)
        controller.startAutomaticCheck()
        scope.advanceUntilIdle()
        assertFalse(controller.state.value.updatePromptVisible)

        now += AndroidUpdateController.AUTOMATIC_INTERVAL_MS
        controller.startAutomaticCheck()
        scope.advanceUntilIdle()
        assertTrue(controller.state.value.updatePromptVisible)
    }

    @Test
    fun manualCheckDuringStartupRevalidationOpensPromptWithoutAnotherRequest() {
        val scope = TestScope(StandardTestDispatcher())
        val store = verifiedStore()
        cachedApk()
        val transport = ImmediateTransport(validManifest())
        val controller = controller(scope, transport, store = store)

        controller.startAutomaticCheck()
        controller.checkManually()
        scope.advanceUntilIdle()

        assertEquals(0, transport.requestCount)
        assertTrue(controller.state.value.updatePromptVisible)
    }

    @Test
    fun updateNowDownloadsVerifiesAndHandsOffWithoutASecondInstallAction() {
        val scope = TestScope(StandardTestDispatcher())
        val store = MemoryStore()
        val installer = RecordingInstaller()
        val transport = DownloadingTransport(validManifest())
        val controller = controller(scope, transport, store = store, installer = installer)
        controller.checkManually()
        scope.advanceUntilIdle()
        assertTrue(controller.state.value.updatePromptVisible)

        controller.beginUpdate()
        scope.advanceUntilIdle()

        assertEquals("state=${controller.state.value}", 1, transport.downloadCount)
        assertEquals(AndroidUpdatePhase.AwaitingInstaller, controller.state.value.phase)
        assertFalse(controller.state.value.updatePromptVisible)
        assertTrue(installer.file?.isFile == true)
        assertEquals(2L, store.loadVerified()?.first?.versionCode)
    }

    @Test
    fun authorizationReturnContinuesTheSameExplicitUpdateRequest() {
        val scope = TestScope(StandardTestDispatcher())
        val store = verifiedStore()
        cachedApk()
        val installer = RecordingInstaller(allowed = false)
        val authorization = RecordingAuthorizationLauncher()
        val controller = controller(
            scope,
            ImmediateTransport(validManifest()),
            store = store,
            installer = installer,
            authorizationLauncher = authorization,
        )
        scope.advanceUntilIdle()
        controller.checkManually()
        controller.beginUpdate()
        scope.advanceUntilIdle()

        assertEquals("state=${controller.state.value}", AndroidUpdatePhase.InstallAuthorizationRequired, controller.state.value.phase)
        assertEquals(1, authorization.openCount)

        installer.allowed = true
        controller.refreshInstallAuthorization()
        scope.advanceUntilIdle()

        assertEquals(AndroidUpdatePhase.AwaitingInstaller, controller.state.value.phase)
        assertTrue(installer.file?.isFile == true)
    }

    @Test
    fun installerReturnRetainsVerifiedPackageAndUsesATransientMessage() {
        val scope = TestScope(StandardTestDispatcher())
        val store = verifiedStore()
        val cached = cachedApk()
        val installer = RecordingInstaller()
        val controller = controller(scope, ImmediateTransport(validManifest()), store = store, installer = installer)
        scope.advanceUntilIdle()
        controller.checkManually()
        controller.beginUpdate()
        scope.advanceUntilIdle()

        controller.refreshInstallAuthorization()

        assertEquals(AndroidUpdatePhase.ReadyToInstall, controller.state.value.phase)
        assertEquals("安装未完成，可以稍后重试", controller.state.value.toastMessage?.text)
        assertTrue(cached.isFile)
        assertEquals(2L, store.loadVerified()?.first?.versionCode)
    }

    @Test
    fun consumingOldToastDoesNotClearANewerMessage() {
        val scope = TestScope(StandardTestDispatcher())
        val controller = controller(scope, FailingTransport())
        controller.checkManually()
        val first = controller.state.value.toastMessage ?: error("missing first toast")
        scope.advanceUntilIdle()
        val second = controller.state.value.toastMessage ?: error("missing second toast")

        controller.consumeToastMessage(first.id)

        assertEquals(second, controller.state.value.toastMessage)
    }

    private fun controller(
        scope: TestScope,
        transport: UpdateHttpTransport,
        clock: () -> Long = { 1_000L },
        store: MemoryStore = MemoryStore(),
        installer: UpdatePackageInstaller = RecordingInstaller(),
        authorizationLauncher: UpdateInstallAuthorizationLauncher = RecordingAuthorizationLauncher(),
        context: Context = TestContext(temporary.root),
    ): AndroidUpdateController = AndroidUpdateController(
        context = context,
        scope = scope,
        manifestUrl = "https://api.gitcode.com/manifest",
        store = store,
        inspector = object : UpdateApkInspector {
            override fun installedVersion() = InstalledAppVersion(1, "1.0", 26)
            override fun verify(file: File, manifest: AndroidUpdateManifest) = ApkVerificationResult.Valid
        },
        transport = transport,
        clock = clock,
        installer = installer,
        authorizationLauncher = authorizationLauncher,
        deviceApi = 36,
        ioDispatcher = StandardTestDispatcher(scope.testScheduler),
    )

    private fun verifiedStore() = MemoryStore().apply {
        lastKnownManifestJson = String(validManifest())
        saveVerified(
            UpdatePackageIdentity(2, "MyCodex-Android-v1.1.0-c2.apk", 10, "a".repeat(64)),
            "2/MyCodex-Android-v1.1.0-c2.apk",
        )
    }

    private fun cachedApk() = File(temporary.root, "updates/2/MyCodex-Android-v1.1.0-c2.apk").apply {
        requireNotNull(parentFile).mkdirs()
        writeBytes(ByteArray(10))
    }

    private class TestContext(private val root: File) : ContextWrapper(null) {
        override fun getCacheDir(): File = root
        override fun getPackageName(): String = UPDATE_APPLICATION_ID
        override fun getApplicationContext(): Context = this
    }

    private class DelayedTransport(private val manifest: ByteArray) : UpdateHttpTransport {
        var requestCount = 0
        val release = CompletableDeferred<Unit>()
        override suspend fun get(url: URI): UpdateHttpResponse {
            requestCount += 1
            release.await()
            return UpdateHttpResponse(200, url, manifest)
        }
        override suspend fun downloadTo(
            url: URI,
            destination: File,
            maximumBytes: Long,
            onProgress: (Long) -> Unit,
        ): UpdateDownloadResponse {
            error("not used")
        }
    }

    private class FailingTransport : UpdateHttpTransport {
        var requestCount = 0
        override suspend fun get(url: URI): UpdateHttpResponse {
            requestCount += 1
            throw java.io.IOException("offline")
        }
        override suspend fun downloadTo(url: URI, destination: File, maximumBytes: Long, onProgress: (Long) -> Unit) =
            error("not used")
    }

    private open class ImmediateTransport(private val manifest: ByteArray) : UpdateHttpTransport {
        var requestCount = 0
        override suspend fun get(url: URI): UpdateHttpResponse {
            requestCount += 1
            return UpdateHttpResponse(200, url, manifest)
        }
        override suspend fun downloadTo(
            url: URI,
            destination: File,
            maximumBytes: Long,
            onProgress: (Long) -> Unit,
        ): UpdateDownloadResponse {
            error("not used")
        }
    }

    private class DownloadingTransport(manifest: ByteArray) : ImmediateTransport(manifest) {
        var downloadCount = 0
        override suspend fun downloadTo(
            url: URI,
            destination: File,
            maximumBytes: Long,
            onProgress: (Long) -> Unit,
        ): UpdateDownloadResponse {
            downloadCount += 1
            destination.writeBytes(ByteArray(10))
            onProgress(10)
            return UpdateDownloadResponse(200, url, 10)
        }
    }

    private class MemoryStore : UpdateStateStore {
        override var lastAutomaticCheckAttemptAt: Long? = null
        override var lastPromptedVersionCode: Long? = null
        override var lastPromptedAttemptWindow: Long? = null
        override var lastKnownManifestJson: String? = null
        private var verified: Pair<UpdatePackageIdentity, String>? = null
        override fun saveVerified(identity: UpdatePackageIdentity, relativePath: String) { verified = identity to relativePath }
        override fun loadVerified() = verified
        override fun clearVerified() { verified = null }
    }

    private class RecordingInstaller(var allowed: Boolean = true) : UpdatePackageInstaller {
        var file: File? = null
        override fun canRequestPackageInstalls() = allowed
        override fun install(file: File) {
            this.file = file
        }
    }

    private class RecordingAuthorizationLauncher : UpdateInstallAuthorizationLauncher {
        var openCount = 0
        override fun open() {
            openCount += 1
        }
    }

    private fun validManifest(): ByteArray = manifest(2)

    private fun manifest(versionCode: Int, minimumApi: Int = 26): ByteArray = """{"schemaVersion":"1","channel":"stable","generatedAt":"2026-08-07T00:00:00Z","packageBaseUrl":"https://api.gitcode.com/api/v5/repos/gcw_SpGZ48lW/mycodex-updates/releases/android-v1.1.0-c$versionCode/attach_files/","app":{"applicationId":"com.mycodex.mobile","versionCode":$versionCode,"versionName":"1.1.$versionCode","minimumAndroidApi":$minimumApi,"releaseDate":"2026-08-07","releaseNotes":{"zh-CN":"说明"},"asset":{"url":"MyCodex-Android-v1.1.0-c$versionCode.apk/download","fileName":"MyCodex-Android-v1.1.0-c$versionCode.apk","sha256":"${"a".repeat(64)}","size":10}}}""".toByteArray()
}
