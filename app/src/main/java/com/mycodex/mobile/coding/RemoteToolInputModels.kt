package com.mycodex.mobile.coding

import org.json.JSONObject

data class RemoteToolInputRequest(
    val requestId: String,
    val sessionId: String?,
    val title: String,
    val headerText: String?,
    val detailsText: String?,
    val questions: List<RemoteToolInputQuestion>,
)

data class RemoteToolInputQuestion(
    val id: String,
    val header: String?,
    val question: String,
    val description: String?,
    val isOther: Boolean,
    val isSecret: Boolean,
    val options: List<RemoteToolInputOption>,
)

data class RemoteToolInputOption(
    val label: String,
    val value: String,
    val description: String?,
)

enum class RemoteToolInputResponseState {
    Completed,
    Canceled,
    Unavailable,
    Error,
}

data class RemoteToolInputResponseResult(
    val state: RemoteToolInputResponseState,
    val completed: Boolean = false,
    val canceled: Boolean = false,
    val statusText: String? = null,
    val errorCode: String? = null,
)

fun JSONObject.toRemoteToolInputRequest(): RemoteToolInputRequest {
    val questionsJson = optJSONArray("questions")
    val questions = buildList {
        if (questionsJson != null) {
            for (index in 0 until questionsJson.length()) {
                val item = questionsJson.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isBlank()) {
                    continue
                }

                add(
                    RemoteToolInputQuestion(
                        id = id,
                        header = item.remoteOptionalString("header"),
                        question = item.optString("question").ifBlank { item.optString("header") }.ifBlank { "请输入" },
                        description = item.remoteOptionalString("description"),
                        isOther = item.optBoolean("isOther", false),
                        isSecret = item.optBoolean("isSecret", false),
                        options = item.optJSONArray("options").toRemoteToolInputOptions(),
                    ),
                )
            }
        }
    }

    return RemoteToolInputRequest(
        requestId = optString("requestId").trim(),
        sessionId = remoteOptionalString("sessionId"),
        title = optString("title").ifBlank { "计划模式" },
        headerText = remoteOptionalString("headerText"),
        detailsText = remoteOptionalString("detailsText"),
        questions = questions,
    )
}

private fun org.json.JSONArray?.toRemoteToolInputOptions(): List<RemoteToolInputOption> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val value = item.optString("value").ifBlank { item.optString("label") }.trim()
            val label = item.optString("label").ifBlank { value }.trim()
            if (label.isBlank() && value.isBlank()) {
                continue
            }

            add(RemoteToolInputOption(label.ifBlank { value }, value.ifBlank { label }, item.remoteOptionalString("description")))
        }
    }
}

private fun JSONObject.remoteOptionalString(key: String): String? {
    if (!has(key) || isNull(key)) {
        return null
    }

    return optString(key).takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
}
