package com.mycodex.mobile.coding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private enum class CodingAttachmentSheetPage {
    Grid,
    Plugins,
    Skills,
}

private data class CodingAttachmentAction(
    val id: String,
    val title: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodingAttachmentSheet(
    sessionId: String,
    repository: CodingRemoteRepository,
    insertedAttachments: List<CodingInputAttachment>,
    onDismiss: () -> Unit,
    onInsertAttachment: (CodingInputAttachment) -> Unit,
) {
    var page by remember { mutableStateOf(CodingAttachmentSheetPage.Grid) }

    BackHandler(enabled = page != CodingAttachmentSheetPage.Grid) {
        page = CodingAttachmentSheetPage.Grid
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "插入附件",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                if (page != CodingAttachmentSheetPage.Grid) {
                    TextButton(onClick = { page = CodingAttachmentSheetPage.Grid }) {
                        Text("返回")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }

            when (page) {
                CodingAttachmentSheetPage.Grid -> CodingAttachmentActionGrid(
                    onPlugins = { page = CodingAttachmentSheetPage.Plugins },
                    onSkills = { page = CodingAttachmentSheetPage.Skills },
                )

                CodingAttachmentSheetPage.Plugins -> CodingAttachmentPicker(
                    title = "插件",
                    loadingText = "正在加载插件",
                    emptyText = "没有可插入的插件",
                    load = { repository.loadPluginAttachments(sessionId) },
                    insertedAttachments = insertedAttachments,
                    onInsertAttachment = onInsertAttachment,
                )

                CodingAttachmentSheetPage.Skills -> CodingAttachmentPicker(
                    title = "技能",
                    loadingText = "正在加载技能",
                    emptyText = "没有可插入的技能",
                    load = { repository.loadSkillAttachments(sessionId) },
                    insertedAttachments = insertedAttachments,
                    onInsertAttachment = onInsertAttachment,
                )
            }
        }
    }
}

@Composable
private fun CodingAttachmentActionGrid(
    onPlugins: () -> Unit,
    onSkills: () -> Unit,
) {
    val actions = listOf(
        CodingAttachmentAction("plugins", "插件"),
        CodingAttachmentAction("skills", "技能"),
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(actions, key = { it.id }) { action ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (action.id == "plugins") {
                                onPlugins()
                            } else {
                                onSkills()
                            }
                        }
                        .clearAndSetSemantics {
                            contentDescription = action.title
                            onClick(label = action.title) {
                                if (action.id == "plugins") {
                                    onPlugins()
                                } else {
                                    onSkills()
                                }
                                true
                            }
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(action.title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun CodingAttachmentPicker(
    title: String,
    loadingText: String,
    emptyText: String,
    load: suspend () -> CodingAttachmentCatalogResult,
    insertedAttachments: List<CodingInputAttachment>,
    onInsertAttachment: (CodingInputAttachment) -> Unit,
) {
    var query by remember(title) { mutableStateOf("") }
    var loading by remember(title) { mutableStateOf(true) }
    var errorCode by remember(title) { mutableStateOf<String?>(null) }
    var items by remember(title) { mutableStateOf<List<CodingInputAttachment>>(emptyList()) }
    var reloadToken by remember(title) { mutableIntStateOf(0) }

    LaunchedEffect(title, reloadToken) {
        loading = true
        errorCode = null
        val result = load()
        loading = false
        if (result.state == CodingAttachmentCatalogState.Loaded) {
            items = result.items
        } else {
            items = emptyList()
            errorCode = result.errorCode ?: "unknown"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        when {
            loading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    loadingText,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            errorCode != null -> Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp),
                ) {
                    Text(
                        "${title}列表加载失败：$errorCode",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Button(onClick = { reloadToken += 1 }) {
                        Text("重试")
                    }
                }
            }

            items.isEmpty() -> Text(emptyText)

            else -> {
                val filtered = items.filter { item ->
                    query.isBlank() ||
                        item.displayName.contains(query, ignoreCase = true) ||
                        (item.name?.contains(query, ignoreCase = true) == true)
                }
                if (filtered.isEmpty() && query.isNotBlank()) {
                    Text("没有匹配结果")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.path }) { item ->
                            val alreadyInserted = insertedAttachments.any { it.path.equals(item.path, ignoreCase = true) }
                            CodingAttachmentPickerRow(
                                item = item,
                                alreadyInserted = alreadyInserted,
                                onClick = {
                                    if (!alreadyInserted) {
                                        onInsertAttachment(item)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodingAttachmentPickerRow(
    item: CodingInputAttachment,
    alreadyInserted: Boolean,
    onClick: () -> Unit,
) {
    val kindText = when (item.kind) {
        CodingAttachmentKind.Plugin -> "插件"
        CodingAttachmentKind.Skill -> "技能"
    }
    val stateText = if (alreadyInserted) {
        "已插入"
    } else if (item.enabled) {
        "已启用"
    } else {
        "未启用"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !alreadyInserted, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(
                        item.displayName,
                        kindText,
                        stateText,
                        item.sourceText,
                    ).joinToString("，")
                    if (!alreadyInserted) {
                        onClick(label = "插入${kindText}") {
                            onClick()
                            true
                        }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.displayName, style = MaterialTheme.typography.titleSmall)
            Text("$kindText，$stateText${item.sourceText?.let { "，$it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            if (!item.summary.isNullOrBlank()) {
                Text(item.summary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
