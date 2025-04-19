package com.paul.infrastructure.repositories

import androidx.compose.runtime.mutableStateListOf
import com.paul.domain.HistoryItem
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json

class HistoryRepository {
    val HISTORY_KEY = "HISTORY"
    val settings: Settings = Settings()
    val history = mutableStateListOf<HistoryItem>()

    init {
        val historyJson = settings.getStringOrNull(HISTORY_KEY)
        if (historyJson != null) {
            try {
                Json.decodeFromString<List<HistoryItem>>(historyJson).forEach {
                    history.add(it)
                }
            } catch (t: Throwable) {
                Napier.d("failed to hydrate history items $t")
            }
        }
    }

    fun add(historyItem: HistoryItem) {
        history.add(historyItem)
        saveHistory()
    }

    private fun saveHistory() {
        // keep only the last few items, we do not want to overflow out internal storage
        settings.putString(HISTORY_KEY, Json.encodeToString(history.toList().takeLast(100)))
    }

    fun clear() {
        history.clear()
        saveHistory()
    }
}