package com.mycodex.mobile.coding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.json.JSONArray
import org.json.JSONObject

class CodingDraftViewModel : ViewModel() {
    private var boundConversation: String? = null
    var workspaceRestored = false

    var text by mutableStateOf("")
    var attachments by mutableStateOf<List<CodingInputAttachment>>(emptyList())
    var sessionCreateTitle by mutableStateOf("")
    var forkTitle by mutableStateOf("")

    fun bindConversation(conversation: String) {
        if (boundConversation != null && boundConversation != conversation) {
            clearComposer()
            clearThreadTitles()
        }
        boundConversation = conversation
    }

    fun snapshot(): JSONObject = JSONObject()
        .put("conversation", boundConversation)
        .put("text", text)
        .put("sessionCreateTitle", sessionCreateTitle)
        .put("forkTitle", forkTitle)
        .put("uncertainSend", uncertainSend)
        .put("attachments", JSONArray().apply {
            attachments.forEach { item -> put(JSONObject()
                .put("kind", item.kind.name).put("type", item.type).put("name", item.name)
                .put("displayName", item.displayName).put("path", item.path)
                .put("enabled", item.enabled).put("installed", item.installed)
                .put("sourceText", item.sourceText).put("scope", item.scope).put("summary", item.summary))
            }
        })

    var uncertainSend by mutableStateOf(false)

    fun restore(snapshot: JSONObject?) {
        clearAll()
        if (snapshot == null) return
        boundConversation = snapshot.optString("conversation").takeIf { it.isNotEmpty() }
        text = snapshot.optString("text")
        sessionCreateTitle = snapshot.optString("sessionCreateTitle")
        forkTitle = snapshot.optString("forkTitle")
        uncertainSend = snapshot.optBoolean("uncertainSend")
        val items = snapshot.optJSONArray("attachments") ?: JSONArray()
        attachments = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            fun optional(key: String) = if (item.isNull(key)) null else item.optString(key).takeIf { it.isNotEmpty() }
            CodingInputAttachment(
                kind = CodingAttachmentKind.valueOf(item.getString("kind")), type = item.getString("type"),
                name = optional("name"), displayName = item.getString("displayName"), path = item.getString("path"),
                enabled = item.optBoolean("enabled"), installed = item.optBoolean("installed", true),
                sourceText = optional("sourceText"), scope = optional("scope"), summary = optional("summary"),
            )
        }
    }

    fun clearAccepted(text: String, attachments: List<CodingInputAttachment>) {
        uncertainSend = false
        if (this.text.trim() == text) this.text = ""
        if (this.attachments == attachments) this.attachments = emptyList()
    }

    fun clearComposer() {
        uncertainSend = false
        text = ""
        attachments = emptyList()
        boundConversation = null
    }

    fun clearSessionCreateTitle() {
        sessionCreateTitle = ""
    }

    fun clearThreadTitles() {
        forkTitle = ""
    }

    fun clearAll() {
        clearComposer()
        clearSessionCreateTitle()
        clearThreadTitles()
    }
}
