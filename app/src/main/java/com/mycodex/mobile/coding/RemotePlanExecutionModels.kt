package com.mycodex.mobile.coding

import org.json.JSONObject

data class RemotePlanExecutionRequest(
    val requestId: String,
    val threadId: String?,
    val title: String,
    val questionText: String,
    val planText: String,
    val actions: List<RemotePlanExecutionActionOption>,
)

data class RemotePlanExecutionActionOption(
    val action: RemotePlanExecutionAction,
    val label: String,
    val enabled: Boolean,
)

enum class RemotePlanExecutionAction(val wireValue: String) {
    Execute("execute"),
    Continue("continue"),
}

enum class RemotePlanExecutionResponseState {
    Completed,
    Unavailable,
    Error,
}

data class RemotePlanExecutionResponseResult(
    val state: RemotePlanExecutionResponseState,
    val completed: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

fun JSONObject.toRemotePlanExecutionRequest(): RemotePlanExecutionRequest {
    val actionsJson = optJSONArray("actions")
    val actions = buildList {
        if (actionsJson != null) {
            for (index in 0 until actionsJson.length()) {
                val item = actionsJson.optJSONObject(index) ?: continue
                val action = item.optString("id").toRemotePlanExecutionAction() ?: continue
                add(
                    RemotePlanExecutionActionOption(
                        action = action,
                        label = item.optString("label").ifBlank { action.defaultLabel() },
                        enabled = item.optBoolean("enabled", true),
                    ),
                )
            }
        }
    }.ifEmpty {
        listOf(
            RemotePlanExecutionActionOption(RemotePlanExecutionAction.Execute, "执行计划", true),
            RemotePlanExecutionActionOption(RemotePlanExecutionAction.Continue, "继续讨论", true),
        )
    }

    return RemotePlanExecutionRequest(
        requestId = optString("requestId").trim(),
        threadId = remotePlanOptionalString("threadId"),
        title = optString("title").ifBlank { "计划已生成" },
        questionText = optString("questionText").ifBlank { "是否执行计划？" },
        planText = optString("planText"),
        actions = actions,
    )
}

private fun String.toRemotePlanExecutionAction(): RemotePlanExecutionAction? {
    return when (trim().lowercase()) {
        "execute" -> RemotePlanExecutionAction.Execute
        "continue" -> RemotePlanExecutionAction.Continue
        else -> null
    }
}

private fun RemotePlanExecutionAction.defaultLabel(): String {
    return when (this) {
        RemotePlanExecutionAction.Execute -> "执行计划"
        RemotePlanExecutionAction.Continue -> "继续讨论"
    }
}

private fun JSONObject.remotePlanOptionalString(key: String): String? {
    if (!has(key) || isNull(key)) {
        return null
    }

    return optString(key).takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
}
