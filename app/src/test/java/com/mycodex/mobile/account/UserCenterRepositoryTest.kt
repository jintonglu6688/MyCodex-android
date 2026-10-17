package com.mycodex.mobile.account

import com.mycodex.mobile.relay.RemoteCommandGateway
import com.mycodex.mobile.relay.RemoteCommandPayload
import com.mycodex.mobile.relay.RemoteEventPayload
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserCenterRepositoryTest {
    @Test
    fun loadWithoutDeviceReportsNoDevice() = runTest {
        assertEquals("no_device", UserCenterRepository(null).load().errorCode)
    }

    @Test
    fun loadRequestsHostAccountAndParsesQuota() = runTest {
        val gateway = FakeGateway(
            RemoteEventPayload(
                requestId = "request-account",
                operationId = null,
                eventType = "host.account.result",
                payload = JSONObject()
                    .put("available", true)
                    .put("email", "user@example.com")
                    .put("planType", "plus")
                    .put("resetCreditsAvailableCount", 2)
                    .put(
                        "resetCredits",
                        JSONArray().put(
                            JSONObject()
                                .put("status", "available")
                                .put("expiresAtUtc", "2026-08-10T10:30:00Z"),
                        ),
                    )
                    .put(
                        "buckets",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "codex")
                                .put("name", "Codex")
                                .put(
                                    "primary",
                                        JSONObject()
                                            .put("usedPercent", 35)
                                            .put("durationMinutes", 300)
                                            .put("resetsAtUtc", "2026-08-04T10:30:00Z"),
                                )
                                .put("secondary", JSONObject.NULL)
                                .put("credits", JSONObject.NULL),
                        ),
                    )
                    .put(
                        "tokenUsage",
                        JSONObject()
                            .put("lifetimeTokens", 3_993_200_000L)
                            .put("peakDailyTokens", 2_059_000_000L)
                            .put("currentStreakDays", 66)
                            .put("longestStreakDays", 66)
                            .put(
                                "dailyBuckets",
                                JSONArray().put(
                                    JSONObject()
                                        .put("startDate", "2026-08-03")
                                        .put("tokens", 1_204_000_000L),
                                ),
                            ),
                    ),
            ),
        )

        val result = UserCenterRepository(gateway).load()

        assertEquals("host.account.get", gateway.lastCommand?.commandType)
        assertEquals("user@example.com", result.snapshot?.email)
        assertEquals(2, result.snapshot?.resetCreditsAvailableCount)
        assertEquals(35.0, result.snapshot?.buckets?.single()?.primary?.usedPercent ?: -1.0, 0.01)
        assertTrue(result.snapshot?.available == true)
        assertEquals(
            listOf(
                "邮箱：user@example.com",
                "方案：plus",
                "5小时剩余65%，8月4日18点30分，4小时30分钟后重置",
                "重置次数1：8月10日18点30分，6天4小时30分钟后过期",
                "累计 token：39.93亿",
                "单日峰值 token：20.59亿",
                "连续使用：当前 66 天，最长 66 天",
                "8月3日：12.04亿",
                "重置额度（剩余2次）",
            ),
            result.snapshot?.detailLines(
                now = Instant.parse("2026-08-04T06:00:00Z"),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        )
    }

    private class FakeGateway(
        private val response: RemoteEventPayload,
    ) : RemoteCommandGateway {
        var lastCommand: RemoteCommandPayload? = null

        override suspend fun sendCommand(command: RemoteCommandPayload): RemoteEventPayload {
            lastCommand = command
            return response.copy(requestId = command.requestId)
        }
    }
}
