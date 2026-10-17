package com.mycodex.mobile.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal interface UpdatePackageInstaller {
    fun canRequestPackageInstalls(): Boolean
    fun install(file: File)
}

internal class AndroidPackageInstaller(
    private val context: Context,
) : UpdatePackageInstaller {
    override fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun install(file: File) {
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.updates.files", file)
        context.startActivity(
            verifiedApkInstallIntent(contentUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

internal fun verifiedApkInstallIntent(contentUri: Uri): Intent =
    Intent(Intent.ACTION_VIEW)
        .setDataAndType(contentUri, APK_MIME_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
