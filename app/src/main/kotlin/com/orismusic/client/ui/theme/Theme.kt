package com.orismusic.client.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * OrisMusic renders dark-only.
 *
 * The light palette was never finished - large parts of the UI hardcode white text and dark
 * glass - so on a light-mode phone the app looked broken rather than light. Pinned here as one
 * switch instead of being decided independently at every call site.
 */
const val ForceDarkTheme = true

@Composable
fun OrisTheme(
    darkTheme: Boolean = ForceDarkTheme,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    com.orismusic.client.settings.AppearanceSettings.init(context)
    val useDynamic = com.orismusic.client.settings.AppearanceSettings.dynamicColor && Build.VERSION.SDK_INT >= 31
    val amoledOn = com.orismusic.client.settings.AppearanceSettings.amoled

    // Dynamic (Material You) colour is deliberately not used - the app ships its own palette.
    // The branch is gone rather than dead-but-present: dynamic*ColorScheme is API 31.
    val baseColorScheme = if (useDynamic) androidx.compose.material3.dynamicDarkColorScheme(context) else run {

        if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFFE9E4DC),
                onPrimary = Color(0xFF151413),
                primaryContainer = Color(0xFF2E2D2B),
                onPrimaryContainer = Color(0xFFF1EDE6),
                secondary = Color(0xFFB9B4AC),
                onSecondary = Color(0xFF151413),
                secondaryContainer = Color(0xFF2B2A29),
                onSecondaryContainer = Color(0xFFEDE9E2),
                tertiary = Color(0xFFD9A7A0),
                onTertiary = Color(0xFF2A1210),
                tertiaryContainer = Color(0xFF3B2B29),
                onTertiaryContainer = Color(0xFFF3DCD8),
                background = Color(0xFF0C0C0D),
                onBackground = Color(0xFFEDEBE8),
                surface = Color(0xFF0C0C0D),
                onSurface = Color(0xFFEDEBE8),
                surfaceVariant = Color(0xFF191919),
                onSurfaceVariant = Color(0xFFA09C96),
                surfaceTint = Color(0xFF0C0C0D),
                surfaceContainer = Color(0xFF151515),
                surfaceContainerLow = Color(0xFF111112),
                surfaceContainerHigh = Color(0xFF1E1E1F),
                surfaceContainerHighest = Color(0xFF282829),
                outline = Color(0xFF6B6863),
                outlineVariant = Color(0xFF2E2D2B),
            )
        } else {
            lightColorScheme(
                primary = Color.Black,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE0E0E0),
                onPrimaryContainer = Color.Black,
                secondary = Color(0xFF333333),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCCCCCC),
                onSecondaryContainer = Color.Black,
                background = Color.White,
                onBackground = Color.Black,
                surface = Color.White,
                onSurface = Color.Black,
                surfaceVariant = Color(0xFFF0F0F0),
                onSurfaceVariant = Color.Black,
                surfaceTint = Color.White
            )
        }
    }

    // Accent seed tints the Oris palette; Material You supplies its own accent.
    val seed = com.orismusic.client.settings.AppearanceSettings.accent
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, amoledOn, seed) {
        var scheme = baseColorScheme
        if (!useDynamic && darkTheme) {
            scheme = scheme.copy(
                tertiary = seed.accent,
                onTertiary = seed.onAccent,
                tertiaryContainer = seed.container,
                onTertiaryContainer = seed.onContainer,
            )
        }
        if (darkTheme && (pureBlack || amoledOn)) {
            scheme.copy(
                surface = Color.Black,
                background = Color.Black,
                surfaceContainerLow = Color.Black,
            )
        } else {
            scheme
        }
    }

    val font = InterFontFamily
    val typography = remember(font) { appTypography(font) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(40.dp),
        ),
    ) {
        ProvideTextStyle(
            value = typography.bodyLarge,
            content = content
        )
    }
}