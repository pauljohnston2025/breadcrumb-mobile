package com.paul.infrastructure.repositories

import com.paul.domain.RouteEntry
import com.paul.domain.RouteType
import com.paul.domain.StravaActivity
import com.paul.domain.StravaTokenResponse
import com.paul.infrastructure.service.IBrowserLauncher
import com.paul.infrastructure.web.KtorClient
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.github.aakira.napier.Napier
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class StravaRepository(private val browserLauncher: IBrowserLauncher) {
    private val settings = Settings()
    private val client = KtorClient.client

    // Internal cache of activities to avoid re-fetching
    private val _activities = MutableStateFlow<List<RouteEntry>>(loadLocalActivities())
    val activities: StateFlow<List<RouteEntry>> = _activities.asStateFlow()

    private val repoScope = CoroutineScope(Dispatchers.Default)

    // UI can observe this to show success/error messages
    private val _loginStatus = MutableStateFlow<String?>(null)
    val loginStatus: StateFlow<String?> = _loginStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    companion object {
        private const val CLIENT_ID_KEY = "STRAVA_CLIENT_ID"
        private const val CLIENT_SECRET_KEY = "STRAVA_CLIENT_SECRET"
        private const val ACCESS_TOKEN_KEY = "STRAVA_ACCESS_TOKEN"
        private const val REFRESH_TOKEN_KEY = "STRAVA_REFRESH_TOKEN"
        private const val LOCAL_ACTIVITIES_KEY = "STRAVA_LOCAL_CACHE"

        private const val REDIRECT_URI = "paulapp://localhost"
        private const val AUTH_URL = "https://www.strava.com/oauth/mobile/authorize"
        private const val API_BASE_URL = "https://www.strava.com/api/v3"
    }

    suspend fun syncNewest() = sync(direction = "after")
    suspend fun syncOlder() = sync(direction = "before")

    private suspend fun sync(direction: String) {
        _isSyncing.value = true // Start Spinner
        val token = settings.getStringOrNull(ACCESS_TOKEN_KEY) ?: return
        var keepGoing = true
        var totalSynced = 0

        // Initial anchor
        var currentAnchor = if (direction == "after") {
            _activities.value.maxOfOrNull { it.createdAt.epochSeconds }
        } else {
            _activities.value.minOfOrNull { it.createdAt.epochSeconds }
        }

        try {
            while (keepGoing) {
                _loginStatus.value = "Syncing Strava ($direction) ($totalSynced found)..."

                val response: List<StravaActivity> =
                    client.get("$API_BASE_URL/athlete/activities") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        url {
                            currentAnchor?.let { parameters.append(direction, it.toString()) }
                            parameters.append("per_page", "100")
                        }
                    }.body()

                if (response.isEmpty()) {
                    keepGoing = false
                } else {
                    val newEntries = response.map { it.toRouteEntry() }
                    totalSynced += newEntries.size

                    val updatedList = if (direction == "after") {
                        // Update anchor to the newest one received to keep moving forward
                        currentAnchor = newEntries.maxOf { it.createdAt.epochSeconds }
                        newEntries.reversed() + _activities.value
                    } else {
                        // Update anchor to the oldest one received to keep moving backward
                        currentAnchor = newEntries.minOf { it.createdAt.epochSeconds }
                        _activities.value + newEntries
                    }

                    _activities.value = updatedList.distinctBy { it.id }
                    saveLocalActivities(_activities.value)

                    // If we got less than 100, we've reached the end of what Strava has for this direction
                    if (response.size < 100) {
                        keepGoing = false
                    }
                }
            }
            _loginStatus.value = "Sync complete ($direction). Total: ${_activities.value.size}"
        } catch (e: Exception) {
            handleSyncError(e)
        } finally {
            _isSyncing.value = false // Stop Spinner even if it fails
        }
    }

    fun clearAllStravaData() {
        _activities.value = emptyList()
        settings.remove(LOCAL_ACTIVITIES_KEY)
        _loginStatus.value = "Local cache cleared."
    }

    suspend fun syncActivities() {
        syncNewest()
        syncOlder()

        _loginStatus.value = "Sync Complete"
    }

    private fun handleSyncError(e: Exception) {
        when (e) {
            is io.ktor.client.plugins.ClientRequestException -> {
                if (e.response.status.value == 429) {
                    _loginStatus.value = "Rate limit hit. Resuming in 15 mins..."
                } else {
                    _loginStatus.value = "Strava error: ${e.response.status.value}"
                }
            }

            else -> {
                _loginStatus.value = "Sync failed. Check connection."
                Napier.e("Sync Error", e)
            }
        }
    }

    // --- Persistence Logic ---

    private fun saveLocalActivities(list: List<RouteEntry>) {
        val json = Json.encodeToString(list)
        settings.putString(LOCAL_ACTIVITIES_KEY, json)
    }

    private fun loadLocalActivities(): List<RouteEntry> {
        val json = settings.getStringOrNull(LOCAL_ACTIVITIES_KEY) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- OAuth & Helpers ---

    // StravaRepository.kt (Common)
    fun launchAuthFlow() {
        val clientId = getClientId().trim()
        if (clientId.isBlank()) return

        // Ensure the redirect URI is properly encoded
        // %3A is : and %2F is /
        val encodedRedirect = "paulapp%3A%2F%2Flocalhost"

        val authUrl = "https://www.strava.com/oauth/mobile/authorize" +
                "?client_id=$clientId" +
                "&redirect_uri=$encodedRedirect" +
                "&response_type=code" +
                "&approval_prompt=auto" +
                "&scope=activity:read_all"

        Napier.d("Launching Strava Auth: $authUrl")
        browserLauncher.openUri(authUrl)
    }

    // --- OAuth Handlers ---

    fun stravaOauthSuccess(code: String) {
        Napier.d("OAuth code received, starting token exchange...")
        _loginStatus.value = "Authenticating with Strava..."

        // Launch a coroutine to handle the suspend login call
        repoScope.launch {
            login(code)
        }
    }

    fun stravaOauthFailed(error: String) {
        Napier.e("Strava Auth Failed: $error")
        _loginStatus.value = "Strava Auth Failed: $error"
    }

    suspend fun login(code: String) {
        val id = getClientId()
        val secret = getClientSecret()

        if (id.isBlank() || secret.isBlank()) {
            _loginStatus.value = "Error: Missing Client ID or Secret"
            return
        }

        try {
            val response: StravaTokenResponse =
                client.post("https://www.strava.com/api/v3/oauth/token") {
                    url {
                        parameters.append("client_id", id)
                        parameters.append("client_secret", secret)
                        parameters.append("code", code)
                        parameters.append("grant_type", "authorization_code")
                    }
                }.body()

            settings[ACCESS_TOKEN_KEY] = response.accessToken
            settings[REFRESH_TOKEN_KEY] = response.refreshToken

            _loginStatus.value = "Success! Fetching activities..."
            syncActivities()
            _loginStatus.value = "Strava Connected: ${_activities.value.size} activities cached."
        } catch (e: Exception) {
            Napier.e("Login failed", e)
            _loginStatus.value = "Login failed: ${e.message}"
        }
    }

    fun clearStatus() {
        _loginStatus.value = null
    }

    fun getClientId() = settings.getString(CLIENT_ID_KEY, "")
    fun saveClientId(id: String) = settings.putString(CLIENT_ID_KEY, id)
    fun getClientSecret() = settings.getString(CLIENT_SECRET_KEY, "")
    fun saveClientSecret(secret: String) = settings.putString(CLIENT_SECRET_KEY, secret)

    private fun StravaActivity.toRouteEntry() = RouteEntry(
        id = "strava_$id",
        name = name,
        type = RouteType.COORDINATES,
        createdAt = startDate,
        sizeBytes = 0,
        hasDirectionInfo = false
    )
}
