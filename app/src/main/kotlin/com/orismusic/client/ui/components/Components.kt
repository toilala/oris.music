/*
 * Small reusable pieces of the Oris look: press feedback, shimmer skeletons,
 * artwork with crossfade, entry animations and artwork colour sampling.
 * Written for Oris.
 */
package com.orismusic.client.ui.components

import com.orismusic.client.ui.theme.OrisDimens
import com.orismusic.client.ui.theme.OrisMotion
import com.orismusic.client.ui.theme.OrisShapes
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import android.provider.Settings
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

/** True when the user asked the OS for no animations (animator duration scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        // The OS "Remove animations" toggle writes the Global namespace; the System
        // constant is deprecated. Read Global first, fall back to System on devices
        // where the setting still lives there.
        val global = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, Float.NaN,
        )
        if (!global.isNaN()) global == 0f
        else Settings.System.getFloat(
            context.contentResolver, Settings.System.ANIMATOR_DURATION_SCALE, 1f,
        ) == 0f
    }
}

/** Springy press feedback: the element shrinks slightly while a finger is down. */
fun Modifier.pressClickable(onClick: () -> Unit, pressedScale: Float = 0.96f): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (rememberReducedMotion()) snap() else OrisMotion.press(),
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
        animationSpec = if (rememberReducedMotion()) snap() else OrisMotion.press(),
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
        animationSpec = infiniteRepeatable(tween(OrisMotion.ShimmerPeriod, easing = LinearEasing), RepeatMode.Restart),
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

/**
 * Which rows already played their entrance, for a screen that is rebuilt when you come back to it.
 * Marks from a stale epoch (a refresh cleared the set meanwhile) are dropped, and a new scope
 * (another account or session) starts empty.
 */
class EntranceSeen {
    private val slots = HashSet<Int>()
    private var scope: Any? = null
    var epoch = 0
        private set

    fun has(slot: Int) = slot in slots
    fun mark(slot: Int, markEpoch: Int) { if (markEpoch == epoch) slots.add(slot) }
    fun clear() { epoch++; slots.clear() }
    fun enter(newScope: Any?) { if (newScope != scope) { scope = newScope; clear() } }
}

/** Null means every row always plays its entrance. */
val LocalEntranceSeen = androidx.compose.runtime.staticCompositionLocalOf<EntranceSeen?> { null }

/** Fades and slides a list item in once, 55ms after the row before it. */
fun Modifier.entrance(index: Int = 0): Modifier = composed {
    val reduced = rememberReducedMotion()
    val seen = LocalEntranceSeen.current
    val slot = androidx.compose.runtime.currentCompositeKeyHash
    val epoch = remember { seen?.epoch ?: 0 }
    val already = remember { seen?.has(slot) == true }
    val progress = remember { Animatable(if (reduced || already) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced || already) return@LaunchedEffect
        delay((index.coerceAtMost(8) * 55).toLong())
        progress.animateTo(1f, OrisMotion.standard())
        seen?.mark(slot, epoch)
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36f
    }
}

/**
 * Covers that have already drawn once in this process. A card that comes back on screen can then
 * offer its shared-cover key from the first frame, before Coil reports success again.
 */
object LoadedCovers {
    private val urls = LinkedHashSet<String>()
    @Synchronized fun has(url: String?) = url != null && url in urls
    @Synchronized fun add(url: String?) {
        if (url.isNullOrBlank()) return
        urls.remove(url)
        urls.add(url)
        while (urls.size > 400) urls.remove(urls.first())
    }
    @Synchronized fun clear() = urls.clear()
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
    onLoaded: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = remember(url, px) { ImageRequest.Builder(context).data(upscaleCover(url, px)).crossfade(300).build() }
    var loaded by remember(url) { androidx.compose.runtime.mutableStateOf(LoadedCovers.has(url)) }
    var failed by remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    // A soft pulse while the cover loads, so empty boxes read as "loading" and not "broken".
    val base = MaterialTheme.colorScheme.surfaceVariant
    val pulse = if (loaded || failed || url.isNullOrBlank()) base else {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "cover pulse")
        val a by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
            OrisMotion.entrance(easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "cover pulse alpha")
        androidx.compose.ui.graphics.lerp(base, MaterialTheme.colorScheme.surfaceContainerHigh, a)
    }
    // A cover that is missing or fails to load gets a considered stand-in, not a hole:
    // a quiet two-tone wash seeded from the title, with a single note at its centre.
    if (url.isNullOrBlank() || failed) {
        val seed = kotlin.math.abs((contentDescription ?: url ?: "oris").hashCode())
        val hues = listOf(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
        )
        val hue = hues[seed % hues.size]
        val depth = 0.16f + (seed / 11 % 18) / 100f
        val low = androidx.compose.ui.graphics.lerp(base, hue, depth)
        val high = androidx.compose.ui.graphics.lerp(base, hue, depth + 0.2f)
        Box(
            modifier.clip(shape).background(Brush.linearGradient(listOf(low, high))),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
        return
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        onSuccess = { loaded = true; LoadedCovers.add(url); onLoaded?.invoke() },
        onError = { failed = true },
        modifier = modifier.clip(shape).background(pulse),
    )
}


@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        // The Oris signature: a small satellite dot ahead of a serif title.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.tertiary))
            Spacer(Modifier.width(9.dp))
            Text(title, style = MaterialTheme.typography.titleLarge,
                fontFamily = com.orismusic.client.ui.theme.DisplaySerif,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 15.dp))
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
            .heightIn(min = OrisDimens.ListRowHeight)
            .padding(horizontal = 12.dp)
            .clip(OrisShapes.Row)
            .background(if (highlight) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .pressClickable(onClick, 0.98f)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(thumbnail, Modifier.size(OrisDimens.ListThumb), OrisShapes.Thumb, px = 300)
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
        Box(Modifier.size(size).clip(OrisShapes.Card).shimmer())
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

/** The screen wears a tint of the given artwork, dissolving into the page background. */
fun Modifier.artworkTintBackdrop(url: String?, strength: Float = 0.3f): Modifier = composed {
    val bg = MaterialTheme.colorScheme.background
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = rememberArtworkColor(url) ?: bg,
        animationSpec = OrisMotion.slow(),
        label = "artwork tint",
    )
    val extent = with(androidx.compose.ui.platform.LocalDensity.current) { 420.dp.toPx() }
    this.background(
        Brush.verticalGradient(listOf(tint.copy(alpha = strength), bg), endY = extent),
    )
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

@Composable
fun AsyncError(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    details: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (onRetry != null) TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Try again") }
        if (details != null) {
            var show by remember { mutableStateOf(false) }
            Text(
                text = if (show) "Hide details" else "Details",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp))
                    .clickable { show = !show }.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (show) Text(
                text = details,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Name-and-create dialog for the song menus' "New playlist" action: creating from a
 *  song menu adds that song to the fresh list, so the action never dead-ends. */
@Composable
fun CreatePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
