package com.orismusic.client.ui.player

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** A window that opens from [from] (the mini player) to the whole screen as [t] goes 0 to 1. */
class RevealShape(private val t: Float, private val from: Rect?) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val start = from ?: Rect(0f, size.height - 160f, size.width, size.height - 80f)
        fun mix(a: Float, b: Float) = a + (b - a) * t
        val radius = mix(start.height / 2f, 0f).coerceAtLeast(0f)
        return Outline.Rounded(
            RoundRect(
                mix(start.left, 0f), mix(start.top, 0f), mix(start.right, size.width), mix(start.bottom, size.height),
                CornerRadius(radius),
            ),
        )
    }
}
