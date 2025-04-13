package com.paul.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paul.composables.LoadingOverlay
import com.paul.viewmodels.ServerType
import com.paul.viewmodels.TileServerInfo
import com.paul.viewmodels.Settings as SettingsViewModel

// Function to validate template (basic example)
fun isValidTileUrlTemplate(url: String): Boolean {
    return url.isNotBlank() && url.contains("{z}") && url.contains("{x}") && url.contains("{y}") &&
            (url.startsWith("http://") || url.startsWith("https://"))
}

@Composable
fun AddCustomServerDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onSave: (name: String, url: String) -> Unit // Callback with validated data
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    // Function to validate input fields
    fun validate(): Boolean {
        nameError = if (name.isBlank()) "Name cannot be empty" else null
        urlError = when {
            url.isBlank() -> "URL template cannot be empty"
            !isValidTileUrlTemplate(url) -> "Invalid template (must contain {z}, {x}, {y} and start with http:// or https://)"
            else -> null
        }
        return nameError == null && urlError == null
    }

    // Reset state when dialog appears (important if reusing the dialog state)
    LaunchedEffect(showDialog) {
        if (showDialog) {
            name = ""
            url = ""
            nameError = null
            urlError = null
        }
    }


    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Add Custom Tile Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // --- Name Input ---
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null // Clear error on change
                        },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g., My Map Server") },
                        singleLine = true,
                        isError = nameError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (nameError != null) {
                        Text(
                            nameError!!,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body1
                        )
                    }

                    Spacer(Modifier.height(8.dp)) // Space between fields

                    // --- URL Input ---
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            urlError = null // Clear error on change
                        },
                        label = { Text("URL Template") },
                        placeholder = { Text("e.g., https://server.com/{z}/{x}/{y}.png") },
                        singleLine = true,
                        isError = urlError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (urlError != null) {
                        Text(
                            urlError!!,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.body1
                        )
                    }
                    Text(
                        "Include {z}, {x}, {y} placeholders.",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (validate()) {
                            onSave(name, url)
                            // Dialog will be dismissed by the caller changing showDialog state
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class) // Needed for ExposedDropdownMenuBox
@Composable
fun Settings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<TileServerInfo?>(null) }
    val showDeleteConfirmDialog = serverToDelete != null

    // --- Call the Add Custom Server Dialog ---
    AddCustomServerDialog(
        showDialog = showAddDialog,
        onDismissRequest = { showAddDialog = false },
        onSave = { name, url ->
            // Create the new TileServer object
            val newServer = TileServerInfo(
                serverType = ServerType.CUSTOM,
                title = name,
                url = url,
                isCustom = true
            )
            viewModel.onAddCustomServer(newServer) // Pass it up to be saved and added to the list
            showAddDialog = false // Close the dialog
        }
    )

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                // User dismissed the dialog (clicked outside, back button)
                serverToDelete = null
            },
            title = {
                Text("Confirm Deletion")
            },
            text = {
                // Use ?.let for safety when accessing the potentially null serverToDelete
                serverToDelete?.let { server ->
                    Text("Are you sure you want to delete the custom tile server '${server.title}'?")
                }
                    ?: Text("Are you sure you want to delete this custom tile server?") // Fallback text
            },
            confirmButton = {
                Button(
                    onClick = {
                        serverToDelete?.let { server ->
                            viewModel.onRemoveCustomServer(server)
                        }
                        serverToDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // User clicked Cancel button
                        serverToDelete = null // Hide dialog
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column( // Use a Column for this specific setting
            modifier = Modifier.fillMaxWidth(),
            // Add vertical spacing between label and dropdown if desired
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Phone Hosted Tile Server:",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.fillMaxWidth() // Take full width for alignment
                // You might want TextAligh.Start if your parent Column is CenterHorizontally
                // textAlign = TextAlign.Start
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween // Align label and add button
            ) {
                Text(
                    text = "Tile Server Enabled:",
                    style = MaterialTheme.typography.body2,
                )
                Switch(
                    checked = viewModel.tileServerEnabled.value,
                    onCheckedChange = { newValue ->
                        viewModel.onTileServerEnabledChange(newValue)
                    },
                )
            }

            if (viewModel.tileServerEnabled.value)
            {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween // Align label and add button
                ) {
                    Text(
                        text = "Add Custom Server:",
                        style = MaterialTheme.typography.body2,
                    )
                    // --- Add Custom Server Button ---
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add custom tile server")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(1.5f)
                    ) {
                        OutlinedTextField(
                            value = viewModel.currentTileServer.value,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Server") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            modifier = Modifier.fillMaxWidth() // Just standard modifiers
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            viewModel.availableServers.forEach { server ->
                                DropdownMenuItem(
                                    onClick = {
                                        viewModel.onServerSelected(server)
                                        expanded = false
                                    }
                                ) {
                                    Text(server.title)

                                    if (server.isCustom) {
                                        IconButton(
                                            onClick = {
                                                serverToDelete = server
                                                expanded = false // Close menu after delete
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .padding(start = 8.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Clear, // Need import androidx.compose.material.icons.filled.Delete
                                                contentDescription = "Delete custom server ${server.title}",
                                                tint = MaterialTheme.colors.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Other settings...
        Spacer(Modifier.height(10.dp))
    }


    LoadingOverlay(
        isLoading = viewModel.sendingMessage.value != "",
        loadingText = viewModel.sendingMessage.value
    )
}

// Optional: Helper composable for consistent setting rows
@Composable
fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.body1)
        content()
    }
}
