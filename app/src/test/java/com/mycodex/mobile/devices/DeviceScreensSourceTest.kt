package com.mycodex.mobile.devices

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceScreensSourceTest {
    private val screenSource = readProjectFile(
        "src/main/java/com/mycodex/mobile/devices/DeviceScreens.kt",
    )
    private val addDeviceSource = screenSource
        .substringAfter("fun AddDevicePage(")
        .substringBefore("@Composable\nfun DeviceDetailPage(")

    @Test
    fun oldHostPortTlsTenantInviteTokenFieldsAreAbsent() {
        listOf(
            "var relayHost by remember",
            "var relayPort by remember",
            "var useTls by remember",
            "var tenantId by remember",
            "var hostId by remember",
            "var inviteId by remember",
            "var pairingToken by remember",
            "DeviceTextField(\"服务地址\"",
            "DeviceTextField(\"租户 ID\"",
            "DeviceTextField(\"主机 ID\"",
            "DeviceTextField(\"邀请 ID\"",
            "label = \"配对令牌\"",
        ).forEach { legacyField ->
            assertFalse("Legacy field remains: $legacyField", addDeviceSource.contains(legacyField))
        }
    }

    @Test
    fun pairingPageHasNoAndroid12GateAndKeepsSecurityErrorsVisible() {
        assertFalse(addDeviceSource.contains("secureRemoteSupported"))
        assertFalse(addDeviceSource.contains("安全远程配对需要 Android 12 或更高版本"))
        assertFalse(addDeviceSource.contains("pairing_unsupported_status"))
        assertTrue(addDeviceSource.contains("pairing_import_clipboard"))
        assertTrue(addDeviceSource.contains("pairing_uri_input"))
        assertTrue(addDeviceSource.contains("pairing_submit"))
        assertTrue(screenSource.contains("DeviceIdentityException.SECURITY_UNAVAILABLE"))
    }

    @Test
    fun clipboardImportAndCompleteUriFallbackUseSameParser() {
        assertEquals(1, countOccurrences(addDeviceSource, "importAndSubmit(value)"))
        assertTrue(addDeviceSource.contains("submit(text, clearClipboardOnImport = true)"))
        assertTrue(addDeviceSource.contains("submit(holder.pairingUri, clearClipboardOnImport = false)"))
        assertTrue(addDeviceSource.contains("holder.launch"))
        assertFalse(addDeviceSource.contains("PairingPayloadParser.parseUri("))
    }

    @Test
    fun waitingStateShowsTargetAndDigitReadableShortCode() {
        assertTrue(addDeviceSource.contains("pairing_target_summary"))
        assertTrue(addDeviceSource.contains("pairingState.target"))
        assertTrue(addDeviceSource.contains("pairing_short_code"))
        assertTrue(addDeviceSource.contains("digitReadableShortCode(pairingState.shortCode)"))
    }

    @Test
    fun pollTicksDoNotCreateRepeatedLiveAnnouncements() {
        val controllerSource = readProjectFile(
            "src/main/java/com/mycodex/mobile/devices/PairingController.kt",
        )

        assertEquals(
            1,
            countOccurrences(controllerSource, "mutableState.value = PairingUiState.Waiting("),
        )
        val pendingBranch = controllerSource
            .substringAfter("STATUS_PENDING -> {")
            .substringBefore("STATUS_APPROVED ->")
        assertFalse(pendingBranch.contains("mutableState.value"))
    }

    @Test
    fun cancelIsReachableDuringWaiting() {
        val cancelControl = addDeviceSource
            .substringBefore(".testTag(\"pairing_cancel\")")
            .substringAfterLast("OutlinedButton(")

        assertTrue(cancelControl.contains("enabled = true"))
    }

    @Test
    fun stageThreeStatesUseVisibleLoadingPoliteErrorsAndIndentedDividers() {
        assertTrue(screenSource.contains("DeviceLoadingPanel("))
        assertTrue(screenSource.contains("CircularProgressIndicator(modifier = Modifier.clearAndSetSemantics {})"))
        assertTrue(screenSource.contains("MaterialTheme.colorScheme.errorContainer"))
        assertTrue(screenSource.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(screenSource.contains("Modifier.padding(start = 16.dp)"))
        assertTrue(screenSource.contains("testTag = \"device_delete_button\""))
    }

    @Test
    fun detailDelegatesExplicitConnectionAndDoesNotOpenTemporaryProbeConnections() {
        val detail = screenSource.substringAfter("fun DeviceDetailPage(")
            .substringBefore("private fun DeviceStatusPanel(")

        assertFalse(detail.contains("temporaryRelayConnection"))
        assertFalse(detail.contains("connectForForegroundEvents"))
        assertFalse(detail.contains("onConnectionConfirmed"))
        assertTrue(detail.contains("testTag = \"device_connect_button\""))
        assertTrue(detail.contains("onClick = onCancelSwitch"))
        assertTrue(detail.contains("enabled = !selectionState.switching"))
        assertFalse(detail.contains("租户 ID"))
        assertFalse(detail.contains("主机 ID"))
    }

    private fun countOccurrences(value: String, search: String): Int {
        return Regex(Regex.escape(search)).findAll(value).count()
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
        )
        return candidates.firstOrNull { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?: error("Unable to locate $relativePath")
    }
}
