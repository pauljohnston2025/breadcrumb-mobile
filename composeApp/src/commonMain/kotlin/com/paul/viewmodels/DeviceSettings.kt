package com.paul.viewmodels

import android.util.Log
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.paul.domain.IqDevice
import com.paul.infrastructure.connectiq.IConnection
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.fromdevice.Settings
import com.paul.protocol.todevice.RequestSettings
import com.paul.protocol.todevice.SaveSettings
import com.paul.ui.Screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Enum to represent property types
enum class PropertyType {
    NUMBER, // Typically Int
    FLOAT,  // Typically Float or Double
    NULLABLE_FLOAT,  // Typically Float or Double
    LIST_NUMBER,  // Typically Float or Double
    BOOLEAN,
    STRING,
    ARRAY,  // Special handling needed
    COLOR,  // Treat as String for now, potential for color picker later
    UNKNOWN
}

data class ListOption(
    val value: Int, // The actual value stored (matches the property's Int state)
    val display: String // The user-friendly text shown in the dropdown
)

// Data class to hold the editable state in the UI
data class EditableProperty<T>(
    val id: String,
    val type: PropertyType,
    val state: MutableState<T>,
    val stringVal: String,
    val options: List<ListOption>? = null // <-- Add nullable options list
) {
    // Helper to get label from id (simple conversion)
    val label: String
        get() = id.replace(Regex("([A-Z])"), " $1").replaceFirstChar { it.uppercase() }

    fun toDef(): PropertyDefinition {
        return PropertyDefinition(id, type, stringVal)
    }
}

data class PropertyDefinition(
    val id: String,
    val type: PropertyType,
    val initialValue: String // Keep initial value as string for easy parsing
)

// Place this somewhere accessible, like a constants file or companion object
val listOptionsMapping: Map<String, List<ListOption>> = mapOf(
    "mode" to listOf(
        ListOption(0, "Track/Route Mode"), // Replace with actual strings later
        ListOption(1, "Elevation Mode"),
        ListOption(2, "Map Move Mode"),
        ListOption(3, "Debug Mode")
    ),
    "zoomAtPaceMode" to listOf(
        ListOption(0, "Zoom based on Pace"),
        ListOption(1, "Zoom when Stopped")
    ),
    "uiMode" to listOf(
        ListOption(0, "Show All UI"),
        ListOption(1, "Hide UI"),
        ListOption(2, "Settings Only UI"),
        ListOption(3, "No UI")
    )
)

class DeviceSettings(
    settings: Settings,
    private val device: IqDevice,
    private val navController: NavHostController,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState,
) : ViewModel() {
    val settingsSaving: MutableState<Boolean> = mutableStateOf(false)

    val propertyDefinitions = settings.settings.mapNotNull { entry ->

        val key = entry.key
        val value = entry.value
        val originalString = value.toString() // Simple string representation for the 4th param

        try {
            // --- Check for LIST_NUMBER properties FIRST ---
            if (listOptionsMapping.containsKey(key)) {
                val options = listOptionsMapping[key]!! // Safe due to containsKey check
                EditableProperty(
                    key,
                    PropertyType.LIST_NUMBER,
                    mutableStateOf(value as Int), // State holds the Int value
                    originalString,
                    options = options // Attach the options
                )
            } else {
                when (key) {
                    // --- Numbers ---
                    "mode",
                    "uiMode",
                    "zoomAtPaceMode",
                    "metersAroundUser",
                    "tileSize",
                    "tileLayerMax",
                    "tileLayerMin",
                    "tileCacheSize",
                    "maxPendingWebRequests",
                    "offTrackAlertsDistanceM",
                    "offTrackAlertsMaxReportIntervalS",
                    "disableMapsFailureCount",
                    "routeMax" -> EditableProperty(
                        key,
                        PropertyType.NUMBER,
                        mutableStateOf(value as Int), // Assumes value is correctly Int
                        originalString
                    )

                    // --- Floats ---
                    "scale",
                    "zoomAtPaceSpeedMPS",
                    "fixedLatitude",
                    "fixedLongitude" -> EditableProperty(
                        key,
                        PropertyType.FLOAT,
                        mutableStateOf(value as Float), // Assumes value is correctly Float
                        originalString
                    )

                    // --- Booleans ---
                    "mapEnabled",
                    "resetDefaults",
                    "enableOffTrackAlerts",
                    "enableRotation",
                    "displayRouteNames",
                    "routesEnabled" -> EditableProperty(
                        key,
                        PropertyType.BOOLEAN,
                        mutableStateOf(value as Boolean), // Assumes value is correctly Boolean
                        originalString
                    )

                    // --- Strings ---
                    "tileUrl" -> EditableProperty(
                        key,
                        PropertyType.STRING,
                        mutableStateOf(value as String), // Assumes value is correctly String
                        originalString
                    )

                    // --- Colors (Treated as String for editing, but typed as COLOR) ---
                    "trackColour",
                    "elevationColour",
                    "userColour",
                    "normalModeColour",
                    "uiColour",
                    "debugColour" -> EditableProperty(
                        key,
                        PropertyType.COLOR, // Use the specific COLOR type
                        mutableStateOf(value as String), // Assumes value is correctly String (Hex)
                        originalString
                    )

                    // --- Arrays ---
                    "routes" -> EditableProperty(
                        key,
                        PropertyType.ARRAY,
                        // State can hold the raw list or Any. Adjust as needed.
                        // If it's always List<String> or similar, cast accordingly.
                        mutableStateOf(value), // Or mutableStateOf(value as List<*>),
                        originalString
                    )

                    // --- Default/Unknown ---
                    // Add cases here if new properties might appear unexpectedly
                    else -> {
                        println("Warning: Unhandled property key '$key' - Treating as UNKNOWN/String")
                        // Fallback: Treat as String or UNKNOWN
                        EditableProperty(
                            key,
                            PropertyType.UNKNOWN, // Or PropertyType.STRING
                            mutableStateOf(value), // Store raw value
                            originalString
                        )
                        // Or return null if you want to skip unknown properties:
                        // null
                    }
                }
            }
        } catch (e: ClassCastException) {
            println("Error: Type mismatch for key '$key'. Expected type based on mapping but got ${value::class.simpleName}. Skipping. Error: ${e.message}")
            null // Skip properties that cause a casting error
        } catch (e: Exception) {
            println("Error creating EditableProperty for key '$key': ${e.message}")
            null // Skip on other errors
        }
    }

    fun onSave(updatedValues: Map<String, Any>) {
        settingsSaving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = connection.send(
                    device,
                    SaveSettings(updatedValues)
                )
                Log.d("stdout", "got settings $settings")
                settingsSaving.value = false
                viewModelScope.launch(Dispatchers.Main) {
                    navController.popBackStack()
                }
            }
            catch (t: Throwable)
            {
                settingsSaving.value = false
                snackbarHostState.showSnackbar("Failed to save settings")
                viewModelScope.launch(Dispatchers.Main) {
                    navController.popBackStack()
                }
            }
        }
    }
}
