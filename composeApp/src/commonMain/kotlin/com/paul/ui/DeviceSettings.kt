package com.paul.ui

import com.paul.ui.OrderedListPopupEditor
import com.paul.ui.OrderedListSummary
import com.paul.ui.PropertyEditorRow
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.paul.composables.ColorPickerDialog
import com.paul.composables.LoadingOverlay
import com.paul.composables.RoutesArrayEditor
import com.paul.composables.colorToHexString
import com.paul.composables.parseColor
import com.paul.viewmodels.DeviceSettings
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.ListOption
import com.paul.viewmodels.PropertyType
import com.paul.viewmodels.RouteItem
import com.paul.viewmodels.modes
import io.github.aakira.napier.Napier
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun DeviceSettings(
    deviceSettings: DeviceSettings,
) {
    BackHandler(enabled = deviceSettings.settingsSaving.value) {
        // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
    }

    val editableProperties = deviceSettings.propertyDefinitions
    // --- State Management ---
    val mapEnabledProp = remember(editableProperties) {
        @Suppress("UNCHECKED_CAST")
        editableProperties.find { it.id == "mapEnabled" } as? EditableProperty<Boolean>
    }
    val showMapSection by mapEnabledProp?.state ?: remember { mutableStateOf(false) }

    val findProp = remember<(String) -> EditableProperty<*>?>(editableProperties) {
        { id -> editableProperties.find { it.id == id } }
    }

    // --- PROPERTY DEFINITIONS LISTS (For section visibility) ---
    val generalProps = remember(editableProperties) {
        listOf(
            "activityType",
            "modeDisplayOrder",
            "mode",
            "uiMode",
            "elevationMode",
            "scale",
            "recalculateIntervalS",
            "renderMode",
            "centerUserOffsetY",
            "displayLatLong",
            "useStartForStop",
            "mapMoveScreenSize"
        ).mapNotNull { findProp(it) }
    }
    val trackProps = remember(editableProperties) {
        listOf(
            "maxTrackPoints",
            "trackStyle",
            "trackWidth",
            "minTrackPointDistanceM",
            "trackPointReductionMethod",
            "useTrackAsHeadingSpeedMPS",
        ).mapNotNull { findProp(it) }
    }
    val dataFieldProps = remember(editableProperties) {
        listOf(
            "topDataType",
            "bottomDataType",
            "dataFieldTextSize",
        ).mapNotNull { findProp(it) }
    }
    val zoomProps = remember(editableProperties) {
        listOf(
            "zoomAtPaceMode",
            "metersAroundUser",
            "zoomAtPaceSpeedMPS"
        ).mapNotNull { findProp(it) }
    }
    val mapSettingProps = remember(editableProperties) {
        listOf(
            "tileCacheSize",
            "tileCachePadding",
            "maxPendingWebRequests",
            "disableMapsFailure",
            "httpErrorTileTTLS",
            "errorTileTTLS",
            "fixedLatitude",
            "fixedLongitude",
            "scaleRestrictedToTileLayers",
            "packingFormat",
            "useDrawBitmap"
        ).mapNotNull { findProp(it) }
    }
    val tileServerProps = remember(editableProperties) {
        listOf(
            "mapChoice",
            "tileUrl",
            "authToken",
            "tileSize",
            "scaledTileSize",
            "tileLayerMax",
            "tileLayerMin",
            "fullTileSize"
        ).mapNotNull { findProp(it) }
    }
    val offlineStorageProps = remember(editableProperties) {
        listOf(
            "cacheTilesInStorage",
            "storageMapTilesOnly",
            "storageTileCacheSize",
            "storageTileCachePageCount",
            "storageSeedBoundingBox",
            "storageSeedRouteDistanceM"
        ).mapNotNull { findProp(it) }
    }
    val alertProps = remember(editableProperties) {
        listOf(
            "enableOffTrackAlerts",
            "offTrackAlertsDistanceM",
            "offTrackCheckIntervalS",
            "offTrackWrongDirection",
            "offTrackAlertsMaxReportIntervalS",
            "drawLineToClosestPoint",
            "drawCheverons",
            "alertType",
            "turnAlertTimeS",
            "minTurnAlertDistanceM"
        ).mapNotNull { findProp(it) }
    }
    val colorProps = remember(editableProperties) {
        listOf(
            "trackColour",
            "trackColour2",
            "defaultRouteColour",
            "elevationColour",
            "userColour",
            "normalModeColour",
            "uiColour",
            "debugColour"
        ).mapNotNull { findProp(it) }
    }
    val routeConfigProps = remember(editableProperties) {
        listOf(
            "routesEnabled",
            "displayRouteNames",
            "routeMax",
            "routes"
        ).mapNotNull { findProp(it) }
    }
    val debugProps = remember(editableProperties) {
        listOf(
            "showPoints",
            "drawLineToClosestTrack",
            "showTileBorders",
            "showErrorTileMessages",
            "tileErrorColour",
            "includeDebugPageInOnScreenUi",
            "drawHitBoxes",
            "showDirectionPoints",
            "showDirectionPointTextUnderIndex"
        ).mapNotNull { findProp(it) }
    }
    // --------------------------------------------------------------------------

    // Box enables stacking the loading overlay on top
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    CollapsibleSectionWithProperties("General", generalProps)
                }

                item {
                    CollapsibleSectionWithProperties("Track", trackProps)
                }

                item {
                    CollapsibleSectionWithProperties("Data Fields", dataFieldProps)
                }

                item {
                    CollapsibleSectionWithProperties("Zoom At Pace", zoomProps)
                }

                // --- Map Settings Toggle ---
                mapEnabledProp?.let {
                    item(key = it.id) {
                        PropertyEditorResolver(it)
                    }
                }

                // --- Map Sections (Conditional based on mapEnabled AND property presence) ---
                item(key = "map_settings_section") {
                    // Check property list presence before AnimatedVisibility to save on rendering cost
                    if (mapSettingProps.isNotEmpty()) {
                        AnimatedVisibility(
                            visible = showMapSection,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            CollapsibleSectionWithProperties("Map Settings", mapSettingProps)
                        }
                    }
                }
                item(key = "tile_Server_section") {
                    if (tileServerProps.isNotEmpty()) {
                        AnimatedVisibility(
                            visible = showMapSection,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            CollapsibleSectionWithProperties(
                                "Tile Server Settings",
                                tileServerProps
                            )
                        }
                    }
                }

                item(key = "offline_tile_storage_section") {
                    if (offlineStorageProps.isNotEmpty()) {
                        AnimatedVisibility(
                            visible = showMapSection,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            CollapsibleSectionWithProperties(
                                "Offline Tile Storage",
                                offlineStorageProps
                            )
                        }
                    }
                }
                // --- End Map Sections ---

                item {
                    CollapsibleSectionWithProperties("Alerts", alertProps)
                }


                item {
                    CollapsibleSectionWithProperties("Colours", colorProps)
                }

                item {
                    // The routes property needs to be passed to the specific RoutesArrayEditor
                    CollapsibleSectionWithProperties("Route Configuration", routeConfigProps)
                }

                item {
                    CollapsibleSectionWithProperties("Debug", debugProps)
                }

                // --- Setting: resetDefaults ---
                val resetProp = findProp("resetDefaults")
                if (resetProp != null) {
                    item(key = resetProp.id) { PropertyEditorResolver(resetProp) }
                }
            } // End LazyColumn

            // --- Action Buttons (remains the same) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = {
                    val updatedValues = editableProperties.associate { prop ->
                        val key = prop.id
                        val currentValue = prop.state.value // Get the value from the MutableState

                        // *** SPECIAL HANDLING FOR 'routes' ARRAY ***
                        if (key == "routes" && currentValue is List<*>) { // Check key and type for safety
                            try {
                                // Attempt to cast to the specific list type we expect
                                @Suppress("UNCHECKED_CAST")
                                val routeList = currentValue as List<RouteItem>

                                // Transform the List<RouteItem> back into a List of Maps
                                val serializableRouteList = routeList.map { routeItem ->
                                    routeItem.toDict()
                                }
                                // Pair the "routes" key with the transformed List<Map<String, Any>>
                                key to serializableRouteList
                            } catch (e: Exception) {
                                // Handle potential casting errors or other issues during transformation
                                Napier.d("Error transforming routes list for saving for key '$key': ${e.message}")
                                key to emptyList<Map<String, Any>>()
                            }
                        } else if (key == "modeDisplayOrder" && currentValue is List<*>) {
                            // Filter to ensure we only have ListOptions, then join values with commas
                            val csvList = (currentValue as List<*>).filterIsInstance<ListOption>()
                                .joinToString(",") { it.value.toString() }
                            key to csvList
                        } else {
                            // For all other properties, use the value directly
                            key to currentValue
                        }
                    }
                    deviceSettings.onSave(updatedValues)
                }) {
                    Text("Save")
                }
            } // End Button Row
        } // End Main Column

        LoadingOverlay(
            isLoading = deviceSettings.settingsSaving.value,
            loadingText = "Settings Saving",
        )
    }
}

