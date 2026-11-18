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
                primary = Color(0xFFC6FF3D),
                onPrimary = Color(0xFF14110A),
                primaryContainer = Color(0xFF3A4A0E),
                onPrimaryContainer = Color(0xFFE6FFA0),
                secondary = Color(0xFFFF6B57),
                onSecondary = Color(0xFF2A0A04),
                secondaryContainer = Color(0xFF4A1F18),
                onSecondaryContainer = Color(0xFFFFD9D2),
                tertiary = Color(0xFFB28CFF),
                onTertiary = Color(0xFF1C0B3D),
                tertiaryContainer = Color(0xFF3A2A66),
                onTertiaryContainer = Color(0xFFE6DAFF),
                background = Color(0xFF0F0A16),
                onBackground = Color(0xFFF6F2FF),
                surface = Color(0xFF0F0A16),
                onSurface = Color(0xFFF6F2FF),
                surfaceVariant = Color(0xFF1B1426),
                onSurfaceVariant = Color(0xFFAAA0BE),
                surfaceTint = Color(0xFF0F0A16),
                surfaceContainer = Color(0xFF1A1325),
                surfaceContainerLow = Color(0xFF140E1E),
                surfaceContainerHigh = Color(0xFF221A30),
                surfaceContainerHighest = Color(0xFF2C2340),
                outline = Color(0xFF6F6488),
                outlineVariant = Color(0xFF32283F),
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

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, amoledOn) {
        if (darkTheme && (pureBlack || amoledOn)) {
            baseColorScheme.copy(
                surface = Color.Black,
                background = Color.Black,
                surfaceContainerLow = Color.Black,
            )
        } else {
            baseColorScheme
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