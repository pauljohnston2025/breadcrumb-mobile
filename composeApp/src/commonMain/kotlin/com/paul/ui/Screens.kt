package com.paul.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

// https://github.com/JetBrains/compose-multiplatform/blob/a6961385ccf0dee7b6d31e3f73d2c8ef91005f1a/examples/nav_cupcake/composeApp/src/commonMain/kotlin/org/jetbrains/nav_cupcake/CupcakeScreen.kt#L89
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Start : Screen(
        "start",
        "Overview",
        Icons.Default.Home
    ) // Add Start if it's also in drawer? Or just initial screen?

    object Settings : Screen("settings", "App Settings", Icons.Default.Settings)
    object Devices : Screen("devices", "Devices", Icons.Default.Star)
    object DeviceSelection : Screen("deviceSelection", "Device Selection", Icons.Default.Star)
    object DeviceSettings : Screen("deviceSettings", "Device Settings", Icons.Default.Settings)
    object Map : Screen("map", "Map View", Icons.Default.Place)
    object Storage : Screen("storage", "Storage Info", Icons.Default.AddCircle) // Placeholder
    object Debug : Screen("debug", "Debug Info", Icons.Default.Build) // Placeholder
    object Routes : Screen("routes", "Routes", Icons.Default.PlayArrow) // Placeholder
}

// List of items specifically for the drawer menu
val drawerScreens = listOf(
    Screen.Start,
    Screen.Routes,
    Screen.Settings,
    Screen.Devices,
    Screen.Map,
    Screen.Storage,
    Screen.Debug,
)
