// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/ui/theme/Theme.kt
package com.goodstadt.john.language.exams.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme

private val AppLightColorScheme = lightColorScheme(
    primary = LightPrimary, // e.g., Color(0xFF6200EE)
    onPrimary = LightOnPrimary, // e.g., Color.White
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnBackground,
    secondary = LightSecondary,
    onSecondary = LightOnPrimary,
    background = LightBackground, // e.g., Color.White
    onBackground = LightOnBackground, // e.g., Color.Black
    surface = LightSurface, // e.g., Color.White
    onSurface = LightOnSurface, // e.g., Color.Black
    surfaceVariant = LightSurfaceVariant, // A good light gray for containers
    onSurfaceVariant = LightOnSurface
)

private val AppDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    secondary = DarkSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
//        surfaceVariant = Color(0xFFF2F2F7) // A good light gray for containers
    surfaceVariant = Color(0xFF303030)
)

@Composable
fun LanguageExamsAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useAlternativePalette: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppDarkColorScheme else AppLightColorScheme
    // DEBUG experiment: swap in the *Alternative ExtendedColors when requested (Settings toggle).
    val extendedColors = when {
        darkTheme && useAlternativePalette -> DarkExtendedColorsAlternative
        darkTheme -> DarkExtendedColors
        useAlternativePalette -> LightExtendedColorsAlternative
        else -> LightExtendedColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            // Dark status-bar icons only when the bar is light (i.e. in light theme).
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}