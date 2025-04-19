package com.paul.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import com.paul.infrastructure.repositories.DebugLogRepository
import com.paul.infrastructure.repositories.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

// Simple KMP ViewModel approach (manage scope manually or via framework)
class DebugViewModel:
    ViewModel() {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Define max logs to keep in UI state to prevent memory issues
    private val maxLogCount = 1000

    init {
        // Collect logs from the repository
        DebugLogRepository.logFlow
            .onEach { newEntry ->
                _logs.update { currentLogs ->
                    val updatedList = currentLogs + newEntry
                    // Trim old logs if the list exceeds the maximum count
                    if (updatedList.size > maxLogCount) {
                        updatedList.takeLast(maxLogCount)
                    } else {
                        updatedList
                    }
                }
            }
            .launchIn(viewModelScope) // Launch collection in the ViewModel's scope
    }
}