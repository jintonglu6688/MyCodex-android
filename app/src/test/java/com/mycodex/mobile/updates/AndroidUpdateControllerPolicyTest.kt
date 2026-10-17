package com.mycodex.mobile.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUpdateControllerPolicyTest {
    @Test
    fun automaticCheckIsThrottledForTwentyFourHoursAfterEveryAttempt() {
        val start = 1_000_000L
        assertTrue(automaticCheckDue(start, null))
        assertFalse(automaticCheckDue(start + AndroidUpdateController.AUTOMATIC_INTERVAL_MS - 1, start))
        assertTrue(automaticCheckDue(start + AndroidUpdateController.AUTOMATIC_INTERVAL_MS, start))
    }

    @Test
    fun clockRollbackDoesNotCrash() {
        assertTrue(automaticCheckDue(100, 200))
    }
}
