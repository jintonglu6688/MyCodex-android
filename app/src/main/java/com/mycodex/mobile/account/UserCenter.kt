package com.mycodex.mobile.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mycodex.mobile.relay.RemoteCommandGateway
import com.mycodex.mobile.relay.RemoteCommandPayload
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Comparator
import java.util.Locale
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class UserCenterWindow(
    val usedPercent: Double,
    val durationMinutes: Int?,
    val resetsAtUtc: String?,
)

data class UserCenterBucket(
    val id: String?,
    val name: String?,
    val primary: UserCenterWindow?,
    val secondary: UserCenterWindow?,
    val credits: UserCenterCredits?,
)

data class UserCenterCredits(
    val hasCredits: Boolean,
    val unlimited: Boolean,
    val balance: String?,
)

data class UserCenterResetCredit(
    val status: String?,
    val expiresAtUtc: String?,
)

data class UserCenterTokenUsageDailyBucket(
    val startDate: String?,
    val tokens: Long,
)

data class UserCenterTokenUsage(
    val lifetimeTokens: Long?,
    val peakDailyTokens: Long?,
    val currentStreakDays: Long?,
    val longestStreakDays: Long?,
    val dailyBuckets: List<UserCenterTokenUsageDailyBucket>,
)

data class UserCenterSnapshot(
    val available: Boolean,
    val email: String?,
    val planType: String?,
    val resetCreditsAvailableCount: Int?,
    val resetCredits: List<UserCenterResetCredit>,
    val buckets: List<UserCenterBucket>,
    val tokenUsage: UserCenterTokenUsage?,
)

data class UserCenterLoadResult(
    val snapshot: UserCenterSnapshot? = null,
    val errorCode: String? = null,
)

class UserCenterRepository(
    private val gateway: RemoteCommandGateway?,
) {
    suspend fun load(): UserCenterLoadResult {
        val commandGateway = gateway ?: return UserCenterLoadResult(errorCode = "no_device")
        val event = commandGateway.sendCommand(
            RemoteCommandPayload(commandType = "host.account.get"),
        ) ?: return UserCenterLoadResult(errorCode = "account_not_loaded")
        event.errorCode?.let { return UserCenterLoadResult(errorCode = it) }
        if (event.eventType != "host.account.result") {
            return UserCenterLoadResult(errorCode = "account_not_loaded")
        }

        return try {
            UserCenterLoadResult(snapshot = event.payload.toUserCenterSnapshot())
        } catch (_: JSONException) {
            UserCenterLoadResult(errorCode = "account_not_loaded")
        }
    }
}

