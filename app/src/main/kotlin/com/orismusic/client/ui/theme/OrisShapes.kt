package com.orismusic.client.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** One corner ladder for surfaces that aren't on the user's own Home corner dial:
 *  tight covers, rows a little softer than their cover, one top curve for sheets. */
object OrisShapes {
    val Thumb = RoundedCornerShape(6.dp)
    val Row = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(12.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Panel = RoundedCornerShape(24.dp)
}
