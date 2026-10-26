package com.mycodex.mobile.settings

data class WindowsSettingsScope(
    val hostName: String,
    val runtimeSource: String,
    val runtimeDisplayName: String,
    val runtimeProfileId: String?,
    val authenticationSourceKind: String?,
    val authenticationDisplayName: String?,
)

data class WindowsSettingsDefaults(
    val modelId: String,
    val reasoningEffort: String?,
    val approvalMode: WindowsApprovalMode,
)

data class WindowsSettingsModelOption(
    val modelId: String,
    val displayName: String,
    val defaultReasoningEffort: String?,
    val reasoningEfforts: List<String>,
)

data class WindowsPermissionOption(
    val id: String,
    val displayName: String,
)

data class WindowsCustomApprovalSettings(
    val availablePermissions: List<WindowsPermissionOption>,
    val enabledPermissions: List<String>,
    val commandPrefixes: List<String>,
)

data class WindowsCustomApprovalUpdate(
    val enabledPermissions: List<String>,
    val commandPrefixes: List<String>,
)

data class WindowsOfficialSettings(
    val modelReasoningSummary: String?,
    val modelVerbosity: String?,
    val serviceTier: String?,
    val webSearchMode: String?,
    val webSearchContextSize: String?,
    val viewImageToolEnabled: Boolean?,
    val toolOutputTokenLimit: Long?,
    val projectDocMaxBytes: Long?,
)

data class WindowsSettingsSnapshot(
    val scope: WindowsSettingsScope,
    val defaults: WindowsSettingsDefaults,
    val models: List<WindowsSettingsModelOption>,
    val customApproval: WindowsCustomApprovalSettings,
    val official: WindowsOfficialSettings?,
)

data class WindowsSettingsUpdate(
    val defaults: WindowsSettingsDefaults? = null,
    val customApproval: WindowsCustomApprovalUpdate? = null,
    val official: WindowsOfficialSettings? = null,
)

enum class WindowsApprovalMode(val wireValue: String) {
    ReadOnly("read-only"),
    Auto("auto"),
    Custom("custom"),
    FullAccess("full-access");

    companion object {
        fun fromWire(value: String?): WindowsApprovalMode? {
            return entries.firstOrNull { it.wireValue == value?.trim()?.lowercase() }
        }
    }
}

enum class WindowsSettingsLoadState {
    Loaded,
    Unavailable,
    Error,
}

data class WindowsSettingsLoadResult(
    val state: WindowsSettingsLoadState,
    val settings: WindowsSettingsSnapshot? = null,
    val errorCode: String? = null,
)

enum class WindowsSettingsUpdateState {
    Completed,
    Unavailable,
    Error,
}

data class WindowsSettingsUpdateResult(
    val state: WindowsSettingsUpdateState,
    val updated: Boolean = false,
    val settings: WindowsSettingsSnapshot? = null,
    val errorCode: String? = null,
)

data class WindowsSettingsDraft(
    val defaults: WindowsSettingsDefaults,
    val customApproval: WindowsCustomApprovalUpdate,
    val official: WindowsOfficialSettings?,
) {
    fun selectModel(
        modelId: String,
        models: List<WindowsSettingsModelOption>,
    ): WindowsSettingsDraft {
        val model = models.firstOrNull { it.modelId == modelId } ?: return this
        val effort = defaults.reasoningEffort.takeIf { it in model.reasoningEfforts }
            ?: model.defaultReasoningEffort.takeIf { it in model.reasoningEfforts }
            ?: model.reasoningEfforts.firstOrNull()
        return copy(
            defaults = defaults.copy(
                modelId = model.modelId,
                reasoningEffort = effort,
            ),
        )
    }

    fun withAddedPrefix(prefix: String): WindowsSettingsDraft {
        return copy(
            customApproval = customApproval.copy(
                commandPrefixes = normalizeWindowsCommandPrefixes(
                    customApproval.commandPrefixes + prefix,
                ),
            ),
        )
    }

    fun withReplacedPrefix(index: Int, prefix: String): WindowsSettingsDraft {
        if (index !in customApproval.commandPrefixes.indices) {
            return this
        }
        val values = customApproval.commandPrefixes.toMutableList()
        values[index] = prefix
        return copy(
            customApproval = customApproval.copy(
                commandPrefixes = normalizeWindowsCommandPrefixes(values),
            ),
        )
    }

    fun withDeletedPrefix(index: Int): WindowsSettingsDraft {
        if (index !in customApproval.commandPrefixes.indices) {
            return this
        }
        return copy(
            customApproval = customApproval.copy(
                commandPrefixes = customApproval.commandPrefixes.filterIndexed { itemIndex, _ ->
                    itemIndex != index
                },
            ),
        )
    }

    companion object {
        fun from(snapshot: WindowsSettingsSnapshot): WindowsSettingsDraft {
            return WindowsSettingsDraft(
                defaults = snapshot.defaults,
                customApproval = WindowsCustomApprovalUpdate(
                    enabledPermissions = snapshot.customApproval.enabledPermissions,
                    commandPrefixes = normalizeWindowsCommandPrefixes(
                        snapshot.customApproval.commandPrefixes,
                    ),
                ),
                official = snapshot.official,
            )
        }
    }
}

