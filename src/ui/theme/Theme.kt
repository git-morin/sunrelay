package dev.morin.sunrelay.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

enum class Accent(val key: String, val label: String, val seed: Long?) {
    Sunshine("sunshine", "Sunshine orange", 0xFFF89A1C),
    Ember("ember", "Ember", 0xFFEF3E23),
    Gold("gold", "Gold", 0xFFFDD107),
    Ocean("ocean", "Ocean", 0xFF2D7FF9),
    Forest("forest", "Forest", 0xFF2E9E5B),
    Violet("violet", "Violet", 0xFF8E5CF7),
    Rose("rose", "Rose", 0xFFE8508A),
    Wallpaper("wallpaper", "Wallpaper", null),
    ;

    val description: String get() = if (this == Wallpaper) "$label (follows your wallpaper colours)" else label

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Sunshine
    }
}

enum class ThemeMode(val key: String, val label: String) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark"),
    ;

    @Composable
    fun isDark(): Boolean = when (this) {
        System -> isSystemInDarkTheme()
        Light -> false
        Dark -> true
    }

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: System
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SunTheme(accent: Accent, mode: ThemeMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = mode.isDark()
    val scheme = when (val seed = accent.seed) {
        null -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> SeedPalette(Color(seed)).scheme(dark)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = emphasizedTypography(),
        content = content,
    )
}

@Composable
private fun emphasizedTypography(): Typography {
    val base = MaterialTheme.typography
    return base.copy(
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}
