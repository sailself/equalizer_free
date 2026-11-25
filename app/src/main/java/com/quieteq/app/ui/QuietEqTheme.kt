package com.quieteq.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.quieteq.app.core.ThemePalette

private data class Palette(val light: ColorScheme, val dark: ColorScheme)

private val Mint = Color(0xFF8DE4D4)
private val Original = Palette(
    light = lightColorScheme(
        primary = Color(0xFF006C5F), onPrimary = Color.White,
        primaryContainer = Color(0xFFADF1E2), onPrimaryContainer = Color(0xFF00201A),
        secondary = Color(0xFF49665E), onSecondary = Color.White,
        secondaryContainer = Color(0xFFCBECE1), onSecondaryContainer = Color(0xFF062018),
        tertiary = Color(0xFF49696A), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCCE8E8), onTertiaryContainer = Color(0xFF042021),
        background = Color(0xFFF5FAF7), onBackground = Color(0xFF17211E),
        surface = Color.White, surfaceTint = Color(0xFF006C5F),
        surfaceVariant = Color(0xFFE1EDE7), surfaceContainerHighest = Color(0xFFDFEAE4),
        onSurface = Color(0xFF17211E), onSurfaceVariant = Color(0xFF43544E),
        outline = Color(0xFF71847C), outlineVariant = Color(0xFFC0D0C8),
    ),
    dark = darkColorScheme(
        primary = Mint, onPrimary = Color(0xFF063B34),
        primaryContainer = Color(0xFF24534B), onPrimaryContainer = Color(0xFFB1F1E5),
        secondary = Color(0xFFACD1C7), onSecondary = Color(0xFF183B34),
        secondaryContainer = Color(0xFF304D47), onSecondaryContainer = Color(0xFFD2EDE5),
        tertiary = Color(0xFFA9CFD1), onTertiary = Color(0xFF163B3E),
        tertiaryContainer = Color(0xFF304F51), onTertiaryContainer = Color(0xFFC5EAEB),
        background = Color(0xFF101515), onBackground = Color(0xFFE6EEEB),
        surface = Color(0xFF191F1E), surfaceTint = Mint,
        surfaceVariant = Color(0xFF252D2B), surfaceContainerHighest = Color(0xFF303A37),
        onSurface = Color(0xFFE6EEEB), onSurfaceVariant = Color(0xFFABBAB5),
        outline = Color(0xFF718680), outlineVariant = Color(0xFF43534E),
    ),
)

private fun tintedScheme(
    base: ColorScheme,
    accent: Long,
    onAccent: Long,
    accentContainer: Long,
    onAccentContainer: Long,
    background: Long,
    surface: Long,
    text: Long,
    mutedText: Long,
    outline: Long,
): ColorScheme {
    val primary = Color(accent)
    val panel = Color(surface)
    val canvas = Color(background)
    val foreground = Color(text)
    val container = Color(accentContainer)
    val onContainer = Color(onAccentContainer)
    return base.copy(
        primary = primary,
        onPrimary = Color(onAccent),
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = primary,
        onSecondary = Color(onAccent),
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
        tertiary = primary,
        onTertiary = Color(onAccent),
        tertiaryContainer = container,
        onTertiaryContainer = onContainer,
        background = canvas,
        onBackground = foreground,
        surface = panel,
        onSurface = foreground,
        inverseSurface = foreground,
        inverseOnSurface = canvas,
        inversePrimary = Color(onAccent),
        surfaceTint = primary,
        surfaceVariant = lerp(panel, primary, 0.12f),
        onSurfaceVariant = Color(mutedText),
        outline = Color(outline),
        outlineVariant = lerp(panel, Color(outline), 0.45f),
        surfaceDim = lerp(canvas, foreground, 0.06f),
        surfaceBright = panel,
        surfaceContainerLowest = panel,
        surfaceContainerLow = lerp(panel, primary, 0.04f),
        surfaceContainer = lerp(panel, primary, 0.07f),
        surfaceContainerHigh = lerp(panel, primary, 0.10f),
        surfaceContainerHighest = lerp(panel, primary, 0.14f),
    )
}

private val Ocean = Palette(
    light = tintedScheme(
        base = Original.light,
        accent = 0xFF205D91,
        onAccent = 0xFFFFFFFF,
        accentContainer = 0xFFD3E7FF,
        onAccentContainer = 0xFF0D304F,
        background = 0xFFEFF6FE,
        surface = 0xFFFFFFFF,
        text = 0xFF152337,
        mutedText = 0xFF4D637B,
        outline = 0xFF697E94,
    ),
    dark = tintedScheme(
        base = Original.dark,
        accent = 0xFF9FCDFF,
        onAccent = 0xFF0A3157,
        accentContainer = 0xFF294769,
        onAccentContainer = 0xFFD7E9FF,
        background = 0xFF0E1826,
        surface = 0xFF162232,
        text = 0xFFE4EEFB,
        mutedText = 0xFFB7C6DA,
        outline = 0xFF6D849E,
    ),
)

private val Forest = Palette(
    light = tintedScheme(
        base = Original.light,
        accent = 0xFF396633,
        onAccent = 0xFFFFFFFF,
        accentContainer = 0xFFD8EDC9,
        onAccentContainer = 0xFF203D1D,
        background = 0xFFF1F7EE,
        surface = 0xFFFFFFFF,
        text = 0xFF19281A,
        mutedText = 0xFF4D614D,
        outline = 0xFF6F816E,
    ),
    dark = tintedScheme(
        base = Original.dark,
        accent = 0xFFB5D78E,
        onAccent = 0xFF213914,
        accentContainer = 0xFF304B2D,
        onAccentContainer = 0xFFD4EDB8,
        background = 0xFF121C15,
        surface = 0xFF1C261F,
        text = 0xFFE6F0E6,
        mutedText = 0xFFB8C8B8,
        outline = 0xFF788F79,
    ),
)

private val Lavender = Palette(
    light = tintedScheme(
        base = Original.light,
        accent = 0xFF694397,
        onAccent = 0xFFFFFFFF,
        accentContainer = 0xFFEBDCFF,
        onAccentContainer = 0xFF412664,
        background = 0xFFF8F2FF,
        surface = 0xFFFFFFFF,
        text = 0xFF261B34,
        mutedText = 0xFF665571,
        outline = 0xFF83748F,
    ),
    dark = tintedScheme(
        base = Original.dark,
        accent = 0xFFD6B8FF,
        onAccent = 0xFF3A205B,
        accentContainer = 0xFF483360,
        onAccentContainer = 0xFFEEDCFF,
        background = 0xFF1B1426,
        surface = 0xFF251D32,
        text = 0xFFF1E8F7,
        mutedText = 0xFFCDC0D9,
        outline = 0xFF927DA4,
    ),
)

internal fun quietEqColorScheme(palette: ThemePalette, dark: Boolean): ColorScheme {
    val colors = when (palette) {
        ThemePalette.ORIGINAL -> Original
        ThemePalette.OCEAN -> Ocean
        ThemePalette.FOREST -> Forest
        ThemePalette.LAVENDER -> Lavender
    }
    return if (dark) colors.dark else colors.light
}
