package com.paul.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.paul.viewmodels.DeviceSettings
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.PropertyType
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@Preview
fun DeviceSettings(deviceSettings: DeviceSettings) {
    // Create the mutable state for all properties
    // Remember this list based on the initial definitions
    val editableProperties = remember(deviceSettings.propertyDefinitions) {
        deviceSettings.propertyDefinitions.sortedBy { it.id }
        // Filter out properties you don't want to show (e.g., hidden ones)
        // Sort alphabetically by label for consistent order (optional)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Edit Properties") })
        }
    ) { paddingValues -> // Use paddingValues provided by Scaffold
        Column(Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.weight(1f) // Takes up available space
            ) {
                itemsIndexed(editableProperties) { index, property ->
                    // Display the correct editor based on the type
                    when (property.type) {
                        PropertyType.STRING -> StringEditor(property as EditableProperty<String>)
                        PropertyType.COLOR -> ColorEditor(property as EditableProperty<String>)
                        PropertyType.NUMBER -> NumberEditor(property as EditableProperty<Int>)
                        PropertyType.LIST_NUMBER -> ListNumberEditor(property as EditableProperty<Int>) // <-- Use the new editor
                        PropertyType.FLOAT -> FloatEditor(property as EditableProperty<Float>)
                        PropertyType.NULLABLE_FLOAT -> NullableFloatEditor(property as EditableProperty<Float?>)
                        PropertyType.BOOLEAN -> BooleanEditor(property as EditableProperty<Boolean>)
                        PropertyType.ARRAY -> ArrayEditor(property) // Type casting might not be needed if state holds Any/String
                        PropertyType.UNKNOWN -> UnknownTypeEditor(property)
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    // Collect current values from states
                    val updatedValues = editableProperties.associate { prop ->
                        prop.id to prop.state.value // Get the value from the MutableState
                    }

                    deviceSettings.onSave(updatedValues)
                }) {
                    Text("Save")
                }
            }
        }
    }

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
fun ColorEditor(property: EditableProperty<String>) {
    // Simple Text Field for Hex Color for now
    // TODO: Implement a proper Color Picker later if needed
    var currentValue by property.state
    PropertyEditorRow(label = property.label) {
        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                // Basic validation: Allow only hex characters (0-9, A-F, a-f)
                if (newValue.all { it.isDigit() || ('a'..'f').contains(it.lowercaseChar()) }) {
                    currentValue = newValue.uppercase() // Store consistently
                }
            },
            modifier = Modifier.width(100.dp),
            singleLine = true,
            placeholder = { Text("RRGGBB") }
        )
        // Optional: Display a color preview box
        // Box(modifier = Modifier.size(24.dp).background(parseColor(currentValue)))
    }
    // Add a helper function `parseColor(hexString)` if you implement the preview
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