// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/ui/theme/Theme.kt
package com.goodstadt.john.language.exams.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    secondary = LightSecondary,
    background = LightBackground, // e.g., Color.White
    onBackground = LightOnBackground, // e.g., Color.Black
    surface = LightSurface, // e.g., Color.White
    onSurface = LightOnSurface, // e.g., Color.Black
    surfaceVariant = Color(0xFFF2F2F7) // A good light gray for containers
    // ... define other colors as needed
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
        surfaceVariant = Color(0xFFF2F2F7) // A good light gray for containers
)

@Composable
fun LanguageExamsAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        AppDarkColorScheme
    } else {
        // For now, we can fall back to the dark one if you haven't defined a light one
//        AppDarkColorScheme
        AppLightColorScheme
    }

    // Forcing dark theme
   // val colorScheme = AppDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
    )
}