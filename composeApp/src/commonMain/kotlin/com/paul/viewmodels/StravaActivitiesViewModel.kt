package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.StravaActivity
import com.paul.infrastructure.repositories.StravaRepository
import com.paul.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

sealed class StravaNavigationEvent {
    // Represents a command to navigate to a specific route
    data class NavigateTo(val route: String) : StravaNavigationEvent()
}

class StravaActivitiesViewModel(
    val stravaRepo: StravaRepository,
    private val mapViewModel: MapViewModel,
    private val snackbarHostState: SnackbarHostState
) : ViewModel() {

    // Use stateIn to convert the Flow from the repo into a StateFlow
    val activities: StateFlow<List<StravaActivity>> = stravaRepo.activitiesByDateRange
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isSyncing = stravaRepo.isSyncing
    val loginStatus = stravaRepo.loginStatus
    val syncErrorStatus = stravaRepo.syncErrorStatus
    val currentRange = stravaRepo.currentRange
    val totalActivityCount: Flow<Long> = stravaRepo.getTotalCountFlow()

    private val _navigationEvents = MutableSharedFlow<StravaNavigationEvent>()
    val navigationEvents: SharedFlow<StravaNavigationEvent> = _navigationEvents.asSharedFlow()

    fun setDateRange(start: Instant, end: Instant) {
        stravaRepo.setDateRange(start, end)
    }

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stravaRepo.syncActivities()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Sync failed: ${e.message}")
            }
        }
    }

    fun previewActivity(activity: StravaActivity) {
        val polyline = activity.map?.summaryPolyline
        if (polyline.isNullOrBlank()) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar("No map data available for this activity")
            }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val stream = stravaRepo.getStreamForActivity(activity.id)
                if (stream == null) {
                    snackbarHostState.showSnackbar("Failed to load activity stream, please do a full delete/resync")
                    return@launch
                }

                mapViewModel.displayRoute(stream.toRoute(activity.name))
                _navigationEvents.emit(StravaNavigationEvent.NavigateTo(Screen.Map.route))
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to parse map data")
            }
        }
    }
}