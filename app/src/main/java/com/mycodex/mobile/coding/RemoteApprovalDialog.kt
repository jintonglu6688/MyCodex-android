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
fun RemoteApprovalDialog(
    state: RemoteApprovalUiState,
    onDecision: (RemoteApprovalDecision) -> Unit,
) {
    val request = state.activeRequest ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(request.title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(request.questionText)
                if (!state.statusText.isNullOrBlank()) {
                    Text(
                        text = state.statusText,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            request.decisions
                .filter { it.decision != RemoteApprovalDecision.Decline }
                .forEach { option ->
                    TextButton(
                        onClick = { onDecision(option.decision) },
                        enabled = option.enabled && !state.submitting,
                    ) {
                        Text(option.label)
                    }
                }
        },
        dismissButton = {
            request.decisions
                .firstOrNull { it.decision == RemoteApprovalDecision.Decline }
                ?.let { option ->
                    TextButton(
                        onClick = { onDecision(RemoteApprovalDecision.Decline) },
                        enabled = option.enabled && !state.submitting,
                    ) {
                        Text(option.label)
                    }
                }
        },
    )
}