// --- Helper Composables ---

/**
 * A reusable composable that displays a clickable header to expand or collapse its content.
 *
 * @param title The text to display in the section header.
 * @param initiallyExpanded Whether the section should be expanded by default.
 * @param content The composable content to be displayed inside the collapsible area.
 * * NOTE: This version is kept in case you use it for sections not defined by a simple list of properties.
 */
@Composable
fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val angle: Float by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "Arrow Angle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(), // Smoothly animates the size change when collapsing/expanding
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.weight(1f), // Title takes up available space
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(angle), // Rotate the icon based on state
            )
        }

        Divider(modifier = Modifier.padding(horizontal = 16.dp))

        // Collapsible Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) { // Add padding for content
                content()
            }
        }
    }
}

/**
 * A reusable composable that displays a clickable header to expand or collapse its content,
 * but only if the provided list of properties is not empty.
 */
@Composable
fun CollapsibleSectionWithProperties(
    title: String,
    properties: List<EditableProperty<*>>,
    initiallyExpanded: Boolean = false,
    content: @Composable (EditableProperty<*>) -> Unit = { PropertyEditorResolver(it) }
) {
    // Core Logic: If no properties exist, don't render anything.
    if (properties.isEmpty()) {
        return
    }

    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val angle: Float by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "Arrow Angle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(angle),
            )
        }

        Divider(modifier = Modifier.padding(horizontal = 16.dp))

        // Collapsible Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                // Render all properties using the provided content lambda (default is Resolver)
                properties.forEach { prop ->
                    content(prop)
                }
            }
        }
    }
}


