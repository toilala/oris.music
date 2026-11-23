package com.orismusic.client.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.ArtworkStyle
import com.orismusic.client.ui.components.Artwork

/** One renderer for the player and settings. Frames never change the carousel's bounds. */
@Composable
fun StyledArtwork(url: String?, title: String?, modifier: Modifier = Modifier,
    style: ArtworkStyle = com.orismusic.client.settings.PlayerSettings.artworkStyle, sample: Boolean = false) {
    val ink = MaterialTheme.colorScheme.primary
    val shape = when(style) {
        ArtworkStyle.Disc, ArtworkStyle.Halo -> CircleShape
        ArtworkStyle.Arch -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 6, bottomEndPercent = 6)
        ArtworkStyle.Soft -> RoundedCornerShape(22)
        ArtworkStyle.Ticket -> RoundedCornerShape(topStartPercent = 3, topEndPercent = 25, bottomStartPercent = 25, bottomEndPercent = 3)
        else -> RoundedCornerShape(6.dp)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (style == ArtworkStyle.Halo) Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            drawCircle(ink.copy(alpha = .18f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        val inset = when(style) { ArtworkStyle.Halo -> 14.dp; ArtworkStyle.Tilt, ArtworkStyle.Float -> 16.dp; else -> 0.dp }
        if (style == ArtworkStyle.Float) Box(Modifier.fillMaxSize().padding(start = 25.dp, top = 26.dp, end = 7.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = .1f)))
        if (style == ArtworkStyle.Tilt) Box(Modifier.fillMaxSize().padding(16.dp).graphicsLayer { rotationZ = 3f }
            .clip(RoundedCornerShape(6.dp)).background(ink.copy(alpha = .18f)))
        Box(Modifier.fillMaxSize().padding(inset).graphicsLayer { rotationZ = if (style == ArtworkStyle.Tilt) -4f else 0f }
            .clip(shape).then(if(style == ArtworkStyle.Ticket) Modifier.border(1.dp, ink.copy(alpha = .4f), shape) else Modifier)) {
            if (sample) Canvas(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF754F49), Color(0xFF2A3543), Color(0xFF89836A))))) {
                drawCircle(Color(0xFFCFB5A1).copy(alpha=.48f), size.minDimension*.36f, androidx.compose.ui.geometry.Offset(size.width*.8f,size.height*.22f))
                drawCircle(Color(0xFF1C2E32).copy(alpha=.7f), size.minDimension*.5f, androidx.compose.ui.geometry.Offset(size.width*.1f,size.height*.85f))
            } else Artwork(url, Modifier.fillMaxSize(), shape, title, px = 1000)
            if (style == ArtworkStyle.Disc) {
                Canvas(Modifier.fillMaxSize()) {
                    listOf(.31f,.34f,.37f,.4f,.43f,.46f).forEach { drawCircle(Color.Black.copy(alpha=.3f), size.minDimension*it,
                        style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
                    drawCircle(Color(0xFF171717), size.minDimension*.1f)
                    drawCircle(ink, size.minDimension*.025f)
                }
            }
            if (style == ArtworkStyle.Ticket) Canvas(Modifier.fillMaxSize()) {
                val x=size.width*.84f
                var y=8.dp.toPx()
                while(y<size.height) { drawLine(ink.copy(alpha=.65f), androidx.compose.ui.geometry.Offset(x,y), androidx.compose.ui.geometry.Offset(x,y+3.dp.toPx()),1.dp.toPx());y+=8.dp.toPx() }
            }
        }
    }
}
