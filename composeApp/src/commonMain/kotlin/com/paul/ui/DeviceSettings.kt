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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
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
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.paul.viewmodels.DeviceSettings
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.PropertyType
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.protocol.todevice.Colour
import kotlin.math.roundToInt

@Composable
@Preview
fun DeviceSettings(deviceSettings: DeviceSettings) {
    val editableProperties =  deviceSettings.propertyDefinitions
    // --- State Management (remains the same) ---
    val mapEnabledProp = remember(editableProperties) {
        editableProperties.find { it.id == "mapEnabled" } as? EditableProperty<Boolean>
    }
    val showMapSection by mapEnabledProp?.state ?: remember { mutableStateOf(false) }

    val offTrackAlertsEnabledProp = remember(editableProperties) {
        editableProperties.find { it.id == "enableOffTrackAlerts" } as? EditableProperty<Boolean>
    }
    val showOffTrackSection by offTrackAlertsEnabledProp?.state ?: remember { mutableStateOf(false) }

    val findProp = remember<(String) -> EditableProperty<*>?>(editableProperties) {
        { id -> editableProperties.find { it.id == id } }
    }

    // --- UI Composition ---
    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit Properties") }) }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(modifier = Modifier.weight(1f)) {

                // --- Top Level Settings ---
                val modeProp = findProp("mode")
                if (modeProp != null) item(key = modeProp.id) { PropertyEditorResolver(modeProp) }
                val uiModeProp = findProp("uiMode")
                if (uiModeProp != null) item(key = uiModeProp.id) { PropertyEditorResolver(uiModeProp) }
                val scaleProp = findProp("scale")
                if (scaleProp != null) item(key = scaleProp.id) { PropertyEditorResolver(scaleProp) }
                val enableRotationProp = findProp("enableRotation")
                if (enableRotationProp != null) item(key = enableRotationProp.id) { PropertyEditorResolver(enableRotationProp) }

                // --- Group: zoomAtPace ---
                item { SectionHeader("Zoom At Pace") } // Keep header separate if group is always visible
                val zoomModeProp = findProp("zoomAtPaceMode")
                if (zoomModeProp != null) item(key = zoomModeProp.id) { PropertyEditorResolver(zoomModeProp) }
                val metersAroundProp = findProp("metersAroundUser")
                if (metersAroundProp != null) item(key = metersAroundProp.id) { PropertyEditorResolver(metersAroundProp) }
                val zoomSpeedProp = findProp("zoomAtPaceSpeedMPS")
                if (zoomSpeedProp != null) item(key = zoomSpeedProp.id) { PropertyEditorResolver(zoomSpeedProp) }


                // --- Group: mapSettings (Conditional) ---
                // Render the toggle first (this is its own item)
                mapEnabledProp?.let { toggleProp ->
                    item(key = toggleProp.id) { PropertyEditorResolver(toggleProp) }
                }
                // Now, create ONE item for the rest of the map section (header + conditional content)
                item(key = "map_settings_section") { // Add a stable key for this combined item
                    Column(modifier = Modifier.animateContentSize()) { // Column wraps header and AnimatedVisibility
                        SectionHeader("Map Settings") // Header is inside the item's Column
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
                                if (tileLayerMaxProp != null) PropertyEditorResolver(tileLayerMaxProp)
                                val tileLayerMinProp = findProp("tileLayerMin")
                                if (tileLayerMinProp != null) PropertyEditorResolver(tileLayerMinProp)
                                val tileCacheSizeProp = findProp("tileCacheSize")
                                if (tileCacheSizeProp != null) PropertyEditorResolver(tileCacheSizeProp)
                                val maxPendingWebRequestsProp = findProp("maxPendingWebRequests")
                                if (maxPendingWebRequestsProp != null) PropertyEditorResolver(maxPendingWebRequestsProp)
                                val disableMapsFailureProp = findProp("disableMapsFailure")
                                if (disableMapsFailureProp != null) PropertyEditorResolver(disableMapsFailureProp)
                                val fixedLatProp = findProp("fixedLatitude")
                                if (fixedLatProp != null) PropertyEditorResolver(fixedLatProp)
                                val fixedLonProp = findProp("fixedLongitude")
                                if (fixedLonProp != null) PropertyEditorResolver(fixedLonProp)
                            }
                        }
                    }
                }

                // --- Group: offTrackAlertsGroup (Conditional) ---
                // Render the toggle first
                offTrackAlertsEnabledProp?.let { toggleProp ->
                    item(key = toggleProp.id) { PropertyEditorResolver(toggleProp) }
                }
                // ONE item for the rest of the off-track section
                item(key = "off_track_section") { // Stable key
                    Column(modifier = Modifier.animateContentSize()) { // Wrap in Column
                        SectionHeader("Off Track Alerts")
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
                if (trackColProp != null) item(key = trackColProp.id) { PropertyEditorResolver(trackColProp) }
                val elevColProp = findProp("elevationColour")
                if (elevColProp != null) item(key = elevColProp.id) { PropertyEditorResolver(elevColProp) }
                val userColourProp = findProp("userColour")
                if (userColourProp != null) item(key = userColourProp.id) { PropertyEditorResolver(userColourProp) }
                val normalModeColourProp = findProp("normalModeColour")
                if (normalModeColourProp != null) item(key = normalModeColourProp.id) { PropertyEditorResolver(normalModeColourProp) }
                val uiColourProp = findProp("uiColour")
                if (uiColourProp != null) item(key = uiColourProp.id) { PropertyEditorResolver(uiColourProp) }
                val debugColProp = findProp("debugColour")
                if (debugColProp != null) item(key = debugColProp.id) { PropertyEditorResolver(debugColProp) }

                // --- Group: routesdesc (Route Configuration) ---
                item { SectionHeader("Route Configuration") } // Header is separate
                val routesEnabledProp = findProp("routesEnabled")
                if (routesEnabledProp != null) item(key = routesEnabledProp.id) { PropertyEditorResolver(routesEnabledProp) }
                val displayNamesProp = findProp("displayRouteNames")
                if (displayNamesProp != null) item(key = displayNamesProp.id) { PropertyEditorResolver(displayNamesProp) }
                val routeMaxProp = findProp("routeMax")
                if (routeMaxProp != null) item(key = routeMaxProp.id) { PropertyEditorResolver(routeMaxProp) }

                // --- Setting: routes (Array - Special Handling) ---
                findProp("routes")?.let { prop ->
                    item(key = prop.id) {
                        PropertyEditorRow(label = prop.label) { /* ... content ... */ }
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

    AnimatedVisibility(deviceSettings.settingsSaving.value,
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
@Composable
fun PropertyEditorResolver(property: EditableProperty<*>) {
    // Your existing when(property.type) { ... } logic here
    when (property.type) {
        PropertyType.STRING -> StringEditor(property as EditableProperty<String>)
        PropertyType.COLOR -> ColorEditor(property as EditableProperty<String>)
        PropertyType.NUMBER -> NumberEditor(property as EditableProperty<Int>)
        PropertyType.FLOAT -> FloatEditor(property as EditableProperty<Float>)
        PropertyType.NULLABLE_FLOAT -> NullableFloatEditor(property as EditableProperty<Float?>)
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
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier
            .weight(1f)
            .padding(end = 16.dp))
        content() // Let the specific editor provide its controls
    }
    Divider() // Optional: separator between rows
}

// --- Specific Editors ---

@Composable
fun StringEditor(property: EditableProperty<String>) {
    var currentValue by property.state
    PropertyEditorRow(label = property.label) {
        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue -> currentValue = newValue },
            modifier = Modifier.widthIn(min = 150.dp), // Adjust width as needed
            singleLine = true
        )
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onColorSelected: (Color) -> Unit // Callback with the final selected color
) {
    if (showDialog) {
        // Remember the slider states within the dialog's lifecycle
        // Initialize them based on the initialColor passed when the dialog appears
        var red by remember { mutableStateOf(initialColor.red) }
        var green by remember { mutableStateOf(initialColor.green) }
        var blue by remember { mutableStateOf(initialColor.blue) }

        // Update sliders if initialColor changes (e.g., external reset while dialog *could* be open)
        LaunchedEffect(initialColor) {
            red = initialColor.red
            green = initialColor.green
            blue = initialColor.blue
        }

        // Derive the preview color directly from the slider states
        val currentColor by remember {
            derivedStateOf { Color(red, green, blue) }
        }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Select Color") },
            text = {
                Column {
                    // Color Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(currentColor) // Show the live color
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Sliders
                    ColorSlider("Red", red) { newValue -> red = newValue }
                    ColorSlider("Green", green) { newValue -> green = newValue }
                    ColorSlider("Blue", blue) { newValue -> blue = newValue }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Optional: Display Hex Value Live
                    Text(
                        "Hex: #${colorToHexString(currentColor)}",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onColorSelected(currentColor) // Pass the final color back
                    onDismissRequest() // Close the dialog
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = onDismissRequest) { // Just close
                    Text("Cancel")
                }
            }
        )
    }
}

// Helper composable for a single color slider row
@Composable
private fun ColorSlider(
    label: String,
    value: Float, // Compose Color components are Floats 0.0f to 1.0f
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.width(50.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            // Optional: Add steps for more discrete control if desired
            // steps = 254 // 255 steps from 0 to 1
        )
        // Display the 0-255 value for user convenience
        Text(
            text = (value * 255).roundToInt().toString(),
            modifier = Modifier.width(35.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.caption
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

    PropertyEditorRow(label = property.label) { // Assuming you still use this layout helper
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

/**
 * Parses an RRGGBB hex string (case-insensitive, optional '#' prefix) into a Compose Color.
 * Returns Color.Black if parsing fails.
 */
fun parseColor(hexString: String): Color {
    return try {
        var sanitized = hexString.trim().removePrefix("#").uppercase()
        if (sanitized.length != 6) {
            // Attempt to handle shorthand like F00 -> FF0000
            if (sanitized.length == 3) {
                sanitized = sanitized.map { "$it$it" }.joinToString("")
            } else {
                throw IllegalArgumentException("Hex string must be 3 or 6 characters long (excluding #)")
            }
        }
        // Use Long parsing to handle potential overflow with Int.parseint for FFFFFF
        val colorLong = sanitized.toLong(16)
        // Create Color object - ensure full alpha (0xFF)
        Color(color = (0xFF000000 or colorLong))
    } catch (e: Exception) {
        println("Error parsing hex color '$hexString': ${e.message}. Defaulting to Black.")
        Color.Black // Default fallback color
    }
}


/**
 * Converts a Compose Color into an uppercase RRGGBB hex string (without '#').
 */
fun colorToHexString(color: Color): String {
    // Use toArgb() to get standard Android integer representation
    // Mask out the alpha channel (or keep it if you need AARRGGBB)
    val rgb = color.toArgb() and 0xFFFFFF
    // Format as 6-digit hex string, padding with leading zeros if needed
    return rgb.toString(16).uppercase().padStart(6, '0')
}

@Composable
fun BooleanEditor(property: EditableProperty<Boolean>) {
    var currentValue by property.state
    PropertyEditorRow(label = property.label) {
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

    PropertyEditorRow(label = property.label) {
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

    PropertyEditorRow(label = property.label) {
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

@Composable
fun NullableFloatEditor(property: EditableProperty<Float?>) {
    // State for the text field's content
    var textValue by remember {
        // Initialize textValue based on the initial state (null -> empty string)
        mutableStateOf(property.state.value?.toString() ?: "")
    }
    // Observe the actual state value
    val stateValue by property.state

    // Effect to synchronize TextField with external state changes (e.g., reset)
    LaunchedEffect(stateValue) {
        val expectedText = stateValue?.toString() ?: ""
        if (textValue != expectedText) {
            // Update the text field if the underlying state changes externally
            textValue = expectedText
        }
    }

    PropertyEditorRow(label = property.label) { // Assuming you still use this layout helper
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                // Always update the text field to show what the user typed
                textValue = newValue

                // Try to update the actual state based on the text field content
                if (newValue.isEmpty()) {
                    // If the field is cleared, set the state to null
                    property.state.value = null
                } else {
                    // Try parsing the text as a Float
                    val parsedFloat = newValue.toFloatOrNull()
                    if (parsedFloat != null) {
                        // If parsing is successful, update the state
                        property.state.value = parsedFloat
                    }
                    // If parsing fails (e.g., text is "-", ".", "abc", "1.2."),
                    // we *don't* update the state. It remains its previous value
                    // until the text becomes empty or a valid Float.
                    // The isError flag will indicate the problem.
                }
            },
            modifier = Modifier.width(100.dp), // Adjust width as needed
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            placeholder = { Text("Optional") }, // Indicate that it can be empty
            // isError is true if the field is NOT empty but IS NOT a valid float
            isError = textValue.isNotEmpty() && textValue.toFloatOrNull() == null,
            trailingIcon = {
                // Optional: Add a clear button if the field is not empty
                if (textValue.isNotEmpty()) {
                    Button(onClick = {
                        textValue = "" // Clear text field
                        property.state.value = null // Set state to null
                    }) {

                    }
                }
            }
        )
    }
}

@Composable
fun ArrayEditor(property: EditableProperty<*>) { // Use wildcard as array content varies
    PropertyEditorRow(label = property.label) {
        Text("Array (Not directly editable)", style = MaterialTheme.typography.caption)
        // Optionally add a button to navigate to a dedicated array editor screen
        // Button(onClick = { /* Navigate or show dialog */ }) { Text("Edit") }
    }
}

@Composable
fun UnknownTypeEditor(property: EditableProperty<*>) {
    PropertyEditorRow(label = property.label) {
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
        PropertyEditorRow(label = property.label) {
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

    PropertyEditorRow(label = property.label) { // Reuse your existing row layout
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.widthIn(min = 180.dp) // Give it enough width
        ) {
            // Text field displaying the current selection (usually read-only)
            OutlinedTextField( // Or regular TextField if preferred
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
                            property.state.value = option.value // Update the state with the selected Int
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