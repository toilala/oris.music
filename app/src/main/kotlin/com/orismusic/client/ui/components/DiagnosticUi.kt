package com.orismusic.client.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orismusic.client.utils.PlaybackDiagnostic

@Composable
fun DiagnosticFailureBanner(onClick: () -> Unit) {
    val activeFailure by PlaybackDiagnostic.activeFailure.collectAsState()
    if (activeFailure != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        ) {
            Text(
                activeFailure.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun DiagnosticHistoryDialog(onDismiss: () -> Unit) {
    val history by PlaybackDiagnostic.history.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback diagnostic") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (history.isEmpty()) Text("Tap a song to test anonymous playback.")
                history.forEach { entry ->
                    Text(entry, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
