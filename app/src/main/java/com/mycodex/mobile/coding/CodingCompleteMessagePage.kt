package com.mycodex.mobile.coding

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter

@Composable
internal fun CodingCompleteMessagePage(
    sessionId: String,
    message: CodingMessage,
    repository: CodingConversationRepository,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chunks by remember(message.id, message.contentVersion) { mutableStateOf(emptyList<String>()) }
    var nextCursor by remember(message.id, message.contentVersion) { mutableStateOf<String?>(null) }
    var complete by remember(message.id, message.contentVersion) { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> saveUri = uri }

    BackHandler(onBack = onBack)
    fun readNext(onChunk: (String) -> Unit) {
        if (loading || complete) return
        scope.launch {
            loading = true
            val result = repository.readMessageContent(sessionId, message.id, message.contentVersion, nextCursor)
            loading = false
            if (result.state == CodingLoadState.Loaded) {
                onChunk(result.text)
                nextCursor = result.nextCursor
                complete = result.complete
                error = null
            } else error = result.errorCode ?: "content_not_loaded"
        }
    }
    saveUri?.let { uri ->
        LaunchedStreamSave(uri, sessionId, message, repository) { error = it; saveUri = null }
    }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回消息" }) { Text("返回") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            items(chunks, key = { index -> "chunk_$index" }) { chunk -> Text(chunk) }
            if (error != null) item { Text("读取失败：$error") }
        }
        if (!complete) TextButton(onClick = { readNext { chunks = chunks + it } }, enabled = !loading) { Text(if (loading) "正在加载" else if (chunks.isEmpty()) "加载正文" else "加载下一部分") }
        TextButton(onClick = { saveLauncher.launch("MyCodex-message.txt") }, enabled = !loading) { Text("保存完整消息") }
    }
}

@Composable
private fun LaunchedStreamSave(
    uri: android.net.Uri,
    sessionId: String,
    message: CodingMessage,
    repository: CodingConversationRepository,
    onFinished: (String?) -> Unit,
) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(uri) {
        val failure = try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    var cursor: String? = null
                    var complete = false
                    while (!complete) {
                        val page = repository.readMessageContent(sessionId, message.id, message.contentVersion, cursor)
                        if (page.state != CodingLoadState.Loaded) throw IllegalStateException(page.errorCode ?: "content_not_loaded")
                        writer.write(page.text)
                        cursor = page.nextCursor
                        complete = page.complete
                    }
                }
            } ?: throw IllegalStateException("save_not_available")
            null
        } catch (error: Exception) { error.message ?: "save_failed" }
        onFinished(failure)
    }
}
