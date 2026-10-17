package com.mycodex.mobile.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidUpdateSourceContractTest {
    @Test
    fun installerHandsVerifiedApkToTheUsersChosenInstaller() {
        val installer = source("app/src/main/java/com/mycodex/mobile/updates/AndroidPackageInstaller.kt")
        val controller = source("app/src/main/java/com/mycodex/mobile/updates/AndroidUpdateController.kt")
        val manifest = source("app/src/main/AndroidManifest.xml")
        val paths = source("app/src/main/res/xml/update_file_paths.xml")
        assertTrue(installer.contains("FileProvider.getUriForFile"))
        assertTrue(installer.contains("Intent(Intent.ACTION_VIEW)"))
        assertTrue(installer.contains("application/vnd.android.package-archive"))
        assertTrue(installer.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertFalse(installer.contains("intent.component"))
        assertFalse(installer.contains("intent.`package`"))
        assertFalse(installer.contains("PackageInstaller.Session"))
        assertFalse(installer.contains("PendingIntent.FLAG_MUTABLE"))
        assertFalse(manifest.contains("UpdateInstallActivity"))
        assertFalse(manifest.contains("android.content.pm.action.CONFIRM_INSTALL"))
        assertFalse(manifest.contains("android.content.pm.action.CONFIRM_PERMISSIONS"))
        assertTrue(manifest.contains("com.mycodex.mobile.updates.VerifiedUpdateFileProvider"))
        val provider = manifest.substringAfter("com.mycodex.mobile.updates.VerifiedUpdateFileProvider").substringBefore("</provider>")
        assertTrue(provider.contains("android:authorities=\"${'$'}{applicationId}.updates.files\""))
        assertTrue(provider.contains("android:exported=\"false\""))
        assertTrue(provider.contains("android:grantUriPermissions=\"true\""))
        assertTrue(provider.contains("@xml/update_file_paths"))
        assertTrue(paths.contains("<cache-path"))
        assertTrue(paths.contains("path=\"updates/\""))
        assertFalse(paths.contains("path=\".\""))
    }

    @Test
    fun updateFlowHasNoSessionInstallerOrNetworkTypeBranch() {
        val updateSources = File(projectRoot(), "app/src/main/java/com/mycodex/mobile/updates")
            .walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(updateSources.contains("packageInstaller"))
        assertFalse(updateSources.contains("UpdateInstallActivity"))
        assertFalse(updateSources.contains("ConnectivityManager"))
        assertFalse(updateSources.contains("WorkManager"))
        assertTrue(updateSources.contains("followSslRedirects(false)"))
        assertTrue(updateSources.contains("requireAllowedResponseChain(response)"))
        assertTrue(updateSources.contains("current.priorResponse"))
    }

    @Test
    fun profileEntryIsBetweenLocalSettingsAndDiagnostics() {
        val app = source("app/src/main/java/com/mycodex/mobile/MyCodexApp.kt")
        val local = app.indexOf("testTag = \"profile_local_settings_row\"")
        val update = app.indexOf("testTag = \"profile_check_updates_row\"")
        val diagnostics = app.indexOf("testTag = \"profile_share_logs_row\"")
        assertTrue(local in 0 until update && update in 0 until diagnostics)
        assertTrue(app.contains("UpdateToastHost("))
        assertTrue(app.contains("UpdatePromptDialog("))
        assertTrue(app.contains("updatePromptVisible && updatePromptAllowed"))
        assertTrue(app.contains("setPromptAllowed(updatePromptAllowed)"))
        assertTrue(app.contains("onUpdateNow = updateController::beginUpdate"))
        assertFalse(app.contains("AppUpdatePage("))
        assertFalse(app.contains("profile_update_status"))
        assertFalse(app.contains("Text(\"安装更新\")"))
        val prompt = source("app/src/main/java/com/mycodex/mobile/updates/UpdatePromptDialog.kt")
        assertTrue(prompt.contains("displayReleaseNotes(release.releaseNotes)"))
        assertTrue(prompt.contains("Text(\"稍后\")"))
        assertTrue(prompt.contains("Text(\"立即更新\")"))
        assertTrue(prompt.contains("verticalScroll"))
    }

    private fun source(relative: String): String = File(projectRoot(), relative).readText()

    private fun projectRoot(): File {
        var current = File(System.getProperty("user.dir") ?: error("user.dir is unavailable")).canonicalFile
        while (!File(current, "settings.gradle.kts").isFile) {
            current = current.parentFile ?: error("Project root was not found")
        }
        return current
    }
}
