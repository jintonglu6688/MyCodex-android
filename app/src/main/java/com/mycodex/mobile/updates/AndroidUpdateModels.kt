package com.mycodex.mobile.updates

import java.net.URI

internal const val UPDATE_APPLICATION_ID = "com.mycodex.mobile"
internal const val MAX_MANIFEST_BYTES = 256 * 1024
internal const val MAX_APK_BYTES = 200L * 1024L * 1024L

internal data class InstalledAppVersion(
    val versionCode: Long,
    val versionName: String,
    val minimumAndroidApi: Int,
)

internal data class LocalizedReleaseNotes(
    val zhCn: String,
    val enUs: String?,
)

internal data class AndroidUpdateAsset(
    val url: URI,
    val fileName: String,
    val sha256: String,
    val size: Long,
)

internal data class AndroidUpdateAppRelease(
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val minimumAndroidApi: Int,
    val releaseDate: String,
    val releaseNotes: LocalizedReleaseNotes,
    val asset: AndroidUpdateAsset,
)

internal data class AndroidUpdateManifest(
    val channel: String,
    val generatedAt: String,
    val packageBaseUrl: URI,
    val app: AndroidUpdateAppRelease,
    val sourceJson: String,
) {
    val identity: UpdatePackageIdentity
        get() = UpdatePackageIdentity(app.versionCode, app.asset.fileName, app.asset.size, app.asset.sha256)
}

internal data class UpdatePackageIdentity(
    val versionCode: Long,
    val fileName: String,
    val size: Long,
    val sha256: String,
)

internal enum class UpdateCheckOrigin { Automatic, Manual }

internal enum class AndroidUpdatePhase {
    Idle,
    CheckingAutomatic,
    CheckingManual,
    UpToDate,
    Incompatible,
    UpdateAvailable,
    Downloading,
    Verifying,
    ReadyToInstall,
    InstallAuthorizationRequired,
    PreparingInstallationHandoff,
    AwaitingInstaller,
}

internal data class UpdateToastMessage(
    val id: Long,
    val text: String,
)

internal data class AndroidUpdateUiState(
    val phase: AndroidUpdatePhase = AndroidUpdatePhase.Idle,
    val installed: InstalledAppVersion = InstalledAppVersion(0, "", 0),
    val manifest: AndroidUpdateManifest? = null,
    val updatePromptVisible: Boolean = false,
    val toastMessage: UpdateToastMessage? = null,
)

internal sealed interface ManifestCheckResult {
    data class Available(val manifest: AndroidUpdateManifest) : ManifestCheckResult
    data object UpToDate : ManifestCheckResult
    data class Incompatible(val manifest: AndroidUpdateManifest) : ManifestCheckResult
}

internal sealed interface ApkVerificationResult {
    data object Valid : ApkVerificationResult
    data class Invalid(val category: String) : ApkVerificationResult
}

internal fun selectManifest(
    manifest: AndroidUpdateManifest,
    installed: InstalledAppVersion,
    deviceApi: Int,
): ManifestCheckResult = when {
    manifest.app.versionCode <= installed.versionCode -> ManifestCheckResult.UpToDate
    manifest.app.minimumAndroidApi > deviceApi -> ManifestCheckResult.Incompatible(manifest)
    else -> ManifestCheckResult.Available(manifest)
}
