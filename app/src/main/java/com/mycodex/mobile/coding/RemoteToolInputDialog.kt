package com.mycodex.mobile.coding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun RemoteToolInputDialog(
    state: RemoteToolInputUiState,
    onAnswerChanged: (String, List<String>) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val request = state.activeRequest ?: return
    AlertDialog(
        onDismissRequest = {},
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                request.questions.forEach { question ->
                    RemoteToolInputQuestionEditor(
                        question = question,
                        values = state.answers[question.id].orEmpty(),
                        enabled = !state.submitting && !state.canceling,
                        onAnswerChanged = { values -> onAnswerChanged(question.id, values) },
                    )
                }
                state.statusText?.takeIf { it.isNotBlank() }?.let { statusText ->
                    Text(
                        text = statusText,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !state.submitting && !state.canceling) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !state.submitting && !state.canceling) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun RemoteToolInputQuestionEditor(
    question: RemoteToolInputQuestion,
    values: List<String>,
    enabled: Boolean,
    onAnswerChanged: (List<String>) -> Unit,
) {
    val selectedValue = values.firstOrNull().orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = question.question,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )

        if (question.options.isNotEmpty()) {
            val options = if (question.isOther) {
                question.options + RemoteToolInputOption("其他", "__other__", null)
            } else {
                question.options
            }

            options.forEach { option ->
                val selected = selectedValue == option.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            onClick = { onAnswerChanged(listOf(option.value)) },
                        )
                        .semantics { contentDescription = option.label }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = enabled,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Column(modifier = Modifier.clearAndSetSemantics {}) {
                        Text(option.label)
                    }
                }
            }

            if (question.isOther && selectedValue == "__other__") {
                OutlinedTextField(
                    value = values.getOrNull(1).orEmpty(),
                    onValueChange = { onAnswerChanged(listOf("__other__", it)) },
                    enabled = enabled,
                    label = { Text("其他") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (question.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
                )
            }
        } else {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = { onAnswerChanged(listOf(it)) },
                enabled = enabled,
                label = { Text(question.question) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (question.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
    }
}
