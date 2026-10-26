package com.mycodex.mobile.coding

import java.time.Instant

enum class CodingLoadState {
    Loaded,
    Unavailable,
    Error,
}

data class CodingListResult<T>(
    val state: CodingLoadState,
    val items: List<T> = emptyList(),
    val errorCode: String? = null,
    val generating: Boolean? = null,
)

enum class CodingRemoteMode(
    val commandPrefix: String,
    val eventPrefix: String,
    val workspaceLabel: String,
    val sessionLabel: String,
    val supportsWorkspaceOperations: Boolean,
    val supportsThreadOperations: Boolean,
    val supportsSubAgents: Boolean,
    val supportsCompact: Boolean,
) {
    Coding(
        commandPrefix = "coding",
        eventPrefix = "coding",
        workspaceLabel = "项目",
        sessionLabel = "会话",
        supportsWorkspaceOperations = true,
        supportsThreadOperations = true,
        supportsSubAgents = true,
        supportsCompact = true,
    ),
    Office(
        commandPrefix = "office",
        eventPrefix = "office",
        workspaceLabel = "办公",
        sessionLabel = "会话",
        supportsWorkspaceOperations = false,
        supportsThreadOperations = false,
        supportsSubAgents = false,
        supportsCompact = false,
    );

    fun command(name: String): String = "$commandPrefix.$name"

    fun event(name: String): String = "$eventPrefix.$name"
}

enum class CodingSendState {
    Accepted,
    Unavailable,
    Error,
}

data class CodingSendResult(
    val state: CodingSendState,
    val errorCode: String? = null,
    val dispatchMode: String? = null,
) {
    val accepted: Boolean
        get() = state == CodingSendState.Accepted
}

enum class CodingWorkspaceOperationState {
    Completed,
    Unavailable,
    Error,
}

