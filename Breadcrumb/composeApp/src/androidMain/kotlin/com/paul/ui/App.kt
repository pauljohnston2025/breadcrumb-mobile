package com.paul.ui

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.paul.infrastructure.connectiq.Connection
import com.paul.viewmodels.DeviceSelector
import com.paul.viewmodels.StartViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App(
    connection: Connection,
    navController: NavHostController = rememberNavController()
) {
    val deviceSelector = viewModel { DeviceSelector(navController, connection) }

    NavHost(
        navController = navController,
        startDestination = Screens.Start.name,
    ) {
        composable(route = Screens.Start.name) {
            Start(
                startViewModel = viewModel { StartViewModel(connection, deviceSelector) },
                deviceSelector = deviceSelector,
            )
        }
        composable(route = Screens.DeviceSelector.name) {
            DeviceSelector(
                deviceSelector = deviceSelector,
            )
        }
    }
}
