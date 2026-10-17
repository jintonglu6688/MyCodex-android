package com.mycodex.mobile.updates

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun UpdateToastHost(
    message: UpdateToastMessage?,
    onConsumed: (Long) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        Toast.makeText(context, current.text, Toast.LENGTH_SHORT).show()
        onConsumed(current.id)
    }
}
