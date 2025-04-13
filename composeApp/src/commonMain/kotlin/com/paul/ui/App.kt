package com.paul.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.connectiq.IDeviceList
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.infrastructure.web.WebServerController
import com.paul.viewmodels.StartViewModel
import com.paul.viewmodels.Settings as SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.paul.viewmodels.DeviceSelector as DeviceSelectorModel
import com.paul.viewmodels.DeviceSettings as DeviceSettingsModel

@Composable
fun App(
    connection: IConnection,
    deviceList: IDeviceList,
    gpxFileLoader: IGpxFileLoader,
    fileHelper: IFileHelper,
    fileLoad: String?,
    shortGoogleUrl: String?,
    komootUrl: String?,
    initialErrorMessage: String?,
    webServerController: WebServerController
) {
    AppTheme {
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val scaffoldState =
            rememberScaffoldState(drawerState = drawerState) // Connect scaffold and drawer states
        val deviceSelector =
            viewModel {
                DeviceSelectorModel(
                    navController,
                    connection,
                    deviceList,
                    scaffoldState.snackbarHostState
                )
            }
        val settingsViewModel = viewModel {
            SettingsViewModel(
                deviceSelector,
                connection,
                scaffoldState.snackbarHostState,
                webServerController
            )
        }

        // Get the current route to potentially change the TopAppBar title
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val currentScreen =
            drawerScreens.find { it.route == currentRoute }
                ?: Screen.Start // Default to Start title

        ModalDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen, // Only allow gesture open/close when open? Or always?
            drawerContent = {
                AppDrawerContent(
                    navController = navController,
                    drawerState = drawerState,
                    modifier = Modifier, // Pass modifier if needed for drawer content styling
                    scope = scope,
                    deviceSelector = deviceSelector,
                )
            }
        ) {

            var title = currentScreen.title
            if (deviceSelector.currentDevice.value != null) {
                title = currentScreen.title + " (" + deviceSelector.currentDevice.value!!.friendlyName + ")"
            }

            // Main content area wrapped by the drawer
            Scaffold(
                scaffoldState = scaffoldState, // Use the state connected to drawerState
                topBar = {
                    TopAppBar(
                        title = { Text(title) }, // Dynamic title based on current screen
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        }
                        // Add actions specific to screens here if needed, maybe conditionally
                    )
                },
                // SnackbarHost defined once here
                snackbarHost = { SnackbarHost(hostState = scaffoldState.snackbarHostState) }
            ) { paddingValues -> // Content receives padding from Scaffold
                NavHost(
                    navController = navController,
                    // Determine your actual start destination
                    startDestination = Screen.Start.route,
                    modifier = Modifier.padding(paddingValues) // Apply padding to NavHost content
                ) {
                    composable(Screen.Start.route) {
                        Start(
                            viewModel = viewModel {
                                StartViewModel(
                                    connection,
                                    deviceSelector,
                                    gpxFileLoader,
                                    fileHelper,
                                    scaffoldState.snackbarHostState,
                                    fileLoad,
                                    shortGoogleUrl,
                                    komootUrl,
                                    initialErrorMessage
                                )
                            },
                            navController = navController,
                        )
                    }

                    composable(Screen.Settings.route) {
                        Settings(
                            viewModel = settingsViewModel,
                        )
                    }

                    composable(Screen.Devices.route) {
                        DeviceSelector(
                            viewModel = deviceSelector,
                            navController = navController, // Pass NavController if event system isn't fully implemented yet
                            false
                        )
                    }

                    composable(Screen.DeviceSelection.route) {
                        DeviceSelector(
                            viewModel = deviceSelector,
                            navController = navController, // Pass NavController if event system isn't fully implemented yet
                            true
                        )
                    }

                    composable(route = Screen.DeviceSettings.route) {
                        // lastLoadedSettings dirty hack
                        val deviceSettings = viewModel {
                            DeviceSettingsModel(
                                deviceSelector.lastLoadedSettings!!,
                                deviceSelector.currentDevice.value!!,
                                navController,
                                connection,
                                scaffoldState.snackbarHostState,
                            )
                        }
                        DeviceSettings(
                            deviceSettings = deviceSettings,
                            navController = navController,
                        )
                    }

                    composable(Screen.Map.route) {
                        // Placeholder for your Map Screen
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Map Screen Placeholder")
                        }
                    }

                    composable(Screen.Storage.route) {
                        // Placeholder for your Storage Screen
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Storage Screen Placeholder")
                        }
                    }

                    composable(Screen.Debug.route) {
                        // Placeholder for your Storage Screen
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Debug Screen Placeholder")
                        }
                    }
                } // End NavHost
            } // End Scaffold
        } // End ModalDrawer
    }
}
