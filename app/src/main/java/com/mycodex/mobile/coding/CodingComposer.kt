package com.mycodex.mobile.coding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import com.mycodex.mobile.NativeAccessibleTextInput

@Composable
fun CodingComposer(
    state: CodingComposerState,
    onTextChange: (String) -> Unit,
    onSend: (String, List<CodingInputAttachment>) -> Unit,
    onStop: () -> Unit,
    onAttachmentClick: () -> Unit,
    onRemoveAttachment: (CodingInputAttachment) -> Unit,
    modifier: Modifier = Modifier,
    attachments: List<CodingInputAttachment> = emptyList(),
) {
    val density = LocalDensity.current
    val fontScale = LocalConfiguration.current.fontScale
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val editorEnabled = !state.sending
    var previousImeVisible by remember { mutableStateOf(false) }
    var inputFocused by remember { mutableStateOf(false) }
    var inputHadImeFocus by remember { mutableStateOf(false) }
    var restoreInputAccessibilityFocus by remember { mutableStateOf(false) }

    LaunchedEffect(imeVisible) {
        if (imeVisible && inputFocused) {
            inputHadImeFocus = true
        }
        if (previousImeVisible && !imeVisible && inputHadImeFocus) {
            restoreInputAccessibilityFocus = true
            inputHadImeFocus = false
        }
        previousImeVisible = imeVisible
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints {
            val stacked = shouldStackCodingComposer(
                maxWidthDp = maxWidth.value,
                fontScale = fontScale,
                showStopAction = state.generating || state.stopping,
            )
            val inputContent: @Composable (Modifier) -> Unit = { inputModifier ->
                NativeAccessibleTextInput(
                    text = state.text,
                    onTextChange = onTextChange,
                    modifier = inputModifier.heightIn(min = 56.dp),
                    testTag = "coding_composer_input",
                    enabled = editorEnabled,
                    label = "消息",
                    hint = "输入消息",
                    minLines = 1,
                    maxLines = 5,
                    restoreFocus = restoreInputAccessibilityFocus,
                    onFocusRestored = { restoreInputAccessibilityFocus = false },
                    onImeSend = {
                        if (state.canSend) {
                            onSend(state.submittedText, attachments)
                        }
                    },
                    onFocusChanged = { focused ->
                        inputFocused = focused
                        if (focused) {
                            inputHadImeFocus = inputHadImeFocus || imeVisible
                        }
                    },
                )
            }
            val actionContent: @Composable () -> Unit = {
                FilledTonalButton(
                    onClick = onAttachmentClick,
                    enabled = editorEnabled,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .testTag("coding_composer_attachment"),
                ) {
                    Text("附件")
                }
                Button(
                    onClick = {
                        if (state.canSend) {
                            onSend(state.submittedText, attachments)
                        }
                    },
                    enabled = state.canSend,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .testTag("coding_composer_send"),
                ) {
                    Text(if (state.sending) "发送中" else "发送")
                }
                if (state.generating || state.stopping) {
                    FilledTonalButton(
                        onClick = {
                            if (state.canStop) {
                                onStop()
                            }
                        },
                        enabled = state.canStop,
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .testTag("coding_composer_stop"),
                    ) {
                        Text(if (state.stopping) "停止中" else "停止")
                    }
                }
            }

            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        attachments.forEach { attachment ->
                            CodingAttachmentChip(
                                attachment = attachment,
                                enabled = editorEnabled,
                                onRemove = { onRemoveAttachment(attachment) },
                            )
                        }
                    }
                }
                if (stacked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        inputContent(Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Spacer(Modifier.weight(1f))
                            actionContent()
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        inputContent(Modifier.weight(1f))
                        actionContent()
                    }
                }
            }
        }
    }
}

internal fun shouldStackCodingComposer(
    maxWidthDp: Float,
    fontScale: Float,
    showStopAction: Boolean = false,
): Boolean {
    return maxWidthDp < 360f || fontScale >= 1.3f || (showStopAction && maxWidthDp < 480f)
}

@Composable
private fun CodingAttachmentChip(
    attachment: CodingInputAttachment,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val kindText = when (attachment.kind) {
        CodingAttachmentKind.Plugin -> "插件"
        CodingAttachmentKind.Skill -> "技能"
    }
    val removeLabel = when (attachment.kind) {
        CodingAttachmentKind.Plugin -> "移除插件 ${attachment.displayName}"
        CodingAttachmentKind.Skill -> "移除技能 ${attachment.displayName}"
    }
    AssistChip(
        onClick = onRemove,
        enabled = enabled,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = removeLabel
            if (enabled) {
                onClick(label = removeLabel) {
                    onRemove()
                    true
                }
            } else {
                disabled()
            }
        },
        label = {
            Text("$kindText ${attachment.displayName}")
        },
        trailingIcon = {
            Text("×")
        },
    )
}
