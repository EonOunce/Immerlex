package com.immerlex.app.ui

import android.app.Activity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.immerlex.core.db.WordEntity
import com.immerlex.core.settings.Appearance
import com.immerlex.core.word.Mastery
import com.immerlex.core.word.mastery

// Design tokens transcribed from prototype/reading.html (editorial paper/rust palette).
object ImmerlexColors {
    val Paper = Color(0xFFF5F4F0)
    val Card = Color(0xFFFBFAF7)
    val Ink = Color(0xFF141414)
    val Body = Color(0xFF5C6070)
    val Meta = Color(0xFF878C9F)
    val Line = Color(0xFFE4E2DB)
    val Rust = Color(0xFFBC4B12)
}

/** Highlight colours for the three mastery states; light and dark sets come from the prototype. */
data class MasteryTones(
    val new: Pair<Color, Color>,
    val learning: Pair<Color, Color>,
    val mastered: Pair<Color, Color>,
) {
    fun of(mastery: Mastery): Pair<Color, Color> = when (mastery) {
        Mastery.NEW -> new
        Mastery.LEARNING -> learning
        Mastery.MASTERED -> mastered
    }
}

internal val LightTones = MasteryTones(
    new = Color(0xFFF6E3D7) to Color(0xFFA03D0C),
    learning = Color(0xFFDCEFE0) to Color(0xFF2F7D4F),
    mastered = Color(0xFFDBE7F5) to Color(0xFF2B6CB0),
)

private val DarkTones = MasteryTones(
    new = Color(0xFF3A2417) to Color(0xFFE8A071),
    learning = Color(0xFF1D3125) to Color(0xFF8FCEA5),
    mastered = Color(0xFF1C2A3A) to Color(0xFF8FB8E8),
)

val LocalMasteryTones = staticCompositionLocalOf { LightTones }
val LocalAppearance = compositionLocalOf { Appearance() }

private val LightScheme = lightColorScheme(
    primary = ImmerlexColors.Rust,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3D7),
    onPrimaryContainer = Color(0xFF7A2E06),
    secondary = Color(0xFF7A5C48),
    onSecondary = Color(0xFFFFF8F2),
    secondaryContainer = Color(0xFFF1E7DF),
    onSecondaryContainer = Color(0xFF3A2A20),
    tertiary = Color(0xFF4F6B57),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCEFE0),
    onTertiaryContainer = Color(0xFF1F3A28),
    background = ImmerlexColors.Paper,
    onBackground = ImmerlexColors.Ink,
    surface = ImmerlexColors.Card,
    onSurface = ImmerlexColors.Ink,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFBF8),
    surfaceContainer = ImmerlexColors.Card,
    surfaceContainerHigh = Color(0xFFF4F2EC),
    surfaceContainerHighest = Color(0xFFEDEAE3),
    inverseSurface = Color(0xFF1F1E1B),
    inverseOnSurface = ImmerlexColors.Paper,
    surfaceVariant = ImmerlexColors.Line,
    onSurfaceVariant = ImmerlexColors.Body,
    outline = ImmerlexColors.Meta,
    outlineVariant = ImmerlexColors.Line,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE4834E),
    onPrimary = Color(0xFF2B1206),
    primaryContainer = Color(0xFF5C2A0C),
    onPrimaryContainer = Color(0xFFF6C9A8),
    secondary = Color(0xFFD8BDA8),
    onSecondary = Color(0xFF3B2A20),
    secondaryContainer = Color(0xFF3A2A1E),
    onSecondaryContainer = Color(0xFFF6E3D7),
    tertiary = Color(0xFFA9CBB2),
    onTertiary = Color(0xFF1D3125),
    tertiaryContainer = Color(0xFF1D3125),
    onTertiaryContainer = Color(0xFFDCEFE0),
    background = Color(0xFF191813),
    onBackground = Color(0xFFF0EEE8),
    surface = Color(0xFF22211B),
    onSurface = Color(0xFFF0EEE8),
    surfaceContainerLowest = Color(0xFF141310),
    surfaceContainerLow = Color(0xFF1B1A15),
    surfaceContainer = Color(0xFF22211B),
    surfaceContainerHigh = Color(0xFF2A2924),
    surfaceContainerHighest = Color(0xFF33312B),
    inverseSurface = Color(0xFFECEAE3),
    inverseOnSurface = Color(0xFF191813),
    surfaceVariant = Color(0xFF302E28),
    onSurfaceVariant = Color(0xFFB8B6AE),
    outline = Color(0xFF7C7A72),
    outlineVariant = Color(0xFF302E28),
)

@Composable
fun ImmerlexTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = appearance.darkMode
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalMasteryTones provides if (dark) DarkTones else LightTones,
        LocalAppearance provides appearance,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            content = content,
        )
    }
}

/** Background / content pair for a word's mastery state, shared by bank list and reader. */
@Composable
fun masteryPalette(word: WordEntity): Pair<Color, Color> =
    LocalMasteryTones.current.of(word.mastery())

/**
 * Feature screens nest their own Scaffold inside the root one, which already padded the host
 * for the status bar and the bottom bar. Left at the default they avoid both a second time and
 * every page grows a dead band under the status bar.
 */
val RootHandledInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
