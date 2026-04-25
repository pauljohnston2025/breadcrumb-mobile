package com.paul.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

val SlateGray = Color(0xFF546E7A)
val DarkSlate = Color(0xFF37474F)

val MutedTeal = Color(0xFF26A69A)
val DarkMutedTeal = Color(0xFF00897B)

val BackgroundLight = Color(0xFFECEFF1)
val SurfaceLight = Color(0xFFFFFFFF)
val OnBackgroundDarkText = Color(0xFF1A1C1E)
val OnSurfaceDarkText = Color(0xFF1A1C1E)

val BackgroundDark = Color(0xFF1A1C1E)
val SurfaceDark = Color(0xFF25282C)
val OnBackgroundLightText = Color(0xFFE2E2E6)
val OnSurfaceLightText = Color(0xFFE2E2E6)

val ErrorRed = Color(0xFFB00020)
val OnErrorLight = Color(0xFFFFFFFF)

private val LightColorPalette = lightColors(
    primary = SlateGray,
    primaryVariant = DarkSlate,
    secondary = MutedTeal,
    secondaryVariant = DarkMutedTeal,
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ErrorRed,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = OnBackgroundDarkText,
    onSurface = OnSurfaceDarkText,
    onError = OnErrorLight
)

private val DarkColorPalette = darkColors(
    primary = SlateGray,
    primaryVariant = DarkSlate,
    secondary = MutedTeal,
    secondaryVariant = DarkMutedTeal,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorRed,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = OnBackgroundLightText,
    onSurface = OnSurfaceLightText,
    onError = OnErrorLight
)


val AppTypography = Typography(
)

val AppShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun M3ThemeWrapper(content: @Composable () -> Unit) {
    val isDark = !MaterialTheme.colors.isLight
    val m2Colors = MaterialTheme.colors

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = m2Colors.primary,
            onPrimary = m2Colors.onPrimary,
            secondary = m2Colors.secondary,
            onSecondary = m2Colors.onSecondary,
            background = m2Colors.background,
            onBackground = m2Colors.onBackground,
            surface = m2Colors.surface,
            onSurface = m2Colors.onSurface,
            error = m2Colors.error,
            onError = m2Colors.onError,
            // DatePicker uses surfaceVariant for the track/header and outline for borders
            surfaceVariant = m2Colors.surface.copy(alpha = 0.8f),
            onSurfaceVariant = m2Colors.onSurface.copy(alpha = 0.7f),
            outline = m2Colors.onSurface.copy(alpha = 0.12f)
        )
    } else {
        lightColorScheme(
            primary = m2Colors.primary,
            onPrimary = m2Colors.onPrimary,
            secondary = m2Colors.secondary,
            onSecondary = m2Colors.onSecondary,
            background = m2Colors.background,
            onBackground = m2Colors.onBackground,
            surface = m2Colors.surface,
            onSurface = m2Colors.onSurface,
            error = m2Colors.error,
            onError = m2Colors.onError,
            surfaceVariant = Color(0xFFE1E2E8), // Subtle light gray for light mode
            onSurfaceVariant = Color(0xFF44474E),
            outline = Color(0xFF74777F)
        )
    }

    M3Theme(
        colorScheme = colorScheme,
        // Optional: inherit shapes from your AppShapes
        content = content
    )
}