package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.paul.domain.IRoute
import com.paul.domain.RouteEntry
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.HistoryRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.infrastructure.service.SendRoute
import com.paul.ui.Screen
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoutesViewModel(
    private val mapViewModel: MapViewModel,
    private val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    val routeRepo: RouteRepository,
    private val historyRepo: HistoryRepository,
    private val snackbarHostState: SnackbarHostState,
    private val navController: NavController
) : ViewModel() {
    // State for controlling the edit dialog
    private val _editingRoute = MutableStateFlow<RouteEntry?>(null)
    val sendingFile: MutableState<String> = mutableStateOf("")
    val editingRoute: StateFlow<RouteEntry?> = _editingRoute.asStateFlow()

    // State for controlling the delete confirmation dialog
    private val _deletingRoute = MutableStateFlow<RouteEntry?>(null)
    val deletingRoute: StateFlow<RouteEntry?> = _deletingRoute.asStateFlow()

    fun startEditing(route: RouteEntry) {
        _editingRoute.value = route
    }

    fun cancelEditing() {
        _editingRoute.value = null
    }

    fun confirmEdit(routeId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            routeRepo.updateRoute(routeId, newName)
            _editingRoute.value = null // Close dialog
        }
    }

    fun requestDelete(route: RouteEntry) {
        _deletingRoute.value = route
    }

    fun cancelDelete() {
        _deletingRoute.value = null
    }

    fun confirmDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            _deletingRoute.value?.let { routeToDelete ->
                routeRepo.deleteRoute(routeToDelete.id)
            }
            _deletingRoute.value = null // Close dialog
        }
    }

    fun previewRoute(route: RouteEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            var iRoute = routeRepo.getRouteI(route.id)
            if (iRoute == null) {
                snackbarHostState.showSnackbar("Unknown route")
                return@launch
            }
            var coords = iRoute.toRoute(snackbarHostState)
            if (coords == null) {
                snackbarHostState.showSnackbar("Bad coordinates")
                return@launch
            }
            mapViewModel.displayRoute(coords)
        }

        // Navigate if necessary
        val current = navController.currentDestination
        if (current?.route != Screen.Map.route) {
            navController.navigate(Screen.Map.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    fun sendRoute(route: RouteEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val route = routeRepo.getRouteI(route.id)
            if (route == null) {
                snackbarHostState.showSnackbar("failed to load route")
                Napier.d("Failed to load route")
                return@launch
            }
            sendRoute(route)
        }
    }

    private suspend fun sendRoute(
        gpxRoute: IRoute
    ) {
        SendRoute.sendRoute(
            gpxRoute,
            deviceSelector,
            snackbarHostState,
            connection,
            routeRepo,
            historyRepo
        )
        { msg, cb ->
            this.sendingMessage(msg, cb)
        }
    }

    private suspend fun sendingMessage(msg: String, cb: suspend () -> Unit) {
        SendMessageHelper.sendingMessage(viewModelScope, sendingFile, msg, cb)
    }
}
