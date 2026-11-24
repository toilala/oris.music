package com.orismusic.client.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.orismusic.client.utils.diagnosticSummary

@Composable
fun DiagnosticFailureBanner(onClick: () -> Unit, onRetry: (() -> Unit)? = null) {
    val activeFailure by PlaybackDiagnostic.activeFailure.collectAsState()
    val failure = activeFailure ?: return
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        // The surface stays human-readable; the raw exception chain lives in the diagnostic history.
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                diagnosticSummary(failure),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row {
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                TextButton(onClick = onClick) { Text("Details") }
            }
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
                // Retry loops flood the log with repeats; show each distinct event once,
                // newest first, with its count so the evidence is preserved.
                val order = mutableListOf<String>()
                val counts = mutableMapOf<String, Int>()
                for (entry in history.asReversed()) {
                    if (entry !in counts) order += entry
                    counts[entry] = (counts[entry] ?: 0) + 1
                }
                order.forEach { entry ->
                    val count = counts.getValue(entry)
                    Text(
                        if (count > 1) "$entry (x$count)" else entry,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