/**
 * A simple, non-collapsible header for use inside other sections.
 */
@Composable
fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth()) { // Use Column to contain Divider
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1, // Or h6
            color = MaterialTheme.colors.primary, // Optional styling
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp), // Adjust padding
        )
        Divider(modifier = Modifier.padding(horizontal = 16.dp)) // Indent divider slightly
    }
}


// Extracted editor resolver to avoid repetition
@Suppress("UNCHECKED_CAST")
@Composable
fun PropertyEditorResolver(property: EditableProperty<*>) {
    // --- Specific check for 'routes' array ---
    if (property.id == "routes" && property.type == PropertyType.ARRAY) {
        // Attempt safe cast based on how createEditableProperties sets it up
        val routeListState = property.state as? MutableState<MutableList<RouteItem>> // Safer cast
        if (routeListState != null) {
            // Create a derived property with the correctly typed state for the editor
            val typedProperty = remember(property.id) { // Remember to avoid recreating constantly
                EditableProperty(
                    id = property.id,
                    type = property.type,
                    state = routeListState, // Use the successfully cast state
                    stringVal = property.stringVal,
                    options = property.options,
                    label = property.label,
                    description = property.description,
                )
            }
            RoutesArrayEditor(property = typedProperty)
            return // Handled this property, exit the resolver
        } else {
            // Fallback if casting fails - indicates issue in createEditableProperties
            Napier.d("Error: Could not cast state for property '${property.id}' to MutableList<RouteItem>. Displaying placeholder.")
            UnknownTypeEditor(property) // Show a generic editor
            return
        }
    }

    // Your existing when(property.type) { ... } logic here
    when (property.type) {
        PropertyType.STRING -> StringEditor(property as EditableProperty<String>)
        PropertyType.COLOR -> ColorEditor(property as EditableProperty<String>)
        PropertyType.COLOR_TRANSPARENT -> TransparentColorEditor(property as EditableProperty<String>)
        PropertyType.NUMBER -> NumberEditor(property as EditableProperty<Int>)
        PropertyType.FLOAT -> FloatEditor(property as EditableProperty<Float>)
        PropertyType.ZERO_DISABLED_FLOAT -> ZeroDisabledFloatEditor(property as EditableProperty<Float>)
        PropertyType.BOOLEAN -> BooleanEditor(property as EditableProperty<Boolean>)
        PropertyType.ARRAY -> ArrayEditor(property) // Array editor might need specific UI
        PropertyType.LIST_NUMBER -> ListNumberEditor(property as EditableProperty<Int>)
        PropertyType.UNKNOWN -> UnknownTypeEditor(property) // Handle unknown gracefully
        PropertyType.SPORT -> SportAndSubSportPicker(property as EditableProperty<Int>)
        PropertyType.CSV_ORDERED_LIST -> {
            var showPopup by remember { mutableStateOf(false) }
            val typedProperty = property as EditableProperty<MutableList<ListOption>>

            // 1. Show the placeholder summary
            OrderedListSummary(
                property = typedProperty,
                onEditClick = { showPopup = true }
            )

            // 2. Show the modal when requested
            if (showPopup) {
                OrderedListPopupEditor(
                    property = typedProperty,
                    allAvailableOptions = modes,
                    onDismiss = { showPopup = false }
                )
            }
        }
    }
}


