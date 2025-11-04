package com.paul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.paul.composables.ColorPickerDialog
import com.paul.composables.LoadingOverlay
import com.paul.composables.RoutesArrayEditor
import com.paul.composables.colorToHexString
import com.paul.composables.parseColor
import com.paul.viewmodels.DeviceSettings
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.PropertyType
import com.paul.viewmodels.RouteItem
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
        editableProperties.find { it.id == "mapEnabled" } as? EditableProperty<Boolean>
    }
    val showMapSection by mapEnabledProp?.state ?: remember { mutableStateOf(false) }

    val findProp = remember<(String) -> EditableProperty<*>?>(editableProperties) {
        { id -> editableProperties.find { it.id == id } }
    }

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
                    CollapsibleSection("General") {
                        findProp("activityType")?.let { PropertyEditorResolver(it) }
                        findProp("mode")?.let { PropertyEditorResolver(it) }
                        findProp("uiMode")?.let { PropertyEditorResolver(it) }
                        findProp("elevationMode")?.let { PropertyEditorResolver(it) }
                        findProp("scale")?.let { PropertyEditorResolver(it) }
                        findProp("recalculateIntervalS")?.let { PropertyEditorResolver(it) }
                        findProp("renderMode")?.let { PropertyEditorResolver(it) }
                        findProp("centerUserOffsetY")?.let { PropertyEditorResolver(it) }
                        findProp("displayLatLong")?.let { PropertyEditorResolver(it) }
                        findProp("maxTrackPoints")?.let { PropertyEditorResolver(it) }
                        findProp("mapMoveScreenSize")?.let { PropertyEditorResolver(it) }
                    }
                }

                item {
                    CollapsibleSection("Zoom At Pace") {
                        findProp("zoomAtPaceMode")?.let { PropertyEditorResolver(it) }
                        findProp("metersAroundUser")?.let { PropertyEditorResolver(it) }
                        findProp("zoomAtPaceSpeedMPS")?.let { PropertyEditorResolver(it) }
                    }
                }

                // --- Map Settings Section ---
                // The main toggle is placed outside the collapsible section, so the entire
                // section can be hidden if maps are disabled.
                mapEnabledProp?.let {
                    item(key = it.id) {
                        PropertyEditorResolver(it)
                    }
                }

                item(key = "map_settings_section") {
                    AnimatedVisibility(
                        visible = showMapSection,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        CollapsibleSection("Map Settings") {
                            // Map Cache & Performance
                            findProp("tileCacheSize")?.let { PropertyEditorResolver(it) }
                            findProp("tileCachePadding")?.let { PropertyEditorResolver(it) }
                            findProp("maxPendingWebRequests")?.let { PropertyEditorResolver(it) }
                            findProp("disableMapsFailure")?.let { PropertyEditorResolver(it) }
                            findProp("httpErrorTileTTLS")?.let { PropertyEditorResolver(it) }
                            findProp("errorTileTTLS")?.let { PropertyEditorResolver(it) }
                            findProp("fixedLatitude")?.let { PropertyEditorResolver(it) }
                            findProp("fixedLongitude")?.let { PropertyEditorResolver(it) }
                            findProp("scaleRestrictedToTileLayers")?.let { PropertyEditorResolver(it) }
                            findProp("packingFormat")?.let { PropertyEditorResolver(it) }
                            findProp("useDrawBitmap")?.let { PropertyEditorResolver(it) }
                        }
                    }
                }
                item(key = "tile_Server_section") {
                    AnimatedVisibility(
                        visible = showMapSection,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        CollapsibleSection("Tile Server Settings") {
                            findProp("mapChoice")?.let { PropertyEditorResolver(it) }
                            findProp("tileUrl")?.let { PropertyEditorResolver(it) }
                            findProp("authToken")?.let { PropertyEditorResolver(it) }
                            findProp("tileSize")?.let { PropertyEditorResolver(it) }
                            findProp("scaledTileSize")?.let { PropertyEditorResolver(it) }
                            findProp("tileLayerMax")?.let { PropertyEditorResolver(it) }
                            findProp("tileLayerMin")?.let { PropertyEditorResolver(it) }
                            findProp("fullTileSize")?.let { PropertyEditorResolver(it) }
                        }
                    }
                }

                item(key = "offline_tile_storage_section") {
                    AnimatedVisibility(
                        visible = showMapSection,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        CollapsibleSection("Offline Tile Storage") {
                            findProp("cacheTilesInStorage")?.let { PropertyEditorResolver(it) }
                            findProp("storageMapTilesOnly")?.let { PropertyEditorResolver(it) }
                            findProp("storageTileCacheSize")?.let { PropertyEditorResolver(it) }
                            findProp("storageTileCachePageCount")?.let { PropertyEditorResolver(it) }
                            findProp("storageSeedBoundingBox")?.let { PropertyEditorResolver(it) }
                            findProp("storageSeedRouteDistanceM")?.let { PropertyEditorResolver(it) }
                        }
                    }
                }

                item {
                    CollapsibleSection("Alerts") {
                        findProp("enableOffTrackAlerts")?.let { PropertyEditorResolver(it) }
                        findProp("offTrackAlertsDistanceM")?.let { PropertyEditorResolver(it) }
                        findProp("offTrackCheckIntervalS")?.let { PropertyEditorResolver(it) }
                        findProp("offTrackWrongDirection")?.let { PropertyEditorResolver(it) }
                        findProp("offTrackAlertsMaxReportIntervalS")?.let {
                            PropertyEditorResolver(
                                it
                            )
                        }
                        findProp("drawLineToClosestPoint")?.let { PropertyEditorResolver(it) }
                        findProp("drawCheverons")?.let { PropertyEditorResolver(it) }
                        findProp("alertType")?.let { PropertyEditorResolver(it) }
                        findProp("turnAlertTimeS")?.let { PropertyEditorResolver(it) }
                        findProp("minTurnAlertDistanceM")?.let { PropertyEditorResolver(it) }
                    }
                }


                item {
                    CollapsibleSection("Colours") {
                        findProp("trackColour")?.let { PropertyEditorResolver(it) }
                        findProp("defaultRouteColour")?.let { PropertyEditorResolver(it) }
                        findProp("elevationColour")?.let { PropertyEditorResolver(it) }
                        findProp("userColour")?.let { PropertyEditorResolver(it) }
                        findProp("normalModeColour")?.let { PropertyEditorResolver(it) }
                        findProp("uiColour")?.let { PropertyEditorResolver(it) }
                        findProp("debugColour")?.let { PropertyEditorResolver(it) }
                    }
                }

                item {
                    CollapsibleSection("Route Configuration") {
                        findProp("routesEnabled")?.let { PropertyEditorResolver(it) }
                        findProp("displayRouteNames")?.let { PropertyEditorResolver(it) }
                        findProp("routeMax")?.let { PropertyEditorResolver(it) }
                        findProp("routes")?.let { PropertyEditorResolver(it) }
                    }
                }

                item {
                    CollapsibleSection("Debug") {
                        findProp("showPoints")?.let { PropertyEditorResolver(it) }
                        findProp("drawLineToClosestTrack")?.let { PropertyEditorResolver(it) }
                        findProp("showTileBorders")?.let { PropertyEditorResolver(it) }
                        findProp("showErrorTileMessages")?.let { PropertyEditorResolver(it) }
                        findProp("tileErrorColour")?.let { PropertyEditorResolver(it) }
                        findProp("includeDebugPageInOnScreenUi")?.let { PropertyEditorResolver(it) }
                        findProp("drawHitBoxes")?.let { PropertyEditorResolver(it) }
                        findProp("showDirectionPoints")?.let { PropertyEditorResolver(it) }
                        findProp("showDirectionPointTextUnderIndex")?.let {
                            PropertyEditorResolver(
                                it
                            )
                        }
                    }
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
        PropertyType.NUMBER -> NumberEditor(property as EditableProperty<Int>)
        PropertyType.FLOAT -> FloatEditor(property as EditableProperty<Float>)
        PropertyType.ZERO_DISABLED_FLOAT -> ZeroDisabledFloatEditor(property as EditableProperty<Float>)
        PropertyType.BOOLEAN -> BooleanEditor(property as EditableProperty<Boolean>)
        PropertyType.ARRAY -> ArrayEditor(property) // Array editor might need specific UI
        PropertyType.LIST_NUMBER -> ListNumberEditor(property as EditableProperty<Int>)
        PropertyType.UNKNOWN -> UnknownTypeEditor(property) // Handle unknown gracefully
        PropertyType.SPORT -> SportAndSubSportPicker(property as EditableProperty<Int>)
    }
}


