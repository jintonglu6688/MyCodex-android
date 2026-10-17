package com.mycodex.mobile.updates

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VerifiedApkInstallIntentTest {
    @Test
    fun verifiedApkIntentAllowsTheUsersChosenInstallerReadOnlyAccess() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val apk = File(context.cacheDir, "updates/intent-test/test.apk").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates.files", apk)

        val intent = verifiedApkInstallIntent(uri)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals("content", intent.data?.scheme)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertNull(intent.component)
        assertNull(intent.`package`)
    }

    @Test
    fun updateProviderRejectsFilesOutsideTheUpdateCache() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val outside = File(context.cacheDir, "not-an-update.apk").apply { writeBytes(byteArrayOf(1)) }

        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, "${context.packageName}.updates.files", outside)
        }
    }
}
