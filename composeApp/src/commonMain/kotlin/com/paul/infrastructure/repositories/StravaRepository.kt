package com.paul.infrastructure.repositories

import androidx.compose.animation.core.copy
import com.paul.domain.StravaActivity
import com.paul.domain.StravaAthleteResponse
import com.paul.domain.StravaGear
import com.paul.domain.StravaStreamEntity
import com.paul.domain.StravaStreamResponse
import com.paul.domain.StravaTokenResponse
import com.paul.infrastructure.dao.StravaDao
import com.paul.infrastructure.service.IBrowserLauncher
import com.paul.infrastructure.web.KtorClient
import com.paul.protocol.todevice.Point
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.github.aakira.napier.Napier
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.get
import io.ktor.client.request.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.time.Duration.Companion.days

class StravaRepository(private val browserLauncher: IBrowserLauncher, private val dao: StravaDao) {
    private val settings = Settings()

    private val _clientId = MutableStateFlow(getClientId())
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow(getClientSecret())
    val clientSecret: StateFlow<String> = _clientSecret.asStateFlow()

    private val stravaClient = KtorClient.client.config {
        // 1. Ensure Auth is installed correctly
        install(Auth) {
            // 2. This must be 'bearer', which takes a config block
            bearer {
                // 3. This block has its own specific scope
                loadTokens {
                    val access = settings.getStringOrNull(ACCESS_TOKEN_KEY)
                    val refresh = settings.getStringOrNull(REFRESH_TOKEN_KEY)
                    if (access != null && refresh != null) {
                        BearerTokens(access, refresh)
                    } else {
                        null
                    }
                }
                refreshTokens {
                    val oldRefresh =
                        settings.getStringOrNull(REFRESH_TOKEN_KEY) ?: return@refreshTokens null

                    try {
                        val response: StravaTokenResponse =
                            KtorClient.client.post("https://www.strava.com/api/v3/oauth/token") {
                                url {
                                    parameters.append("client_id", getClientId())
                                    parameters.append("client_secret", getClientSecret())
                                    parameters.append("grant_type", "refresh_token")
                                    parameters.append("refresh_token", oldRefresh)
                                }
                            }.body()

                        // NO CURLY BRACES HERE
                        settings[ACCESS_TOKEN_KEY] = response.accessToken
                        settings[REFRESH_TOKEN_KEY] = response.refreshToken

                        BearerTokens(response.accessToken, response.refreshToken)
                    } catch (e: Exception) {
                        Napier.e("Token refresh failed", e)
                        null
                    }
                }
            }
        }

        install(DefaultRequest) {
            url("https://www.strava.com/api/v3/")
        }
    }

    val allGear: Flow<List<StravaGear>> = dao.getAllGear()

    private val repoScope = CoroutineScope(Dispatchers.Default)

    // UI can observe this to show success/error messages
    private val _loginStatus = MutableStateFlow<String?>(null)
    val loginStatus: StateFlow<String?> = _loginStatus.asStateFlow()

    private val _syncErrorStatus = MutableStateFlow<String?>(null)
    val syncErrorStatus: StateFlow<String?> = _syncErrorStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // UI updates this range; the Flow below automatically reacts
    private val _currentRange = MutableStateFlow(getInitialRange())
    val currentRange: StateFlow<ClosedRange<Instant>> = _currentRange.asStateFlow()

    private val _currentPage = MutableStateFlow(getInitialPage())
    val currentPage: StateFlow<Long> = _currentPage.asStateFlow()

    private val _currentPageSize = MutableStateFlow(getInitialPageSize())
    val currentPageSize: StateFlow<Long> = _currentPageSize.asStateFlow()

