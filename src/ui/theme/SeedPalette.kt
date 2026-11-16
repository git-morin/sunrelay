package dev.morin.sunrelay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

internal class SeedPalette(seed: Color) {
    private val hue: Float
    private val saturation: Float

    init {
        val r = seed.red
        val g = seed.green
        val b = seed.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val lightness = (max + min) / 2
        if (delta == 0f) {
            hue = 0f
            saturation = 0f
        } else {
            val h = when (max) {
                r -> 60 * (((g - b) / delta) % 6)
                g -> 60 * ((b - r) / delta + 2)
                else -> 60 * ((r - g) / delta + 4)
            }
            hue = (h + 360) % 360
            saturation = (delta / (1 - abs(2 * lightness - 1))).coerceIn(0f, 1f)
        }
    }

    fun scheme(dark: Boolean): ColorScheme = if (dark) dark() else light()

    private fun primary(l: Float) = Color.hsl(hue, saturation, l)
    private fun secondary(l: Float) = Color.hsl(hue, saturation * 0.35f, l)
    private fun tertiary(l: Float) = Color.hsl((hue + 50) % 360, saturation * 0.6f, l)
    private fun neutral(l: Float) = Color.hsl(hue, minOf(saturation, 0.5f) * 0.12f, l)
    private fun neutralVariant(l: Float) = Color.hsl(hue, minOf(saturation, 0.5f) * 0.25f, l)

    private fun light() = lightColorScheme(
        primary = primary(0.42f), onPrimary = Color.White,
        primaryContainer = primary(0.88f), onPrimaryContainer = primary(0.14f),
        inversePrimary = primary(0.75f),
        secondary = secondary(0.40f), onSecondary = Color.White,
        secondaryContainer = secondary(0.88f), onSecondaryContainer = secondary(0.14f),
        tertiary = tertiary(0.40f), onTertiary = Color.White,
        tertiaryContainer = tertiary(0.88f), onTertiaryContainer = tertiary(0.14f),
        background = neutral(0.985f), onBackground = neutral(0.10f),
        surface = neutral(0.985f), onSurface = neutral(0.10f),
        surfaceVariant = neutralVariant(0.90f), onSurfaceVariant = neutralVariant(0.30f),
        surfaceTint = primary(0.42f),
        inverseSurface = neutral(0.19f), inverseOnSurface = neutral(0.95f),
        outline = neutralVariant(0.50f), outlineVariant = neutralVariant(0.80f),
        surfaceBright = neutral(0.985f), surfaceDim = neutral(0.87f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = neutral(0.96f),
        surfaceContainer = neutral(0.94f), surfaceContainerHigh = neutral(0.92f), surfaceContainerHighest = neutral(0.90f),
    )

    private fun dark() = darkColorScheme(
        primary = primary(0.72f), onPrimary = primary(0.16f),
        primaryContainer = primary(0.28f), onPrimaryContainer = primary(0.90f),
        inversePrimary = primary(0.42f),
        secondary = secondary(0.75f), onSecondary = secondary(0.16f),
        secondaryContainer = secondary(0.27f), onSecondaryContainer = secondary(0.90f),
        tertiary = tertiary(0.75f), onTertiary = tertiary(0.16f),
        tertiaryContainer = tertiary(0.28f), onTertiaryContainer = tertiary(0.90f),
        background = neutral(0.07f), onBackground = neutral(0.90f),
        surface = neutral(0.07f), onSurface = neutral(0.90f),
        surfaceVariant = neutralVariant(0.28f), onSurfaceVariant = neutralVariant(0.80f),
        surfaceTint = primary(0.72f),
        inverseSurface = neutral(0.90f), inverseOnSurface = neutral(0.19f),
        outline = neutralVariant(0.58f), outlineVariant = neutralVariant(0.28f),
        surfaceBright = neutral(0.24f), surfaceDim = neutral(0.07f),
        surfaceContainerLowest = neutral(0.04f), surfaceContainerLow = neutral(0.10f),
        surfaceContainer = neutral(0.12f), surfaceContainerHigh = neutral(0.16f), surfaceContainerHighest = neutral(0.20f),
    )
}
