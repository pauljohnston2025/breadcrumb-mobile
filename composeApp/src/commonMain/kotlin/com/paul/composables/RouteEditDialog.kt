package com.paul.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.paul.viewmodels.ListOption
import com.paul.viewmodels.RouteItem

val trackStyles = listOf(
    ListOption(0, "Line"),
    ListOption(1, "Dashed (CPU)"),
    ListOption(2, "Raw Points"),
    ListOption(3, "Points (CPU)"),
    ListOption(4, "Raw Boxes"),
    ListOption(5, "Boxes (CPU)"),
    ListOption(6, "Raw Filled Squares"),
    ListOption(7, "Filled Squares (CPU)"),
    ListOption(8, "Raw Circle Outlines"),
    ListOption(9, "Circle Outlines (CPU)"),
    ListOption(10, "Checkerboard (Texture)"),
    ListOption(11, "Hazard Stripes (Texture)"),
    ListOption(12, "Dot Matrix (Texture)"),
    ListOption(13, "Polka Dot (Texture)"),
    ListOption(14, "Diamond Scale (Texture)")
)

@Composable
fun RouteEditDialog(
    initialRoute: RouteItem,
    isAdding: Boolean,
    onDismissRequest: () -> Unit,
    onSaveRoute: (RouteItem) -> Unit
) {

    var name by remember { mutableStateOf(initialRoute.name) }
    var enabled by remember { mutableStateOf(initialRoute.enabled) }
    var reversed by remember { mutableStateOf(initialRoute.reversed) }
    var colorHex by remember { mutableStateOf(initialRoute.colour) } // Store hex AARRGGBB
    var color2Hex by remember { mutableStateOf(initialRoute.colour2) } // Store hex AARRGGBB

    // --- New State for Style and Width ---
    var selectedStyle by remember { mutableStateOf(initialRoute.style.toInt()) }
    var widthText by remember { mutableStateOf(initialRoute.width.toString()) }
    // Ensure width is a valid number and at least 1
    val widthValue = widthText.toIntOrNull() ?: 1
    val isWidthValid = widthValue >= 1

    var showColorPicker by remember { mutableStateOf(false) }
    var showColorPicker2 by remember { mutableStateOf(false) }
    var styleExpanded by remember { mutableStateOf(false) }

    // Parse the current hex for the preview/picker initial state
    val currentColor = remember(colorHex) { parseColor(colorHex) } // Use updated parser
    val currentColor2 = remember(color2Hex) { parseColor(color2Hex) } // Use updated parser

    val isNameValid = name.isNotBlank()

    // Use custom Dialog if AlertDialog spacing was an issue, otherwise AlertDialog is fine
    Dialog(onDismissRequest = onDismissRequest) { // Or AlertDialog(...)
        Card( // Provides background, shape, elevation
            modifier = Modifier.wrapContentHeight().fillMaxWidth(0.95f),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            // Added verticalScroll to ensure the dialog is usable on smaller screens
            Column(modifier = Modifier
                .padding(vertical = 20.dp, horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
            ) {

                // --- Title ---
                Text(
                    if (isAdding) "Add New Route" else "Edit Route",
                    style = MaterialTheme.typography.h6
                )
                Spacer(Modifier.height(8.dp))
                // Display Route ID only when editing (it's read-only)
                if (!isAdding) {
                    Text("Route ID: ${initialRoute.routeId}", style = MaterialTheme.typography.caption)
                }
                Spacer(Modifier.height(16.dp))

                // --- Name TextField ---
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Route Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isNameValid
                )
                if (!isNameValid) {
                    Text("Name cannot be empty", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
                Spacer(Modifier.height(16.dp))

                // --- Track Style Dropdown ---
                Box {
                    OutlinedTextField(
                        value = trackStyles.find { it.value == selectedStyle }?.display ?: "Select Style",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Style") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { styleExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    // Transparent overlay to make the whole field clickable for the dropdown
                    Box(modifier = Modifier
                        .matchParentSize()
                        .clickable { styleExpanded = true })

                    DropdownMenu(
                        expanded = styleExpanded,
                        onDismissRequest = { styleExpanded = false }
                    ) {
                        trackStyles.forEach { option ->
                            DropdownMenuItem(onClick = {
                                selectedStyle = option.value
                                styleExpanded = false
                            }) {
                                Text(option.display)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // --- Width Number Picker ---
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { newValue ->
                        // Only allow numeric input
                        if (newValue.all { it.isDigit() }) {
                            widthText = newValue
                        }
                    },
                    label = { Text("Width (pixels)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isWidthValid
                )
                if (!isWidthValid) {
                    Text("Width must be 1 or more", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
                Spacer(Modifier.height(16.dp))

                // --- Enabled Switch ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enabled", style = MaterialTheme.typography.body1)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Spacer(Modifier.height(16.dp))

                // --- Reversed Switch ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Reversed", style = MaterialTheme.typography.body1)
                    Switch(checked = reversed, onCheckedChange = { reversed = it })
                }
                Spacer(Modifier.height(16.dp))

                // --- Color Picker Row ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Colour", style = MaterialTheme.typography.body1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${colorHex.uppercase()}", style = MaterialTheme.typography.caption) // Show hex
                        Spacer(Modifier.width(8.dp))
                        Box( // Color Preview Button
                            modifier = Modifier
                                .size(36.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(currentColor)
                                .border(1.dp, LocalContentColor.current.copy(alpha = ContentAlpha.disabled), MaterialTheme.shapes.small)
                                .clickable { showColorPicker = true }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp)) // Space before buttons

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Colour 2", style = MaterialTheme.typography.body1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${color2Hex.uppercase()}", style = MaterialTheme.typography.caption) // Show hex
                        Spacer(Modifier.width(8.dp))
                        Box( // Color Preview Button
                            modifier = Modifier
                                .size(36.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(currentColor2)
                                .border(1.dp, LocalContentColor.current.copy(alpha = ContentAlpha.disabled), MaterialTheme.shapes.small)
                                .clickable { showColorPicker2 = true }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp)) // Space before buttons

                // --- Action Buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) { // Less emphasis on Cancel
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updatedRoute = initialRoute.copy(
                                // routeId might need special handling if adding vs editing
                                name = name.trim(), // Trim whitespace
                                enabled = enabled,
                                reversed = reversed,
                                colour = colorHex, // Save the hex string directly
                                style = selectedStyle,
                                width = widthValue,
                                colour2 = color2Hex
                            )
                            onSaveRoute(updatedRoute)
                        },
                        enabled = isNameValid
                    ) {
                        Text("Done")
                    }
                }
            } // End main Column in Card
        } // End Card
    } // End Dialog (or AlertDialog)


    // --- Conditional Color Picker Dialog ---
    // Needs ColorPickerDialog composable from previous examples
    if (showColorPicker) {
        ColorPickerDialog( // Reuse the existing dialog
            initialColor = currentColor,
            showDialog = showColorPicker,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { selectedColor ->
                // Update the hex state within this dialog when picker confirms
                colorHex = colorToHexString(selectedColor) // Use updated converter
                showColorPicker = false // Close picker immediately after selection
            }
        )
    }

    if (showColorPicker2) {
        ColorPickerDialog( // Reuse the existing dialog
            initialColor = currentColor2,
            showDialog = showColorPicker2,
            onDismissRequest = { showColorPicker2 = false },
            onColorSelected = { selectedColor ->
                // Update the hex state within this dialog when picker confirms
                color2Hex = colorToHexString(selectedColor, true) // Use updated converter
                showColorPicker2 = false // Close picker immediately after selection
            },
            allowTransparent = true
        )
    }
}