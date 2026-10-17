package com.mycodex.mobile.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDisplayNameTest {
    @Test
    fun configuredDeviceNameTakesPriority() {
        assertEquals(
            "JTL Phone",
            selectDeviceDisplayName("  JTL Phone  ", "Xiaomi", "23116PN5BC"),
        )
    }

    @Test
    fun manufacturerAndModelProvideReadableFallback() {
        assertEquals(
            "Android Xiaomi 23116PN5BC",
            selectDeviceDisplayName(null, "Xiaomi", "23116PN5BC"),
        )
    }

    @Test
    fun manufacturerIsNotDuplicatedWhenModelAlreadyContainsIt() {
        assertEquals(
            "Android Google Pixel 9",
            selectDeviceDisplayName("", "Google", "Google Pixel 9"),
        )
    }

    @Test
    fun emptyHardwareFieldsUseAndroidFallback() {
        assertEquals("Android", selectDeviceDisplayName(" ", " ", null))
    }
}
