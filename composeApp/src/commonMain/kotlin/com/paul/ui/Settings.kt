package com.paul.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paul.composables.LoadingOverlay
import com.paul.viewmodels.Settings as SettingsViewModel

@OptIn(ExperimentalMaterialApi::class) // Needed for ExposedDropdownMenuBox
@Composable
fun Settings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
) {
    var expanded by remember { mutableStateOf(false) }

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
                text = "Map Tile Server:",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.fillMaxWidth() // Take full width for alignment
                // You might want TextAligh.Start if your parent Column is CenterHorizontally
                // textAlign = TextAlign.Start
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1.5f)
                ) {
                    // NOTE: No .menuAnchor() modifier needed here for M2
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
                            }
                        }
                    }
                }
            }
        }
        // Other settings...
        Spacer(Modifier.weight(1f))
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