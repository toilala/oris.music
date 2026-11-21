package com.orismusic.client.ui.player

/*
 * The player layout, button shapes and motion in this file follow the Echo Music app
 * (GPL-3.0, https://github.com/EchoMusicApp/Echo-Music). All code here is Oris's own.
 */

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

// Fixed palette for the gradient player: light controls over a dark artwork wash.
internal val PlayerTextButtonColor = Color.White
internal val PlayerIconButtonColor = Color.Black
internal val PlayerSideButtonContainer = Color.White.copy(alpha = 0.2f)
internal val PlayerSideButtonContent = Color.White

// Asymmetric cluster shapes: the ends fan out, the middles stay square.
internal val ClusterStartShape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 7, bottomEndPercent = 7)
internal val ClusterEndShape = RoundedCornerShape(topStartPercent = 7, bottomStartPercent = 7, topEndPercent = 50, bottomEndPercent = 50)
internal val ClusterMiddleShape = RoundedCornerShape(3.dp)

/**
 * Rotating blob used for the play button while music is playing: a circle whose radius
 * breathes with a sine wave of [sides] bumps, spun slowly by the caller.
 */
internal class WavyShape(
    private val sides: Int,
    private val indent: Float,
    private val rotationDegrees: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rotationRad = rotationDegrees * Math.PI / 180.0
        val steps = 120
        for (i in 0..steps) {
            val angle = i * Math.PI * 2 / steps
            val bumpAngle = angle - rotationRad
            val r = 1f - indent + indent * cos(sides * bumpAngle)
            val x = (cx + cx * r * cos(angle)).toFloat()
            val y = (cy + cy * r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** The play/pause button: a white 88dp blob that turns wavy and slowly spins while playing. */
@Composable
internal fun PlayPauseBlob(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    wavy: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "play press")
    val indent by animateFloatAsState(if (playing) 0.08f else 0f, tween(300), label = "blob indent")
    val rotation = rememberInfiniteTransition(label = "blob spin")
    val degrees by rotation.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "blob rotation",
    )
    val shape: Shape = if (wavy && playing) WavyShape(9, indent, degrees) else RoundedCornerShape(26.dp)
    FilledIconButton(
        onClick = onClick,
        interactionSource = interaction,
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = PlayerTextButtonColor,
            contentColor = PlayerIconButtonColor,
        ),
        modifier = modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Icon(icon, contentDescription, Modifier.size(38.dp))
    }
}

/** Previous / next: 88dp frosted squircle flanking the play blob. */
@Composable
internal fun SkipSquircle(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "skip press")
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(26.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = PlayerSideButtonContainer,
            contentColor = PlayerSideButtonContent,
            disabledContainerColor = PlayerSideButtonContainer.copy(alpha = 0.1f),
            disabledContentColor = PlayerSideButtonContent.copy(alpha = 0.35f),
        ),
        modifier = modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Icon(icon, contentDescription, Modifier.size(36.dp))
    }
}

/**
 * Small 42dp cluster button used for the action row and the bottom bar.
 * Active fills solid white with a dark icon; inactive is a thin white outline.
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
    val border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, PlayerTextButtonColor.copy(alpha = 0.3f))
    val container by animateColorAsState(if (active) PlayerTextButtonColor else Color.Transparent, tween(200), label = "cluster bg")
    val contentColor = if (active) PlayerIconButtonColor else PlayerTextButtonColor
    val view = androidx.compose.ui.platform.LocalView.current
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