    fun getTotalCountFlow() = dao.sizeFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activitiesByDateRange: Flow<List<StravaActivity>> = _currentRange.flatMapLatest { range ->
        dao.getActivitiesByDateRange(range.start, range.endInclusive)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val maxPages: Flow<Long> = combine(_currentRange, _currentPageSize) { range, size ->
        range to size
    }.flatMapLatest { (range, size) ->
        dao.getTotalActivityCount(range.start, range.endInclusive).map { count ->
            if (size <= 0) 1L else ceil(count.toDouble() / size).toLong()
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activitiesByDateRangeAndPage: Flow<List<StravaActivity>> = combine(
        _currentRange,
        _currentPage,
        _currentPageSize
    ) { range, page, pageSize ->
        Triple(range, page, pageSize)
    }.distinctUntilChanged() // Prevents redundant DB hits if state emits same values
        .flatMapLatest { (range, page, pageSize) ->
            dao.getActivitiesByDateRangeAndPage(
                start = range.start,
                end = range.endInclusive,
                page = page,
                pageSize = pageSize
            )
        }

    fun setDateRange(start: Instant, end: Instant) {
        settings.putLong(START_DATE_KEY, start.epochSeconds)
        settings.putLong(END_DATE_KEY, end.epochSeconds)
        _currentRange.value = start..end
        // Reset to page 0 when date range changes to avoid empty views
        setPage(0)
    }

    fun setPage(page: Long) {
        settings.putLong(PAGE_KEY, page)
        _currentPage.value = page
    }

    fun setPageSize(pageSize: Long) {
        settings.putLong(PAGE_SIZE_KEY, pageSize)
        _currentPageSize.value = pageSize
        // Reset to page 0 when page size changes to maintain consistency
        setPage(0)
    }

    private fun getInitialRange(): ClosedRange<Instant> {
        val now = Clock.System.now()
        val oneMonthAgo = now.toLocalDateTime(TimeZone.currentSystemDefault())
            .toInstant(TimeZone.currentSystemDefault())
            .minus(30.days)

        val startEpoch = settings.getLongOrNull(START_DATE_KEY)
        val endEpoch = settings.getLongOrNull(END_DATE_KEY)

        val start = if (startEpoch != null) Instant.fromEpochSeconds(startEpoch) else oneMonthAgo
        val end = if (endEpoch != null) Instant.fromEpochSeconds(endEpoch) else now

        return start..end
    }

    private fun getInitialPage(): Long {
        return settings.getLongOrNull(PAGE_KEY) ?: 0L
    }

    private fun getInitialPageSize(): Long {
        return settings.getLongOrNull(PAGE_SIZE_KEY) ?: 20L
    }

    companion object {
        private const val CLIENT_ID_KEY = "STRAVA_CLIENT_ID"
        private const val CLIENT_SECRET_KEY = "STRAVA_CLIENT_SECRET"
        private const val ACCESS_TOKEN_KEY = "STRAVA_ACCESS_TOKEN"
        private const val REFRESH_TOKEN_KEY = "STRAVA_REFRESH_TOKEN"
        private const val LOCAL_ACTIVITIES_KEY = "STRAVA_LOCAL_CACHE"
        private const val START_DATE_KEY = "FILTER_START_DATE"
        private const val END_DATE_KEY = "FILTER_END_DATE"
        private const val PAGE_KEY = "FILTER_PAGE"
        private const val PAGE_SIZE_KEY = "FILTER_PAGE_SIZE"

        private const val REDIRECT_URI = "paulapp://localhost"
        private const val AUTH_URL = "https://www.strava.com/oauth/mobile/authorize"
        private const val API_BASE_URL = "https://www.strava.com/api/v3"
    }

    suspend fun getActivityStreams(activityId: Long): List<Point> {
        return try {
            // 1. Request the response (don't call .body() yet)
            val response = stravaClient.get("activities/$activityId/streams") {
                url {
                    parameters.append("keys", "latlng,altitude")
                    parameters.append("key_by_type", "true")
                }
            }

            // 2. Check for 404 (Resource Not Found)
            if (response.status == io.ktor.http.HttpStatusCode.NotFound) {
                // or it may be an activity without a gps recording (manual entry)
                Napier.w("Streams not found for activity $activityId (404). It may be deleted or private.")
                return emptyList()
            }

            // 3. Parse the body now that we know it's a success
            val streamData: StravaStreamResponse = response.body()

            // 4. Extract data safely.
            val latLngData = streamData.latlng?.data ?: return emptyList()
            val altitudeData = streamData.altitude?.data

            // 5. Map to your internal Point objects
            latLngData.mapIndexed { index, coords ->
                // coords is a List<Double> containing [lat, lng]
                val lat = coords.getOrNull(0)?.toFloat() ?: 0f
                val lng = coords.getOrNull(1)?.toFloat() ?: 0f

                // If altitudeData exists and has a value for this index, use it.
                // Otherwise, default to 0f.
                val alt = altitudeData?.getOrNull(index) ?: 0f

                Point(
                    latitude = lat,
                    longitude = lng,
                    altitude = alt
                )
            }
        } catch (e: Exception) {
            // Essential: Do not swallow CancellationExceptions or the sync loop will hang
            if (e is kotlinx.coroutines.CancellationException) throw e

            // Handle the case where DefaultResponseValidation still throws for other 4xx/5xx errors
            if (e is io.ktor.client.plugins.ClientRequestException && e.response.status == io.ktor.http.HttpStatusCode.NotFound) {
                Napier.w("Caught 404 via Exception for activity $activityId")
                return emptyList()
            }

            Napier.e("Failed to fetch/parse streams for $activityId", e)
            _syncErrorStatus.value = "Failed to parse data for $activityId"
            throw e
        }
    }

    suspend fun syncNewest() = sync(direction = "after")
    suspend fun syncOlder() = sync(direction = "before")

    private suspend fun sync(direction: String) {
        _isSyncing.value = true
        var keepGoing = true
        var totalDiscovered = 0
        var currentAnchor: Long? = if (direction == "after") {
            dao.getLatestTimestamp()?.epochSeconds
        } else {
            dao.getOldestTimestamp()?.epochSeconds
        }

        try {
            while (keepGoing) {
                _loginStatus.value = "Fetching next batch from Strava..."

                val response: List<StravaActivity> = stravaClient.get("athlete/activities") {
                    url {
                        currentAnchor?.let { parameters.append(direction, it.toString()) }
                        parameters.append("per_page", "100")
                    }
                }.body()

                if (response.isEmpty()) {
                    keepGoing = false
                } else {
                    val batchSize = response.size
                    totalDiscovered += batchSize

                    // Process this batch immediately
                    response.forEachIndexed { index, activity ->
                        // This calculates global progress:
                        // (Number of items from previous batches) + (current item index + 1)
                        val globalIndex = (totalDiscovered - batchSize) + (index + 1)

                        _loginStatus.value =
                            "Processing $globalIndex of $totalDiscovered: ${activity.name}"

                        // 1. Fetch and save heavy stream data
                        val fullPoints = getActivityStreams(activity.id)
                        dao.insertStream(StravaStreamEntity(activity.id, fullPoints))

                        // 2. Save activity header
                        dao.insertActivities(listOf(activity))
                    }

                    // Update anchor for next batch
                    currentAnchor = if (direction == "after") {
                        response.maxOf { it.startDate.epochSeconds }
                    } else {
                        response.minOf { it.startDate.epochSeconds }
                    }

                    // Stop if we reached the end of the available data
                    if (batchSize < 100) {
                        keepGoing = false
                    }
                }
            }
            _loginStatus.value = "Sync complete. Total processed: $totalDiscovered"
        } catch (e: Exception) {
            handleSyncError(e)
        } finally {
            _loginStatus.value = null // moving on to next step, or coroutine killed
            _isSyncing.value = false
        }
    }

    suspend fun syncMissingStreams() {
        val missingIds = dao.getActivityIdsMissingStreams()
        if (missingIds.isEmpty()) return

        _isSyncing.value = true
        val total = missingIds.size

        try {
            missingIds.forEachIndexed { index, id ->
                val current = index + 1
                _loginStatus.value = "Repairing streams: $current of $total"

                val points = getActivityStreams(id)
                if (points.isNotEmpty()) {
                    dao.insertStream(StravaStreamEntity(id, points))
                }
            }
            _loginStatus.value = "Stream repair complete."
        } catch (e: Exception) {
            handleSyncError(e)
        } finally {
            _loginStatus.value = null // moving on to next step, or coroutine killed
            _isSyncing.value = false
        }
    }

    suspend fun clearAllStravaData() {
        dao.clearAll()
        dao.clearAllStreams()
        dao.deleteAllGear()
        _loginStatus.value = "Local cache cleared."
    }

    suspend fun getStreamForActivity(id: Long): StravaStreamEntity? {
        return dao.getStreamForActivity(id)
    }

    suspend fun getStreamsForActivityIds(ids: List<Long>): Map<Long, StravaStreamEntity> {
        return dao.getStreamsForActivityIds(ids).associateBy { it.activityId }
    }

    suspend fun syncAthleteMetadata() {
        _isSyncing.value = true
        _loginStatus.value = "Fetching athlete metadata from Strava..."
        try {
            val athlete: StravaAthleteResponse = stravaClient.get("athlete").body()

            // Map the plain JSON lists to our Entity with the correct types
            val bikes = athlete.bikes.map { it.copy(type = StravaGear.TYPE_BIKE) }
            val shoes = athlete.shoes.map { it.copy(type = StravaGear.TYPE_SHOE) }

            dao.insertGear(bikes + shoes)
        } catch (e: Exception) {
            Napier.e("Failed to sync Strava gear metadata", e)
            handleSyncError(e)
        } finally {
            _loginStatus.value = null // moving on to next step, or coroutine killed
            _isSyncing.value = false
        }
    }

    suspend fun syncActivities() {
        _syncErrorStatus.value = null
        syncAthleteMetadata()
        if (_syncErrorStatus.value != null) {
            return
        }
        syncMissingStreams()
        if (_syncErrorStatus.value != null) {
            return
        }
        syncNewest()
        if (_syncErrorStatus.value != null) {
            return
        }
        syncOlder()
        if (_syncErrorStatus.value != null) {
            return
        }

        _loginStatus.value = "Sync Complete"
    }

    private fun handleSyncError(e: Exception) {
        // Essential: Do not swallow CancellationExceptions or the sync loop will hang
        if (e is kotlinx.coroutines.CancellationException) throw e

        when (e) {
            is io.ktor.client.plugins.ClientRequestException -> {
                if (e.response.status.value == 429) {
                    _syncErrorStatus.value = "Rate limit hit. Try Again in 15 mins..."
                } else {
                    _syncErrorStatus.value = "Strava error: ${e.response.status.value}"
                }
            }

            else -> {
                _syncErrorStatus.value = "Sync failed. Check connection."
                Napier.e("Sync Error", e)
            }
        }
    }

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
                "&scope=read,activity:read_all,profile:read_all"

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
        if (id.isBlank() || secret.isBlank()) return

        try {
            // Use base client for initial login to avoid Auth plugin logic
            val response: StravaTokenResponse =
                KtorClient.client.post("https://www.strava.com/api/v3/oauth/token") {
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
        } catch (e: Exception) {
            _loginStatus.value = "Login failed: ${e.message}"
        }
    }

    fun getClientId() = settings.getString(CLIENT_ID_KEY, "")
    fun saveClientId(id: String) {
        settings.putString(CLIENT_ID_KEY, id)
        _clientId.value = id
    }

    fun getClientSecret() = settings.getString(CLIENT_SECRET_KEY, "")
    fun saveClientSecret(secret: String) {
        settings.putString(CLIENT_SECRET_KEY, secret)
        _clientSecret.value = secret
    }
    fun openActivityInBrowser(id: Long) {
        val activityUrl = "https://www.strava.com/activities/$id"
        try {
            browserLauncher.openUri(activityUrl)
        } catch (e: Exception) {
            Napier.e("Could not open Strava activity in browser", e)
            throw e
        }
    }

    suspend fun getActivity(activityId: Long): StravaActivity? {
        return dao.getActivity(activityId)
    }
}
