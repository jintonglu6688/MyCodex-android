package com.mycodex.mobile.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

class AndroidUpdateReleaseToolsContractTest {
    @Test
    fun releaseVersionIsCentralizedAndNeverReturnsBelowBootstrap() {
        val properties = Properties().apply {
            File(root(), "version.properties").inputStream().use(::load)
        }
        assertTrue(properties.getProperty("VERSION_CODE").toInt() >= 2)
        assertTrue(properties.getProperty("VERSION_NAME").isNotBlank())
        val gradle = File(root(), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionProperties.getProperty(\"VERSION_CODE\")"))
        assertTrue(gradle.contains("versionProperties.getProperty(\"VERSION_NAME\")"))
        assertTrue(gradle.contains("updates-android-stable/attach_files/android-stable.json/download"))
        assertFalse(gradle.contains("myCodexUpdateChannel"))
        assertFalse(gradle.contains("updates-android-test"))
        assertFalse(gradle.contains("versionCode = 1"))
    }

    @Test
    fun publisherHasPrepareOnlyAndImmutablePointerLastGuards() {
        val script = File(root(), "tools/gitcode/Publish-GitCodeAndroidRelease.ps1").readText()
        val prepareReturn = script.indexOf("if (\$PrepareOnly)")
        val firstPublication = script.indexOf("New-Release \$releaseTag")
        val pointerPublication = script.indexOf("New-Release \$pointerTag")
        assertTrue(prepareReturn in 0 until firstPublication)
        assertTrue(firstPublication in 0 until pointerPublication)
        assertTrue(script.contains("assembleRelease"))
        assertTrue(script.contains("updates-android-stable"))
        assertFalse(script.contains("ReusePublishedApk"))
        assertFalse(script.contains("updates-android-test"))
        assertFalse(script.contains("[ValidateSet(\"stable\", \"test\")]"))
        assertTrue(script.contains("if (\$releaseExists)"))
        assertTrue(script.contains("Confirm-PublicAsset \$publicApk \$versionedApk"))
        assertTrue(script.contains("has a gitcode remote"))
        assertTrue(script.contains("Unable to request GitCode asset upload authorization"))
        assertTrue(script.contains("VERSION_NAME is not safe for Android release naming"))
        assertTrue(script.contains("^[A-Za-z0-9._-]+$"))
        assertFalse(script.contains("throw \$_"))
        assertFalse(script.contains("E:\\MyCodex\\"))
        assertFalse(script.contains("E:\\MyCodex-relay\\"))
    }

    @Test
    fun manifestGeneratorRequiresSignatureAndCrossChecksApkVersion() {
        val script = File(root(), "tools/update/New-MyCodexAndroidUpdateManifest.ps1").readText()
        assertTrue(script.contains("apksigner verify --print-certs"))
        assertTrue(script.contains("APK version does not match version.properties"))
        assertTrue(script.contains("Get-FileHash"))
        assertTrue(script.contains("UTF8Encoding]::new(\$false)"))
        assertTrue(script.contains("APK stable manifest URL is invalid"))
        assertFalse(script.contains("update_channel"))
        assertFalse(script.contains("[ValidateSet(\"stable\", \"test\")]"))
        assertTrue(script.contains("VERSION_NAME is not safe for Android release naming"))
        assertTrue(script.contains("^[A-Za-z0-9._-]+$"))
    }

    private fun root(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir is unavailable")).canonicalFile
        while (!File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile ?: error("Project root was not found")
        }
        return directory
    }
}
