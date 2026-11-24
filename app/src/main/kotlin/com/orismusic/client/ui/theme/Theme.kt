package com.orismusic.client.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.orismusic.client.settings.AppearanceSettings
import com.orismusic.client.settings.ThemeMode

/**
 * One Oris look in two lights. Dark is warm charcoal, light is warm paper; both share the
 * same accent, shapes and type so the app reads as one thing day and night. The player and
 * lyrics stay a dark room on purpose: that is where the artwork glows.
 */
@Composable
fun OrisTheme(
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    AppearanceSettings.init(context)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (AppearanceSettings.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val useDynamic = AppearanceSettings.dynamicColor && Build.VERSION.SDK_INT >= 31
    val amoledOn = AppearanceSettings.amoled

    val baseColorScheme = if (useDynamic) {
        if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(context)
        else androidx.compose.material3.dynamicLightColorScheme(context)
    } else if (darkTheme) {
        // Warm charcoal. Never pure grey: every step leans a little brown so the
        // dark app feels like a dim room, not a void.
        darkColorScheme(
            primary = Color(0xFFEDE6DB),
            onPrimary = Color(0xFF1A1613),
            primaryContainer = Color(0xFF322C26),
            onPrimaryContainer = Color(0xFFF1EBE1),
            secondary = Color(0xFFBEB4A7),
            onSecondary = Color(0xFF1A1613),
            secondaryContainer = Color(0xFF2E2924),
            onSecondaryContainer = Color(0xFFEDE6DB),
            tertiary = Color(0xFFD9A7A0),
            onTertiary = Color(0xFF2A1210),
            tertiaryContainer = Color(0xFF3B2B29),
            onTertiaryContainer = Color(0xFFF3DCD8),
            background = Color(0xFF131110),
            onBackground = Color(0xFFEFE9E1),
            surface = Color(0xFF131110),
            onSurface = Color(0xFFEFE9E1),
            surfaceVariant = Color(0xFF1D1A17),
            onSurfaceVariant = Color(0xFFA79D91),
            surfaceTint = Color(0xFFEDE6DB),
            surfaceContainerLowest = Color(0xFF0E0C0A),
            surfaceContainerLow = Color(0xFF17140F),
            surfaceContainer = Color(0xFF1C1915),
            surfaceContainerHigh = Color(0xFF242019),
            surfaceContainerHighest = Color(0xFF2E2822),
            outline = Color(0xFF6F665B),
            outlineVariant = Color(0xFF35302A),
        )
    } else {
        // Warm paper. Soft cream surfaces with ink text; the accent deepens so it
        // still reads on the light ground.
        lightColorScheme(
            primary = Color(0xFF292421),
            onPrimary = Color(0xFFFBF7F1),
            primaryContainer = Color(0xFFE8E0D5),
            onPrimaryContainer = Color(0xFF2A2520),
            secondary = Color(0xFF6B6157),
            onSecondary = Color(0xFFFBF7F1),
            secondaryContainer = Color(0xFFE4DCD1),
            onSecondaryContainer = Color(0xFF2A2520),
            tertiary = Color(0xFFA15B53),
            onTertiary = Color(0xFFFFFAF7),
            tertiaryContainer = Color(0xFFF4D9D3),
            onTertiaryContainer = Color(0xFF59302B),
            background = Color(0xFFFAF6F0),
            onBackground = Color(0xFF211C17),
            surface = Color(0xFFFAF6F0),
            onSurface = Color(0xFF211C17),
            surfaceVariant = Color(0xFFEAE3D9),
            onSurfaceVariant = Color(0xFF6E6459),
            surfaceTint = Color(0xFF292421),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF3EDE5),
            surfaceContainer = Color(0xFFEDE6DB),
            surfaceContainerHigh = Color(0xFFE7DFD3),
            surfaceContainerHighest = Color(0xFFE0D7C9),
            outline = Color(0xFF9C9184),
            outlineVariant = Color(0xFFD9CFC1),
        )
    }

    // Accent seed tints the Oris palette; Material You supplies its own accent.
    val seed = AppearanceSettings.accent
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, amoledOn, seed) {
        var scheme = baseColorScheme
        if (!useDynamic) {
            scheme = scheme.copy(
                tertiary = if (darkTheme) seed.accent else seed.lightAccent,
                onTertiary = if (darkTheme) seed.onAccent else seed.lightOnAccent,
                tertiaryContainer = if (darkTheme) seed.container else seed.lightContainer,
                onTertiaryContainer = if (darkTheme) seed.onContainer else seed.lightOnContainer,
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