// --- Specific Editors ---
@Composable
fun StringEditor(property: EditableProperty<String>) {
    // can't seem to get this to scroll on large text tried BringIntoViewRequester but could not get it working
    // it does scroll when typing, or when using the spacebar as a trackpad
    // but not when the onscreen cursor is used
    var currentValue by property.state
    PropertyEditorRow(label = property.label, description = property.description) {
        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue -> currentValue = newValue },
            modifier = Modifier.widthIn(min = 150.dp), // Adjust width as needed
            singleLine = true,
        )
    }
}

@Composable
fun ColorEditor(property: EditableProperty<String>) {
    // State to control the visibility of the color picker dialog
    var showDialog by remember { mutableStateOf(false) }

    // Get the current hex string state
    val currentHex by property.state // Observe the state directly

    // Parse the current hex string into a Compose Color for the preview/initial dialog state
    // Use remember with currentHex as key to recalculate only when hex changes
    val currentColor = remember(currentHex) { parseColor(currentHex) }

    PropertyEditorRow(
        label = property.label,
        description = property.description,
    ) { // Assuming you still use this layout helper
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Clickable Color Preview Box
            Box(
                modifier = Modifier
                    .size(36.dp) // Adjust size as needed
                    .clip(MaterialTheme.shapes.small)
                    .background(currentColor)
                    .border(
                        width = 1.dp,
                        // Use LocalContentColor for border to adapt to theme light/dark
                        color = LocalContentColor.current.copy(alpha = ContentAlpha.disabled),
                        shape = MaterialTheme.shapes.small,
                    )
                    .clickable { showDialog = true }, // Open the dialog on click
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Display the Hex value (read-only)
            Text(
                text = "#${currentHex.uppercase()}", // Display with # prefix
                style = MaterialTheme.typography.body2, // Or caption
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .width(90.dp)
                    .clickable { showDialog = true }, // Open the dialog on click
            )
        }
    }

    // Conditionally display the ColorPickerDialog
    // Pass currentColor derived from the state. It acts as the initial value
    // when the dialog opens.
    ColorPickerDialog(
        initialColor = currentColor,
        showDialog = showDialog,
        onDismissRequest = { showDialog = false }, // Lambda to close the dialog
        onColorSelected = { selectedColor ->
            // Update the property's state with the new hex string
            // This will trigger recomposition, ColorEditor will recalculate currentColor,
            // and the preview box will update.
            property.state.value = colorToHexString(selectedColor)
            // Dialog dismissal is handled by its own buttons triggering onDismissRequest
        },
    )
}

@Composable
fun TransparentColorEditor(property: EditableProperty<String>) {
    // State to control the visibility of the color picker dialog
    var showDialog by remember { mutableStateOf(false) }

    // Get the current hex string state
    val currentHex by property.state // Observe the state directly

    // Parse the current hex string into a Compose Color for the preview/initial dialog state
    // Use remember with currentHex as key to recalculate only when hex changes
    val currentColor = remember(currentHex) { parseColor(currentHex) }

    PropertyEditorRow(
        label = property.label,
        description = property.description,
    ) { // Assuming you still use this layout helper
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Clickable Color Preview Box
            Box(
                modifier = Modifier
                    .size(36.dp) // Adjust size as needed
                    .clip(MaterialTheme.shapes.small)
                    .background(currentColor)
                    .border(
                        width = 1.dp,
                        // Use LocalContentColor for border to adapt to theme light/dark
                        color = LocalContentColor.current.copy(alpha = ContentAlpha.disabled),
                        shape = MaterialTheme.shapes.small,
                    )
                    .clickable { showDialog = true }, // Open the dialog on click
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Display the Hex value (read-only)
            Text(
                text = "#${currentHex.uppercase()}", // Display with # prefix
                style = MaterialTheme.typography.body2, // Or caption
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .width(90.dp)
                    .clickable { showDialog = true }, // Open the dialog on click
            )
        }
    }

    // Conditionally display the ColorPickerDialog
    // Pass currentColor derived from the state. It acts as the initial value
    // when the dialog opens.
    ColorPickerDialog(
        initialColor = currentColor,
        showDialog = showDialog,
        onDismissRequest = { showDialog = false }, // Lambda to close the dialog
        onColorSelected = { selectedColor ->
            // Update the property's state with the new hex string
            // This will trigger recomposition, ColorEditor will recalculate currentColor,
            // and the preview box will update.
            property.state.value = colorToHexString(selectedColor)
            // Dialog dismissal is handled by its own buttons triggering onDismissRequest
        },
        allowTransparent = true
    )
}

