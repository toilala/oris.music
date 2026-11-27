package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.AccentSeed
import com.orismusic.client.settings.AppIcons
import com.orismusic.client.settings.AppearanceSettings
import com.orismusic.client.settings.BackgroundMode

/** Colours, player background, screen and app icon: every look option in one folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val look = AppearanceSettings
    Scaffold(
        topBar = { TopAppBar(title = { Text("Appearance") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurfacePreview()
            GroupLabel("Theme")
            ChoiceRow("App theme", com.orismusic.client.settings.ThemeMode.entries.toList(), look.themeMode, { it.label }) {
                look.themeMode = it; look.save()
            }
            Spacer(Modifier.height(6.dp))
            GroupLabel("Colours")
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                ToggleRow(
                    "Wallpaper colours",
                    if (look.dynamicColor) "On: colours follow your Android wallpaper (Material You)." else "Off: the Oris palette with your accent below.",
                    look.dynamicColor,
                ) { look.dynamicColor = it; look.save() }
            }
            if (!look.dynamicColor) {
                Column {
                    Text("Accent", style = MaterialTheme.typography.titleMedium)
                    Text("Tints the play key, the navigation bar and the highlights across the app.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccentSeed.entries.forEach { seed ->
                            val selected = look.accent == seed
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(
                                            if (selected) 3.dp else 0.dp,
                                            if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            CircleShape,
                                        )
                                        .clickable { look.accent = seed; look.save() }
                                        .semantics { contentDescription = "Accent " + seed.label }
                                        .padding(if (selected) 6.dp else 3.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.fillMaxSize().clip(CircleShape).background(seed.swatch))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(seed.label, style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            ToggleRow("Pure black", "Black backgrounds for OLED screens.", look.amoled) { look.amoled = it; look.save() }

            GroupLabel("Player background")
            ToggleRow("Adaptive player background", "The player takes its colours from the song artwork.", look.adaptiveBackground) {
                look.adaptiveBackground = it; look.save()
            }
            if (look.adaptiveBackground) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackgroundMode.entries.forEach { mode ->
                        FilterChip(
                            selected = look.backgroundMode == mode,
                            onClick = { look.backgroundMode = mode; look.save() },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Column {
                    Text("Intensity", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = look.intensity,
                        onValueChange = { look.intensity = it },
                        onValueChangeFinished = { look.save() },
                        valueRange = 0.2f..1f,
                    )
                }
            }

            GroupLabel("Screen")
            ToggleRow("Hide status bar", "Full screen. Swipe down from the top edge to peek at the clock and notifications.", look.hideStatusBar) {
                look.hideStatusBar = it; look.save()
            }

            GroupLabel("App icon")
            val context = LocalContext.current
            var chosenIcon by remember { mutableStateOf(AppIcons.current(context)) }
            Column {
                Text("Pick how Oris looks on your home screen. Your launcher may take a few seconds to update, and the app can close once while it switches.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppIcons.variants.forEach { v ->
                        val selected = v == chosenIcon
                        Column(
                            Modifier.width(76.dp).clip(MaterialTheme.shapes.medium)
                                .clickable { chosenIcon = v; AppIcons.select(context, v) }
                                .semantics { contentDescription = "App icon " + v.label },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(64.dp).clip(CircleShape)
                                    .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(painterResource(v.preview), null, Modifier.requiredSize(104.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(v.label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SurfacePreview() {
    androidx.compose.material3.Surface(shape = com.orismusic.client.ui.theme.OrisShapes.Panel,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text("♪", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall)
            }
            Column {
                Text("Made for your music", style = MaterialTheme.typography.titleSmall)
                Text("Live colour preview", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
