package com.paul.ui

import android.net.Uri
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.connectiq.IDeviceList
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.viewmodels.StartViewModel
import com.paul.viewmodels.DeviceSettings as DeviceSettingsModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.paul.viewmodels.DeviceSelector as DeviceSelectorModel

@Composable
@Preview
fun App(
    connection: IConnection,
    deviceList: IDeviceList,
    gpxFileLoader: IGpxFileLoader,
    fileHelper: IFileHelper,
    fileLoad: String?,
    shortGoogleUrl: String?,
    initialErrorMessage: String?,
    navController: NavHostController = rememberNavController()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deviceSelector = viewModel { DeviceSelectorModel(navController, connection, deviceList, snackbarHostState) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { _ ->

        NavHost(
            navController = navController,
            startDestination = Screens.Start.name,
        ) {
            composable(route = Screens.Start.name) {
                Start(
                    viewModel = viewModel {
                        StartViewModel(
                            connection,
                            deviceSelector,
                            gpxFileLoader,
                            fileHelper,
                            snackbarHostState,
                            fileLoad,
                            shortGoogleUrl,
                            initialErrorMessage
                        )
                    },
                    deviceSelector = deviceSelector,
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(route = Screens.DeviceSelector.name) {
                DeviceSelector(
                    viewModel = deviceSelector,
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(route = Screens.DeviceSettings.name) {
                // lastLoadedSettings dirty hack
                val deviceSettings = viewModel { DeviceSettingsModel(
                    deviceSelector.lastLoadedSettings!!,
                    deviceSelector.currentDevice!!,
                    navController,
                    connection,
                    snackbarHostState,
                ) }
                DeviceSettings(
                    deviceSettings = deviceSettings,
                )
            }
        }
    }
}
