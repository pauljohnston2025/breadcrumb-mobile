package com.paul.ui

import android.util.Log
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.paul.composables.LoadingOverlay
import com.paul.viewmodels.DeviceSelector
import com.paul.viewmodels.HistoryItem
import com.paul.viewmodels.StartViewModel

@Composable
// Removed @Preview as it requires providing ViewModel instances, complex setup
fun Start(
    viewModel: StartViewModel,
    deviceSelector: DeviceSelector, // Keep for navigation action if needed, or use ViewModel event
    snackbarHostState: SnackbarHostState // For potential feedback
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // --- Confirmation Dialog ---
    if (showClearHistoryDialog) {
        ClearHistoryConfirmationDialog(
            onConfirm = {
                viewModel.clearHistory()
                showClearHistoryDialog = false
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    Scaffold(
        scaffoldState = rememberScaffoldState(snackbarHostState = snackbarHostState),
        topBar = { TopAppBar(title = { Text("Breadcrumb Nav") }) } // App title
    ) { paddingValues ->

        // Box allows stacking the sending overlay
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {

            // Main content column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Add vertical scroll for the *entire* content if it might overflow
                    // Do NOT put scroll modifiers inside individual AnimatedVisibility blocks
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp), // Padding around the content
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // --- Error Messages ---
                AnimatedVisibility(
                    visible = viewModel.errorMessage.value != ""
                ) {
                    ErrorDisplay(
                        errorMessage = viewModel.errorMessage.value,
                        onDismiss = { viewModel.errorMessage.value = "" }
                    )
                }
                AnimatedVisibility(
                    visible = viewModel.htmlErrorMessage.value != ""
                ) {
                    HtmlErrorDisplay(
                        htmlErrorMessage = viewModel.htmlErrorMessage.value,
                        onDismiss = { viewModel.htmlErrorMessage.value = "" }
                    )
                }

                // --- Location Section ---
                LocationInputSection(
                    lat = viewModel.lat,
                    long = viewModel.long,
                    onLatChange = { viewModel.lat = it },
                    onLongChange = { viewModel.long = it },
                    onClearLocation = { viewModel.clearLocation() },
                    onLoadLocation = {
                        // Add validation before calling load
                        val latFloat = viewModel.lat.toFloatOrNull()
                        val longFloat = viewModel.long.toFloatOrNull()
                        if (latFloat != null && longFloat != null) {
                            viewModel.loadLocation(latFloat, longFloat)
                        } else {
                            // Show snackbar or some feedback about invalid input
                            Log.w("StartScreen", "Invalid Lat/Long input for load")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- Main Action Buttons ---
                MainActions(
                    onSelectDevice = { deviceSelector.selectDeviceUi() }, // Keep direct call for now
                    onImportFile = { viewModel.pickRoute() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- History Section ---
                HistoryListSection(
                    history = viewModel.history,
                    onHistoryItemClick = { viewModel.loadFileFromHistory(it) }, // Pass item ID or URI
                    onClearHistoryClick = { showClearHistoryDialog = true }
                )

            } // End Main Column

            // --- Sending Overlay ---
            SendingFileOverlay(
                sendingMessage = viewModel.sendingFile.value
            )

        } // End Root Box
    } // End Scaffold
}

// --- Extracted Composables for Sections ---

@Composable
private fun ErrorDisplay(errorMessage: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = errorMessage != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            backgroundColor = MaterialTheme.colors.error,
            contentColor = MaterialTheme.colors.onError,
            elevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = errorMessage ?: "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.body2
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss error")
                }
            }
        }
    }
}

@Composable
private fun HtmlErrorDisplay(htmlErrorMessage: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = htmlErrorMessage != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            backgroundColor = MaterialTheme.colors.error, // Or a different color?
            contentColor = MaterialTheme.colors.onError,
            elevation = 4.dp
        ) {
            Column {
                // Dismiss button at top right
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(4.dp)
                            .size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss HTML error")
                    }
                }
                // AndroidView for HTML content
                AndroidView(
                    factory = { context -> TextView(context).apply { setTextColor(android.graphics.Color.WHITE) } }, // Ensure contrast
                    update = {
                        it.text = HtmlCompat.fromHtml(
                            htmlErrorMessage ?: "",
                            HtmlCompat.FROM_HTML_MODE_COMPACT
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .padding(top = 0.dp) // Adjust padding
                )
            }
        }
    }
}


