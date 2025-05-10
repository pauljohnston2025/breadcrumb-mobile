package com.paul.infrastructure.repositories

import com.paul.domain.Profile
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class ProfileRepo() {
    companion object {
        val PROFILES_KEY = "PROFILES"
        val settings: Settings = Settings()
    }

    private val availableProfiles = MutableStateFlow(listOf<Profile>())

    fun availableProfilesFlow() = availableProfiles.asStateFlow()

    init {
        val profilesString = settings.getStringOrNull(PROFILES_KEY)
        val profiles = when (profilesString) {
            null -> listOf()
            else -> Json.decodeFromString<List<Profile>>(profilesString)
        }

        val newList = mutableListOf<Profile>()
        profiles.forEach { newList.add(it) }
        availableProfiles.value = newList.toList()
    }

    fun get(id: String): Profile? {
        return availableProfiles.value.find { it.profileSettings.id == id }
    }

    suspend fun addProfile(profile: Profile) {
        val newList = availableProfiles.value.toMutableList()
        newList.add(profile)
        availableProfiles.emit(newList.toList())
        settings.putString(PROFILES_KEY, Json.encodeToString(newList))
    }

    suspend fun removeProfile(id: String) {
        val newList = availableProfiles.value.toMutableList()
        newList.removeIf { it.profileSettings.id == id }
        availableProfiles.emit(newList.toList())
        settings.putString(PROFILES_KEY, Json.encodeToString(newList))
    }

    suspend fun updateProfile(profile: Profile) {
        // could do this on one op?
        removeProfile(profile.profileSettings.id)
        addProfile(profile)
    }
}