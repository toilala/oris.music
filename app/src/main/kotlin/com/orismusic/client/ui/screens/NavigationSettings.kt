package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
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
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(label(o)) })
            }
        }
    }
}
