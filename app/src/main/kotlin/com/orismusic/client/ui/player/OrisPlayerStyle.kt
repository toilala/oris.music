package com.orismusic.client.ui.player

/*
 * Oris's own player control language: the orbit from the Oris mark (a clean disc with a
 * satellite dot circling it), ghost ring skip keys, and uniform soft-square chips.
 * Replaced the earlier reference-influenced blob and segmented cluster shapes.
 */

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// Light controls over the dark player wash.
internal val PlayerTextButtonColor = Color.White
internal val PlayerIconButtonColor = Color.Black

/** One chip shape for every small player button: a soft square, no fan-out ends. */
internal val PlayerChipShape = RoundedCornerShape(14.dp)

/**
 * The play/pause key: a clean white disc. While music is playing and [orbit] is on, a thin
 * ring sits around the disc with one bright satellite dot circling it, the orbit from the
 * Oris mark. Paused, the ring rests. The disc squeezes under the finger.
 */
@Composable
internal fun OrbitPlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    orbit: Boolean = true,
    style: com.orismusic.client.settings.PlayButtonStyle = com.orismusic.client.settings.PlayButtonStyle.Orbit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "play press")
    val ringAlpha by animateFloatAsState(if (orbit && style == com.orismusic.client.settings.PlayButtonStyle.Orbit && playing) 1f else 0f, tween(350), label = "orbit ring")
    val spin = rememberInfiniteTransition(label = "orbit spin")
    val degrees by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "orbit degrees",
    )
    Box(
        modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        // Disc.
        val phase by spin.animateFloat(0f, 1f,
            infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "shape breath")
        val active by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "shape active")
        Canvas(Modifier.size(76.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            when (style) {
                com.orismusic.client.settings.PlayButtonStyle.Pebble,
                com.orismusic.client.settings.PlayButtonStyle.Bloom -> {
                    val bloom = style == com.orismusic.client.settings.PlayButtonStyle.Bloom
                    val path = androidx.compose.ui.graphics.Path()
                    for (step in 0..160) {
                        val theta = step / 160.0 * Math.PI * 2
                        val breath = sin(phase * Math.PI * 2).toFloat() * active
                        val rr = radius * if (bloom) (0.91f + 0.065f * cos(theta * 6 + breath * 0.35).toFloat())
                            else (0.93f + 0.04f * sin(theta * 3 + 0.6 + breath * 0.4).toFloat())
                        val x = c.x + rr * cos(theta).toFloat()
                        val y = c.y + rr * sin(theta).toFloat()
                        if (step == 0) path.moveTo(x,y) else path.lineTo(x,y)
                    }
                    path.close(); drawPath(path, PlayerTextButtonColor)
                }
                else -> drawCircle(PlayerTextButtonColor, radius = radius * if (style == com.orismusic.client.settings.PlayButtonStyle.Ripple) 0.85f else 1f)
            }
            if (style == com.orismusic.client.settings.PlayButtonStyle.Ripple) {
                val pulse = (sin(phase * Math.PI * 2).toFloat() * 0.5f + 0.5f) * active
                drawCircle(PlayerTextButtonColor.copy(alpha = 0.3f + 0.2f * pulse), radius * (0.94f + 0.04f * pulse), style = Stroke(1.dp.toPx()))
            }
        }
        // Orbit: a hairline ring with one satellite dot, drawn only while playing.
        if (ringAlpha > 0.01f) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 2.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    PlayerTextButtonColor.copy(alpha = 0.45f * ringAlpha),
                    radius = radius,
                    center = center,
                    style = Stroke(1.6.dp.toPx()),
                )
                val rad = Math.toRadians(degrees.toDouble())
                val dot = Offset(
                    (center.x + radius * cos(rad)).toFloat(),
                    (center.y + radius * sin(rad)).toFloat(),
                )
                drawCircle(PlayerTextButtonColor.copy(alpha = ringAlpha), radius = 3.dp.toPx(), center = dot)
            }
        }
        Icon(icon, contentDescription, Modifier.size(36.dp), tint = PlayerIconButtonColor)
    }
}

/**
 * Previous / next: a ghost ring the icon sits inside. Pressing leans the key the way it
 * goes and the ring brightens. No fill, no squircle slab.
 */
@Composable
internal fun GhostSkipButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    direction: Int,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "skip press")
    val lean by animateFloatAsState(if (pressed) 5f * direction else 0f, spring(dampingRatio = 0.5f, stiffness = 450f), label = "skip lean")
    val ringAlpha by animateFloatAsState(if (pressed) 0.8f else 0.35f, tween(180), label = "skip ring")
    Box(
        modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale; translationX = lean.dp.toPx() }
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Canvas(Modifier.size(64.dp)) {
            drawCircle(
                PlayerTextButtonColor.copy(alpha = ringAlpha),
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(1.4.dp.toPx()),
            )
        }
        Icon(icon, contentDescription, Modifier.size(32.dp), tint = PlayerTextButtonColor)
    }
}

