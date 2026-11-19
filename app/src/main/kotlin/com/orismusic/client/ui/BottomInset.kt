package com.orismusic.client.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height of the floating nav bar. Lists add it as bottom content padding so they scroll behind it. */
val LocalBottomInset = compositionLocalOf { 0.dp }

@Composable
fun bottomInsetPadding(extra: Dp = 0.dp): PaddingValues = PaddingValues(bottom = LocalBottomInset.current + extra)
