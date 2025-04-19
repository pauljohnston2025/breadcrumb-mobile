package com.paul.ui

import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.navigation.NavHostController
import com.paul.composables.LoadingOverlay
import com.paul.domain.HistoryItem
import com.paul.domain.RouteEntry
import com.paul.viewmodels.StartViewModel
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.toLocalDateTime

@Composable
fun Start(
    viewModel: StartViewModel,
    navController: NavHostController,
) {
    BackHandler {
        if (viewModel.sendingFile.value != "") {
            // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
            return@BackHandler
        }

        navController.popBackStack()
    }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var routes = viewModel.routeRepo.routes

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

    // Box allows stacking the sending overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {

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

            Row(
                Modifier.fillMaxWidth(),
//                .padding(vertical = 8.dp, horizontal = 16.dp)
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { viewModel.openDeviceSettings() }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Device Settings")
                }

                Button(onClick = { viewModel.pickRoute() }) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Import GPX")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- History Section ---
            HistoryListSection(
                routes = routes,
                history = viewModel.historyRepo.history,
                onHistoryItemClick = { viewModel.loadFileFromHistory(it) }, // Pass item ID or URI
                onClearHistoryClick = { showClearHistoryDialog = true }
            )

        } // End Main Column

        // --- Sending Overlay ---
        SendingFileOverlay(
            sendingMessage = viewModel.sendingFile
        )

    } // End Root Box
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
private fun HistoryListSection(
    routes: SnapshotStateList<RouteEntry>,
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
            Text("Route History:", style = MaterialTheme.typography.h6)
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
                    val list = history.toList().sortedByDescending { it.timestamp }
                    itemsIndexed(list, key = { index, item -> item.id }) { index, item ->
                        HistoryListItem(
                            routes = routes,
                            item = item,
                            onClick = { onHistoryItemClick(item) })
                        Divider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class) // For ListItem onClick
@Composable
private fun HistoryListItem(
    routes: SnapshotStateList<RouteEntry>,
    item: HistoryItem,
    onClick: () -> Unit
) {
    val route = routes.find { it.id == item.routeId }
    var name = if (route == null || route.name.isEmpty()) "<unknown>" else route.name
    ListItem( // Use standard ListItem composable
        modifier = Modifier.clickable(onClick = onClick),
        text = {
            Text(
                name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryText = {
            Text(
                item.timestamp.toLocalDateTime(currentSystemDefault()).toString(),
                maxLines = 1
            )
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
fun SendingFileOverlay(sendingMessage: MutableState<String>) {
    // Use the same LoadingOverlay structure as in DeviceSelectorScreen
    LoadingOverlay(
        isLoading = sendingMessage.value != "",
        loadingText = sendingMessage.value
    )
}