@Composable
fun UserCenterPage(
    deviceId: String?,
    repository: UserCenterRepository,
    contentPadding: PaddingValues,
) {
    var reloadToken by rememberSaveable(deviceId) { mutableIntStateOf(0) }
    var loading by remember(deviceId) { mutableStateOf(false) }
    var result by remember(deviceId) { mutableStateOf(UserCenterLoadResult()) }

    LaunchedEffect(deviceId, repository, reloadToken) {
        loading = true
        result = repository.load()
        loading = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag("user_center_page"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = when {
                    loading -> "正在刷新..."
                    result.errorCode != null -> describeUserCenterError(result.errorCode)
                    else -> "刷新完成"
                },
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("user_center_status"),
                color = if (result.errorCode == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        result.snapshot?.let { snapshot ->
            val lines = snapshot.detailLines()
            items(lines.size) { index ->
                Text(
                    text = lines[index],
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_center_detail_$index"),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            Button(
                onClick = { reloadToken += 1 },
                enabled = !loading,
                modifier = Modifier.testTag("user_center_refresh"),
            ) {
                Text("刷新")
            }
        }
    }
}

internal fun UserCenterSnapshot.detailLines(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<String> {
    val lines = mutableListOf(
        "邮箱：${email?.takeIf(String::isNotBlank) ?: "(未提供)"}",
        "方案：${planType?.takeIf(String::isNotBlank) ?: "(未知)"}",
    )
    var hasQuota = false
    if (available) {
        buckets.sortedWith(userCenterBucketComparator).forEach { bucket ->
            val prefix = if (bucket.searchText().isSparkBucket()) "Spark：" else ""
            bucket.primary?.let {
                lines += "$prefix${it.windowText(primary = true, now, zoneId)}"
                hasQuota = true
            }
            bucket.secondary?.let {
                lines += "$prefix${it.windowText(primary = false, now, zoneId)}"
                hasQuota = true
            }
            if (bucket.primary == null && bucket.secondary == null && bucket.credits != null) {
                lines += "积分信息：${bucket.credits.displayText()}。"
                hasQuota = true
            }
        }
        if (!hasQuota) lines += "(暂无额度信息)"
        resetCredits
            .filter { it.status.equals("available", ignoreCase = true) && it.expiresAtUtc != null }
            .forEachIndexed { index, credit ->
                lines += "重置次数${index + 1}：${formatResetTime(credit.expiresAtUtc, now, zoneId)}过期"
            }
        tokenUsage?.appendDetailLines(lines)
        lines += resetCreditsAvailableCount?.let { "重置额度（剩余${it}次）" } ?: "重置额度（不可用）"
    } else {
        lines += "当前认证为 API 配置。用户中心仅适用于 ChatGPT 账号。"
    }
    return lines
}

private fun UserCenterTokenUsage.appendDetailLines(lines: MutableList<String>) {
    lifetimeTokens?.let { lines += "累计 token：${formatTokenCount(it)}" }
    peakDailyTokens?.let { lines += "单日峰值 token：${formatTokenCount(it)}" }
    if (currentStreakDays != null || longestStreakDays != null) {
        lines += "连续使用：当前 ${currentStreakDays ?: "(未知)"} 天，最长 ${longestStreakDays ?: "(未知)"} 天"
    }
    dailyBuckets
        .filter { !it.startDate.isNullOrBlank() }
        .sortedByDescending { it.startDate }
        .take(7)
        .forEach { lines += "${formatUsageDate(it.startDate)}：${formatTokenCount(it.tokens)}" }
}

private fun formatTokenCount(value: Long): String = when {
    value >= 100_000_000L -> formatScaledTokenCount(value, 100_000_000L, "亿")
    value >= 10_000L -> formatScaledTokenCount(value, 10_000L, "万")
    else -> NumberFormat.getIntegerInstance(Locale.CHINA).format(value)
}

private fun formatScaledTokenCount(value: Long, divisor: Long, unit: String): String {
    val scaled = BigDecimal.valueOf(value)
        .divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    return "$scaled$unit"
}

private fun formatUsageDate(value: String?): String = value?.takeIf(String::isNotBlank)?.let { text ->
    runCatching { LocalDate.parse(text.trim()) }
        .map { "${it.monthValue}月${it.dayOfMonth}日" }
        .getOrDefault(text)
} ?: "未知时间"

private val userCenterBucketComparator = Comparator<UserCenterBucket> { left, right ->
    compareValuesBy(
        left,
        right,
        { it.sortRank() },
        { it.displayLabel().lowercase(Locale.ROOT) },
        { it.id.orEmpty().lowercase(Locale.ROOT) },
    )
}

private fun UserCenterBucket.sortRank(): Int {
    val text = searchText()
    return when {
        text.isSparkBucket() -> 1
        listOf("codex", "all model", "all-model", "all_model", "全模型").any(text::contains) -> 0
        listOf("fast", "quick", "5.3", "快速").any(text::contains) -> 2
        listOf("general", "default", "common", "shared", "通用", "gpt-5", "gpt 5").any(text::contains) -> 3
        else -> 4
    }
}

private fun UserCenterBucket.searchText(): String = "${name.orEmpty()} ${id.orEmpty()}".lowercase(Locale.ROOT)

private fun String.isSparkBucket(): Boolean = contains("spark") || contains("5.3")

private fun UserCenterBucket.displayLabel(): String = name?.trim().takeUnless { it.isNullOrEmpty() }
    ?: id?.trim()?.substringAfterLast(':')?.replace('_', ' ').orEmpty()

private fun UserCenterWindow.windowText(primary: Boolean, now: Instant, zoneId: ZoneId): String {
    val remaining = (100.0 - usedPercent).coerceIn(0.0, 100.0)
    return "${windowLabel(durationMinutes, primary)}剩余${formatPercent(remaining)}%，" +
        "${formatResetTime(resetsAtUtc, now, zoneId)}重置"
}

private fun windowLabel(minutes: Int?, primary: Boolean): String = when {
    minutes == null || minutes <= 0 -> if (primary) "本周期" else "附加周期"
    minutes == 60 -> "1小时"
    minutes == 300 -> "5小时"
    minutes == 1_440 -> "每天"
    minutes == 10_080 -> "每周"
    minutes % 1_440 == 0 -> "${minutes / 1_440}天"
    minutes % 60 == 0 -> "${minutes / 60}小时"
    else -> "${minutes}分钟"
}

private fun formatPercent(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
    .trimEnd('0')
    .trimEnd('.')

private fun formatResetTime(value: String?, now: Instant, zoneId: ZoneId): String {
    val instant = value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "未知时间"
    val local = instant.atZone(zoneId)
    val timeText = if (local.minute == 0) {
        "${local.monthValue}月${local.dayOfMonth}日${local.hour}点"
    } else {
        "${local.monthValue}月${local.dayOfMonth}日${local.hour}点${local.minute}分"
    }
    val remainingMillis = instant.toEpochMilli() - now.toEpochMilli()
    if (remainingMillis <= 0) return "$timeText，已过期"
    var totalMinutes = ceil(remainingMillis / 60_000.0).toInt()
    val days = totalMinutes / (24 * 60)
    totalMinutes %= 24 * 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val countdown = buildString {
        if (days > 0) append("${days}天")
        if (hours > 0) append("${hours}小时")
        if (minutes > 0 || isEmpty()) append("${minutes}分钟")
    }
    return "$timeText，${countdown}后"
}

private fun UserCenterCredits.displayText(): String = when {
    unlimited -> "无限制"
    !hasCredits -> "不可用"
    balance.isNullOrBlank() -> "可用"
    else -> balance
}

private fun JSONObject.toUserCenterSnapshot(): UserCenterSnapshot {
    return UserCenterSnapshot(
        available = getBoolean("available"),
        email = nullableString("email"),
        planType = nullableString("planType"),
        resetCreditsAvailableCount = nullableInt("resetCreditsAvailableCount"),
        resetCredits = nullableArray("resetCredits")?.objects()?.map { credit ->
            UserCenterResetCredit(
                status = credit.nullableString("status"),
                expiresAtUtc = credit.nullableString("expiresAtUtc"),
            )
        }.orEmpty(),
        buckets = getJSONArray("buckets").objects().map { bucket ->
            UserCenterBucket(
                id = bucket.nullableString("id"),
                name = bucket.nullableString("name"),
                primary = bucket.nullableObject("primary")?.toWindow(),
                secondary = bucket.nullableObject("secondary")?.toWindow(),
                credits = bucket.nullableObject("credits")?.toCredits(),
            )
        },
        tokenUsage = nullableObject("tokenUsage")?.toTokenUsage(),
    )
}

private fun JSONObject.toTokenUsage(): UserCenterTokenUsage = UserCenterTokenUsage(
    lifetimeTokens = nullableLong("lifetimeTokens"),
    peakDailyTokens = nullableLong("peakDailyTokens"),
    currentStreakDays = nullableLong("currentStreakDays"),
    longestStreakDays = nullableLong("longestStreakDays"),
    dailyBuckets = getJSONArray("dailyBuckets").objects().map { bucket ->
        UserCenterTokenUsageDailyBucket(
            startDate = bucket.nullableString("startDate"),
            tokens = bucket.getLong("tokens"),
        )
    },
)

private fun JSONObject.toWindow(): UserCenterWindow {
    return UserCenterWindow(
        usedPercent = getDouble("usedPercent"),
        durationMinutes = nullableInt("durationMinutes"),
        resetsAtUtc = nullableString("resetsAtUtc"),
    )
}

private fun JSONObject.toCredits(): UserCenterCredits = UserCenterCredits(
    hasCredits = getBoolean("hasCredits"),
    unlimited = getBoolean("unlimited"),
    balance = nullableString("balance"),
)

private fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.nullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONObject.nullableLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

private fun JSONObject.nullableObject(key: String): JSONObject? =
    if (!has(key) || isNull(key)) null else getJSONObject(key)

private fun JSONObject.nullableArray(key: String): JSONArray? =
    if (!has(key) || isNull(key)) null else getJSONArray(key)

private fun JSONArray.objects(): List<JSONObject> =
    List(length()) { index -> getJSONObject(index) }

private fun describeUserCenterError(code: String?): String = when (code) {
    "no_device" -> "请先在“设备管理”中选择 Windows 设备。"
    "unsupported_command" -> "Windows 端版本过低，请先更新 Windows 版 MyCodex。"
    else -> "无法读取额度，请检查远程连接后重试。"
}
