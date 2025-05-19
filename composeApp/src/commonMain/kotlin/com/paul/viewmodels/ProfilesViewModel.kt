package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.benasher44.uuid.uuid4
import com.paul.domain.AppSettings
import com.paul.domain.ExportedProfile
import com.paul.domain.LastKnownDevice
import com.paul.domain.Profile
import com.paul.domain.ProfileSettings
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.ProfileRepo
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.IClipboardHandler
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.fromdevice.Settings
import com.paul.protocol.todevice.RequestSettings
import com.paul.protocol.todevice.SaveSettings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
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
    val navController: NavController,
    val clipboardHandler: IClipboardHandler,
) : ViewModel() {
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
                    deviceSettings = that.loadDeviceSettings()?.settings
                    val appVersion = getAppVersion()
                    if (appVersion == null) {
                        return@launch
                    }

                    val device = deviceSelector.currentDevice()
                    if (device == null) {
                        snackbarHostState.showSnackbar("no devices selected")
                        return@launch
                    }

                    lastKnownDevice = LastKnownDevice(appVersion, device.friendlyName)
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

    fun exportProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exported = profile.export(tileServerRepo)
                clipboardHandler.copyTextToClipboard(Json {
                    prettyPrint = true
                }.encodeToString(exported))
                // already logged in clipboard handler
                // snackbarHostState.showSnackbar("Profile copied to clipboard")
            } catch (t: Throwable) {
                Napier.e("Failed to export profile: $t")
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
            val appVersion = getAppVersion()
            if (appVersion != null && profile.lastKnownDevice.appVersion > appVersion) {
                snackbarHostState.showSnackbar("Newer profile detected, some settings might not be applied")
            }

        sendingMessage("Applying settings to device") {
            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return@sendingMessage
            }

            connection.send(device, SaveSettings(profile.deviceSettings()))
            delay(1000) // wait for a bit so users can read message (its almost instant in sim but real device goes slow anyway)
        }
        sendingMessage("Applying app settings") {
            val tileServer = tileServerRepo.get(profile.appSettings.tileServerId)
            if (tileServer == null) {
                snackbarHostState.showSnackbar("unknown tile server")
                return@sendingMessage
            }
            tileServerRepo.onTileServerEnabledChange(profile.appSettings.tileServerEnabled)
            tileServerRepo.updateCurrentTileServer(tileServer)
            tileServerRepo.updateAuthToken(profile.appSettings.authToken)
            tileServerRepo.updateCurrentTileType(profile.appSettings.tileType)
            delay(1000) // wait for a bit so users can read message
        }
    }

    fun onImportProfile(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exportedProfile =
                    Json { ignoreUnknownKeys = true }.decodeFromString<ExportedProfile>(json)
                if (profileRepo.get(exportedProfile.profileSettings.id) != null) {

                    snackbarHostState.showSnackbar("Profile id already exists, skipping")
                    return@launch
                }

                if (exportedProfile.customServers.isNotEmpty()) {
                    for (server in exportedProfile.customServers) {
                        if (tileServerRepo.get(server.id) != null) {
                            continue; // we already have this server
                        }
                        tileServerRepo.onAddCustomServer(server)
                    }
                }

                val profile = exportedProfile.toProfile()
                profileRepo.addProfile(profile)
                applyProfile(profile) // apply the profile when we load it from json
            } catch (t: Throwable) {
                Napier.e("Failed to load profile from json: $t")
                snackbarHostState.showSnackbar("Failed to load profile from json")
            }
        }
    }

    fun onCreateProfile(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileSettings = ProfileSettings(
                uuid4().toString(),
                label,
                Clock.System.now()
            )

            val appSettings = loadAppSettings()

            val deviceSettings = loadDeviceSettings()
            if (deviceSettings == null) {
                return@launch
            }

            val appVersion = getAppVersion()
            if (appVersion == null) {
                return@launch
            }

            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            val profile = Profile.build(
                profileSettings,
                appSettings,
                deviceSettings.settings,
                LastKnownDevice(appVersion, device.friendlyName)
            )

            profileRepo.addProfile(profile)

            // todo open navhost to the newly created profile
        }
    }

    private suspend fun <T> sendingMessage(msg: String, cb: suspend () -> T?): T? {
        return SendMessageHelper.sendingMessage(viewModelScope, sendingMessage, msg, cb)
    }

    private fun loadAppSettings(): AppSettings {
        return AppSettings(
            tileServerRepo.currentlyEnabled(),
            tileServerRepo.currentTileTypeFlow().value,
            tileServerRepo.currentTokenFlow().value,
            tileServerRepo.currentServerFlow().value.id,
        )
    }

    private suspend fun loadDeviceSettings(): Settings? {
        val device = deviceSelector.currentDevice()
        if (device == null) {
            snackbarHostState.showSnackbar("no devices selected")
            return null
        }

        return sendingMessage("Loading Settings From Device.\nEnsure an activity with the datafield is running (or at least open) or this will fail.") {
            return@sendingMessage try {
                val settings = connection.query<Settings>(
                    device,
                    RequestSettings(),
                    ProtocolResponse.PROTOCOL_SEND_SETTINGS
                )
                Napier.d("got settings $settings")
                settings
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to load settings. Please ensure an activity is running on the watch.")
                null
            }
        }
    }

    private suspend fun getAppVersion(): Int? {
        val device = deviceSelector.currentDevice()
        if (device == null) {
            snackbarHostState.showSnackbar("no devices selected")
            return null
        }

        return sendingMessage("Loading App Version From Device.\nEnsure an activity with the datafield is running (or at least open) or this will fail.") {
            return@sendingMessage try {
                val appInfo = connection.appInfo(
                    device
                )
                appInfo.version
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to load settings. Please ensure an activity is running on the watch.")
                null
            }
        }
    }
}
