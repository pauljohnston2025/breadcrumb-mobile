package com.paul.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.CircularProgressIndicator
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
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.composables.ColorPickerDialog
import com.paul.composables.colorToHexString
import com.paul.composables.parseColor
import com.paul.viewmodels.DeviceSettings
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.PropertyType
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun DeviceSettings(deviceSettings: DeviceSettings) {
    val editableProperties = deviceSettings.propertyDefinitions
    // --- State Management (remains the same) ---
    val mapEnabledProp = remember(editableProperties) {
        editableProperties.find { it.id == "mapEnabled" } as? EditableProperty<Boolean>
    }
    val showMapSection by mapEnabledProp?.state ?: remember { mutableStateOf(false) }

    val offTrackAlertsEnabledProp = remember(editableProperties) {
        editableProperties.find { it.id == "enableOffTrackAlerts" } as? EditableProperty<Boolean>
    }
    val showOffTrackSection by offTrackAlertsEnabledProp?.state
        ?: remember { mutableStateOf(false) }

    val findProp = remember<(String) -> EditableProperty<*>?>(editableProperties) {
        { id -> editableProperties.find { it.id == id } }
    }

    // --- UI Composition ---
    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit Properties") }) }
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(modifier = Modifier.weight(1f)) {

                // --- Top Level Settings ---
                val modeProp = findProp("mode")
                if (modeProp != null) item(key = modeProp.id) { PropertyEditorResolver(modeProp) }
                val uiModeProp = findProp("uiMode")
                if (uiModeProp != null) item(key = uiModeProp.id) {
                    PropertyEditorResolver(
                        uiModeProp
                    )
                }
                val scaleProp = findProp("scale")
                if (scaleProp != null) item(key = scaleProp.id) { PropertyEditorResolver(scaleProp) }
                val enableRotationProp = findProp("enableRotation")
                if (enableRotationProp != null) item(key = enableRotationProp.id) {
                    PropertyEditorResolver(
                        enableRotationProp
                    )
                }

                // --- Group: zoomAtPace ---
                item { SectionHeader("Zoom At Pace") } // Keep header separate if group is always visible
                val zoomModeProp = findProp("zoomAtPaceMode")
                if (zoomModeProp != null) item(key = zoomModeProp.id) {
                    PropertyEditorResolver(
                        zoomModeProp
                    )
                }
                val metersAroundProp = findProp("metersAroundUser")
                if (metersAroundProp != null) item(key = metersAroundProp.id) {
                    PropertyEditorResolver(
                        metersAroundProp
                    )
                }
                val zoomSpeedProp = findProp("zoomAtPaceSpeedMPS")
                if (zoomSpeedProp != null) item(key = zoomSpeedProp.id) {
                    PropertyEditorResolver(
                        zoomSpeedProp
                    )
                }


                item(key = "map_settings_header") {
                    SectionHeader("Map Settings")
                }

                // --- Group: mapSettings (Conditional) ---
                // Render the toggle first (this is its own item)
                mapEnabledProp?.let { toggleProp ->
                    item(key = toggleProp.id) { PropertyEditorResolver(toggleProp) }
                }
                // Now, create ONE item for the rest of the map section (header + conditional content)
                item(key = "map_settings_section") { // Add a stable key for this combined item
                    Column(modifier = Modifier.animateContentSize()) { // Column wraps header and AnimatedVisibility
                        AnimatedVisibility(
                            visible = showMapSection,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            // This inner Column holds the actual settings
                            Column {
                                val tileUrlProp = findProp("tileUrl")
                                if (tileUrlProp != null) PropertyEditorResolver(tileUrlProp)
                                val tileSizeProp = findProp("tileSize")
                                if (tileSizeProp != null) PropertyEditorResolver(tileSizeProp)
                                val tileLayerMaxProp = findProp("tileLayerMax")
                                if (tileLayerMaxProp != null) PropertyEditorResolver(
                                    tileLayerMaxProp
                                )
                                val tileLayerMinProp = findProp("tileLayerMin")
                                if (tileLayerMinProp != null) PropertyEditorResolver(
                                    tileLayerMinProp
                                )
                                val tileCacheSizeProp = findProp("tileCacheSize")
                                if (tileCacheSizeProp != null) PropertyEditorResolver(
                                    tileCacheSizeProp
                                )
                                val maxPendingWebRequestsProp = findProp("maxPendingWebRequests")
                                if (maxPendingWebRequestsProp != null) PropertyEditorResolver(
                                    maxPendingWebRequestsProp
                                )
                                val disableMapsFailureProp = findProp("disableMapsFailure")
                                if (disableMapsFailureProp != null) PropertyEditorResolver(
                                    disableMapsFailureProp
                                )
                                val fixedLatProp = findProp("fixedLatitude")
                                if (fixedLatProp != null) PropertyEditorResolver(fixedLatProp)
                                val fixedLonProp = findProp("fixedLongitude")
                                if (fixedLonProp != null) PropertyEditorResolver(fixedLonProp)
                            }
                        }
                    }
                }

                // --- Group: offTrackAlertsGroup (Conditional) ---
                item(key = "off_track_alerts_header") {
                    SectionHeader("Off Track Alerts")
                }
                // Render the toggle first
                offTrackAlertsEnabledProp?.let { toggleProp ->
                    item(key = toggleProp.id) { PropertyEditorResolver(toggleProp) }
                }
                // ONE item for the rest of the off-track section
                item(key = "off_track_section") { // Stable key
                    Column(modifier = Modifier.animateContentSize()) { // Wrap in Column
                        AnimatedVisibility(
                            visible = showOffTrackSection,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            // Inner Column for the settings
                            Column {
                                val distProp = findProp("offTrackAlertsDistanceM")
                                if (distProp != null) PropertyEditorResolver(distProp)
                                val intervalProp = findProp("offTrackAlertsMaxReportIntervalS")
                                if (intervalProp != null) PropertyEditorResolver(intervalProp)
                            }
                        }
                    }
                }

                // --- Group: colours ---
                item { SectionHeader("Colours") } // Header is separate
                // Render individual color properties as separate items
                val trackColProp = findProp("trackColour")
                if (trackColProp != null) item(key = trackColProp.id) {
                    PropertyEditorResolver(
                        trackColProp
                    )
                }
                val elevColProp = findProp("elevationColour")
                if (elevColProp != null) item(key = elevColProp.id) {
                    PropertyEditorResolver(
                        elevColProp
                    )
                }
                val userColourProp = findProp("userColour")
                if (userColourProp != null) item(key = userColourProp.id) {
                    PropertyEditorResolver(
                        userColourProp
                    )
                }
                val normalModeColourProp = findProp("normalModeColour")
                if (normalModeColourProp != null) item(key = normalModeColourProp.id) {
                    PropertyEditorResolver(
                        normalModeColourProp
                    )
                }
                val uiColourProp = findProp("uiColour")
                if (uiColourProp != null) item(key = uiColourProp.id) {
                    PropertyEditorResolver(
                        uiColourProp
                    )
                }
                val debugColProp = findProp("debugColour")
                if (debugColProp != null) item(key = debugColProp.id) {
                    PropertyEditorResolver(
                        debugColProp
                    )
                }

                // --- Group: routesdesc (Route Configuration) ---
                item { SectionHeader("Route Configuration") } // Header is separate
                val routesEnabledProp = findProp("routesEnabled")
                if (routesEnabledProp != null) item(key = routesEnabledProp.id) {
                    PropertyEditorResolver(
                        routesEnabledProp
                    )
                }
                val displayNamesProp = findProp("displayRouteNames")
                if (displayNamesProp != null) item(key = displayNamesProp.id) {
                    PropertyEditorResolver(
                        displayNamesProp
                    )
                }
                val routeMaxProp = findProp("routeMax")
                if (routeMaxProp != null) item(key = routeMaxProp.id) {
                    PropertyEditorResolver(
                        routeMaxProp
                    )
                }

                // --- Setting: routes (Array - Special Handling) ---
                findProp("routes")?.let { prop ->
                    item(key = prop.id) {
                        PropertyEditorRow(label = prop.label, description = prop.description) { /* ... content ... */ }
                    }
                }

                // --- Setting: resetDefaults ---
                val resetProp = findProp("resetDefaults")
                if (resetProp != null) item(key = resetProp.id) { PropertyEditorResolver(resetProp) }

            } // End LazyColumn

            // --- Action Buttons (remains the same) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    val updatedValues = editableProperties
                        .associate { prop -> prop.id to prop.state.value }
                    deviceSettings.onSave(updatedValues)
                }) {
                    Text("Save")
                }
                // Optional: A dedicated Reset button
            } // End Button Row

        } // End Main Column
    } // End Scaffold

    AnimatedVisibility(
        deviceSettings.settingsSaving.value,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(enabled = false) { /* No action, just blocks clicks */ },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(), // Make the Column fill the available space
                horizontalAlignment = Alignment.CenterHorizontally // Center the children horizontally
            ) {
                Text(
                    text = "Settings Saving",
                    Modifier
                        .padding(top = 150.dp)
                        .align(Alignment.CenterHorizontally),
                    color = Color.White,
                    style = MaterialTheme.typography.body1.copy(
                        fontSize = 30.sp,
                        lineHeight = 35.sp,
                        textAlign = TextAlign.Center,
                    )
                )
            }
            CircularProgressIndicator(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center),
                color = Color.Blue
            )
        }
    }
}

