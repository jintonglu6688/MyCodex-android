package com.mycodex.mobile.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

internal interface UpdateApkInspector {
    fun installedVersion(): InstalledAppVersion
    fun verify(file: File, manifest: AndroidUpdateManifest): ApkVerificationResult
}

internal class AndroidApkInspector(private val context: Context) : UpdateApkInspector {
    private val packageManager = context.packageManager

    override fun installedVersion(): InstalledAppVersion {
        val info = packageInfo(context.packageName)
        return InstalledAppVersion(
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            versionName = info.versionName.orEmpty(),
            minimumAndroidApi = info.applicationInfo?.minSdkVersion ?: Build.VERSION_CODES.O,
        )
    }

    override fun verify(file: File, manifest: AndroidUpdateManifest): ApkVerificationResult {
        if (!file.isFile || file.length() != manifest.app.asset.size) return invalid("size")
        if (AndroidUpdateRepository.sha256(file) != manifest.app.asset.sha256) return invalid("sha256")
        val archive = archiveInfo(file) ?: return invalid("package_parse")
        if (archive.packageName != UPDATE_APPLICATION_ID) return invalid("application_id")
        if (PackageInfoCompat.getLongVersionCode(archive) != manifest.app.versionCode) return invalid("version_code")
        if (archive.versionName != manifest.app.versionName) return invalid("version_name")
        val minSdk = archive.applicationInfo?.minSdkVersion ?: return invalid("minimum_api")
        if (minSdk != manifest.app.minimumAndroidApi || minSdk > Build.VERSION.SDK_INT) return invalid("minimum_api")
        if (manifest.app.versionCode <= installedVersion().versionCode) return invalid("not_newer")
        val installed = packageInfo(context.packageName)
        if (!signaturesCompatible(installed, archive)) return invalid("signing")
        return ApkVerificationResult.Valid
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File): PackageInfo? = packageManager.getPackageArchiveInfo(
        file.absolutePath,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
    )

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String): PackageInfo = packageManager.getPackageInfo(
        packageName,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
    )

    @Suppress("DEPRECATION")
    private fun signaturesCompatible(installed: PackageInfo, archive: PackageInfo): Boolean {
        val installedCertificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.signingCertificateHistory?.map { it.toByteArray() }.orEmpty()
        } else {
            installed.signatures?.map { it.toByteArray() }.orEmpty()
        }
        val archiveCertificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.signingInfo?.signingCertificateHistory?.map { it.toByteArray() }.orEmpty()
        } else {
            archive.signatures?.map { it.toByteArray() }.orEmpty()
        }
        return installedCertificates.isNotEmpty() &&
            archiveCertificates.any { archiveCertificate ->
                installedCertificates.any { installedCertificate -> archiveCertificate.contentEquals(installedCertificate) }
            }
    }

    private fun invalid(category: String) = ApkVerificationResult.Invalid(category)
}
