package com.orismusic.client.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** The app's one motion scale: pick a named feel instead of inventing a number at the call
 *  site. snap() stays the zero. Two specs stay local on purpose: the dock lens stretch and
 *  the dock hide tuck (both directional finger effects, not shared feels). */
object OrisMotion {
    const val Micro = 140
    const val Quick = 250
    const val Standard = 350
    const val Slow = 650
    const val Entrance = 900

    const val WavePeriod = 1700
    const val ShimmerPeriod = 1300
    const val OrbitPeriod = 6000
    const val BreathPeriod = 4200
    const val Drift = 13000
    const val SlowDrift = 17000

    fun <T> micro(delayMillis: Int = 0, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(Micro, delayMillis, easing)

    fun <T> quick(delayMillis: Int = 0, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(Quick, delayMillis, easing)

    fun <T> standard(delayMillis: Int = 0, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(Standard, delayMillis, easing)

    fun <T> slow(delayMillis: Int = 0, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(Slow, delayMillis, easing)

    fun <T> entrance(delayMillis: Int = 0, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(Entrance, delayMillis, easing)

    /** Playful pop: no overshoot, low stiffness (player open/close). */
    fun <T> pop(): SpringSpec<T> = spring(1f, 195f)

    /** No-overshoot slide. */
    fun <T> smooth(): SpringSpec<T> = spring(1f, 300f)

    /** Default spring for dots, offsets and thumbs. */
    fun <T> settle(): SpringSpec<T> = spring(0.6f, 400f)

    /** Calm settle with a little give. */
    fun <T> soft(): SpringSpec<T> = spring(0.8f, 380f)

    /** Finger-down feedback. */
    fun <T> press(): SpringSpec<T> = spring(0.5f, 500f)
}
