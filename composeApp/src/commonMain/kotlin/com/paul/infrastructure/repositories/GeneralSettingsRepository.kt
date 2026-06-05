package com.paul.infrastructure.repositories

import com.paul.domain.GeneralSettings
import com.paul.infrastructure.repositories.TileServerRepo.Companion.settings
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GeneralSettingsRepository {
    companion object {
        private const val TAG = "GeneralSettingsRepository"
        private val SETTINGS_KEY = "GENERAL_SETTINGS"

        fun getSettings(): GeneralSettings {
            val generalSettings = settings.getStringOrNull(SETTINGS_KEY)

            if (generalSettings == null) {
                return GeneralSettings.default
            }

            return try {
                Json.decodeFromString<GeneralSettings>(generalSettings)
            } catch (t: Throwable) {
                Napier.w("Failed to decode GeneralSettings, using defaults", t, tag = TAG)
                return GeneralSettings.default
            }
        }
    }

    val settings: Settings = Settings()
    private val currentSettings: MutableStateFlow<GeneralSettings> = MutableStateFlow(
        getSettings()
    )

    fun currentSettingsFlow(): StateFlow<GeneralSettings> {
        return currentSettings.asStateFlow()
    }

    suspend fun saveSettings(generalSettings: GeneralSettings) {
        currentSettings.emit(generalSettings)
        settings.putString(SETTINGS_KEY, Json.encodeToString(generalSettings))
    }
}
