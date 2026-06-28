package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benasher44.uuid.uuid4
import com.paul.domain.AppSettings
import com.paul.domain.ExportedProfile
import com.paul.domain.IqDevice
import com.paul.domain.LastKnownDevice
import com.paul.domain.Profile
import com.paul.domain.ProfileSettings
import com.paul.domain.ProfileAppInfo
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.connectiq.QueryResult
import com.paul.infrastructure.repositories.ColourPaletteRepository
import com.paul.infrastructure.repositories.GeneralSettingsRepository
import com.paul.infrastructure.repositories.ProfileRepo
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.StravaRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.IClipboardHandler
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.fromdevice.Settings
import com.paul.protocol.todevice.RequestSettings
import com.paul.protocol.todevice.SaveSettings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json


class ProfilesViewModel(
    private val deviceSelector: DeviceSelector,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState,
    private val tileServerRepo: TileServerRepo,
    val profileRepo: ProfileRepo,
    val clipboardHandler: IClipboardHandler,
    val routeRepo: RouteRepository,
    val generalSettingsRepo: GeneralSettingsRepository,
    private val colourPaletteRepo: ColourPaletteRepository,
    private val stravaRepo: StravaRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "ProfilesViewModel"
    }

    val sendingMessage: MutableState<String> = mutableStateOf("")

    private val _creatingProfile = MutableStateFlow<Boolean>(false)
    val creatingProfile: StateFlow<Boolean> = _creatingProfile.asStateFlow()

    private val _importingProfile = MutableStateFlow<Boolean>(false)
    val importingProfile: StateFlow<Boolean> = _importingProfile.asStateFlow()

    private val _editingProfile = MutableStateFlow<Profile?>(null)
    val editingProfile: StateFlow<Profile?> = _editingProfile.asStateFlow()

    // State for controlling the delete confirmation dialog
    private val _deletingProfile = MutableStateFlow<Profile?>(null)
    val deletingProfile: StateFlow<Profile?> = _deletingProfile.asStateFlow()

    val settingsLoading: MutableState<Boolean> = mutableStateOf(false)
    public var settingsJob: Deferred<QueryResult<Settings>?>? = null

    fun startCreate() {
        _creatingProfile.value = true
    }

    fun cancelCreate() {
        _creatingProfile.value = false
    }

    fun confirmCreate(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            onCreateProfile(label)
            _creatingProfile.value = false // Close dialog
        }
    }

    fun startImport() {
        _importingProfile.value = true
    }

    fun cancelImport() {
        _importingProfile.value = false
    }

    fun confirmImport(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            onImportProfile(json)
            _importingProfile.value = false // Close dialog
        }
    }

    fun startEditing(profile: Profile) {
        _editingProfile.value = profile
    }

    fun cancelEditing() {
        _editingProfile.value = null
    }

    fun confirmEdit(
        profileId: String,
        label: String,
        loadWatchSettings: Boolean,
        loadAppSettings: Boolean
    ) {
        val that = this
        viewModelScope.launch(Dispatchers.IO) {
            _editingProfile.value = null // Close dialog
            profileRepo.get(profileId)?.let { profile ->
                var deviceSettings: Map<String, Any>? = profile.deviceSettings()
                var lastKnownDevice = profile.lastKnownDevice

                if (loadWatchSettings) {
                    val settingsResult = that.loadDeviceSettings()
                    deviceSettings = settingsResult?.response?.settings
                    val installedApps = getInstalledApps()
                    if (installedApps.isEmpty()) {
                        return@launch
                    }

                    val device = deviceSelector.currentDevice()
                    if (device == null) {
                        snackbarHostState.showSnackbar("no devices selected")
                        return@launch
                    }

                    val settingsSourceAppId = settingsResult?.appId
                    val profileApps = installedApps.map {
                        ProfileAppInfo(it.version, it.appId, it.displayName, it.appId == settingsSourceAppId)
                    }
                    val primaryApp = profileApps.find { it.isSettingsSource } ?: profileApps.first()
                    val primaryVersion = primaryApp.version

                    lastKnownDevice = LastKnownDevice(primaryVersion, device.friendlyName, profileApps)
                }

                if (deviceSettings == null) {
                    snackbarHostState.showSnackbar("no device settings")
                    return@launch
                }

                profileRepo.updateProfile(
                    Profile.build(
                        profile.profileSettings.copy(
                            label = label
                        ),
                        if (loadAppSettings) that.loadAppSettings() else profile.appSettings,
                        deviceSettings,
                        lastKnownDevice,
                    )
                )
            }
        }
    }

    fun requestDelete(profile: Profile) {
        _deletingProfile.value = profile
    }

    fun cancelDelete() {
        _deletingProfile.value = null
    }

    fun confirmDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            _deletingProfile.value?.let { profileToDelete ->
                profileRepo.removeProfile(profileToDelete.profileSettings.id)
            }
            _deletingProfile.value = null // Close dialog
        }
    }

    private val json1 = Json {
        prettyPrint = true
    }

    fun exportProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exported = profile.export(tileServerRepo, colourPaletteRepo)
                clipboardHandler.copyTextToClipboard(json1.encodeToString(exported))
                // already logged in clipboard handler
                // snackbarHostState.showSnackbar("Profile copied to clipboard")
            } catch (t: Throwable) {
                Napier.e("Failed to export profile", t, tag = TAG)
                snackbarHostState.showSnackbar("Failed to export profile")
            }
        }
    }

    fun applyProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            applyProfileInner(profile)
        }
    }

    suspend fun applyProfileInner(profile: Profile) {
        val installedApps = getInstalledApps()
        val currentAppId = connection.connectIqAppIdFlow().value
        
        // Find the app that was the source of settings in the profile
        val sourceAppInProfile = profile.lastKnownDevice.installedApps.find { it.isSettingsSource }
            ?: profile.lastKnownDevice.installedApps.firstOrNull()
        
        // Find that same app on the current device
        val matchingAppOnDevice = if (sourceAppInProfile != null) {
            installedApps.find { it.appId == sourceAppInProfile.appId }
        } else {
            installedApps.find { it.appId == currentAppId } ?: installedApps.firstOrNull()
        }

        val appVersion = matchingAppOnDevice?.version

        if (appVersion != null && profile.lastKnownDevice.appVersion > appVersion) {
            snackbarHostState.showSnackbar("Newer profile detected, some settings might not be applied")
        }

        val baseMsg = "Applying settings to device"
        sendingMessage(baseMsg) { updateMsg ->
            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return@sendingMessage
            }

            connection.send(device, SaveSettings(profile.deviceSettings())) { appName ->
                updateMsg("$appName\n$baseMsg")
            }
            delay(1000) // wait for a bit so users can read message (its almost instant in sim but real device goes slow anyway)
        }
        sendingMessage("Applying app settings") { _ ->
            val tileServer = tileServerRepo.get(profile.appSettings.tileServerId)
            if (tileServer == null) {
                snackbarHostState.showSnackbar("unknown tile server")
                return@sendingMessage
            }
            tileServerRepo.onTileServerEnabledChange(profile.appSettings.tileServerEnabled)
            tileServerRepo.updateCurrentTileServer(tileServer)
            tileServerRepo.updateAuthToken(profile.appSettings.authToken)
            tileServerRepo.updateCurrentTileType(profile.appSettings.tileType)
            routeRepo.saveSettings(profile.appSettings.routeSettings)
            generalSettingsRepo.saveSettings(profile.appSettings.generalSettings)
            if (profile.appSettings.colourPaletteUniqueId != null) {
                val colourPallet =
                    colourPaletteRepo.getPaletteByUUID(profile.appSettings.colourPaletteUniqueId)
                if (colourPallet == null) {
                    snackbarHostState.showSnackbar("unknown colour palette")
                    return@sendingMessage
                }
                colourPaletteRepo.updateCurrentColourPalette(colourPallet)
            }

            profile.appSettings.connectIqAppId?.let {
                connection.updateConnectIqAppId(it)
            }
            profile.appSettings.webServerPort?.let {
                tileServerRepo.updateWebPort(it)
            }
            profile.appSettings.stravaClientId?.let {
                stravaRepo.saveClientId(it)
            }
            profile.appSettings.stravaClientSecret?.let {
                stravaRepo.saveClientSecret(it)
            }

            delay(1000) // wait for a bit so users can read message
        }
    }

    private val json2 = Json { ignoreUnknownKeys = true }

    fun onImportProfile(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exportedProfile =
                    json2.decodeFromString<ExportedProfile>(json)
                if (profileRepo.get(exportedProfile.profileSettings.id) != null) {

                    snackbarHostState.showSnackbar("Profile id already exists, skipping")
                    return@launch
                }

                if (exportedProfile.customServers.isNotEmpty()) {
                    for (server in exportedProfile.customServers) {
                        if (tileServerRepo.get(server.id) != null) {
                            continue; // we already have this server
                        }
                        tileServerRepo.saveCustomServer(server)
                    }
                }

                if (exportedProfile.customColourPalettes.isNotEmpty()) {
                    for (palette in exportedProfile.customColourPalettes) {
                        if (colourPaletteRepo.getPaletteByUUID(palette.uniqueId) != null) {
                            continue; // we already have this colour pallet
                        }
                        colourPaletteRepo.addOrUpdateCustomPalette(palette)
                    }
                }

                val profile = exportedProfile.toProfile()
                profileRepo.addProfile(profile)
                applyProfile(profile) // apply the profile when we load it from json
            } catch (t: Throwable) {
                Napier.e("Failed to load profile from json", t, tag = TAG)
                snackbarHostState.showSnackbar("Failed to load profile from json")
            }
        }
    }

    fun onCreateProfile(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileSettings = ProfileSettings(
                uuid4().toString(),
                label,
                Clock.System.now(),
                null
            )

            val appSettings = loadAppSettings()

            val settingsResult = loadDeviceSettings()
            if (settingsResult == null) {
                return@launch
            }

            val installedApps = getInstalledApps()
            if (installedApps.isEmpty()) {
                return@launch
            }

            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            val settingsSourceAppId = settingsResult.appId
            val profileApps = installedApps.map {
                ProfileAppInfo(it.version, it.appId, it.displayName, it.appId == settingsSourceAppId)
            }
            val primaryApp = profileApps.find { it.isSettingsSource } ?: profileApps.first()
            val primaryVersion = primaryApp.version

            val profile = Profile.build(
                profileSettings,
                appSettings,
                settingsResult.response.settings,
                LastKnownDevice(primaryVersion, device.friendlyName, profileApps)
            )

            profileRepo.addProfile(profile)
        }
    }

    private suspend fun <T> sendingMessage(msg: String, cb: suspend (updateMsg: suspend (String) -> Unit) -> T?): T? {
        return SendMessageHelper.sendingMessage(viewModelScope, sendingMessage, msg, cb)
    }

    private fun loadAppSettings(): AppSettings {
        return AppSettings(
            tileServerRepo.currentlyEnabled(),
            tileServerRepo.currentTileTypeFlow().value,
            tileServerRepo.currentTokenFlow().value,
            tileServerRepo.currentServerFlow().value.id,
            routeRepo.currentSettingsFlow().value,
            generalSettingsRepo.currentSettingsFlow().value,
            colourPaletteRepo.currentColourPaletteFlow.value.uniqueId,
            connection.connectIqAppIdFlow().value,
            tileServerRepo.webServerPortFlow().value,
            stravaRepo.getClientId(),
            stravaRepo.getClientSecret()
        )
    }

    private suspend fun loadDeviceSettings(): QueryResult<Settings>? {
        val device = deviceSelector.currentDevice()
        if (device == null) {
            snackbarHostState.showSnackbar("no devices selected")
            return null
        }
        settingsJob = viewModelScope.async(Dispatchers.IO) {
            loadDeviceSettingsSuspend(device)
        }

        return settingsJob!!.await()
    }

    suspend fun loadDeviceSettingsSuspend(device: IqDevice): QueryResult<Settings>? {
        settingsLoading.value = true
        val baseMsg = "Loading Settings From Device.\n" +
                "Ensure the datafield/app is running (or at least open) or this will fail. Note: this can take a long time (up to 5 minutes) on older devices, be patient. Press back to cancel"
        return sendingMessage(baseMsg) { updateMsg ->
            return@sendingMessage try {
                val result = connection.query<Settings>(
                    device,
                    RequestSettings(),
                    ProtocolResponse.PROTOCOL_SEND_SETTINGS
                ) { appName ->
                    updateMsg("$appName\n$baseMsg")
                }
                Napier.i("got settings for ${result.appId}", tag = TAG)
                settingsLoading.value = false
                result
            } catch (t: Throwable) {
                Napier.e("Failed to load settings from device", t, tag = TAG)
                settingsLoading.value = false
                snackbarHostState.showSnackbar("Failed to load settings. Please ensure an activity is running on the watch.")
                null
            }
        }
    }

    fun cancelDeviceSettingsLoading() {
        Napier.i("Cancelling settings load job.", tag = TAG)
        settingsJob?.cancel()
        // Immediately update UI state
        settingsLoading.value = false
    }

    private suspend fun getInstalledApps(): List<com.paul.infrastructure.connectiq.AppInfo> {
        val device = deviceSelector.currentDevice()
        if (device == null) {
            snackbarHostState.showSnackbar("no devices selected")
            return emptyList()
        }

        return sendingMessage("Loading App Info From Device.\nEnsure an activity with the datafield is running (or at least open) or this will fail.") { _ ->
            return@sendingMessage try {
                connection.allAppInfos(device).values.toList()
            } catch (t: Throwable) {
                Napier.e("Failed to load app info from device", t, tag = TAG)
                snackbarHostState.showSnackbar("Failed to load settings. Please ensure an activity is running on the watch.")
                emptyList()
            }
        } ?: emptyList()
    }
}
