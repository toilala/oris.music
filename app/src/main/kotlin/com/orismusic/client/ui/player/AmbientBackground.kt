package com.orismusic.client.ui.player

import com.orismusic.client.ui.theme.OrisMotion
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.runtime.withFrameNanos

/**
 * Ambient player backdrop: three artwork colours drift through each other in one small shader.
 * The idea follows Mochy Music's flagship ambient backdrop (GPL-3.0); this shader and code are
 * Oris's own. Below Android 13 there is no RuntimeShader, so it falls back to a still colour.
 */
private const val AMBIENT_SHADER = """
uniform float2 uSize;
uniform float uTime;
uniform float3 uA;
uniform float3 uB;
uniform float3 uC;

half4 main(float2 p) {
    float2 uv = p / uSize;
    float t = uTime;
    float2 p1 = float2(0.5 + 0.34 * sin(t * 0.21), 0.5 + 0.34 * cos(t * 0.17));
    float2 p2 = float2(0.5 + 0.38 * sin(t * 0.13 + 2.1), 0.5 + 0.38 * cos(t * 0.19 + 1.3));
    float2 p3 = float2(0.5 + 0.30 * cos(t * 0.11 + 4.2), 0.5 + 0.33 * sin(t * 0.15 + 3.1));
    float w1 = 1.0 / (0.20 + 4.0 * dot(uv - p1, uv - p1));
    float w2 = 1.0 / (0.20 + 4.0 * dot(uv - p2, uv - p2));
    float w3 = 1.0 / (0.20 + 4.0 * dot(uv - p3, uv - p3));
    float sum = w1 + w2 + w3;
    float3 col = (uA * w1 + uB * w2 + uC * w3) / sum;
    col *= 0.55;
    return half4(col, 1.0);
}
"""

@Composable
fun AmbientBackground(a: Color, b: Color, c: Color, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 33) {
        Box(modifier.background(a))
        return
    }
    val shader = remember { android.graphics.RuntimeShader(AMBIENT_SHADER) }
    // Colours ease over a beat so a song change never pops.
    val animA by animateColorAsState(a, OrisMotion.entrance(), label = "ambient a")
    val animB by animateColorAsState(b, OrisMotion.entrance(), label = "ambient b")
    val animC by animateColorAsState(c, OrisMotion.entrance(), label = "ambient c")
    // Free-running clock in seconds; the shader's drift loops wrap cleanly at 100s.
    val clock = produceState(0f) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> value = ((now - start) / 1_000_000_000f) % 100f }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("uSize", floatArrayOf(size.width, size.height))
        shader.setFloatUniform("uTime", clock.value)
        shader.setFloatUniform("uA", floatArrayOf(animA.red, animA.green, animA.blue))
        shader.setFloatUniform("uB", floatArrayOf(animB.red, animB.green, animB.blue))
        shader.setFloatUniform("uC", floatArrayOf(animC.red, animC.green, animC.blue))
        drawRect(ShaderBrush(shader))
    }
}
