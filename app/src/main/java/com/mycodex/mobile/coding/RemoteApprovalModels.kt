package com.mycodex.mobile.coding

import org.json.JSONObject

data class RemoteApprovalRequest(
    val requestId: String,
    val sessionId: String?,
    val title: String,
    val questionText: String,
    val summaryText: String?,
    val detailsText: String?,
    val canApproveForSession: Boolean,
    val decisions: List<RemoteApprovalDecisionOption>,
)

data class RemoteApprovalDecisionOption(
    val decision: RemoteApprovalDecision,
    val label: String,
    val enabled: Boolean,
)

enum class RemoteApprovalDecision(val wireValue: String) {
    AllowOnce("allow_once"),
    AllowForSession("allow_for_session"),
    Decline("decline"),
}

enum class RemoteApprovalResponseState {
    Completed,
    Unavailable,
    Error,
}

data class RemoteApprovalResponseResult(
    val state: RemoteApprovalResponseState,
    val completed: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

fun JSONObject.toRemoteApprovalRequest(): RemoteApprovalRequest {
    val decisionsJson = optJSONArray("decisions")
    val decisions = buildList {
        if (decisionsJson != null) {
            for (index in 0 until decisionsJson.length()) {
                val item = decisionsJson.optJSONObject(index) ?: continue
                val decision = item.optString("id").toRemoteApprovalDecision() ?: continue
                add(
                    RemoteApprovalDecisionOption(
                        decision = decision,
                        label = item.optString("label").ifBlank { decision.defaultLabel() },
                        enabled = item.optBoolean("enabled", true),
                    ),
                )
            }
        }
    }.ifEmpty {
        listOf(
            RemoteApprovalDecisionOption(RemoteApprovalDecision.AllowOnce, "允许一次", true),
            RemoteApprovalDecisionOption(RemoteApprovalDecision.Decline, "拒绝", true),
        )
    }

    return RemoteApprovalRequest(
        requestId = optString("requestId"),
        sessionId = optionalString("sessionId"),
        title = optString("title").ifBlank { "审批请求" },
        questionText = optString("questionText").ifBlank { optString("summaryText").ifBlank { "允许此请求？" } },
        summaryText = optionalString("summaryText"),
        detailsText = optionalString("detailsText"),
        canApproveForSession = optBoolean("canApproveForSession", false),
        decisions = decisions,
    )
}

private fun String.toRemoteApprovalDecision(): RemoteApprovalDecision? {
    return when (trim().lowercase()) {
        "allow_once", "allowonce" -> RemoteApprovalDecision.AllowOnce
        "allow_for_session", "allowforsession" -> RemoteApprovalDecision.AllowForSession
        "decline" -> RemoteApprovalDecision.Decline
        else -> null
    }
}

private fun RemoteApprovalDecision.defaultLabel(): String {
    return when (this) {
        RemoteApprovalDecision.AllowOnce -> "允许一次"
        RemoteApprovalDecision.AllowForSession -> "允许本会话"
        RemoteApprovalDecision.Decline -> "拒绝"
    }
}

private fun JSONObject.optionalString(key: String): String? {
    if (!has(key) || isNull(key)) {
        return null
    }

    return optString(key).takeUnless {
        it.isBlank() || it.equals("null", ignoreCase = true)
    }
}
