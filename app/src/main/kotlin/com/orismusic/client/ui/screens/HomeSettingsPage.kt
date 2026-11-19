package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.HomeCorners
import com.orismusic.client.settings.HomeCoverSize
import com.orismusic.client.settings.HomeDensity
import com.orismusic.client.settings.HeroStyle
import com.orismusic.client.settings.HomeSection
import com.orismusic.client.settings.HomeSettings
import com.orismusic.client.settings.JumpStyle

/** Everything about Home in one place. Changes apply as soon as you go back to Home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSettingsPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Home") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ToggleRow("Top banner", "The big artwork banner at the top. Off gives a plain header.", HomeSettings.hero) {
                HomeSettings.hero = it; HomeSettings.save()
            }
            ChoiceRow("Banner style", HeroStyle.entries, HomeSettings.heroStyle, { it.label }) { HomeSettings.heroStyle = it; HomeSettings.save() }
            ToggleRow("YouTube Music shelves", "The shelves from YouTube Music below your own.", HomeSettings.youtube) {
                HomeSettings.youtube = it; HomeSettings.save()
            }
            Text("Sections", style = MaterialTheme.typography.titleMedium)
            Text("Switch a section off, or move it up and down.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HomeSettings.order.toList().forEach { sec -> SectionRow(sec) }
            ChoiceRow("Spacing", HomeDensity.entries, HomeSettings.density, { it.label }) { HomeSettings.density = it; HomeSettings.save() }
            ChoiceRow("Cover size", HomeCoverSize.entries, HomeSettings.coverSize, { it.label }) { HomeSettings.coverSize = it; HomeSettings.save() }
            ChoiceRow("Cover corners", HomeCorners.entries, HomeSettings.corners, { it.label }) { HomeSettings.corners = it; HomeSettings.save() }
            ChoiceRow("Jump back in layout", JumpStyle.entries, HomeSettings.jumpStyle, { it.label }) { HomeSettings.jumpStyle = it; HomeSettings.save() }
            ChoiceRow("Artist shelves", listOf(1, 2, 3, 4, 5), HomeSettings.becauseCount, { "$it" }) { HomeSettings.becauseCount = it; HomeSettings.save() }
            TextButton(onClick = { HomeSettings.reset() }) { Text("Reset Home") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionRow(sec: HomeSection) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(sec.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { HomeSettings.move(sec, -1) }) { Text("Up") }
            TextButton(onClick = { HomeSettings.move(sec, 1) }) { Text("Down") }
            Switch(checked = HomeSettings.visible(sec), onCheckedChange = { HomeSettings.setVisible(sec, it) })
        }
    }
}
