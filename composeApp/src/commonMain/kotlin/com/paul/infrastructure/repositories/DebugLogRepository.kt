package com.paul.infrastructure.repositories

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime

// Simple Log Entry structure (can be enhanced)
data class LogEntry(
    val timestamp: Long, // Consider using kotlinx-datetime for multiplatform time
    val level: String,
    val tag: String?,
    val message: String,
    val id: String = uuid4().toString()
) {
    // Basic formatter for display
    override fun toString(): String {
        val timeStr = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(currentSystemDefault()).toString()
        val tagStr = tag?.let { "[$it]" } ?: ""
        return "$timeStr ${level.padEnd(5)} $tagStr: $message"
    }
}

object DebugLogRepository {
    // Use a CoroutineScope that won't be tied to a specific screen lifecycle
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Use SharedFlow for multiple collectors and to emit events
    // replay=500 keeps a buffer of the last 500 logs for new subscribers
    private val _logFlow = MutableSharedFlow<LogEntry>(replay = 500)
    val logFlow = _logFlow.asSharedFlow()

    // Function for the Antilog to add entries
    fun addLog(logEntry: LogEntry) {
        // Use tryEmit for non-suspending contexts like Napier's log function
        // If buffer is full, oldest entries are dropped (based on replay)
        scope.launch { // Launch helps ensure emission even if tryEmit buffer is momentarily full
            _logFlow.emit(logEntry)
        }
    }

    // Optional: Function to clear logs (if needed from ViewModel/UI)
    // This only clears the replay buffer, existing collectors won't lose past logs
    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearLogs() {
        _logFlow.resetReplayCache()
        // You might want to add a special "Logs Cleared" entry
        // addLog(LogEntry(System.currentTimeMillis(), "INFO", "DebugLog", "Logs Cleared"))
    }
}