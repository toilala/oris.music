/*
 * Small reusable pieces of the Oris look: press feedback, shimmer skeletons,
 * artwork with crossfade, entry animations and artwork colour sampling.
 * Written for Oris.
 */
package com.orismusic.client.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import kotlinx.coroutines.delay

/** Springy press feedback: the element shrinks slightly while a finger is down. */
fun Modifier.pressClickable(onClick: () -> Unit, pressedScale: Float = 0.96f): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Springy press feedback with a long-press action, for chips that hide their menu. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.pressCombinedClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
}

/** Moving highlight for loading placeholders. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer x",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val shine = MaterialTheme.colorScheme.surfaceContainerHighest
    this.background(
        Brush.linearGradient(
            colors = listOf(base, shine, base),
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x + 400f, 200f),
        ),
    )
}

/** Fades and slides a list item in once, a little later for each index. */
fun Modifier.entrance(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * 45).toLong())
        progress.animateTo(1f, tween(380))
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36f
    }
}

/** Cover art with a soft placeholder and a crossfade when it arrives. */
/**
 * YouTube Music hands out small cover URLs (60 to 226 px). Their image host serves any size from the same
 * path, so ask for the size we actually draw. Other hosts are left alone.
 */
fun upscaleCover(url: String?, px: Int): String? {
    if (url.isNullOrBlank()) return url
    if (!url.contains("googleusercontent.com") && !url.contains("ggpht.com")) return url
    var out: String = url
    Regex("=w(\\d+)-h(\\d+)").find(out)?.let { m ->
        if (m.groupValues[1] == m.groupValues[2] && m.groupValues[1].toInt() < px) out = out.replace(m.value, "=w$px-h$px")
    }
    Regex("=s(\\d+)").find(out)?.let { m ->
        if (m.groupValues[1].toInt() < px) out = out.replace(m.value, "=s$px")
    }
    return out
}

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    contentDescription: String? = null,
    px: Int = 600,
) {
    val context = LocalContext.current
    val request = remember(url, px) { ImageRequest.Builder(context).data(upscaleCover(url, px)).crossfade(300).build() }
    var loaded by remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    var failed by remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    // A soft pulse while the cover loads, so empty boxes read as "loading" and not "broken".
    val base = MaterialTheme.colorScheme.surfaceVariant
    val pulse = if (loaded || failed || url.isNullOrBlank()) base else {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "cover pulse")
        val a by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "cover pulse alpha")
        androidx.compose.ui.graphics.lerp(base, MaterialTheme.colorScheme.surfaceContainerHigh, a)
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        onSuccess = { loaded = true },
        onError = { failed = true },
        modifier = modifier.clip(shape).background(pulse),
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One song line used across Library, queue and detail lists. */
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    thumbnail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .pressClickable(onClick, 0.98f)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(thumbnail, Modifier.size(54.dp), RoundedCornerShape(10.dp), px = 300)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** Placeholder card and row shapes shown while content loads. */
@Composable
fun SkeletonCard(size: androidx.compose.ui.unit.Dp = 140.dp) {
    Column(Modifier.width(size)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).shimmer())
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(5.dp)).shimmer())
    }
}

/**
 * Average colour of the artwork, leaning towards the more saturated pixels, so screens can
 * wear a tint of the song that is playing. Null until the image has loaded.
 */
@Composable
fun rememberArtworkColor(url: String?): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(null, url) {
        if (url == null) { value = null; return@produceState }
        val request = ImageRequest.Builder(context).data(url).size(48, 48).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap: Bitmap? = (result as? SuccessResult)?.image?.toBitmap()
        value = bitmap?.let { sampleColor(it) } ?: value
    }
    return color
}

private fun sampleColor(bitmap: Bitmap): Color {
    var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
    val w = bitmap.width; val h = bitmap.height
    val hsv = FloatArray(3)
    for (y in 0 until h step 2) for (x in 0 until w step 2) {
        val p = bitmap.getPixel(x, y)
        android.graphics.Color.colorToHSV(p, hsv)
        val wt = 0.15 + hsv[1] * hsv[2]
        r += android.graphics.Color.red(p) * wt
        g += android.graphics.Color.green(p) * wt
        b += android.graphics.Color.blue(p) * wt
        weight += wt
    }
    if (weight == 0.0) return Color.DarkGray
    return Color((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt())
}
