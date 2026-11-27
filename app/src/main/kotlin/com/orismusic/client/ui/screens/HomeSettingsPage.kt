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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.HomeCorners
import com.orismusic.client.settings.HomeCoverSize
import com.orismusic.client.settings.HomeDensity
import com.orismusic.client.settings.HeroStyle
import com.orismusic.client.settings.HomeSection
import com.orismusic.client.settings.HomeSettings
import com.orismusic.client.settings.JumpStyle
import com.orismusic.client.settings.ShelfStyle

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
            HomePreview()
            ToggleRow("Minimal Home", "Continue listening, Quick picks and one discovery shelf. Turn off for your full feed.", HomeSettings.minimal) {
                HomeSettings.minimal = it; HomeSettings.save()
            }
            SettingsFold("Sections and layout") {
            Text("These extra sections appear when Minimal Home is off. The top banner shows either way.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
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
            ChoiceRow("Motion", com.orismusic.client.settings.HomeMotion.entries, HomeSettings.motion, { it.label }) { HomeSettings.motion = it; HomeSettings.save() }
            ChoiceRow("Cover size", HomeCoverSize.entries, HomeSettings.coverSize, { it.label }) { HomeSettings.coverSize = it; HomeSettings.save() }
            ChoiceRow("Cover corners", HomeCorners.entries, HomeSettings.corners, { it.label }) { HomeSettings.corners = it; HomeSettings.save() }
            ChoiceRow("Recently played style", ShelfStyle.entries, HomeSettings.shelfStyle, { it.label }) { HomeSettings.shelfStyle = it; HomeSettings.save() }
            ChoiceRow("Jump back in layout", JumpStyle.entries, HomeSettings.jumpStyle, { it.label }) { HomeSettings.jumpStyle = it; HomeSettings.save() }
            ChoiceRow("Artist shelves", listOf(1, 2, 3, 4, 5), HomeSettings.becauseCount, { "$it" }) { HomeSettings.becauseCount = it; HomeSettings.save() }
            TextButton(onClick = { HomeSettings.reset() }) { Text("Reset Home") }
            }
            SettingsFold("Filters") {
            DiscoveryFiltersSection()
            }
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

/** A small sample of a shelf. It follows the cover size, corners, spacing and shelf style chosen below. */
@Composable
private fun HomePreview() {
    val k = HomeSettings.coverSize.k
    val side = (64 * k).dp
    val colors = listOf(0xFF8E5B52, 0xFF4F6B5E, 0xFF6C5A86, 0xFF8A7A52)
    androidx.compose.material3.Surface(
        shape = com.orismusic.client.ui.theme.OrisShapes.Panel,
        color = androidx.compose.ui.graphics.Color(0xFF151312), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy((HomeSettings.density.gap / 2).dp)) {
            if (HomeSettings.hero) {
                val deck = HomeSettings.heroStyle == com.orismusic.client.settings.HeroStyle.Deck
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth().height(if (deck) 70.dp else 54.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(if (deck) 20.dp else 6.dp))
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF5B4A47), androidx.compose.ui.graphics.Color(0xFF2D2625)))),
                    contentAlignment = Alignment.Center,
                ) { Text(if (deck) "Deck" else "Banner", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)) }
            }
            Text(if (HomeSettings.minimal) "Continue listening" else "Recently played", style = MaterialTheme.typography.titleSmall)
            val scatter = HomeSettings.shelfStyle == com.orismusic.client.settings.ShelfStyle.Scatter
            Row(horizontalArrangement = Arrangement.spacedBy(if (scatter) (-8).dp else 10.dp)) {
                colors.take(4).forEachIndexed { i, c ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(side)
                            .androidx_graphics(if (scatter) (if (i % 2 == 0) -3.5f else 3f) else 0f, if (scatter && i % 2 == 1) 6f else 0f)
                            .clip(HomeSettings.corners.shape(16))
                            .background(androidx.compose.ui.graphics.Color(c)),
                    )
                }
            }
        }
    }
}

private fun Modifier.androidx_graphics(rotation: Float, shiftDp: Float): Modifier =
    this.graphicsLayer { rotationZ = rotation; translationY = shiftDp.dp.toPx() }

@Composable
private fun DiscoveryFiltersSection() {
    val store = com.orismusic.client.library.DiscoveryFilterStore.get(androidx.compose.ui.platform.LocalContext.current)
    val filters = store.filters.collectAsState().value
    Text("Hidden and disliked songs and blocked artists stay out of Quick picks. Remove one to bring it back.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FilterGroup("Hidden songs", filters.hidden.map { it.id to listOf(it.title, it.artist) }) { store.unhide(it) }
    FilterGroup("Disliked songs", filters.disliked.map { it.id to listOf(it.title, it.artist) }) { store.undislike(it) }
    FilterGroup("Blocked artists", filters.blockedArtists.map { it to listOf(it, "") }) { store.unblockArtist(it) }
}

@Composable
private fun FilterGroup(title: String, entries: List<Pair<String, List<String>>>, onRemove: (String) -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text("$title (${entries.size})", style = MaterialTheme.typography.titleSmall)
    if (entries.isEmpty()) {
        Text("Nothing here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    entries.forEach { (key, label) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label[0], style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                if (label[1].isNotEmpty()) Text(label[1], style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            TextButton(onClick = { onRemove(key) }) { Text("Remove") }
        }
    }
}
