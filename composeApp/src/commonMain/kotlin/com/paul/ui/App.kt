package com.paul.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.connectiq.IDeviceList
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.ProfileRepo
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.service.IClipboardHandler
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.infrastructure.service.ILocationService
import com.paul.infrastructure.service.IntentHandler
import com.paul.infrastructure.web.WebServerController
import com.paul.viewmodels.DebugViewModel
import com.paul.viewmodels.DeviceSettingsNavigationEvent
import com.paul.viewmodels.MapViewModel
import com.paul.viewmodels.NavigationEvent
import com.paul.viewmodels.ProfilesViewModel
import com.paul.viewmodels.RoutesNavigationEvent
import com.paul.viewmodels.RoutesViewModel
import com.paul.viewmodels.StartNavigationEvent
import com.paul.viewmodels.StartViewModel
import com.paul.viewmodels.StorageViewModel
import kotlinx.coroutines.launch
import com.paul.viewmodels.DeviceSelector as DeviceSelectorModel
import com.paul.viewmodels.DeviceSettings as DeviceSettingsModel
import com.paul.viewmodels.Settings as SettingsViewModel

@Composable
fun App(
    tileRepo: ITileRepository,
    connection: IConnection,
    deviceList: IDeviceList,
    gpxFileLoader: IGpxFileLoader,
    fileHelper: IFileHelper,
    clipboardHandler: IClipboardHandler,
    webServerController: WebServerController,
    intentHandler: IntentHandler,
    locationService: ILocationService,

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
                    connection,
                    deviceList,
                    scaffoldState.snackbarHostState
                )
            }
        val debugViewModel = viewModel { DebugViewModel() }
        val settingsViewModel = viewModel {
            SettingsViewModel(
                deviceSelector,
                connection,
                scaffoldState.snackbarHostState,
                webServerController,
                tileRepo,
                RouteRepository(fileHelper, gpxFileLoader)
            )
        }

        val profilesViewModel = viewModel {
            ProfilesViewModel(
                deviceSelector,
                connection,
                scaffoldState.snackbarHostState,
                settingsViewModel.tileServerRepo,
                ProfileRepo(), // needs to be a singleton if anything else uses it
                clipboardHandler,
                settingsViewModel.routesRepo
            )
        }

        val mapViewModel = viewModel {
            MapViewModel(
                connection,
                deviceSelector,
                tileRepository = tileRepo,
                tileServerRepository = settingsViewModel.tileServerRepo,
                snackbarHostState = scaffoldState.snackbarHostState,
                locationService = locationService,
            )
        }

        val startViewModel = viewModel {
            StartViewModel(
                connection,
                deviceSelector,
                gpxFileLoader,
                fileHelper,
                scaffoldState.snackbarHostState,
                mapViewModel,
                settingsViewModel.routesRepo
            )
        }

        LaunchedEffect(Unit) {
            intentHandler.updateStartViewModel(startViewModel)
        }

        // Listen for navigation events from the DeviceSelector ViewModel
        // The LaunchedEffect that listens for navigation events now becomes more descriptive
        LaunchedEffect(key1 = Unit) {
            deviceSelector.navigationEvents.collect { event ->
                // Use a 'when' statement to handle all possible navigation events
                when (event) {
                    is NavigationEvent.NavigateTo -> {
                        navController.navigate(event.route)
                    }

                    is NavigationEvent.PopBackStack -> {
                        // Here is where we move the logic from the ViewModel!
                        // The UI is now responsible for checking its own state.
                        if (navController.currentDestination?.route == Screen.DeviceSelection.route) {
                            navController.popBackStack()
                        }
                    }
                }
            }
        }

        LaunchedEffect(key1 = Unit) {
            startViewModel.navigationEvents.collect { event ->
                // Use a 'when' statement to handle all possible navigation events
                when (event) {
                    is StartNavigationEvent.Load -> {
                        if (navController.currentDestination != null) {
                            navController.navigate(Screen.Start.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }

                    is StartNavigationEvent.NavigateTo -> {
                        navController.navigate(event.route)
                    }
                }
            }
        }

        // Get the current route to potentially change the TopAppBar title
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val currentScreen =
            allScreens.find { it.route == currentRoute }
                ?: Screen.Start // Default to Start title

        ModalDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen, // Only allow gesture open/close when open? Or always?
            drawerContent = {
                AppDrawerContent(
                    navController = navController,
                    drawerState = drawerState,
                    modifier = Modifier, // Pass modifier if needed for drawer content styling
                    scope = scope
                )
            }
        ) {

            var title = currentScreen.title
            if (deviceSelector.currentDevice.value != null) {
                title =
                    currentScreen.title + " (" + deviceSelector.currentDevice.value!!.friendlyName + ")"
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
//                    val current = navController.currentDestination
//                    // if we are on the select device screen but navigating to a new screen then pop stack before navigate
//                    if (current?.route == Screen.DeviceSelection.route) {
//                        navController.popBackStack()
//                    }

                    composable(Screen.Start.route) {
                        Start(
                            viewModel = startViewModel,
                        )
                    }

                    composable(Screen.Routes.route) {
                        val routesViewModel = viewModel {
                            RoutesViewModel(
                                mapViewModel,
                                connection,
                                deviceSelector,
                                startViewModel.routeRepo,
                                startViewModel.historyRepo,
                                scaffoldState.snackbarHostState,
                            )
                        }

                        LaunchedEffect(key1 = Unit) {
                            routesViewModel.navigationEvents.collect { event ->
                                // Use a 'when' statement to handle all possible navigation events
                                when (event) {
                                    is RoutesNavigationEvent.NavigateTo -> {
                                        navController.navigate(event.route)
                                    }
                                }
                            }
                        }

                        RoutesScreen(
                            viewModel = routesViewModel,
                        )
                    }

                    composable(Screen.Profiles.route) {
                        ProfilesScreen(
                            viewModel = profilesViewModel,
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
                            false
                        )
                    }

                    composable(Screen.DeviceSelection.route) {
                        DeviceSelector(
                            viewModel = deviceSelector,
                            true
                        )
                    }

                    composable(route = Screen.DeviceSettings.route) {
                        // lastLoadedSettings dirty hack
                        val deviceSettings = viewModel {
                            DeviceSettingsModel(
                                deviceSelector.lastLoadedSettings!!,
                                deviceSelector.currentDevice.value!!,
                                connection,
                                scaffoldState.snackbarHostState,
                            )
                        }
                        LaunchedEffect(key1 = Unit) {
                            deviceSettings.navigationEvents.collect { event ->
                                // Use a 'when' statement to handle all possible navigation events
                                when (event) {
                                    is DeviceSettingsNavigationEvent.PopBackStack -> {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }
                        DisposableEffect(Unit) {
                            // onDispose is called when the composable leaves the composition
                            onDispose {
                                deviceSelector.onDeviceSettingsClosed()
                            }
                        }
                        DeviceSettings(
                            deviceSettings = deviceSettings,
                        )
                    }

                    composable(Screen.Map.route) {
                        mapViewModel.refresh() // make sure the latest tile server changes are applied
                        MapScreen(mapViewModel)
                    }

                    composable(Screen.Storage.route) {
                        val storageViewModel = viewModel {
                            StorageViewModel(
                                fileHelper,
                                startViewModel.routeRepo,
                            )
                        }
                        storageViewModel.refresh()
                        StorageScreen(settingsViewModel.tileServerRepo, storageViewModel)
                    }

                    composable(Screen.Debug.route) {
                        DebugScreen(debugViewModel)
                    }
                } // End NavHost
            } // End Scaffold
        } // End ModalDrawer
    }
}
