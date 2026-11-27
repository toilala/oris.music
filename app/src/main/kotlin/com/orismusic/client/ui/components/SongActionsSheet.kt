package com.orismusic.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.orismusic.client.ui.theme.OrisShapes
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One row in the shared song menu. [tint] overrides the row colour only for actions
 *  that remove or block something; leave it null for the quiet default. Rows with
 *  [enabled] false stay visible but dim and do not answer taps. */
data class SongAction(
    val label: String,
    val icon: ImageVector? = null,
    val tint: Color? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** The one song menu. Wherever a song offers actions (library rows, home picks, the
 *  player tray) it opens this sheet: artwork header up top, actions below, same 32dp
 *  top corners and sheet motion as the player tray, so every surface answers the same
 *  way. Screens supply the actions; the sheet owns the look.
 *
 *  Rows keep a 48dp minimum touch target at any font scale and labels wrap without a
 *  line cap, so long playlist names stay fully readable. The action list is
 *  height-capped and scrolls; the system navigation bar is padded out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    title: String,
    subtitle: String?,
    thumbnail: String?,
    actions: List<SongAction>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = OrisShapes.Sheet,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(thumbnail, Modifier.size(52.dp), OrisShapes.Row, title, px = 160)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(
            Modifier.heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 12.dp)
        ) {
            actions.forEach { action ->
                val tint = action.tint ?: MaterialTheme.colorScheme.onSurface
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .alpha(if (action.enabled) 1f else 0.4f)
                        .clickable(enabled = action.enabled) { action.onClick() }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (action.icon != null) {
                        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(action.label, style = MaterialTheme.typography.bodyLarge, color = tint)
                }
            }
        }
    }
}
