package com.mycodex.mobile.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal interface UpdateInstallAuthorizationLauncher {
    fun open()
}

internal class AndroidInstallAuthorizationLauncher(
    private val context: Context,
) : UpdateInstallAuthorizationLauncher {
    override fun open() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