@Composable
private fun LocationInputSection(
    lat: String,
    long: String,
    onLatChange: (String) -> Unit,
    onLongChange: (String) -> Unit,
    onClearLocation: () -> Unit,
    onLoadLocation: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp) // Spacing between inputs
        ) {
            FloatInput(
                label = "Latitude", // Clearer labels
                value = lat,
                onValueChange = onLatChange,
                modifier = Modifier.weight(1f) // Make inputs share space
            )
            FloatInput(
                label = "Longitude",
                value = long,
                onValueChange = onLongChange,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly // Space out buttons
        ) {
            OutlinedButton(onClick = onClearLocation) { // Less emphasis for Clear
                Icon(
                    Icons.Default.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Clear Location")
            }
            Button(onClick = onLoadLocation) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Use Location") // Clearer action text
            }
        }
    }
}

@Composable
private fun MainActions(
    onSelectDevice: () -> Unit,
    onImportFile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = onSelectDevice) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Devices")
        }
        Button(onClick = onImportFile) {
            Icon(
                Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Import GPX") // Be more specific?
        }
    }
}

@Composable
private fun HistoryListSection(
    history: SnapshotStateList<HistoryItem>,
    onHistoryItemClick: (HistoryItem) -> Unit,
    onClearHistoryClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Recent Routes:", style = MaterialTheme.typography.h6)
            OutlinedButton(onClick = onClearHistoryClick) { // Less emphasis
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Clear All")
            }
        }

        if (history.isEmpty()) {
            Text(
                "No recent routes found.",
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            )
        } else {
            // Card provides a container for the list
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp), // Constrain height
                elevation = 2.dp
            ) {
                LazyColumn {
                    // Sort history newest first based on timestamp
//                    items(history.sortedByDescending { it.timestamp }, key = { it.id }) { item ->
                    itemsIndexed(history) { index, item ->
                        HistoryListItem(item = item, onClick = { onHistoryItemClick(item) })
                        Divider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class) // For ListItem onClick
@Composable
private fun HistoryListItem(item: HistoryItem, onClick: () -> Unit) {
    ListItem( // Use standard ListItem composable
        modifier = Modifier.clickable(onClick = onClick),
        text = {
            Text(
                item.name.ifBlank { "(Untitled Route)" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryText = {
//            Text(
//                historyDateFormatter.format(Date(item.timestamp)), // Format date/time
//                maxLines = 1
//            )
        }
        // Optional: Add an icon if relevant (e.g., route type)
        // icon = { Icon(...) }
    )
}


@Composable
private fun ClearHistoryConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Clear History") },
        text = { Text("Are you sure you want to clear all route history? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error) // Destructive action color
            ) { Text("Clear All") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SendingFileOverlay(sendingMessage: String?) {
    // Use the same LoadingOverlay structure as in DeviceSelectorScreen
    LoadingOverlay(
        isLoading = sendingMessage != null && sendingMessage != "",
        loadingText = sendingMessage ?: "Sending..." // Default text if somehow null but visible
    )
}


// FloatInput extracted and improved slightly
@Composable
private fun FloatInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier // Allow passing modifier
) {
    OutlinedTextField( // Use OutlinedTextField for consistency
        value = value,
        onValueChange = { newValue ->
            // Allow empty, '-', '.', '-.' OR valid Float
            if (newValue.isEmpty() || newValue == "-" || newValue == "." || newValue == "-." || newValue.toFloatOrNull() != null) {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(), // Take full width within its container (e.g., Row weight)
        singleLine = true,
        // Optional: Add error indication if needed
        isError = value.isNotEmpty() && value != "-" && value != "." && value != "-." && value.toFloatOrNull() == null
    )
}