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

val GrassGreen = Color(0xFF558B2F)
val DarkGreen = Color(0xFF33691E)
val PathOrange = Color(0xFFFB8C00)
val BurntOrange = Color(0xFFE65100)
val MountainGray = Color(0xFF607D8B)

val BackgroundLight = Color(0xFFFAFAFA)
val SurfaceLight = Color(0xFFFFFFFF)
val OnBackgroundDarkText = Color(0xFF1F1F1F)
val OnSurfaceDarkText = Color(0xFF1F1F1F)

val BackgroundDark = Color(0xFF1A241B)
val SurfaceDark = Color(0xFF2A332B)
val OnBackgroundLightText = Color(0xFFE0E0E0)
val OnSurfaceLightText = Color(0xFFFFFFFF)

val ErrorRed = Color(0xFFB00020)
val OnErrorLight = Color(0xFFFFFFFF)

private val LightColorPalette = lightColors(
    primary = GrassGreen,
    primaryVariant = DarkGreen,
    secondary = PathOrange,
    secondaryVariant = BurntOrange,
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ErrorRed,
    onPrimary = Color.White,
    onSecondary = Color.Black, // Black text might be more readable on PathOrange
    onBackground = OnBackgroundDarkText,
    onSurface = OnSurfaceDarkText,
    onError = OnErrorLight
)

private val DarkColorPalette = darkColors(
    primary = GrassGreen, // Keep the same green, it should contrast well
    primaryVariant = DarkGreen,
    secondary = PathOrange, // Orange pops nicely in dark mode
    secondaryVariant = BurntOrange,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorRed, // Or a lighter variant if needed
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = OnBackgroundLightText,
    onSurface = OnSurfaceLightText,
    onError = OnErrorLight
)


val AppTypography = Typography(
    h5 = Typography().h5.copy(),
    button = Typography().button.copy(),
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