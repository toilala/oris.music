package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.NavAccent
import com.orismusic.client.settings.NavEffect
import com.orismusic.client.settings.NavLift
import com.orismusic.client.settings.NavSettings
import com.orismusic.client.settings.NavSize
import com.orismusic.client.settings.NavStyle
import com.orismusic.client.settings.NavWidth
import com.orismusic.client.ui.components.OrisDock

/** Everything about the bottom bar in one place. A live bar at the top shows each change. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSettings(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var previewTab by remember { mutableIntStateOf(0) }
    val names = listOf("Home", "Search", "Library")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Search, Icons.Rounded.LibraryMusic)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Navigation") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                OrisDock(3, previewTab, { names[it] }, { icons[it] }, { previewTab = it; })
            }
            Text("Tap the bar above to try it. Changes apply to the whole app straight away.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow("Selection effect", NavEffect.entries, NavSettings.effect, { it.label }) { NavSettings.effect = it; NavSettings.save() }
            ChoiceRow("Bar style", NavStyle.entries, NavSettings.style, { it.label }) { NavSettings.style = it; NavSettings.save() }
            ChoiceRow("Accent", NavAccent.entries, NavSettings.accent, { it.label }) { NavSettings.accent = it; NavSettings.save() }
            ChoiceRow("Icon size", NavSize.entries, NavSettings.size, { it.label }) { NavSettings.size = it; NavSettings.save() }
            ChoiceRow("Width", NavWidth.entries, NavSettings.width, { it.label }) { NavSettings.width = it; NavSettings.save() }
            ChoiceRow("Distance from the bottom", NavLift.entries, NavSettings.lift, { it.label }) { NavSettings.lift = it; NavSettings.save() }
            ToggleRow("Hide while scrolling", "The bar slides away when you scroll down and returns when you scroll up.", NavSettings.hideOnScroll) {
                NavSettings.hideOnScroll = it; NavSettings.save()
            }
            ToggleRow("Tap feedback", "A short vibration when you switch tabs.", NavSettings.haptics) {
                NavSettings.haptics = it; NavSettings.save()
            }
            TextButton(onClick = { NavSettings.reset() }) { Text("Reset navigation") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    Surface(onClick = { open = true }, color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(label(selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp).widthIn(max = 150.dp), maxLines = 2)
            Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) androidx.compose.material3.AlertDialog(onDismissRequest = { open = false }, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            options.forEach { value ->
                Row(Modifier.fillMaxWidth().clickable { onPick(value); open = false }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = value == selected, onClick = { onPick(value); open = false })
                    Text(label(value), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } }, confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } })
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp))
        content()
    }
}

@Composable
internal fun SettingsFold(title: String, content: @Composable () -> Unit) {
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { open = !open }) {
            Text(title)
            Text(if (open) "  −" else "  +")
        }
        if (open) content()
    }
}