/**
 * Small 42dp chip used for the action row and the bottom bar. One soft-square shape for
 * every chip; active fills solid white with a dark icon, inactive is a hairline ring.
 */
@Composable
internal fun ClusterButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, PlayerTextButtonColor.copy(alpha = 0.22f))
    val container by animateColorAsState(if (active) PlayerTextButtonColor else Color.Transparent, tween(200), label = "chip bg")
    val contentColor = if (active) PlayerIconButtonColor else PlayerTextButtonColor
    val view = LocalView.current
    androidx.compose.material3.Surface(
        onClick = {
            if (com.orismusic.client.settings.PlayerSettings.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        enabled = enabled,
        shape = shape,
        color = container,
        border = border,
        modifier = modifier.size(42.dp).alpha(if (enabled) 1f else 0.35f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            if (content != null) content() else Icon(icon, contentDescription, Modifier.size(22.dp), tint = contentColor)
        }
    }
}

/** Slim seek track: a 10dp rounded bar, no thumb, active portion in solid white. */
@Composable
internal fun SlimSliderTrack(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val height = 10.dp.toPx()
        val top = (size.height - height) / 2f
        val corner = CornerRadius(height / 2f)
        drawRoundRect(
            color = PlayerTextButtonColor.copy(alpha = 0.4f),
            topLeft = Offset(0f, top),
            size = Size(size.width, height),
            cornerRadius = corner,
        )
        val activeWidth = (size.width * fraction.coerceIn(0f, 1f))
        if (activeWidth > 0f) {
            drawRoundRect(
                color = PlayerTextButtonColor,
                topLeft = Offset(0f, top),
                size = Size(activeWidth, height),
                cornerRadius = corner,
            )
        }
    }
}

/** Heart that bursts: a quick scale pop plus an expanding fading ring when a song gets liked. */
@Composable
internal fun HeartBurstIcon(liked: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) { first = false; return@LaunchedEffect }
        if (liked) {
            scale.snapTo(0.6f)
            ring.snapTo(0f)
            kotlinx.coroutines.coroutineScope {
                launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 400f)) }
                launch { ring.animateTo(1f, tween(450)) }
            }
        }
    }
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (ring.value in 0.01f..0.99f) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f * (0.5f + ring.value * 0.9f)
                drawCircle(PlayerTextButtonColor.copy(alpha = (1f - ring.value) * 0.6f), radius = r)
            }
        }
        Icon(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/**
 * Artwork-driven background colours: the strongest swatch, softened for a dark screen,
 * fading to black. Returns null until the artwork has been sampled.
 */
@Composable
internal fun rememberPlayerGradientColors(url: String?): List<Color>? {
    val context = LocalContext.current
    val colors by produceState<List<Color>?>(null, url) {
        if (url.isNullOrBlank()) { value = null; return@produceState }
        val request = ImageRequest.Builder(context).data(url).size(CoilSize(100, 100)).allowHardware(false).build()
        val bitmap: Bitmap? = runCatching {
            (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
        }.getOrNull()
        value = bitmap?.let { bmp ->
            val palette = Palette.from(bmp).maximumColorCount(8).generate()
            val swatches = palette.swatches.filter { swatch ->
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                hsv[1] > 0.15f && hsv[2] > 0.15f
            }.ifEmpty { palette.swatches }
            val best = swatches.maxByOrNull { swatch ->
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                val vibrantBonus = if (hsv[1] > 0.3f && hsv[2] > 0.3f) 1.5f else 1f
                swatch.population * 2f * vibrantBonus * (hsv[1] + hsv[2]) / 2f
            } ?: return@let null
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(best.rgb, hsv)
            val vibrant = hsv[1] > 0.25f && hsv[2] in 0.2f..0.9f
            hsv[1] = (hsv[1] * if (vibrant) 1.3f else 1.1f).coerceAtMost(1f)
            hsv[2] = (hsv[2] * 0.9f).coerceIn(0.4f, 0.85f)
            val primary = Color(android.graphics.Color.HSVToColor(hsv))
            listOf(
                primary,
                primary.copy(alpha = 0.6f),
                Color.Black,
            )
        }
    }
    return colors
}

/** Zero-size thumb so the Material slider keeps its gestures without drawing a knob. */
@Composable
internal fun InvisibleSliderThumb() {
    Spacer(Modifier.size(0.dp))
}