@Composable
fun BooleanEditor(property: EditableProperty<Boolean>) {
    var currentValue by property.state
    PropertyEditorRow(label = property.label, description = property.description) {
        // Use Switch or Checkbox based on preference
        Switch(
            checked = currentValue,
            onCheckedChange = { newValue -> currentValue = newValue },
        )
        /* Alternative: Checkbox
        Checkbox(
            checked = currentValue,
            onCheckedChange = { newValue -> currentValue = newValue }
        )
        */
    }
}

@Composable
fun NumberEditor(property: EditableProperty<Int>) {
    var textValue by remember(property.state.value) { mutableStateOf(property.state.value.toString()) }
    val stateValue by property.state

    LaunchedEffect(stateValue) {
        // Update text field if state changes programmatically (e.g., reset)
        if (textValue.toIntOrNull() != stateValue) {
            textValue = stateValue.toString()
        }
    }

    PropertyEditorRow(label = property.label, description = property.description) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                // Allow empty or valid integer input
                if (newValue.isEmpty() || newValue == "-" || newValue.toIntOrNull() != null) {
                    textValue = newValue
                    // Update the actual state only if valid
                    newValue.toIntOrNull()?.let {
                        property.state.value = it
                    }
                }
            },
            modifier = Modifier.width(100.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = textValue.isNotEmpty() && textValue != "-" && textValue.toIntOrNull() == null, // Basic validation indication
        )
    }
}

@Composable
fun FloatEditor(property: EditableProperty<Float>) {
    var textValue by remember(property.state.value) { mutableStateOf(property.state.value.toString()) }
    val stateValue by property.state

    LaunchedEffect(stateValue) {
        // Update text field if state changes programmatically (e.g., reset)
        if (textValue.toFloatOrNull() != stateValue) {
            textValue = stateValue.toString()
        }
    }

    PropertyEditorRow(label = property.label, description = property.description) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                // Basic check for valid float input characters
                if (newValue.isEmpty() || newValue == "-" || newValue == "." || newValue == "-." || newValue.toFloatOrNull() != null) {
                    textValue = newValue
                    // Update the actual state only if valid
                    newValue.toFloatOrNull()?.let {
                        property.state.value = it
                    }
                }
            },
            modifier = Modifier.width(100.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = textValue.isNotEmpty() && textValue != "-" && textValue != "." && textValue != "-." && textValue.toFloatOrNull() == null, // Basic validation indication
        )
    }
}

/**
 * Float editor where 0.0f represents the "unset" or "null" state.
 * Displays an empty field for 0.0f, saves 0.0f when the field is cleared or set to 0.
 * Expects EditableProperty<Float>.
 */