// --- Helper Composables ---
@Composable
fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth()) { // Use Column to contain Divider
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1, // Or h6
            color = MaterialTheme.colors.primary, // Optional styling
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp) // Adjust padding
        )
        Divider(modifier = Modifier.padding(horizontal = 16.dp)) // Indent divider slightly
    }
}

// Extracted editor resolver to avoid repetition
@Suppress("UNCHECKED_CAST")
@Composable
fun PropertyEditorResolver(property: EditableProperty<*>) {
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
    }
}


// Generic Row Layout for Editors
@Composable
fun PropertyEditorRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null, // Add description parameter
//    content: @Composable BoxScope.() -> Unit
    content: @Composable RowScope.() -> Unit
) {
    Column { // Wrap the Row and Divider in a Column for better structure
        Row(
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column for Label and Description
            Column(
                modifier = Modifier
                    .weight(1f) // Takes available horizontal space
                    .padding(end = 16.dp) // Padding between label/desc and controls
            ) {
                // Main Label Text
                Text(
                    text = label,
                    style = MaterialTheme.typography.body1 // Or subtitle1
                )
                // Conditional Description Text
                if (!description.isNullOrBlank()) { // Check if description exists and isn't empty
                    Text(
                        text = description,
                        style = MaterialTheme.typography.caption, // Smaller style
                        color = LocalContentColor.current.copy(alpha = ContentAlpha.medium), // De-emphasize
                        modifier = Modifier.padding(top = 2.dp) // Small gap
                    )
                }
            }

            // Spacer to push controls (removed if using weight on Label Column already handles it)
            // Spacer(Modifier.weight(1f)) // May not be needed if Column above uses weight

            // Box for Controls (fixed width, pushed right)
//            Box(
//                modifier = Modifier.width(controlContentWidth),
//                contentAlignment = Alignment.CenterEnd
//            ) {
                content() // The actual editor UI (TextField, Switch, etc.)
//            }
        }
        Divider(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) // Indent divider slightly
    }
}

