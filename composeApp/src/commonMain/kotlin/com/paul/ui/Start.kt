package com.paul.ui

import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.paul.composables.LoadingOverlay
import com.paul.composables.RouteMiniMap
import com.paul.domain.HistoryItem
import com.paul.domain.RouteEntry
import com.paul.domain.StravaActivity
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.StravaRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.StartViewModel
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime

@Composable
fun Start(
    viewModel: StartViewModel,
    tileRepository: ITileRepository,
) {
    BackHandler(enabled = viewModel.sendingFile.value != "" || viewModel.deviceSelector.settingsLoading.value) {
        // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
        if (viewModel.deviceSelector.settingsLoading.value) {
            viewModel.deviceSelector.cancelDeviceSettingsLoading()
        }
    }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var routes = viewModel.routeRepo.routes
    val historyItemBeingDeleted by viewModel.deletingHistoryItem.collectAsState()
    val routeBeingEdited by viewModel.editingRoute.collectAsState()
    val tileServer by viewModel.tileServerRepo.currentServerFlow()
        .collectAsState(TileServerRepo.defaultTileServer)

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

    // Delete Confirmation Dialog
    historyItemBeingDeleted?.let { historyItem ->
        DeleteConfirmationDialog(
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    routeBeingEdited?.let { route ->
        EditRouteDialog(
            route = route,
            onConfirm = { newName -> viewModel.confirmRouteEdit(route.id, newName) },
            onDismiss = { viewModel.cancelRouteEditing() }
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
                        Icons.Default.FileOpen,
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
                onEditClick = { viewModel.startRouteEditing(it) },
                onPreviewClick = { viewModel.previewRoute(it) },
                onSendClick = { viewModel.loadFileFromHistory(it) },
                onDeleteClick = { viewModel.requestDelete(it) },
                onClearHistoryClick = { showClearHistoryDialog = true },
                tileRepository,
                viewModel.routeRepo,
                viewModel.snackbarHostState,
                viewModel.stravaRepository,
                viewModel::previewActivity,
                viewModel::sendActivityToDevice,
                viewModel::openActivityInStrava,
                tileServer,
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
    onEditClick: (RouteEntry) -> Unit,
    onPreviewClick: (RouteEntry) -> Unit,
    onSendClick: (HistoryItem) -> Unit,
    onDeleteClick: (HistoryItem) -> Unit,
    onClearHistoryClick: () -> Unit,
    tileRepository: ITileRepository,
    routeRepo: RouteRepository,
    snackbarHostState: SnackbarHostState,
    stravaRepository: StravaRepository,
    onPreviewStrava: (StravaActivity) -> Unit,
    onSendStrava: (StravaActivity) -> Unit,
    openActivityInStrava: (Long) -> Unit,
    tileServer: TileServerInfo,
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
                            onEditClick = { onEditClick(it) },
                            onPreviewClick = { onPreviewClick(it) },
                            onSendClick = { onSendClick(item) },
                            onDeleteClick = { onDeleteClick(item) },
                            tileRepository,
                            routeRepo,
                            snackbarHostState,
                            stravaRepository,
                            onPreviewStrava,
                            onSendStrava,
                            openActivityInStrava,
                            tileServer,
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryListItem(
    routes: SnapshotStateList<RouteEntry>,
    item: HistoryItem,
    onEditClick: (RouteEntry) -> Unit,
    onPreviewClick: (RouteEntry) -> Unit,
    onSendClick: () -> Unit,
    onDeleteClick: () -> Unit,
    tileRepository: ITileRepository,
    routeRepo: RouteRepository,
    snackbarHostState: SnackbarHostState,
    stravaRepository: StravaRepository,
    onPreviewStrava: (StravaActivity) -> Unit,
    onSendStrava: (StravaActivity) -> Unit,
    openActivityInStrava: (Long) -> Unit,
    tileServer: TileServerInfo,
) {
    // 1. Resolve local route if it exists
    val localRoute = remember(item.routeId, routes) { routes.find { it.id == item.routeId } }

    // 2. State for Display Name and Strava Activity
    var displayName by remember { mutableStateOf(localRoute?.name ?: "Loading...") }
    var stravaActivity by remember { mutableStateOf<StravaActivity?>(null) }
    var isStravaMetadataLoaded by remember { mutableStateOf(false) }

    // 3. Fetch Strava metadata if this is a Strava item
    LaunchedEffect(item.id) {
        if (item.isStrava()) {
            val activityId = item.stravaId()
            if (activityId != null) {
                val activity = stravaRepository.getActivity(activityId)
                if (activity != null) {
                    stravaActivity = activity
                    displayName = activity.name
                } else {
                    displayName = "Strava Activity (${item.stravaId()})"
                }
            }
            isStravaMetadataLoaded = true
        } else if (localRoute != null) {
            displayName = localRoute.name
        } else {
            displayName = "Unknown Route"
        }
    }

    // 4. Fetch Route Detail (for the MiniMap)
    val routeDetail by produceState<Route?>(initialValue = null, localRoute?.id, stravaActivity) {
        val result = if (item.isStrava() && isStravaMetadataLoaded) {
            stravaActivity?.summaryToRoute()
        } else {
            routeRepo.getRouteEntrySummary(localRoute, snackbarHostState)
        }

        // If result is null, provide an empty Route object so the UI
        // stops showing the CircularProgressIndicator.
        value = result ?: Route("ignored name", emptyList(), emptyList())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- Map Thumbnail ---
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            routeDetail?.let { activeRoute ->
                RouteMiniMap(
                    route = activeRoute,
                    tileRepository = tileRepository,
                    modifier = Modifier.fillMaxSize(),
                    tileServer,
                )
            } ?: run {
                androidx.compose.material.CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // --- Info Column ---
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.timestamp.toLocalDateTime(currentSystemDefault()).toString(),
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))

                // Action Buttons
                if (!item.isStrava() && localRoute != null) {
                    // Only show Edit for local routes
                    IconButton(
                        onClick = { onEditClick(localRoute) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = Color.Gray)
                    }
                }

                if (item.isStrava()) {
                    IconButton(
                        onClick = {
                            openActivityInStrava(
                                item.stravaId()
                            )
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = "Open in Strava",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }

                if (item.isStrava() && stravaActivity != null) {
                    IconButton(
                        onClick = { stravaActivity?.let { onPreviewStrava(it) } },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                } else if (localRoute != null) {
                    IconButton(
                        onClick = { onPreviewClick(localRoute) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }

                // Send/Play Action
                IconButton(
                    onClick = {
                        if (item.isStrava()) {
                            stravaActivity?.let { onSendStrava(it) }
                        } else {
                            onSendClick()
                        }
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        Modifier.size(22.dp),
                        tint = Color(0xFF4CAF50)
                    )
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colors.error.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
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

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Delete") },
        text = { Text("Are you sure you want to delete the history item? Note: this does not remove the underlying route.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error) // Destructive action color
            ) { Text("Delete") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