@Composable
fun ZeroDisabledFloatEditor(property: EditableProperty<Float>) { // Takes EditableProperty<Float>
    // State for the text field's content
    var textValue by remember {
        // Initialize textValue: empty if state is 0.0f, otherwise the string representation
        val initialFloat = property.state.value
        mutableStateOf(if (initialFloat == 0.0f) "" else initialFloat.toString())
    }
    // Observe the actual state value
    val stateValue by property.state

    // Effect to synchronize TextField with external state changes (e.g., reset)
    LaunchedEffect(stateValue) {
        // Determine the text representation based on the state value (0.0f -> "")
        val expectedText = if (stateValue == 0.0f) "" else stateValue.toString()
        if (textValue != expectedText) {
            // Update the text field if the underlying state changes externally
            textValue = expectedText
        }
    }

    PropertyEditorRow(
        label = property.label,
        description = property.description,
    ) { // Assuming you still use this layout helper
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                // Always update the text field first to show immediate user input
                textValue = newValue

                // Now, determine how to update the Float state based on the text
                if (newValue.isEmpty()) {
                    // If the field is explicitly cleared, set state to 0.0f (our "null")
                    property.state.value = 0.0f
                } else {
                    // Try parsing the text as a Float
                    val parsedFloat = newValue.toFloatOrNull()
                    if (parsedFloat != null) {
                        // If parsing is successful:
                        if (parsedFloat == 0.0f) {
                            // User entered "0", "0.0", etc. Treat as clearing/unsetting.
                            property.state.value = 0.0f
                            // Also clear the text field visually for consistency
                            // Use LaunchedEffect or post to handler if immediate update causes issues
                            textValue = ""
                        } else {
                            // User entered a valid non-zero float
                            property.state.value = parsedFloat
                        }
                    }
                    // If parsing fails (e.g., text is "-", ".", "abc"):
                    // We *don't* update the Float state. It remains its previous valid value.
                    // The isError flag below will indicate the input problem.
                }
            },
            modifier = Modifier.width(150.dp), // Adjust width as needed
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            placeholder = { Text("0 = unset") }, // Updated placeholder
            // isError is true if the field is NOT empty but IS NOT a valid float
            isError = textValue.isNotEmpty() && textValue.toFloatOrNull() == null,
            trailingIcon = {
                // Clear button: Sets text to "" and state to 0.0f
                if (textValue.isNotEmpty()) {
                    IconButton(onClick = {
                        textValue = "" // Clear text field
                        property.state.value = 0.0f // Set state to 0.0f ("null")
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear (set to 0)",
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun ArrayEditor(property: EditableProperty<*>) { // Use wildcard as array content varies
    PropertyEditorRow(label = property.label, description = property.description) {
        Text("Array (Not directly editable)", style = MaterialTheme.typography.caption)
        // Optionally add a button to navigate to a dedicated array editor screen
        // Button(onClick = { /* Navigate or show dialog */ }) { Text("Edit") }
    }
}

@Composable
fun UnknownTypeEditor(property: EditableProperty<*>) {
    PropertyEditorRow(label = property.label, description = property.description) {
        Text("Unknown Type", style = MaterialTheme.typography.caption)
        OutlinedTextField(
            value = property.state.value.toString(), // Display raw value
            onValueChange = { /* Read-only or basic string edit */ },
            modifier = Modifier.widthIn(min = 150.dp),
            readOnly = true, // Or allow basic string editing if appropriate
        )
    }
}

@Composable
fun PropertyRow(
    label: String,
    description: String? = null,
    editor: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .defaultMinSize(minHeight = 56.dp), // Standard Material touch target height
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label Section: Takes up all available space and wraps text
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.subtitle1,
                    softWrap = true
                )
                if (!description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Editor Section: Sits on the right
            Box(
                modifier = Modifier.widthIn(max = 200.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                editor()
            }
        }

        Divider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ListNumberEditor(property: EditableProperty<Int>) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = property.options?.find { it.value == property.state.value }

    PropertyRow(
        label = property.label,
        description = property.description,
        editor = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedOption?.display ?: property.state.value.toString(),
                    onValueChange = {},
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    textStyle = MaterialTheme.typography.body2,
                    modifier = Modifier.widthIn(min = 140.dp) // Ensures readability
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    property.options?.forEach { option ->
                        DropdownMenuItem(onClick = {
                            property.state.value = option.value
                            expanded = false
                        }) {
                            Text(text = option.display)
                        }
                    }
                }
            }
        }
    )
}

data class SubSport(val name: String, val value: Int)
data class Sport(val name: String, val value: Int, val subSports: List<SubSport>)

val sportsData = listOf(
    // CAT_RUN
    Sport(
        "Running", 1000, listOf(
            SubSport("Running", 1000),
            SubSport("Treadmill", 1001),
            SubSport("Street", 1002),
            SubSport("Trail", 1003),
            SubSport("Track", 1004),
            SubSport("Indoor", 1045),
            SubSport("Virtual", 1058),
            SubSport("Obstacle", 1059),
            SubSport("Ultra", 1067)
        )
    ),

    // CAT_CYCLE
    Sport(
        "Cycling", 2000, listOf(
            SubSport("Cycling", 2000),
            SubSport("Spin", 2005),
            SubSport("Indoor", 2006),
            SubSport("Road", 2007),
            SubSport("Mountain", 2008),
            SubSport("Downhill", 2009),
            SubSport("Recumbent", 2010),
            SubSport("Cyclocross", 2011),
            SubSport("Hand", 2012),
            SubSport("Track", 2013),
            SubSport("BMX", 2029),
            SubSport("Gravel", 2046),
            SubSport("Commute", 2048),
            SubSport("Mixed Surface", 2049),
            SubSport("E-Bike", 21000),
            SubSport("E-Bike Fitness", 21028),
            SubSport("E-Bike Mountain", 21047)
        )
    ),

    // CAT_WALK_MULTI
    Sport(
        "Walking & Multi", 11000, listOf(
            SubSport("Walking", 11000),
            SubSport("Indoor Walking", 11027),
            SubSport("Casual Walking", 11030),
            SubSport("Speed Walking", 11031),
            SubSport("Multisport", 18000),
            SubSport("Triathlon", 18078),
            SubSport("Duathlon", 18079),
            SubSport("Brick", 18080),
            SubSport("Swimrun", 18081),
            SubSport("Adventure Race", 18082),
            SubSport("Transition", 3000)
        )
    ),

    // CAT_GYM
    Sport(
        "Fitness & Gym", 4000, listOf(
            SubSport("Fitness Equipment", 4000),
            SubSport("Indoor Rowing", 4014),
            SubSport("Elliptical", 4015),
            SubSport("Stair Climbing", 4016),
            SubSport("Strength", 4020),
            SubSport("Cardio", 4026),
            SubSport("Yoga", 4043),
            SubSport("Pilates", 4044),
            SubSport("Indoor Climbing", 4068),
            SubSport("Bouldering", 4069),
            SubSport("Floor Climbing", 48000),
            SubSport("HIIT", 62000),
            SubSport("HIIT AMRAP", 62073),
            SubSport("HIIT EMOM", 62074),
            SubSport("HIIT Tabata", 62075)
        )
    ),

    // CAT_WATER
    Sport(
        "Water Sports", 5000, listOf(
            SubSport("Swimming", 5000),
            SubSport("Lap Swimming", 5017),
            SubSport("Open Water", 5018),
            SubSport("Rowing", 15000),
            SubSport("Paddling", 19000),
            SubSport("Boating", 23000),
            SubSport("Boating / Sailing", 23032),
            SubSport("Sailing", 32000),
            SubSport("Sailing Race", 32065),
            SubSport("SUP", 37000),
            SubSport("Surfing", 38000),
            SubSport("Wakeboarding", 39000),
            SubSport("Water Skiing", 40000),
            SubSport("Kayaking", 41000),
            SubSport("White Water Kayak", 41041),
            SubSport("Rafting", 42000),
            SubSport("White Water Rafting", 42041),
            SubSport("Windsurfing", 43000),
            SubSport("Kitesurfing", 44000),
            SubSport("Tubing", 76000),
            SubSport("Wakesurfing", 77000)
        )
    ),

    // CAT_WINTER
    Sport(
        "Winter Sports", 58000, listOf(
            SubSport("Winter Sports", 58000),
            SubSport("XC Skiing", 12000),
            SubSport("XC Skate Ski", 12042),
            SubSport("Alpine Skiing", 13000),
            SubSport("Backcountry Ski", 13037),
            SubSport("Resort Ski", 13038),
            SubSport("Snowboarding", 14000),
            SubSport("Backcountry Snowboard", 14037),
            SubSport("Resort Snowboard", 14038),
            SubSport("Ice Skating", 33000),
            SubSport("Ice Skating / Hockey", 33073),
            SubSport("Snowshoeing", 35000),
            SubSport("Snowmobiling", 36000)
        )
    ),

    // CAT_RACKET_BALL
    Sport(
        "Racket & Ball", 64000, listOf(
            SubSport("Racket Sports", 64000),
            SubSport("Pickleball", 64084),
            SubSport("Padel", 64085),
            SubSport("Squash", 64094),
            SubSport("Badminton", 64095),
            SubSport("Racquetball", 64096),
            SubSport("Table Tennis", 64097),
            SubSport("Basketball", 6000),
            SubSport("Soccer", 7000),
            SubSport("Tennis", 8000),
            SubSport("American Football", 9000),
            SubSport("Baseball", 49000),
            SubSport("Softball Fast", 50000),
            SubSport("Softball Slow", 51000),
            SubSport("Team Sport", 70000),
            SubSport("Ultimate Disc", 70092),
            SubSport("Cricket", 71000),
            SubSport("Rugby", 72000),
            SubSport("Hockey", 73000),
            SubSport("Field Hockey", 73090),
            SubSport("Ice Hockey", 73091),
            SubSport("Lacrosse", 74000),
            SubSport("Volleyball", 75000)
        )
    ),

    // CAT_OUTDOOR_GOLF
    Sport(
        "Outdoor & Golf", 17000, listOf(
            SubSport("Hiking", 17000),
            SubSport("Mountaineering", 16000),
            SubSport("Golf", 25000),
            SubSport("Hang Gliding", 26000),
            SubSport("Horseback Riding", 27000),
            SubSport("Hunting", 28000),
            SubSport("Fishing", 29000),
            SubSport("Rock Climbing", 31000),
            SubSport("Indoor Rock Climbing", 31068),
            SubSport("Bouldering", 31069),
            SubSport("Sky Diving", 34000),
            SubSport("Wingsuit", 34040),
            SubSport("Disc Golf", 69000)
        )
    ),

    // CAT_MISC
    Sport(
        "Misc", 0, listOf(
            SubSport("Generic", 0),
            SubSport("Training", 10000),
            SubSport("Flying", 20000),
            SubSport("Drone", 20039),
            SubSport("Motorcycle", 22000),
            SubSport("ATV", 22035),
            SubSport("Motocross", 22036),
            SubSport("Driving", 24000),
            SubSport("Inline Skating", 30000),
            SubSport("Tactical", 45000),
            SubSport("Jumpmaster", 46000),
            SubSport("Boxing", 47000),
            SubSport("Shooting", 56000),
            SubSport("Auto Racing", 57000),
            SubSport("Grinding", 59000),
            SubSport("Health Monitoring", 60000),
            SubSport("Marine", 61000),
            SubSport("Gaming", 63000),
            SubSport("Esports", 63077),
            SubSport("Wheelchair Walk", 65000),
            SubSport("Wheelchair Walk Indoor", 65086),
            SubSport("Wheelchair Run", 66000),
            SubSport("Wheelchair Run Indoor", 66087),
            SubSport("Meditation", 67000),
            SubSport("Breathwork", 67062),
            SubSport("Parasport", 68000)
        )
    )
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SportAndSubSportPicker(property: EditableProperty<Int>) {
    val selectedValue by property.state
    val sportValue = selectedValue / 1000 * 1000
    val subSportValue = selectedValue

    var selectedSport by remember { mutableStateOf(sportsData.find { it.value == sportValue }) }
    var selectedSubSport by remember {
        mutableStateOf(
            selectedSport?.subSports?.find { it.value == subSportValue })
    }

    var sportExpanded by remember { mutableStateOf(false) }
    var subSportExpanded by remember { mutableStateOf(false) }

    Column {
        PropertyEditorRow(label = "Sport") {
            ExposedDropdownMenuBox(
                expanded = sportExpanded,
                onExpandedChange = { sportExpanded = !sportExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSport?.name ?: "Select Sport",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sportExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = sportExpanded,
                    onDismissRequest = { sportExpanded = false }
                ) {
                    sportsData.forEach { sport ->
                        DropdownMenuItem(onClick = {
                            selectedSport = sport
                            selectedSubSport = sport.subSports.first()
                            property.state.value = selectedSubSport!!.value
                            sportExpanded = false
                        }) {
                            Text(text = sport.name)
                        }
                    }
                }
            }
        }

        PropertyEditorRow(label = "Sub-Sport") {
            ExposedDropdownMenuBox(
                expanded = subSportExpanded,
                onExpandedChange = { subSportExpanded = !subSportExpanded }
            ) {
                OutlinedTextField(
                    value = selectedSubSport?.name ?: "Select Sub-Sport",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subSportExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = subSportExpanded,
                    onDismissRequest = { subSportExpanded = false }
                ) {
                    selectedSport?.subSports?.forEach { subSport ->
                        DropdownMenuItem(onClick = {
                            selectedSubSport = subSport
                            property.state.value = subSport.value
                            subSportExpanded = false
                        }) {
                            Text(text = subSport.name)
                        }
                    }
                }
            }
        }
    }
}