object WindowsOfficialChoices {
    val reasoningSummaries = listOf(null, "auto", "concise", "detailed", "none")
    val verbosities = listOf(null, "low", "medium", "high")
    val webSearchModes = listOf(null, "disabled", "cached", "indexed", "live")
    val searchContextSizes = listOf(null, "low", "medium", "high")
    val viewImageValues = listOf<Boolean?>(null, true, false)
    val toolOutputLimits = listOf<Long?>(null, 8192L, 16384L, 32768L)
    val projectDocLimits = listOf<Long?>(null, 65536L, 131072L, 262144L)

}

internal fun displayOfficialReasoningSummary(value: String?): String {
    return when (value) {
        null -> "跟随默认"
        "auto" -> "自动"
        "concise" -> "简短"
        "detailed" -> "详细"
        "none" -> "无"
        else -> value
    }
}

internal fun displayOfficialLevel(value: String?): String {
    return when (value) {
        null -> "跟随默认"
        "low" -> "低"
        "medium" -> "中"
        "high" -> "高"
        else -> value
    }
}

internal fun displayOfficialWebSearch(value: String?): String {
    return when (value) {
        null -> "跟随默认"
        "disabled" -> "禁用"
        "cached" -> "缓存"
        "indexed" -> "索引"
        "live" -> "实时"
        else -> value
    }
}

internal fun displayOfficialBoolean(value: Boolean?): String {
    return when (value) {
        null -> "跟随默认"
        true -> "启用"
        false -> "禁用"
    }
}

internal fun displayToolOutputLimit(value: Long?): String {
    return when (value) {
        null -> "跟随默认"
        8192L -> "8K"
        16384L -> "16K"
        32768L -> "32K"
        else -> "$value tokens"
    }
}

internal fun displayProjectDocLimit(value: Long?): String {
    return when (value) {
        null -> "跟随默认"
        65536L -> "64 KB"
        131072L -> "128 KB"
        262144L -> "256 KB"
        else -> "$value 字节"
    }
}

fun WindowsSettingsDraft.defaultsUpdate(
    snapshot: WindowsSettingsSnapshot,
): WindowsSettingsUpdate? {
    return defaults.takeIf { it != snapshot.defaults }?.let {
        WindowsSettingsUpdate(defaults = it)
    }
}

fun WindowsSettingsDraft.customApprovalUpdate(
    snapshot: WindowsSettingsSnapshot,
): WindowsSettingsUpdate? {
    val original = WindowsCustomApprovalUpdate(
        enabledPermissions = snapshot.customApproval.enabledPermissions,
        commandPrefixes = normalizeWindowsCommandPrefixes(snapshot.customApproval.commandPrefixes),
    )
    return customApproval.takeIf { it != original }?.let {
        WindowsSettingsUpdate(customApproval = it)
    }
}

fun WindowsSettingsDraft.officialUpdate(
    snapshot: WindowsSettingsSnapshot,
): WindowsSettingsUpdate? {
    return official.takeIf { it != snapshot.official }?.let {
        WindowsSettingsUpdate(official = it)
    }
}

fun normalizeWindowsCommandPrefixes(prefixes: List<String>): List<String> {
    val seen = linkedSetOf<String>()
    prefixes.forEach { prefix ->
        prefix.trim().takeIf { it.isNotEmpty() }?.let(seen::add)
    }
    return seen.toList()
}
