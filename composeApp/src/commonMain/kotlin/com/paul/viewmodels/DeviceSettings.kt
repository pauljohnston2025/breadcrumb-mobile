package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.paul.domain.IqDevice
import com.paul.infrastructure.connectiq.IConnection
import com.paul.protocol.fromdevice.Settings
import com.paul.protocol.todevice.SaveSettings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class DeviceSettingsNavigationEvent {
    // Represents a command to navigate to a specific route
//    data class NavigateTo(val route: String) : DeviceSettingsNavigationEvent()

    // Represents a command to pop the back stack
    object PopBackStack : DeviceSettingsNavigationEvent()
}

@Stable
data class RouteItem(
    val routeId: Int,             // Read-only identifier (or generated)
    val name: String = "",
    val enabled: Boolean = true,
    val reversed: Boolean = false,
    val colour: String = "FF0000FF" // Default to opaque Blue (AARRGGBB)
) {
    companion object {
        fun fromDict(map: Map<*, *>): RouteItem {
            return RouteItem(
                routeId = (map["routeId"] as Number).toInt(),
                name = map["name"] as String,
                enabled = map["enabled"] as Boolean,
                reversed = map["reversed"] as Boolean,
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
            "reversed" to reversed,
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
        reversed = false,
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
        ListOption(0, "Zoom When Moving"),
        ListOption(1, "Zoom When Stopped"),
        ListOption(2, "Never Zoom"),
        ListOption(3, "Always Zoom"),
        ListOption(4, "Routes Without Track"),
    ),
    "uiMode" to listOf(
        ListOption(0, "Show On Top"),
        ListOption(1, "Hidden"),
        ListOption(2, "Disabled")
    ),
    "alertType" to listOf(
        ListOption(0, "Toast (notification)"),
        ListOption(1, "Alerts (requires enable in garmin settings)"),
        ListOption(2, "Image"),
    ),
    "renderMode" to listOf(
        ListOption(0, "Buffered Rotations - performance"),
        ListOption(1, "Unbuffered Rotations - low memory"),
        ListOption(2, "Buffered Without Rotations - performance"),
        ListOption(3, "No Buffer No Rotations - low memory")
    ),
    "elevationMode" to listOf(
        ListOption(0, "Stacked"),
        ListOption(1, "Ordered Routes"),
    ),
    "packingFormat" to listOf(
        ListOption(0, "Default"),
        ListOption(1, "YUV"),
        ListOption(2, "PNG"),
        ListOption(3, "JPG"),
    ),
    "mapChoice" to listOf(
        ListOption(0, "Custom"),
        ListOption(1, "Companion App"),
        ListOption(2, "Open Topo Map"),
        ListOption(3, "Esri - World Imagery"),
        ListOption(4, "Esri - World Street Map"),
        ListOption(5, "Esri - World Topo Map"),
        ListOption(6, "Esri - World Transportation"),
        ListOption(7, "Esri - World Dark Gray Base"),
        ListOption(8, "Esri - World Hillshade"),
        ListOption(9, "Esri - World Hillshade Dark"),
        ListOption(10, "Esri - World Light Gray Base"),
        ListOption(11, "Esri - USA Topo Maps"),
        ListOption(12, "Esri - World Ocean Base"),
        ListOption(13, "Esri - World Shaded Relief"),
        ListOption(14, "Esri - Nat Geo World Map"),
        ListOption(15, "Esri - World Navigation Charts"),
        ListOption(16, "Esri - World Physical Map"),
        ListOption(17, "Open Street Map - Cyclosm"),
        ListOption(18, "Stadia - Alidade Smooth (auth required)"),
        ListOption(19, "Stadia - Alidade Smooth Dark (auth required)"),
        ListOption(20, "Stadia - Outdoors (auth required)"),
        ListOption(21, "Stadia - Stamen Toner (auth required)"),
        ListOption(22, "Stadia - Stamen Toner Lite (auth required)"),
        ListOption(23, "Stadia - Stamen Terrain (auth required)"),
        ListOption(24, "Stadia - Stamen Watercolor (auth required)"),
        ListOption(25, "Stadia - OSM Bright (auth required)"),
        ListOption(26, "Carto - Voyager"),
        ListOption(27, "Carto - Dark Matter"),
        ListOption(28, "Carto - Dark Light All"),
        ListOption(29, "Mapy - Basic (auth required)"),
        ListOption(30, "Mapy - Outdoor (auth required)"),
        ListOption(31, "Mapy - Winter (auth required)"),
        ListOption(32, "Mapy - Aerial (auth required)"),
    )
)

val labelOverrides: Map<String, String> = mapOf(
    "zoomAtPaceMode" to "Mode",
    "offTrackAlertsDistanceM" to "Off Track Alert Distance (m)",
    "offTrackAlertsMaxReportIntervalS" to "Max Report Interval (s)",
    "offTrackCheckIntervalS" to "Max Check Interval (s)",
    "tileSize" to "Tile Size (pixels)",
    "drawLineToClosestPoint" to "Draw Line To Closest Point",
    "cacheTilesInStorage" to "Store Tiles For Offline Use",
    "storageMapTilesOnly" to "Only Use Stored Tiles (no ble data)",
    "storageTileCacheSize" to "Storage Tile Cache Size (tiles)",
    "httpErrorTileTTLS" to "Http Error Tile TTL (s)",
    "errorTileTTLS" to "Error Tile TTL (s)",
    "useDrawBitmap" to "Use Draw Bitmap (legacy)",
    "offTrackWrongDirection" to "Wrong Direction Alerts",
    "centerUserOffsetY" to "Center User Offset Y (ratio)",
    "mapMoveScreenSize" to "Map Move Screen Size (ratio)",
    "storageSeedBoundingBox" to "Seed Entire Bounding Box",
    "storageSeedRouteDistanceM" to "Seed Route Path Distance (m)",
    "turnAlertTimeS" to "Turn Alert Time (s)",
    "minTurnAlertDistanceM" to "Min Turn Alert Distance (m)",
    "maxTrackPoints" to "Max Track Points",
    "drawHitBoxes" to "Draw Hit Boxes",
    "showDirectionPoints" to "Show Turn Points",
    "showDirectionPointTextUnderIndex" to "Show Turn Point Text Under Index",
)

val descriptions: Map<String, String> = mapOf(
    "mapEnabled" to "Choose these values wisely. Too big = crash, too small = crash or slow performance.",
    "tileSize" to "Tile size should be a multiple of Scaled Tile Size for best results. The tile size in pixels loaded from the companion app or other source. Should be equal to Scaled Tile Size if using a template url.",
    "fullTileSize" to "Should usually be 256. The size of the tiles on the remote tile server",
    "scaledTileSize" to "The tile size to fetch images from the web (or companion app). Setting this to something like 128 will result in 4 times the performance (compared to 256), since the images we need to download are much smaller. Smaller sizes such as 128 come at the cost of resolution as each pixel of the downloaded tile will be 4 pixels on the screen (at full resolution). Smaller values are much faster, but may not look as nice (slightly blurry)",
    "tileUrl" to "Tile url should be 'http://127.0.0.1:8080' for companion app, it can also be a template.\neg. 'https://a.tile.opentopomap.org/{z}/{x}/{y}.png'.\nTile size should generally be 256 if using a template.",
    "turnAlertTimeS" to "The time away from the turn the alert will fire (based on current speed), -1 to disable.",
    "minTurnAlertDistanceM" to "The minimum distance away from the turn that a turn alert can trigger, -1 to disable.",
)

fun padColorString(original: String): String {
    val atLeast8 = original.padStart(6, '0').padStart(8, 'F')
    return atLeast8.drop(atLeast8.length - 8)
}

class DeviceSettings(
    settings: Settings,
    private val device: IqDevice,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState,
) : ViewModel() {
    val settingsSaving: MutableState<Boolean> = mutableStateOf(false)

    private val _navigationEvents = MutableSharedFlow<DeviceSettingsNavigationEvent>()
    val navigationEvents: SharedFlow<DeviceSettingsNavigationEvent> = _navigationEvents.asSharedFlow()

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
                    "turnAlertTimeS",
                    "minTurnAlertDistanceM",
                    "maxTrackPoints",
                    "showDirectionPointTextUnderIndex",
                    "mode",
                    "uiMode",
                    "alertType",
                    "renderMode",
                    "elevationMode",
                    "zoomAtPaceMode",
                    "metersAroundUser",
                    "tileSize",
                    "fullTileSize",
                    "scaledTileSize",
                    "tileLayerMax",
                    "tileLayerMin",
                    "tileCacheSize",
                    "storageTileCacheSize",
                    "tileCachePadding",
                    "recalculateIntervalS",
                    "maxPendingWebRequests",
                    "offTrackAlertsDistanceM",
                    "offTrackAlertsMaxReportIntervalS",
                    "offTrackCheckIntervalS",
                    "disableMapsFailureCount",
                    "httpErrorTileTTLS",
                    "errorTileTTLS",
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
                    "storageSeedRouteDistanceM",
                    "centerUserOffsetY",
                    "mapMoveScreenSize",
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
                    "storageSeedBoundingBox",
                    "useDrawBitmap",
                    "drawCheverons",
                    "offTrackWrongDirection",
                    "cacheTilesInStorage",
                    "storageMapTilesOnly",
                    "displayLatLong",
                    "drawHitBoxes",
                    "showDirectionPoints",
                    "scaleRestrictedToTileLayers",
                    "resetDefaults",
                    "enableOffTrackAlerts",
                    "drawLineToClosestPoint",
                    "enableRotation",
                    "displayRouteNames",
                    "showPoints",
                    "drawLineToClosestTrack",
                    "showTileBorders",
                    "showErrorTileMessages",
                    "includeDebugPageInOnScreenUi",
                    "routesEnabled" -> EditableProperty(
                        key,
                        PropertyType.BOOLEAN,
                        mutableStateOf(value as Boolean), // Assumes value is correctly Boolean
                        originalString,
                        description = description,
                        label = label
                    )

                    // --- Strings ---
                    "authToken",
                    "tileUrl" -> EditableProperty(
                        key,
                        PropertyType.STRING,
                        mutableStateOf(value as String), // Assumes value is correctly String
                        originalString,
                        description = description,
                        label = label
                    )

                    // --- Colors (Treated as String for editing, but typed as COLOR) ---
                    "tileErrorColour",
                    "trackColour",
                    "defaultRouteColour",
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
                                    Napier.d("Error parsing route item content at index $index: $e. Skipping.")
                                    null // Skip items that cause errors during content parsing
                                }
                            } else {
                                Napier.d("Route item at index $index is not a Map. Skipping.")
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
                        Napier.d("Warning: Unhandled property key '$key' - Treating as UNKNOWN/String")
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
            Napier.d("Error: Type mismatch for key '$key'. Expected type based on mapping but got ${value::class.simpleName}. Skipping. Error: ${e.message}")
            null // Skip properties that cause a casting error
        } catch (e: Exception) {
            Napier.d("Error creating EditableProperty for key '$key': ${e.message}")
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
                Napier.d("got settings $settings")
                settingsSaving.value = false
                viewModelScope.launch {
                    _navigationEvents.emit(DeviceSettingsNavigationEvent.PopBackStack)
                }
            } catch (t: Throwable) {
                settingsSaving.value = false
                Napier.d("Failed to save settings $t")
                snackbarHostState.showSnackbar("Failed to save settings")
                viewModelScope.launch {
                    _navigationEvents.emit(DeviceSettingsNavigationEvent.PopBackStack)
                }
            }
        }
    }
}