// Generic Row Layout for Editors
@Composable
fun PropertyEditorRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null, // Add description parameter
//    content: @Composable BoxScope.() -> Unit
    content: @Composable RowScope.() -> Unit,
) {
    Column { // Wrap the Row and Divider in a Column for better structure
        Row(
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Column for Label and Description
            Column(
                modifier = Modifier,
//                    .weight(1f) // Takes available horizontal space
//                    .padding(end = 16.dp) // Padding between label/desc and controls
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Main Label Text
                    Text(
                        text = label,
                        style = MaterialTheme.typography.body1, // Or subtitle1
                    )

                    Spacer(Modifier.weight(1f)) // fill the gap in the iddle to push the box to the right

//                    Box(
//                        // enough room for description to render on the left, of the content gets to wide and
//                        // the description is long the description become vertical and makes this item take
//                        // up too much space
//                        modifier = Modifier.widthIn(100.dp, 200.dp),
//                        contentAlignment = Alignment.CenterEnd
//                    ) {
                    content()
//                    }
                }

                // Conditional Description Text
                if (!description.isNullOrBlank()) { // Check if description exists and isn't empty
                    Text(
                        text = description,
                        style = MaterialTheme.typography.caption, // Smaller style
                        color = LocalContentColor.current.copy(alpha = ContentAlpha.medium), // De-emphasize
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
        Divider(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) // Indent divider slightly
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

@OptIn(ExperimentalMaterialApi::class) // For ExposedDropdownMenuBox in M2
@Composable
fun ListNumberEditor(property: EditableProperty<Int>) {
    // Retrieve options safely, ensuring they exist for this property type
    val options = remember(property.id) { property.options ?: emptyList() }
    if (options.isEmpty() && property.type == PropertyType.LIST_NUMBER) {
        // This case indicates an error in setup (called ListNumberEditor without options)
        PropertyEditorRow(label = property.label, description = property.description) {
            Text("Error: Options missing", color = MaterialTheme.colors.error)
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val selectedValue by property.state // Observe the current Int value state

    // Find the display text corresponding to the currently selected value
    val selectedDisplayText = remember(selectedValue, options) {
        options.find { it.value == selectedValue }?.display ?: "Select..." // Default/fallback text
    }

    PropertyEditorRow(
        label = property.label,
        description = property.description,
    ) { // Reuse your existing row layout
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
//            modifier = Modifier.widthIn(min = 200.dp, max = 200.dp) // Give it enough width
        ) {
            // Text field displaying the current selection (usually read-only)
            OutlinedTextField(
                // Or regular TextField if preferred
                value = selectedDisplayText,
                onValueChange = {}, // Selection is handled by the menu items
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                // colors = ExposedDropdownMenuDefaults.textFieldColors(), // Optional M2 styling
//                modifier = Modifier.menuAnchor() // Anchor the dropdown menu to this text field (M3)
                // For M2, this modifier might not be needed directly on TextField
            )

            // The actual dropdown menu
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        onClick = {
                            property.state.value =
                                option.value // Update the state with the selected Int
                            expanded = false // Close the dropdown
                        },
                    ) {
                        Text(text = option.display)
                    }
                }
            }
        }
    }
}

data class SubSport(val name: String, val value: Int)
data class Sport(val name: String, val value: Int, val subSports: List<SubSport>)

val sportsData = listOf(
    Sport("Generic", 0, listOf(SubSport("Generic", 0))),
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
            SubSport("Mixed Surface", 2049)
        )
    ),
    Sport("Transition", 3000, listOf(SubSport("Transition", 3000))),
    Sport(
        "Fitness Equipment", 4000, listOf(
            SubSport("Fitness Equipment", 4000),
            SubSport("Indoor Rowing", 4014),
            SubSport("Elliptical", 4015),
            SubSport("Stair Climbing", 4016),
            SubSport("Strength", 4020),
            SubSport("Cardio", 4026),
            SubSport("Yoga", 4043),
            SubSport("Pilates", 4044),
            SubSport("Indoor Climbing", 4068),
            SubSport("Bouldering", 4069)
        )
    ),
    Sport(
        "Swimming", 5000, listOf(
            SubSport("Swimming", 5000),
            SubSport("Lap", 5017),
            SubSport("Open Water", 5018)
        )
    ),
    Sport("Basketball", 6000, listOf(SubSport("Basketball", 6000))),
    Sport("Soccer", 7000, listOf(SubSport("Soccer", 7000))),
    Sport("Tennis", 8000, listOf(SubSport("Tennis", 8000))),
    Sport("American Football", 9000, listOf(SubSport("American Football", 9000))),
    Sport("Training", 10000, listOf(SubSport("Training", 10000))),
    Sport(
        "Walking", 11000, listOf(
            SubSport("Walking", 11000),
            SubSport("Indoor", 11027),
            SubSport("Casual", 11030),
            SubSport("Speed", 11031)
        )
    ),
    Sport(
        "XC Skiing", 12000, listOf(
            SubSport("XC Skiing", 12000),
            SubSport("Skate", 12042)
        )
    ),
    Sport(
        "Alpine Skiing", 13000, listOf(
            SubSport("Alpine Skiing", 13000),
            SubSport("Backcountry", 13037),
            SubSport("Resort", 13038)
        )
    ),
    Sport(
        "Snowboarding", 14000, listOf(
            SubSport("Snowboarding", 14000),
            SubSport("Backcountry", 14037),
            SubSport("Resort", 14038)
        )
    ),
    Sport("Rowing", 15000, listOf(SubSport("Rowing", 15000))),
    Sport("Mountaineering", 16000, listOf(SubSport("Mountaineering", 16000))),
    Sport("Hiking", 17000, listOf(SubSport("Hiking", 17000))),
    Sport(
        "Multisport", 18000, listOf(
            SubSport("Multisport", 18000),
            SubSport("Triathlon", 18078),
            SubSport("Duathlon", 18079),
            SubSport("Brick", 18080),
            SubSport("Swimrun", 18081),
            SubSport("Adventure Race", 18082)
        )
    ),
    Sport("Paddling", 19000, listOf(SubSport("Paddling", 19000))),
    Sport(
        "Flying", 20000, listOf(
            SubSport("Flying", 20000),
            SubSport("Drone", 20039)
        )
    ),
    Sport(
        "E-Biking", 21000, listOf(
            SubSport("E-Biking", 21000),
            SubSport("Fitness", 21028),
            SubSport("Mountain", 21047)
        )
    ),
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