// --- Specific Editors ---

@Composable
fun StringEditor(property: EditableProperty<String>) {
    var currentValue by property.state
    PropertyEditorRow(label = property.label, description = property.description) {
        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue -> currentValue = newValue },
            modifier = Modifier.widthIn(min = 150.dp), // Adjust width as needed
            singleLine = true
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

    PropertyEditorRow(label = property.label, description = property.description) { // Assuming you still use this layout helper
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
                    .clickable { showDialog = true } // Open the dialog on click
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Display the Hex value (read-only)
            Text(
                text = "#${currentHex.uppercase()}", // Display with # prefix
                style = MaterialTheme.typography.body2, // Or caption
                modifier = Modifier.align(Alignment.CenterVertically)
                    .width(90.dp)
                    .clickable { showDialog = true } // Open the dialog on click
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
        }
    )
}

@Composable
fun BooleanEditor(property: EditableProperty<Boolean>) {
    var currentValue by property.state
    PropertyEditorRow(label = property.label, description = property.description) {
        // Use Switch or Checkbox based on preference
        Switch(
            checked = currentValue,
            onCheckedChange = { newValue -> currentValue = newValue }
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
            isError = textValue.isNotEmpty() && textValue != "-" && textValue.toIntOrNull() == null // Basic validation indication
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
            isError = textValue.isNotEmpty() && textValue != "-" && textValue != "." && textValue != "-." && textValue.toFloatOrNull() == null // Basic validation indication
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

    PropertyEditorRow(label = property.label, description = property.description) { // Assuming you still use this layout helper
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
                            contentDescription = "Clear (set to 0)"
                        )
                    }
                }
            }
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
            readOnly = true // Or allow basic string editing if appropriate
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

    PropertyEditorRow(label = property.label, description = property.description) { // Reuse your existing row layout
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.widthIn(min = 200.dp, max = 200.dp) // Give it enough width
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
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        onClick = {
                            property.state.value =
                                option.value // Update the state with the selected Int
                            expanded = false // Close the dropdown
                        }
                    ) {
                        Text(text = option.display)
                    }
                }
            }
        }
    }
}