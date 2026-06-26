package com.paul.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import com.paul.infrastructure.repositories.DebugLogRepository
import com.paul.infrastructure.repositories.LogEntry
import com.paul.infrastructure.repositories.SpatialIndexRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class DebugViewModel : ViewModel() {
    private val settings = Settings()
    private val SPATIAL_INDEX_VERSION_KEY = "SPATIAL_INDEX_VERSION"

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    // UI State for Sorting
    private val _isDescending = MutableStateFlow(true)
    val isDescending = _isDescending.asStateFlow()

    val spatialIndexVersion: Int
        get() = settings.getInt(SPATIAL_INDEX_VERSION_KEY, 0)

    val targetSpatialIndexVersion: Int = SpatialIndexRepository.SPATIAL_INDEX_VERSION

    init {
        // 1. Collect New Logs
        DebugLogRepository.logFlow
            .onEach { entry ->
                _logs.update { (it + entry).takeLast(1000) }
            }
            .launchIn(viewModelScope)

        // 2. Listen for Clear Signal
        DebugLogRepository.clearSignal
            .onEach { _logs.value = emptyList() }
            .launchIn(viewModelScope)
    }

    fun clear() = DebugLogRepository.clearLogs()

    fun toggleSort() {
        _isDescending.update { !it }
    }
}