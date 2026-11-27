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
import androidx.compose.ui.Alignment
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 2.dp),
    ) {
        // One compact strip: an error-tinted icon carries the severity, the surface stays
        // calm, and the artwork keeps its room. The raw exception chain lives in Details.
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                diagnosticSummary(failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            TextButton(onClick = onClick) { Text("Details") }
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
