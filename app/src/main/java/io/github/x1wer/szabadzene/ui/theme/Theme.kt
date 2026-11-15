package io.github.x1wer.szabadzene.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.x1wer.szabadzene.data.settings.AccentColor

/** Saját, zöldeskék alapszín – Android 12 előtt, vagy ha a dinamikus szín ki van kapcsolva. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA8F0DA),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8DD),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF416277),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC5E7FF),
    onTertiaryContainer = Color(0xFF001E2D),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF181D1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF181D1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F2),
    surfaceContainer = Color(0xFFEBEFEC),
    surfaceContainerHigh = Color(0xFFE6E9E7),
    surfaceContainerHighest = Color(0xFFE0E3E1),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFFA8F0DA),
    secondary = Color(0xFFB1CCC1),
    onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF344C44),
    onSecondaryContainer = Color(0xFFCDE8DD),
    tertiary = Color(0xFFA8CBE3),
    onTertiary = Color(0xFF0F3447),
    tertiaryContainer = Color(0xFF284B5E),
    onTertiaryContainer = Color(0xFFC5E7FF),
    background = Color(0xFF101513),
    onBackground = Color(0xFFE0E3E1),
    surface = Color(0xFF101513),
    onSurface = Color(0xFFE0E3E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color(0xFF0B0F0E),
    surfaceContainerLow = Color(0xFF181D1B),
    surfaceContainer = Color(0xFF1C211F),
    surfaceContainerHigh = Color(0xFF262B29),
    surfaceContainerHighest = Color(0xFF313634),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
)

/** A kiemelőszín elsődleges színei világos és sötét témához. A zöld az alapértelmezett. */
private data class AccentPalette(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
)

private fun palette(accent: AccentColor): AccentPalette? = when (accent) {
    AccentColor.GREEN -> null
    AccentColor.BLUE -> AccentPalette(
        Color(0xFF2F5DA8), Color.White, Color(0xFFD7E3FF), Color(0xFF001B3E),
        Color(0xFFABC7FF), Color(0xFF002F65), Color(0xFF11458E), Color(0xFFD7E3FF),
    )
    AccentColor.PURPLE -> AccentPalette(
        Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D),
        Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF),
    )
    AccentColor.ORANGE -> AccentPalette(
        Color(0xFF9A4600), Color.White, Color(0xFFFFDBC8), Color(0xFF331200),
        Color(0xFFFFB68B), Color(0xFF532200), Color(0xFF763300), Color(0xFFFFDBC8),
    )
    AccentColor.RED -> AccentPalette(
        Color(0xFFB3261E), Color.White, Color(0xFFF9DEDC), Color(0xFF410E0B),
        Color(0xFFF2B8B5), Color(0xFF601410), Color(0xFF8C1D18), Color(0xFFF9DEDC),
    )
}

@Composable
fun SzabadZeneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accent: AccentColor = AccentColor.GREEN,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val base = if (darkTheme) DarkColors else LightColors
            val p = palette(accent)
            when {
                p == null -> base
                darkTheme -> base.copy(
                    primary = p.darkPrimary,
                    onPrimary = p.darkOnPrimary,
                    primaryContainer = p.darkContainer,
                    onPrimaryContainer = p.darkOnContainer,
                )
                else -> base.copy(
                    primary = p.lightPrimary,
                    onPrimary = p.lightOnPrimary,
                    primaryContainer = p.lightContainer,
                    onPrimaryContainer = p.lightOnContainer,
                )
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