data class CodingWorkspaceOperationResult(
    val state: CodingWorkspaceOperationState,
    val workspaceId: String? = null,
    val action: String? = null,
    val deleteLocalHistory: Boolean = false,
    val processedCount: Int = 0,
    val removed: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

enum class CodingSessionOperationState {
    Completed,
    Unavailable,
    Error,
}

data class CodingSessionOperationResult(
    val state: CodingSessionOperationState,
    val sessionId: String? = null,
    val workspaceId: String? = null,
    val action: String? = null,
    val deleteLocalHistory: Boolean = false,
    val processedCount: Int = 0,
    val removed: Boolean = false,
    val updated: Boolean = false,
    val title: String? = null,
    val statusText: String? = null,
    val errorCode: String? = null,
)

data class CodingSessionThreadOperationResult(
    val state: CodingSessionOperationState,
    val sessionId: String? = null,
    val workspaceId: String? = null,
    val action: String? = null,
    val mode: String? = null,
    val turnId: String? = null,
    val accepted: Boolean = false,
    val createdSession: CodingSession? = null,
    val switchToCreatedSession: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

enum class CodingStopState {
    Completed,
    Unavailable,
    Error,
}

data class CodingStopResult(
    val state: CodingStopState,
    val stopped: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

enum class CodingSessionCreateState {
    Created,
    Unavailable,
    Error,
}

data class CodingSessionCreateResult(
    val state: CodingSessionCreateState,
    val session: CodingSession? = null,
    val errorCode: String? = null,
)

enum class CodingSessionSettingsState {
    Loaded,
    Unavailable,
    Error,
}

data class CodingSessionSettingsResult(
    val state: CodingSessionSettingsState,
    val settings: CodingSessionSettings? = null,
    val errorCode: String? = null,
)

enum class CodingSessionSettingsUpdateState {
    Completed,
    Unavailable,
    Error,
}

data class CodingSessionSettingsUpdateResult(
    val state: CodingSessionSettingsUpdateState,
    val sessionId: String? = null,
    val workspaceId: String? = null,
    val updated: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

enum class CodingAttachmentCatalogState {
    Loaded,
    Unavailable,
    Error,
}

data class CodingAttachmentCatalogResult(
    val state: CodingAttachmentCatalogState,
    val items: List<CodingInputAttachment> = emptyList(),
    val errorCode: String? = null,
)

enum class CodingAttachmentKind {
    Plugin,
    Skill,
}

fun CodingAttachmentKind.displayText(): String {
    return when (this) {
        CodingAttachmentKind.Plugin -> "插件"
        CodingAttachmentKind.Skill -> "技能"
    }
}

data class CodingInputAttachment(
    val kind: CodingAttachmentKind,
    val type: String,
    val name: String?,
    val displayName: String,
    val path: String,
    val enabled: Boolean = false,
    val installed: Boolean = true,
    val sourceText: String? = null,
    val scope: String? = null,
    val summary: String? = null,
)

data class CodingSessionSettingsUpdate(
    val sessionId: String,
    val modelId: String? = null,
    val reasoningEffort: String? = null,
    val approvalMode: CodingApprovalMode? = null,
    val updateModelSettings: Boolean = true,
    val isPlanModeEnabled: Boolean? = null,
)

data class CodingSessionSettings(
    val sessionId: String,
    val workspaceId: String?,
    val modelId: String?,
    val reasoningEffort: String?,
    val approvalMode: CodingApprovalMode?,
    val isPlanModeEnabled: Boolean,
    val effectiveModelId: String?,
    val effectiveReasoningEffort: String?,
    val effectiveApprovalMode: CodingApprovalMode?,
    val defaultModelId: String?,
    val defaultReasoningEffort: String?,
    val defaultApprovalMode: CodingApprovalMode?,
    val availableModels: List<CodingModelOption>,
)

data class CodingModelOption(
    val modelId: String,
    val displayName: String,
    val defaultReasoningEffort: String?,
    val reasoningEfforts: List<String>,
)

enum class CodingApprovalMode {
    ReadOnly,
    Auto,
    Custom,
    FullAccess,
}

data class CodingConversationState(
    val loadState: CodingLoadState = CodingLoadState.Unavailable,
    val messages: List<CodingMessage> = emptyList(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val generating: Boolean = false,
    val stopping: Boolean = false,
    val hasNewMessagesAwayFromBottom: Boolean = false,
    val hasOlder: Boolean = false,
    val hasNewer: Boolean = false,
    val loadingOlder: Boolean = false,
    val loadingNewer: Boolean = false,
    val tailConnected: Boolean = true,
    val latestAssistantMessageId: String? = null,
    val pagingSupported: Boolean = false,
    val legacyLimited: Boolean = false,
    val pendingPagingViewportRestore: CodingPagingViewportRestore? = null,
    val pagingViewportRestoreGeneration: Long = 0L,
    val restoreFocusMessageId: String? = null,
    val restoreFocusGeneration: Long = 0L,
    val scrollToLatestGeneration: Long = 0L,
    val needsRetryLatest: Boolean = false,
    val composerStatusText: String? = null,
    val errorCode: String? = null,
)

data class CodingPagingViewportAnchor(
    val messageId: String,
    val scrollOffset: Int,
)

data class CodingPagingViewportRestore(
    val messageId: String,
    val scrollOffset: Int,
    val generation: Long,
)

data class CodingWorkspace(
    val id: String,
    val name: String,
    val path: String,
    val lastSessionId: String?,
)

data class CodingSession(
    val id: String,
    val workspaceId: String,
    val title: String,
    val createdAt: String?,
    val updatedAt: String?,
    val branchKind: CodingSessionBranchKind = CodingSessionBranchKind.Mainline,
    val subAgentCount: Int = 0,
    val subAgents: List<CodingSession> = emptyList(),
    val subAgentName: String? = null,
    val subAgentRole: String? = null,
)

enum class CodingSessionBranchKind {
    Mainline,
    Fork,
    Review,
}

data class CodingMessage(
    val id: String,
    val role: CodingMessageRole,
    val content: String,
    val createdAt: String?,
    val threadId: String? = null,
    val turnId: String? = null,
    val turnIndex: Int = 0,
    val turnCount: Int = 0,
    val turnPreview: String? = null,
    val actions: CodingMessageActions = CodingMessageActions(copy = content.isNotBlank()),
    val operationPreview: CodingMessageOperationPreview = CodingMessageOperationPreview(),
    val contentTruncated: Boolean = false,
    val contentUtf8ByteCount: Long = content.toByteArray(Charsets.UTF_8).size.toLong(),
    val contentVersion: String? = null,
)

enum class RemoteHostCapabilityState { Unknown, Supported, Legacy }

data class CodingMessagePageResult(
    val state: CodingLoadState,
    val items: List<CodingMessage> = emptyList(),
    val errorCode: String? = null,
    val generating: Boolean? = null,
    val olderCursor: String? = null,
    val newerCursor: String? = null,
    val hasOlder: Boolean = false,
    val hasNewer: Boolean = false,
    val tailConnected: Boolean = true,
    val latestAssistantMessageId: String? = null,
    val historyRevision: String? = null,
    val anchorFound: Boolean = true,
    val resolvedAnchorMessageId: String? = null,
    val capabilityState: RemoteHostCapabilityState = RemoteHostCapabilityState.Unknown,
)

data class CodingMessageContentResult(
    val state: CodingLoadState,
    val text: String = "",
    val nextCursor: String? = null,
    val loadedUtf8Bytes: Long = 0,
    val totalUtf8Bytes: Long = 0,
    val complete: Boolean = false,
    val errorCode: String? = null,
)

data class CodingMessageActions(
    val copy: Boolean = false,
    val forkFromTurn: Boolean = false,
)

data class CodingMessageOperationPreview(
    val trimTurnsAfterTurn: Int = 0,
)

const val CollapsedMessageMaxLines = 6
private const val CollapsibleMessageCharacterThreshold = 120

fun CodingMessage.accessibilityText(): String {
    return "${role.displayName()}：$content"
}

fun shouldCollapseMessageContent(content: String): Boolean {
    return content.length > CollapsibleMessageCharacterThreshold ||
        content.count { it == '\n' } >= CollapsedMessageMaxLines
}

fun messageExpansionLabel(expanded: Boolean): String {
    return if (expanded) "收起" else "展开全文"
}

enum class CodingMessageRole {
    User,
    Assistant,
    System,
    Tool,
    Unknown,
}

fun CodingMessageRole.displayName(): String {
    return when (this) {
        CodingMessageRole.User -> "我"
        CodingMessageRole.Assistant -> "codex"
        CodingMessageRole.System -> "系统"
        CodingMessageRole.Tool -> "工具"
        CodingMessageRole.Unknown -> "未知"
    }
}

fun threadOperationFailureText(operationName: String, errorCode: String): String {
    val reason = when (errorCode) {
        "unsupported_command" -> "Windows 端不支持此操作，请先更新 Windows 版 MyCodex。"
        "operation_not_available" -> "Windows 端当前无法执行此操作。"
        "session_busy" -> "会话正在处理其他请求，请稍后重试。"
        else -> errorCode
    }
    return "${operationName}失败：$reason"
}

fun turnTrimSummaryText(turnIndex: Int, trimTurnsAfterTurn: Int, targetName: String): String {
    return if (trimTurnsAfterTurn <= 0) {
        "将保留到第 $turnIndex 轮。"
    } else {
        "将保留到第 $turnIndex 轮，后续 $trimTurnsAfterTurn 轮不会进入$targetName。"
    }
}

fun copyAllMessagesText(messages: List<CodingMessage>): String {
    return messages.joinToString(separator = "\n\n") { message ->
        "${message.role.displayName()}: ${message.content}"
    }
}

fun CodingSession.displayText(nowEpochMillis: Long = System.currentTimeMillis()): String {
    val titleText = "${branchKind.displayName()}：$title"
    val ageText = createdAgoText(createdAt, nowEpochMillis)
    val sessionText = if (ageText.isBlank()) {
        titleText
    } else {
        "$ageText，$titleText"
    }

    return if (subAgentCount > 0) {
        "有 $subAgentCount 个子代理，$sessionText"
    } else {
        sessionText
    }
}

fun CodingSession.metadataText(nowEpochMillis: Long = System.currentTimeMillis()): String {
    return buildList {
        createdAgoText(updatedAt ?: createdAt, nowEpochMillis)
            .takeIf(String::isNotBlank)
            ?.let(::add)
        add(branchKind.displayName())
        if (subAgentCount > 0) {
            add("$subAgentCount 个子代理")
        }
    }.joinToString("，")
}

fun CodingSession.subAgentDisplayText(): String {
    val parts = mutableListOf<String>()
    if (!subAgentName.isNullOrBlank()) {
        parts += subAgentName
    }
    if (!subAgentRole.isNullOrBlank()) {
        parts += subAgentRole
    }
    parts += "${branchKind.displayName()}：$title"
    return parts.joinToString("，")
}

fun CodingSessionBranchKind.displayName(): String {
    return when (this) {
        CodingSessionBranchKind.Mainline -> "主线"
        CodingSessionBranchKind.Fork -> "分叉"
        CodingSessionBranchKind.Review -> "审核"
    }
}

fun CodingApprovalMode.displayName(): String {
    return when (this) {
        CodingApprovalMode.ReadOnly -> "只读"
        CodingApprovalMode.Auto -> "自动"
        CodingApprovalMode.Custom -> "自定义"
        CodingApprovalMode.FullAccess -> "完全访问"
    }
}

fun CodingApprovalMode.toWireValue(): String {
    return when (this) {
        CodingApprovalMode.ReadOnly -> "read-only"
        CodingApprovalMode.Auto -> "auto"
        CodingApprovalMode.Custom -> "custom"
        CodingApprovalMode.FullAccess -> "full-access"
    }
}

fun String?.toCodingApprovalMode(): CodingApprovalMode? {
    return when (this?.trim()?.lowercase()) {
        "read-only", "readonly" -> CodingApprovalMode.ReadOnly
        "auto" -> CodingApprovalMode.Auto
        "custom" -> CodingApprovalMode.Custom
        "full-access", "fullaccess" -> CodingApprovalMode.FullAccess
        else -> null
    }
}

fun reasoningEffortDisplayName(effortId: String?): String {
    return when (effortId?.trim()?.lowercase()) {
        "minimal" -> "最小"
        "low" -> "低"
        "medium" -> "中"
        "high" -> "高"
        "xhigh" -> "超高"
        "none" -> "无"
        else -> effortId?.takeIf { it.isNotBlank() } ?: "默认"
    }
}

fun String?.toCodingSessionBranchKind(): CodingSessionBranchKind {
    return when (this?.trim()?.lowercase()) {
        "fork" -> CodingSessionBranchKind.Fork
        "review" -> CodingSessionBranchKind.Review
        else -> CodingSessionBranchKind.Mainline
    }
}

private fun createdAgoText(createdAt: String?, nowEpochMillis: Long): String {
    if (createdAt.isNullOrBlank()) {
        return ""
    }

    val createdEpochMillis = try {
        Instant.parse(createdAt).toEpochMilli()
    } catch (_: RuntimeException) {
        return ""
    }
    val elapsedMillis = nowEpochMillis - createdEpochMillis
    val elapsedHours = elapsedMillis / (60L * 60L * 1000L)
    if (elapsedHours < 1L) {
        return ""
    }

    val elapsedDays = elapsedHours / 24L
    return if (elapsedDays >= 1L) {
        "${elapsedDays}天前"
    } else {
        "${elapsedHours}小时前"
    }
}

fun CodingListResult<CodingMessage>.initialMessageListScrollIndex(): Int? {
    if (state != CodingLoadState.Loaded || items.isEmpty()) {
        return null
    }

    return items.size
}

fun isNearMessageListBottom(lastVisibleMessageIndex: Int, messageCount: Int): Boolean {
    if (messageCount <= 0) {
        return true
    }

    return lastVisibleMessageIndex >= messageCount - 1
}

fun shouldShowMessageListBottomButton(lastVisibleItemIndex: Int?, totalItems: Int): Boolean {
    if (totalItems <= 0 || lastVisibleItemIndex == null) {
        return false
    }

    return lastVisibleItemIndex < totalItems - 1
}
