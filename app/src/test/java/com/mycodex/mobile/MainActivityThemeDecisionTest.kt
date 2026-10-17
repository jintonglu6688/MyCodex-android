package com.mycodex.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityThemeDecisionTest {
    @Test
    fun effectiveChangeQueuesFocusAndRequestsActivityRecreate() {
        val decision = decideThemeApplication(
            oldDark = false,
            newDark = true,
        )

        assertTrue(decision.queueFocus)
        assertTrue(decision.manuallyRecreate)
    }

    @Test
    fun unchangedEffectiveAppearanceNeverQueuesFocusOrManualRecreate() {
        val decision = decideThemeApplication(
            oldDark = true,
            newDark = true,
        )

        assertFalse(decision.queueFocus)
        assertFalse(decision.manuallyRecreate)
    }
}
