package com.paul.ui

import androidx.compose.foundation.layout.Column
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.connectiq.IDeviceList
import com.paul.infrastructure.repositories.ColourPaletteRepository
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.ProfileRepo
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.service.IClipboardHandler
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.infrastructure.service.ILocationService
import com.paul.infrastructure.service.IntentHandler
import com.paul.infrastructure.web.KtorClient.client
import com.paul.infrastructure.web.WebServerController
import com.paul.infrastructure.web.versionName
import com.paul.viewmodels.DebugViewModel
import com.paul.viewmodels.DeviceSettingsNavigationEvent
import com.paul.viewmodels.MapViewModel
import com.paul.viewmodels.NavigationEvent
import com.paul.viewmodels.ProfilesViewModel
import com.paul.viewmodels.RoutesNavigationEvent
import com.paul.viewmodels.RoutesViewModel
import com.paul.viewmodels.StartNavigationEvent
import com.paul.viewmodels.MapViewNavigationEvent
import com.paul.viewmodels.StartViewModel
import com.paul.viewmodels.StorageViewModel
import io.github.aakira.napier.Napier
import io.ktor.client.request.head
import kotlinx.coroutines.launch
import com.paul.viewmodels.DeviceSelector as DeviceSelectorModel
import com.paul.viewmodels.DeviceSettings as DeviceSettingsModel
import com.paul.viewmodels.Settings as SettingsViewModel
import androidx.compose.material.AlertDialog

@Composable
fun App(
    tileRepo: ITileRepository,
    colourPaletteRepo: ColourPaletteRepository,
    connection: IConnection,
    deviceList: IDeviceList,
    gpxFileLoader: IGpxFileLoader,
    fileHelper: IFileHelper,
    clipboardHandler: IClipboardHandler,
    webServerController: WebServerController,
    intentHandler: IntentHandler,
    locationService: ILocationService,

    ) {
    // Inside the App composable:
    var updateVersion by remember { mutableStateOf<String?>(null) }
    val currentVersion = versionName()

    LaunchedEffect(Unit) {
        val latestUrl = "https://github.com/pauljohnston2025/breadcrumb-mobile/releases/latest"

        try {
            // Use a HEAD request to find the redirect tag without downloading the whole page
            val response = client.head(latestUrl)
            val finalUrl = response.call.request.url.toString()

            // Extracts "0.0.16" from ".../tag/0.0.16"
            val latestVersion = finalUrl.substringAfterLast("/")

            if (latestVersion != currentVersion && latestVersion.isNotEmpty()) {
                updateVersion = latestVersion
            }
        } catch (e: Exception) {
            Napier.d("Update check failed: $e")
        }
    }

    if (updateVersion != null) {
        AlertDialog(
            onDismissRequest = { updateVersion = null },
            title = { Text("Update Available") },
            text = { Text("A new version ($updateVersion) is available. Your version is ${currentVersion}.") },
            confirmButton = {
                Button(onClick = {
                    webServerController.openWebPage("https://github.com/pauljohnston2025/breadcrumb-mobile/releases/latest")
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                Button(onClick = { updateVersion = null }) {
                    Text("Later")
                }
            }
        )
    }

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
                RouteRepository(fileHelper, gpxFileLoader),
                colourPaletteRepo
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

        LaunchedEffect(key1 = Unit) {
            mapViewModel.navigationEvents.collect { event ->
                // Use a 'when' statement to handle all possible navigation events
                when (event) {
                    is MapViewNavigationEvent.NavigateTo -> {
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

            val connectIqAppId by settingsViewModel.connectIqAppId.collectAsState()
            val selectedAppName = IConnection.availableConnectIqApps
                .find { it.id == connectIqAppId }?.name ?: "App"

            // Main content area wrapped by the drawer
            Scaffold(
                scaffoldState = scaffoldState, // Use the state connected to drawerState
                topBar = {
                    TopAppBar(
                        title = { // Use a Column to stack two Text composables vertically
                            Column {
                                // 1. The main screen title with default styling
                                Text(currentScreen.title)

                                // 2. The subtitle with the device and app name
                                val subtitle = deviceSelector.currentDevice.value?.let { device ->
                                    "${device.friendlyName} - $selectedAppName"
                                } ?: "No Device - $selectedAppName"

                                Text(
                                    text = subtitle,
                                    // Apply a smaller style. 'caption' is a standard small font.
                                    style = MaterialTheme.typography.caption,
                                    // Ensure it doesn't wrap to another line and adds "..." if it's still too long
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }, // Dynamic title based on current screen
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
                        val profilesViewModel = viewModel {
                            ProfilesViewModel(
                                deviceSelector,
                                connection,
                                scaffoldState.snackbarHostState,
                                settingsViewModel.tileServerRepo,
                                ProfileRepo(), // needs to be a singleton if anything else uses it
                                clipboardHandler,
                                settingsViewModel.routesRepo,
                                colourPaletteRepo
                            )
                        }
                        ProfilesScreen(
                            viewModel = profilesViewModel,
                        )
                    }

                    composable(Screen.Settings.route) {
                        @SuppressLint("StateFlowValueCalledInComposition")
                        val currentColourPaletteEdit = mapViewModel.newlyCreatedPalette.value
                        mapViewModel.onPaletteCreationHandled()
                        Settings(
                            viewModel = settingsViewModel,
                            paletteToCreate = currentColourPaletteEdit
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
