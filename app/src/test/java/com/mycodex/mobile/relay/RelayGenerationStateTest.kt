package com.mycodex.mobile.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RelayGenerationStateTest {
    @Test
    fun invalidationCleanupCompletesBeforeTheNextGenerationCanActivate() {
        val state = RelayGenerationState()
        state.activate(1) {}
        val cleanupStarted = CountDownLatch(1)
        val releaseCleanup = CountDownLatch(1)
        val activationAttempted = CountDownLatch(1)
        var cleanupComplete = false
        var activatedBeforeCleanup = false

        val invalidator = thread {
            state.invalidateIfCurrent(1) {
                cleanupStarted.countDown()
                assertTrue(releaseCleanup.await(1, TimeUnit.SECONDS))
                cleanupComplete = true
            }
        }
        assertTrue(cleanupStarted.await(1, TimeUnit.SECONDS))
        val activator = thread {
            activationAttempted.countDown()
            state.activate(2) { activatedBeforeCleanup = !cleanupComplete }
        }
        assertTrue(activationAttempted.await(1, TimeUnit.SECONDS))
        releaseCleanup.countDown()
        invalidator.join(1_000)
        activator.join(1_000)

        assertFalse(invalidator.isAlive)
        assertFalse(activator.isAlive)
        assertFalse(activatedBeforeCleanup)
        assertEquals(2L, state.current())
    }
}
