package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.IRoute
import com.paul.domain.StravaActivity
import com.paul.domain.StravaGear
import com.paul.domain.StravaIRoute
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.GeneralSettingsRepository
import com.paul.infrastructure.repositories.HistoryRepository
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.StravaRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.ILocationService
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.infrastructure.service.SendRoute
import com.paul.infrastructure.service.StravaImportService
import com.paul.ui.Screen
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed class StravaNavigationEvent {
    // Represents a command to navigate to a specific route
    data class NavigateTo(val route: String) : StravaNavigationEvent()
}

class StravaActivitiesViewModel(
    val stravaRepo: StravaRepository,
    private val mapViewModel: MapViewModel,
    private val snackbarHostState: SnackbarHostState,
    val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    val routeRepository: RouteRepository,
    val historyRepo: HistoryRepository,
    val tileServerRepo: TileServerRepo,
    private val fileHelper: IFileHelper,
    private val stravaImportService: StravaImportService,
    val generalSettingsRepo: GeneralSettingsRepository,
) : ViewModel() {

    // Use stateIn to convert the Flow from the repo into a StateFlow
    val activities: StateFlow<List<StravaActivity>> = stravaRepo.activitiesByDateRange
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allGear: StateFlow<List<StravaGear>> = stravaRepo.allGear.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val isSyncing = stravaRepo.isSyncing
    val loginStatus = stravaRepo.loginStatus
    val syncErrorStatus = stravaRepo.syncErrorStatus
    val currentRange = stravaRepo.currentRange
    val totalActivityCount: Flow<Long> = stravaRepo.getTotalCountFlow()
    val sendingFile: MutableState<String> = mutableStateOf("")
    val isImporting: MutableState<Boolean> = mutableStateOf(false)
    private var importJob: kotlinx.coroutines.Job? = null

    private val _navigationEvents = MutableSharedFlow<StravaNavigationEvent>()
    val navigationEvents: SharedFlow<StravaNavigationEvent> = _navigationEvents.asSharedFlow()

    // --- Chart State ---
    private val _isChartVisible = MutableStateFlow(false)
    val isChartVisible = _isChartVisible.asStateFlow()

    private val _chartMetric = MutableStateFlow("Distance") // "Count" or "Distance"
    val chartMetric = _chartMetric.asStateFlow()

    private val _chartInterval = MutableStateFlow("Year") // "Year" or "Month"
    val chartInterval = _chartInterval.asStateFlow()

    private val _chartScale = MutableStateFlow("Linear") // "Linear" or "Log"
    val chartScale = _chartScale.asStateFlow()

    private val _selectedGearChart = MutableStateFlow<String?>(null)
    val selectedGearChart = _selectedGearChart.asStateFlow()

    private val _selectedChartYear = MutableStateFlow(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year)
    val selectedChartYear = _selectedChartYear.asStateFlow()

    private val _yearRangeEnd = MutableStateFlow(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year)
    val yearRangeEnd = _yearRangeEnd.asStateFlow()

    val chartYearBounds: StateFlow<Pair<Int, Int>> = activities.map { acts: List<StravaActivity> ->
        if (acts.isEmpty()) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
            now to now
        } else {
            val tz = TimeZone.currentSystemDefault()
            val years = acts.map { it.startDate.toLocalDateTime(tz).year }
            years.min() to years.max()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let { it.year to it.year })

    data class ChartPoint(val x: Float, val y: Float)

    val chartData: StateFlow<Map<String, List<ChartPoint>>> = combine(
        activities,
        allGear,
        _chartMetric,
        _chartInterval,
        _selectedChartYear,
        _yearRangeEnd,
        generalSettingsRepo.currentSettingsFlow()
    ) { args: Array<Any> ->
        val activities = args[0] as List<StravaActivity>
        val gear = args[1] as List<StravaGear>
        val metric = args[2] as String
        val interval = args[3] as String
        val year = args[4] as Int
        val yearEnd = args[5] as Int
        val settings = args[6] as com.paul.domain.GeneralSettings

        val gearMap = gear.associateBy { it.id }
        val tz = TimeZone.currentSystemDefault()

        val filtered = if (interval == "Month") {
            activities.filter { it.startDate.toLocalDateTime(tz).year == year }
        } else {
            val startYear = yearEnd - settings.chartYearRange + 1
            activities.filter { it.startDate.toLocalDateTime(tz).year in startYear..yearEnd }
        }

        val grouped = filtered.groupBy { it.gearId?.let { id -> gearMap[id]?.name } ?: "No Gear" }

        grouped.mapValues { (_, acts: List<StravaActivity>) ->
            if (interval == "Month") {
                (1..12).map { month ->
                    val total = acts.filter { it.startDate.toLocalDateTime(tz).monthNumber == month }
                        .sumOf { if (metric == "Distance") it.distance.toDouble() else 1.0 }
                    ChartPoint(month.toFloat(), total.toFloat())
                }
            } else {
                val startYear = yearEnd - settings.chartYearRange + 1
                (startYear..yearEnd).map { y ->
                    val total = acts.filter { it.startDate.toLocalDateTime(tz).year == y }
                        .sumOf { if (metric == "Distance") it.distance.toDouble() else 1.0 }
                    ChartPoint(y.toFloat(), total.toFloat())
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleChart() {
        _isChartVisible.value = !_isChartVisible.value
    }

    fun setChartMetric(metric: String) {
        _chartMetric.value = metric
    }

    fun setChartInterval(interval: String) {
        _chartInterval.value = interval
    }

    fun setChartScale(scale: String) {
        _chartScale.value = scale
    }

    fun selectGearChart(gearName: String?) {
        if (_selectedGearChart.value == gearName) {
            _selectedGearChart.value = null
        } else {
            _selectedGearChart.value = gearName
        }
    }

    fun setChartYear(year: Int) {
        val bounds = chartYearBounds.value
        _selectedChartYear.value = year.coerceIn(bounds.first, bounds.second)
    }

    fun shiftYearRange(delta: Int) {
        val bounds = chartYearBounds.value
        val range = generalSettingsRepo.currentSettingsFlow().value.chartYearRange
        val newEnd = (_yearRangeEnd.value + delta).coerceIn(bounds.first + range - 1, bounds.second)
        _yearRangeEnd.value = newEnd
    }

    fun setDateRange(start: Instant, end: Instant) {
        stravaRepo.setDateRange(start, end)
    }

    fun sync() {
        if (stravaRepo.getClientId().isBlank() || stravaRepo.getClientSecret().isBlank()) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar("Please set Strava Client ID and Secret in Settings")
            }
            return
        }
        stravaRepo.startSyncActivities()
    }

    fun stopSync() {
        stravaRepo.stopSyncActivities()
    }

    fun importStravaData() {
        importJob = viewModelScope.launch {
            try {
                val zipUri = fileHelper.findFile(listOf("application/zip"))
                isImporting.value = true
                sendingFile.value = "Importing Strava Export..."
                val result = stravaImportService.importFromZip(zipUri) { progress ->
                    sendingFile.value = progress
                }
                sendingFile.value = ""
                isImporting.value = false

                if (result.isSuccess) {
                    snackbarHostState.showSnackbar("Import completed successfully")
                } else {
                    val error = result.exceptionOrNull()
                    if (error !is kotlinx.coroutines.CancellationException) {
                        snackbarHostState.showSnackbar("Import failed: ${error?.message}")
                    }
                }
            } catch (e: Exception) {
                sendingFile.value = ""
                isImporting.value = false
                if (e !is kotlinx.coroutines.CancellationException && e.message?.contains("failed to load file") == false) {
                    snackbarHostState.showSnackbar("Import failed: ${e.message}")
                }
            }
        }
    }

    fun stopImport() {
        importJob?.cancel()
        importJob = null
        isImporting.value = false
        sendingFile.value = ""
    }
    
    fun previewActivity(activity: StravaActivity) {
        val polyline = activity.map?.summaryPolyline
        if (polyline.isNullOrBlank()) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar("No map data available for this activity")
            }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val stream = stravaRepo.getStreamForActivity(activity.id)
                if (stream == null) {
                    snackbarHostState.showSnackbar("Failed to load activity stream, please do a full delete/resync")
                    return@launch
                }

                mapViewModel.displayRoute(stream.toRouteForDevice(activity.name), StravaIRoute(activity, stream))
                _navigationEvents.emit(StravaNavigationEvent.NavigateTo(Screen.Map.route))
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to parse map data")
            }
        }
    }

    fun sendActivityToDevice(activity: StravaActivity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = stravaRepo.getStreamForActivity(activity.id)
                if (stream == null) {
                    snackbarHostState.showSnackbar("Failed to load activity stream, please do a full delete/resync")
                    return@launch
                }
                sendRoute(StravaIRoute(activity, stream))

            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to load activity preview")
                Napier.e("Preview failed", t)
            }
        }
    }

    fun openActivityInStrava(id: Long) {
        viewModelScope.launch {
            try {
                // This usually involves calling a method on the repository to get the URL
                // and then using an Intent handler or similar mechanism to open it.
                stravaRepo.openActivityInBrowser(id)
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Could not open Strava activity")
                Napier.e("Failed to open Strava", t)
            }
        }
    }

    private suspend fun sendRoute(
        gpxRoute: IRoute
    ) {
        SendRoute.sendRoute(
            gpxRoute,
            deviceSelector,
            snackbarHostState,
            connection,
            routeRepository,
            historyRepo,
        )
        { msg, cb ->
            this.sendingMessage(msg, cb)
        }
    }

    private suspend fun sendingMessage(msg: String, cb: suspend (updateMsg: suspend (String) -> Unit) -> Unit) {
        SendMessageHelper.sendingMessage(viewModelScope, sendingFile, msg, cb)
    }
}