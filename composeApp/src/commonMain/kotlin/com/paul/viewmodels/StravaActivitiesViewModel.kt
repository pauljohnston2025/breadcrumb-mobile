package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.StravaActivity
import com.paul.infrastructure.repositories.StravaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class StravaActivitiesViewModel(
    val stravaRepo: StravaRepository,
    private val snackbarHostState: SnackbarHostState
) : ViewModel() {

    // Use stateIn to convert the Flow from the repo into a StateFlow
    val activities: StateFlow<List<StravaActivity>> = stravaRepo.activities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isSyncing = stravaRepo.isSyncing
    val loginStatus = stravaRepo.loginStatus
    val currentRange = stravaRepo.currentRange
    val totalActivityCount: Flow<Long> = stravaRepo.getTotalCountFlow()

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
}