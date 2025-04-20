package com.paul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import com.paul.viewmodels.RoutesViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Use items, not itemsIndexed if index not needed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation.NavController
import com.paul.domain.RouteEntry
import com.paul.infrastructure.service.formatBytes
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import okhttp3.internal.wait


@Composable
fun RoutesScreen(viewModel: RoutesViewModel, navController: NavController) {
    BackHandler {
        if (viewModel.sendingFile.value != "") {
            // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
            return@BackHandler
        }

        navController.popBackStack()
    }

    val routes = viewModel.routeRepo.routes
    val routeBeingEdited by viewModel.editingRoute.collectAsState()
    val routeBeingDeleted by viewModel.deletingRoute.collectAsState()
// Box allows stacking the sending overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // --- UI ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Maybe add a button to create a new route? (Optional)
            // Button(onClick = { /* TODO: Navigate to route creation */ }) { Text("Create New Route") }
            // Spacer(Modifier.height(16.dp))

            RouteListSection(
                routes = routes,
                onEditClick = { viewModel.startEditing(it) },
                onPreviewClick = { viewModel.previewRoute(it) },
                onSendClick = { viewModel.sendRoute(it) },
                onDeleteClick = { viewModel.requestDelete(it) }
            )
        }

        // --- Dialogs ---
        // Edit Dialog
        routeBeingEdited?.let { route ->
            EditRouteDialog(
                route = route,
                onConfirm = { newName -> viewModel.confirmEdit(route.id, newName) },
                onDismiss = { viewModel.cancelEditing() }
            )
        }

        // Delete Confirmation Dialog
        routeBeingDeleted?.let { route ->
            DeleteConfirmationDialog(
                routeName = route.name,
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.cancelDelete() }
            )
        }

        // --- Sending Overlay ---
        SendingFileOverlay(
            sendingMessage = viewModel.sendingFile
        )

    } // End Root Box
}

@Composable
private fun RouteListSection(
    routes: SnapshotStateList<RouteEntry>,
    onEditClick: (RouteEntry) -> Unit,
    onPreviewClick: (RouteEntry) -> Unit,
    onSendClick: (RouteEntry) -> Unit,
    onDeleteClick: (RouteEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (routes.isEmpty()) {
            Text(
                "No saved routes found.",
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(), // Let height grow naturally or set max
                elevation = 2.dp
            ) {
                val list = routes.toList().sortedByDescending { it.createdAt }
                LazyColumn {
                    items(list, key = { route -> route.id }) { route ->
                        RouteListItem(
                            route = route,
                            onEditClick = { onEditClick(route) },
                            onPreviewClick = { onPreviewClick(route) },
                            onSendClick = { onSendClick(route) },
                            onDeleteClick = { onDeleteClick(route) }
                        )
                        Divider() // Separator between items
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteListItem(
    route: RouteEntry,
    onEditClick: () -> Unit,
    onPreviewClick: () -> Unit,
    onSendClick: () -> Unit,
    onDeleteClick: () -> Unit
) {

    Row(
        Modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = if (route.name.isNotBlank()) route.name else "<No Name>",
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Row(
        Modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "Created At: " + route.createdAt.toLocalDateTime(currentSystemDefault()).toString(),
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    // Action Buttons Section
    Row(
        verticalAlignment = Alignment.Top,
        // Add some space between info and buttons if needed
        modifier = Modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
    ) {
        Column {
            Text(
                text = "Size: ${formatBytes(route.sizeBytes)}", // Display type or other info
                style = MaterialTheme.typography.caption,
                maxLines = 1
            )
            Text(
                text = "Type: ${route.type}", // Display type or other info
                style = MaterialTheme.typography.caption,
                maxLines = 1
            )
        }

        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically, // Center buttons vertically within this row
            horizontalArrangement = Arrangement.End // Arrange buttons closely together at the end
        ) {
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Route Name")
            }
            IconButton(onClick = onPreviewClick) {
                Icon(Icons.Default.LocationOn, contentDescription = "Preview Route")
            }
            IconButton(onClick = onSendClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Send Route To Watch")
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Route",
                    tint = MaterialTheme.colors.error
                ) // Indicate destructive action
            }
        }
    }
}

@Composable
private fun EditRouteDialog(
    route: RouteEntry,
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentName by remember(route.id) { mutableStateOf(route.name) } // Reset when route changes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Route Name") },
        text = {
            Column { // Use column in case you add more fields later
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { currentName = it },
                    label = { Text("Route Name") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentName) },
                enabled = currentName.isNotBlank() // Optionally disable if name is empty
            ) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    routeName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Delete") },
        text = { Text("Are you sure you want to delete the route \"${if (routeName.isNotBlank()) routeName else "<No Name>"}\"? This action cannot be undone.") },
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
