package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.benasher44.uuid.uuid4
import com.paul.domain.AppSettings
import com.paul.domain.Profile
import com.paul.domain.ProfileSettings
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.ProfileRepo
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.fromdevice.Settings
import com.paul.protocol.todevice.RequestSettings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock


class ProfilesViewModel(
    private val deviceSelector: DeviceSelector,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState,
    private val tileServerRepo: TileServerRepo,
    val profileRepo: ProfileRepo,
    val navController: NavController
) : ViewModel() {
    val sendingMessage: MutableState<String> = mutableStateOf("")
    private val _editingProfile = MutableStateFlow<Profile?>(null)
    val editingProfile: StateFlow<Profile?> = _editingProfile.asStateFlow()

    // State for controlling the delete confirmation dialog
    private val _deletingProfile = MutableStateFlow<Profile?>(null)
    val deletingProfile: StateFlow<Profile?> = _deletingProfile.asStateFlow()

    fun startEditing(profile: Profile) {
        _editingProfile.value = profile
    }

    fun cancelEditing() {
        _editingProfile.value = null
    }

    fun confirmEdit(profileId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            profileRepo.removeProfile(profileId)
            _editingProfile.value = null // Close dialog
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

    fun onCreateProfile(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileSettings = ProfileSettings(
                uuid4().toString(),
                label,
                Clock.System.now()
            )

            val appSettings = AppSettings(
                tileServerRepo.currentlyEnabled(),
                tileServerRepo.currentTileTypeFlow().value,
                tileServerRepo.currentTokenFlow().value,
                tileServerRepo.currentServerFlow().value.id,
            )

            val deviceSettings = loadDeviceSettings()
            if (deviceSettings == null) {
                return@launch
            }

            val profile = Profile.build(
                profileSettings,
                appSettings,
                deviceSettings.settings
            )

            profileRepo.addProfile(profile)

            // todo open navhost to the newly created profile
        }
    }

    private suspend fun <T> sendingMessage(msg: String, cb: suspend () -> T?): T? {
        return SendMessageHelper.sendingMessage(viewModelScope, sendingMessage, msg, cb)
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
}
