package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.IRoute
import com.paul.domain.RouteEntry
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.HistoryRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.infrastructure.service.SendRoute
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RoutesViewModel(
    private val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    val routeRepo: RouteRepository,
    private val historyRepo: HistoryRepository,
    private val snackbarHostState: SnackbarHostState
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
        // TODO: show route on maps screen
        println("Previewing route: ${route.name} (${route.id})")
    }

    fun sendRoute(route: RouteEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val route = routeRepo.getRouteI(route.id)
            if(route == null) {
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
