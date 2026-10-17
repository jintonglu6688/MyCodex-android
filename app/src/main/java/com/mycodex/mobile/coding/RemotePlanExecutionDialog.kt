package com.mycodex.mobile.coding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun RemotePlanExecutionDialog(
    state: RemotePlanExecutionUiState,
    onAction: (RemotePlanExecutionAction) -> Unit,
) {
    val request = state.activeRequest ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(request.title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(request.questionText)
                Text(request.planText)
                state.statusText?.takeIf { it.isNotBlank() }?.let { statusText ->
                    Text(
                        text = statusText,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            request.actions
                .filter { it.action == RemotePlanExecutionAction.Execute }
                .forEach { option ->
                    TextButton(
                        onClick = { onAction(option.action) },
                        enabled = option.enabled && !state.submitting,
                    ) {
                        Text(option.label)
                    }
                }
        },
        dismissButton = {
            request.actions
                .firstOrNull { it.action == RemotePlanExecutionAction.Continue }
                ?.let { option ->
                    TextButton(
                        onClick = { onAction(RemotePlanExecutionAction.Continue) },
                        enabled = option.enabled && !state.submitting,
                    ) {
                        Text(option.label)
                    }
                }
        },
    )
}
