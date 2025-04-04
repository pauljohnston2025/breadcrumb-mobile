package com.paul.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.paul.viewmodels.RouteItem

@Composable
fun RouteEditDialog(
    initialRoute: RouteItem,
    isAdding: Boolean,
    onDismissRequest: () -> Unit,
    onSaveRoute: (RouteItem) -> Unit
) {
    var name by remember { mutableStateOf(initialRoute.name) }
    var enabled by remember { mutableStateOf(initialRoute.enabled) }
    var colorHex by remember { mutableStateOf(initialRoute.colour) } // Store hex AARRGGBB
    var showColorPicker by remember { mutableStateOf(false) }

    // Parse the current hex for the preview/picker initial state
    val currentColor = remember(colorHex) { parseColor(colorHex) } // Use updated parser

    val isNameValid = name.isNotBlank()

    // Use custom Dialog if AlertDialog spacing was an issue, otherwise AlertDialog is fine
    Dialog(onDismissRequest = onDismissRequest) { // Or AlertDialog(...)
        Card( // Provides background, shape, elevation
            modifier = Modifier.wrapContentHeight().fillMaxWidth(0.95f),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = 24.dp)) {

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
                                colour = colorHex // Save the hex string directly
                            )
                            onSaveRoute(updatedRoute)
                        },
                        enabled = isNameValid
                    ) {
                        Text("Save")
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
}