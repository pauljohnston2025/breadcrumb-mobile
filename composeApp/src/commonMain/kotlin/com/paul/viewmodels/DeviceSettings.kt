package com.paul.viewmodels

import android.util.Log
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.paul.composables.colorToHexString
import com.paul.composables.parseColor
import com.paul.domain.IqDevice
import com.paul.infrastructure.connectiq.IConnection
import com.paul.protocol.fromdevice.Settings
import com.paul.protocol.todevice.SaveSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Stable
data class RouteItem(
    val routeId: Int,             // Read-only identifier (or generated)
    val name: String = "",
    val enabled: Boolean = true,
    val colour: String = "FF0000FF" // Default to opaque Blue (AARRGGBB)
) {
    companion object {
        fun fromDict(map: Map<*, *>): RouteItem {
            return RouteItem(
                routeId = (map["routeId"] as Number).toInt(),
                name = map["name"] as String,
                enabled = map["enabled"] as Boolean,
                colour = padColorString(map["colour"] as String)
            )
        }
    }

    fun toDict(): Map<String, Any> {
        return mapOf<String, Any>( // Explicit Map type for clarity
            // Use the keys that your storage/Garmin settings expect
            "routeId" to routeId,
            "name" to name,
            "enabled" to enabled,
            "colour" to colour // Ensure this is the AARRGGBB string
        )
    }
}

// Helper function (adjust ID logic as needed)
fun createNewRouteItem(idSuggestion: Int = -1): RouteItem {
    // If IDs need to be unique and managed, implement proper generation.
    // Using suggestion (like list size) or -1 is a placeholder.
    return RouteItem(
        routeId = idSuggestion,
        name = "New Route",
        enabled = true,
        colour = "FF0000FF"
    )
}

// Enum to represent property types
enum class PropertyType {
    NUMBER, // Typically Int
    FLOAT,  // Typically Float or Double
    ZERO_DISABLED_FLOAT,  // Typically Float or Double
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
    val options: List<ListOption>? = null, // <-- Add nullable options list
    val description: String? = null, // <-- Add nullable description field
    val label: String,
) {
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

val labelOverrides: Map<String, String> = mapOf(
    "zoomAtPaceSpeedMPS" to "Zoom At Pace (mps)",
    "offTrackAlertsDistanceM" to "Off Track Alert Distance (m)",
    "offTrackAlertsMaxReportIntervalS" to "Max Report Interval (s)",
    "tileSize" to "Tile Size (pixels)",
)

val descriptions: Map<String, String> = mapOf(
    "mapEnabled" to "Choose these values wisely. Too big = crash, too small = crash or slow performance",
    "tileSize" to "Should be a multiple of 256 for best results",
    "tileUrl" to "Tile url should be 'http://127.0.0.1:8080' for companion app or template eg. 'https://a.tile.opentopomap.org/{z}/{x}/{y}.png'. Tile size should generally be 256 if using a template.",
)

fun padColorString(original: String): String {
    val atLeast8 = original.padStart(6, '0').padStart(8, 'F')
    return atLeast8.drop(atLeast8.length - 8)
}

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
        val description = descriptions[key] // Will be null if not found
        val label = labelOverrides[key] ?: key.replace(Regex("([A-Z])"), " $1")
            .replaceFirstChar { it.uppercase() }

        try {
            // --- Check for LIST_NUMBER properties FIRST ---
            if (listOptionsMapping.containsKey(key)) {
                val options = listOptionsMapping[key]!! // Safe due to containsKey check
                EditableProperty(
                    key,
                    PropertyType.LIST_NUMBER,
                    mutableStateOf(value as Int), // State holds the Int value
                    originalString,
                    options = options, // Attach the options,
                    description = description,
                    label = label
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
                        originalString,
                        description = description,
                        label = label
                    )

                    // --- Floats ---
                    "zoomAtPaceSpeedMPS" -> EditableProperty(
                        key,
                        PropertyType.FLOAT,
                        mutableStateOf(value as Float), // Assumes value is correctly Float
                        originalString,
                        description = description,
                        label = label
                    )

                    // --- Floats ---
                    "scale",
                    "fixedLatitude",
                    "fixedLongitude" -> EditableProperty(
                        key,
                        PropertyType.ZERO_DISABLED_FLOAT,
                        mutableStateOf(value as Float), // Assumes value is correctly Float
                        originalString,
                        description = description,
                        label = label
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
                        originalString,
                        description = description,
                        label = label
                    )

                    // --- Strings ---
                    "tileUrl" -> EditableProperty(
                        key,
                        PropertyType.STRING,
                        mutableStateOf(value as String), // Assumes value is correctly String
                        originalString,
                        description = description,
                        label = label
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
                        mutableStateOf(padColorString((value as String))),
                        padColorString(originalString),
                        description = description,
                        label = label
                    )

                    // --- Array: Routes ---
                    "routes" -> {
                        // Attempt to parse the incoming value into a list of RouteItems
                        val initialListValue = value as? List<*> ?: emptyList<Any>()
                        val routeItemList = initialListValue.mapIndexedNotNull { index, itemData ->
                            val map = itemData as? Map<*, *>
                            if (map != null) {
                                try {
                                    RouteItem.fromDict(map)
                                } catch (e: Exception) {
                                    println("Error parsing route item content at index $index: $e. Skipping.")
                                    null // Skip items that cause errors during content parsing
                                }
                            } else {
                                println("Route item at index $index is not a Map. Skipping.")
                                null // Skip items that aren't maps
                            }
                        }.toMutableList() // Crucial: Convert to MutableList for state modification

                        EditableProperty(
                            key,
                            PropertyType.ARRAY, // Specific type or keep ARRAY
                            mutableStateOf(routeItemList), // State holds the mutable list
                            originalString,
                            label = label,
                            description = description,
                        )
                    }

                    // --- Default/Unknown ---
                    // Add cases here if new properties might appear unexpectedly
                    else -> {
                        println("Warning: Unhandled property key '$key' - Treating as UNKNOWN/String")
                        // Fallback: Treat as String or UNKNOWN
                        EditableProperty(
                            key,
                            PropertyType.UNKNOWN, // Or PropertyType.STRING
                            mutableStateOf(value), // Store raw value
                            originalString,
                            description = description,
                            label = label
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
            } catch (t: Throwable) {
                settingsSaving.value = false
                snackbarHostState.showSnackbar("Failed to save settings")
                viewModelScope.launch(Dispatchers.Main) {
                    navController.popBackStack()
                }
            }
        }
    }
}
