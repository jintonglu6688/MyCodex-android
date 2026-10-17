package com.mycodex.mobile.relay

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteCommandSingleFlightTest {
    @Test
    fun concurrentRequestsWithSameKeyShareOneOperation() = runTest {
        val singleFlight = RemoteCommandSingleFlight<String>()
        val release = CompletableDeferred<Unit>()
        var operationCount = 0

        val first = async {
            singleFlight.run("coding:session_a") {
                operationCount += 1
                release.await()
                "result"
            }
        }
        runCurrent()
        val second = async {
            singleFlight.run("coding:session_a") {
                operationCount += 1
                "duplicate"
            }
        }
        runCurrent()

        assertEquals(1, operationCount)
        release.complete(Unit)
        assertEquals("result", first.await())
        assertEquals("result", second.await())
    }
}
