package com.paul.infrastructure.service

import com.paul.infrastructure.repositories.DebugLogRepository
import com.paul.infrastructure.repositories.LogEntry
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

class InMemoryDebugAntilog : Antilog() {

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        val logMessage = buildString {
            message?.let { append(it) }
            throwable?.let { append("\n${it.stackTraceToString()}") } // Include stack trace
        }

        if (logMessage.isNotEmpty()) {
            val entry = LogEntry(
                timestamp = currentTimeMillis(), // Platform-specific or kotlinx-datetime
                level = priority.name,
                tag = tag,
                message = logMessage
            )
            DebugLogRepository.addLog(entry)
        }
    }

    // Helper function to get current time (implement expect/actual if needed)
    // Or use kotlinx-datetime Instant.now().toEpochMilliseconds()
    private fun currentTimeMillis(): Long = platformCurrentTimeMillis()
}

// Add expect/actual for time if not using kotlinx-datetime
// commonMain
expect fun platformCurrentTimeMillis(): Long
