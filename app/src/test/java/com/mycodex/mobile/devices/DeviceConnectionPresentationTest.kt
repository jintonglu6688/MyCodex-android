package com.mycodex.mobile.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceConnectionPresentationTest {
    @Test
    fun pairedAndPreviouslyConnectedTargetsStayUncheckedUntilExplicitConnection() {
        val presentation = present(device("b"), current = false)

        assertEquals("非当前设备，已配对，待检查", presentation.summary)
        assertEquals("检查并连接", presentation.actionLabel)
        assertTrue(presentation.canConnect)
        assertFalse(presentation.connected)
        assertNull(presentation.lastCheckText)
    }

    @Test
    fun actualCurrentSecureConnectionAllowsSettingsAndExplicitRecheck() {
        val state = DeviceSelectionState(currentHostId = "a", connected = true)
        val connected = present(device("a"), true, state)
        val other = present(device("b"), false, state)

        assertEquals("当前设备，已配对，已连接", connected.summary)
        assertEquals("重新检查连接", connected.actionLabel)
        assertTrue(connected.canConnect)
        assertTrue(connected.connected)
        assertFalse(other.connected)
        assertTrue(other.canConnect)
    }

    @Test
    fun partialReadFailureKeepsRealConnectedStateAndOffersExplicitRetry() {
        val presentation = present(
            device("a"), true,
            DeviceSelectionState(
                currentHostId = "a", connected = true, targetHostId = "a",
                statusText = "已连接，但待处理审批未能刷新，请重试连接。",
            ),
        )

        assertEquals("当前设备，已配对，已连接", presentation.summary)
        assertTrue(presentation.connected)
        assertTrue(presentation.canConnect)
        assertEquals("重新检查连接", presentation.actionLabel)
    }

    @Test
    fun failedAttemptKeepsReasonTimeAndRetryWithoutInventingOfflineCause() {
        val presentation = present(
            device("b"),
            false,
            DeviceSelectionState(
                currentHostId = "a",
                connected = true,
                checks = mapOf("b" to DeviceConnectionCheck(1_700_000_000_000, false, "连接超时")),
            ),
        )

        assertEquals("重试连接", presentation.actionLabel)
        assertTrue(presentation.summary.contains("不可用，上次连接未成功"))
        val lastCheck = requireNotNull(presentation.lastCheckText)
        assertTrue(lastCheck.contains("连接超时"))
        assertTrue(Regex("最近检查：\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}，").containsMatchIn(lastCheck))
        assertFalse(lastCheck.contains("未运行"))
    }

    @Test
    fun switchingDisablesEveryTargetAndOldCurrentNoLongerClaimsConnected() {
        val state = DeviceSelectionState(
            currentHostId = "a", connected = true, switching = true, targetHostId = "b",
        )
        val original = present(device("a"), true, state)
        val target = present(device("b"), false, state)

        assertFalse(original.canConnect)
        assertFalse(original.connected)
        assertFalse(target.canConnect)
        assertEquals("正在检查并连接", target.actionLabel)
    }

    @Test
    fun failedRestorationReportsOldCurrentAsDisconnectedEvenAfterPastSuccess() {
        val presentation = present(
            device("a"),
            true,
            DeviceSelectionState(
                currentHostId = "a", connected = false,
                checks = mapOf("a" to DeviceConnectionCheck(10L, true)),
            ),
        )

        assertEquals("当前设备，已配对，未连接", presentation.summary)
        assertTrue(presentation.canConnect)
        assertFalse(presentation.connected)
    }


    @Test
    fun returningFocusUsesHostIdAndAccountsForEmptyListAndDeletedRow() {
        val devices = listOf(device("a"), device("b"))

        assertEquals(1, deviceListFocusIndex(devices, "device_row:b"))
        assertEquals(2, deviceListFocusIndex(devices, "device_row:deleted"))
        assertEquals(2, deviceListFocusIndex(devices, "device_add_button"))
        assertEquals(1, deviceListFocusIndex(emptyList(), "device_add_button"))
    }

    private fun present(
        device: ManagedDevice,
        current: Boolean,
        state: DeviceSelectionState = DeviceSelectionState(),
    ) = deviceConnectionPresentation(device, current, state)

    private fun device(hostId: String) = ManagedDevice(
        binding = DeviceBinding(
            relayUrl = "https://relay.example.com", relaySigningPublicKey = "relay-key",
            relaySigningKeyFingerprint = "relay-pin", certificateSha256 = null,
            tenantId = "tenant", hostId = hostId, hostName = "办公电脑 · $hostId",
            deviceId = "phone", hostSigningPublicKey = "host-signing", hostAgreementPublicKey = "host-agreement",
            deviceSigningPublicKey = "device-signing", deviceAgreementPublicKey = "device-agreement",
            hostKeyVersion = 1, deviceKeyVersion = 1, bindingVersion = 1,
        ),
        createdAtEpochMillis = 1L, lastUsedAtEpochMillis = null,
    )
}
