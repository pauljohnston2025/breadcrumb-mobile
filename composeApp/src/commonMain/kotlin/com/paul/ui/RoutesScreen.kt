package com.paul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.composables.RouteMiniMap
import com.paul.domain.RouteEntry
import com.paul.domain.RouteType
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.formatBytes
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.RoutesViewModel
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime

enum class RouteSortOrder(val label: String) {
    DATE_DESC("Newest"),
    DATE_ASC("Oldest"),
    NAME_ASC("A-Z"),
    SIZE_DESC("Largest")
}

@Composable
fun RoutesScreen(viewModel: RoutesViewModel, tileRepository: ITileRepository) {
    val scope = rememberCoroutineScope()
    val routes = viewModel.routeRepo.routes
    val routeBeingEdited by viewModel.editingRoute.collectAsState()
    val routeBeingDeleted by viewModel.deletingRoute.collectAsState()

    // --- Filter & Sort State ---
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<RouteType?>(null) }
    var sortOrder by remember { mutableStateOf(RouteSortOrder.DATE_DESC) }

    val filteredAndSortedRoutes by remember {
        derivedStateOf {
            routes.filter { route ->
                val matchesSearch = route.name.contains(searchQuery, ignoreCase = true)
                val matchesType = selectedType == null || route.type == selectedType
                matchesSearch && matchesType
            }.let { filtered ->
                when (sortOrder) {
                    RouteSortOrder.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
                    RouteSortOrder.DATE_ASC -> filtered.sortedBy { it.createdAt }
                    RouteSortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                    RouteSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
                }
            }
        }
    }

    val tileServer by viewModel.tileServerRepo.currentServerFlow()
        .collectAsState(TileServerRepo.defaultTileServer)

    BackHandler(enabled = viewModel.sendingFile.value != "") { /* Handle Cancel */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {

            // --- Search, Type Filter, and Sort Header ---
            RoutesHeader(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                selectedType = selectedType,
                onTypeChange = { selectedType = it },
                currentSort = sortOrder,
                onSortChange = { sortOrder = it }
            )

            // --- Main List ---
            RouteListSection(
                routes = filteredAndSortedRoutes,
                onEditClick = { viewModel.startEditing(it) },
                onPreviewClick = { viewModel.previewRoute(it) },
                onSendClick = { viewModel.sendRoute(it) },
                onDeleteClick = { viewModel.requestDelete(it) },
                tileRepository,
                viewModel.routeRepo,
                viewModel.snackbarHostState,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                tileServer,
            )
        }

        // --- Dialogs ---
        routeBeingEdited?.let { route ->
            EditRouteDialog(
                route = route,
                onConfirm = { viewModel.confirmEdit(route.id, it) },
                onDismiss = { viewModel.cancelEditing() }
            )
        }

        routeBeingDeleted?.let { route ->
            DeleteConfirmationDialog(
                routeName = route.name,
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.cancelDelete() }
            )
        }

        SendingFileOverlay(sendingMessage = viewModel.sendingFile)
    }
}

@Composable
private fun RoutesHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedType: RouteType?,
    onTypeChange: (RouteType?) -> Unit,
    currentSort: RouteSortOrder,
    onSortChange: (RouteSortOrder) -> Unit
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(36.dp)
                .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
                .border(1.dp, Color.LightGray.copy(0.5f), RoundedCornerShape(8.dp)),
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colors.onSurface),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search routes...",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            // Sort Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val nextIndex =
                            (RouteSortOrder.entries.indexOf(currentSort) + 1) % RouteSortOrder.entries.size
                        onSortChange(RouteSortOrder.entries[nextIndex])
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    null,
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    currentSort.label,
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(12.dp)
                    .background(Color.Gray.copy(0.3f))
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        label = "All",
                        isSelected = selectedType == null
                    ) { onTypeChange(null) }
                }
                items(RouteType.entries) { type ->
                    FilterChip(
                        label = type.name,
                        isSelected = selectedType == type
                    ) { onTypeChange(type) }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colors.primary.copy(alpha = 0.15f) // Subtle tint of your primary brand color
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = 0.05f) // Very light gray/white depending on theme
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = 0.6f) // De-emphasized text
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
    }

    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RouteListSection(
    routes: List<RouteEntry>,
    onEditClick: (RouteEntry) -> Unit,
    onPreviewClick: (RouteEntry) -> Unit,
    onSendClick: (RouteEntry) -> Unit,
    onDeleteClick: (RouteEntry) -> Unit,
    tileRepository: ITileRepository,
    routeRepo: RouteRepository,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    tileServer: TileServerInfo,
) {
    if (routes.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No routes match your filters.", style = MaterialTheme.typography.caption)
        }
    } else {
        Card(modifier = modifier, elevation = 2.dp, shape = RoundedCornerShape(12.dp)) {
            LazyColumn {
                items(routes, key = { it.id }) { route ->
                    RouteListItem(
                        route,
                        onEditClick,
                        onPreviewClick,
                        onSendClick,
                        onDeleteClick,
                        tileRepository,
                        routeRepo,
                        snackbarHostState,
                        tileServer,
                    )
                    Divider(color = Color.LightGray.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun RouteListItem(
    route: RouteEntry,
    onEditClick: (RouteEntry) -> Unit,
    onPreviewClick: (RouteEntry) -> Unit,
    onSendClick: (RouteEntry) -> Unit,
    onDeleteClick: (RouteEntry) -> Unit,
    tileRepository: ITileRepository,
    routeRepo: RouteRepository,
    snackbarHostState: SnackbarHostState,
    tileServer: TileServerInfo,
) {
    // 1. Handle the suspend call to get the Route object
    // produceState starts a coroutine that handles the async fetching
    val routeDetail by produceState<Route?>(initialValue = null, route.id) {
        value = routeRepo.getRouteEntrySummary(route, snackbarHostState)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 2. Display the MiniMap (or a placeholder while loading)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = 0.1f)),
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
                // Optional: Show a small loader or icon while fetching coordinates
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        Spacer(Modifier.width(12.dp))

        // 3. Information Column
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (route.hasDirectionInfo) {
                    Icon(
                        Icons.Default.Directions,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colors.primary
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    route.name.ifBlank { "<No Name>" },
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(route.type.name, style = MaterialTheme.typography.overline, color = Color.Gray)
            }

            Text(
                text = route.createdAt.toLocalDateTime(currentSystemDefault()).toString()
                    .replace("T", " ").substring(0, 16),
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    "Size: ${formatBytes(route.sizeBytes)}",
                    style = MaterialTheme.typography.caption
                )
                Spacer(Modifier.weight(1f))

                // Action Buttons
                ActionButton(Icons.Default.Edit, onClick = { onEditClick(route) })
                ActionButton(Icons.Default.LocationOn, onClick = { onPreviewClick(route) })
                ActionButton(
                    Icons.Default.PlayArrow,
                    tint = Color(0xFF4CAF50),
                    onClick = { onSendClick(route) })
                ActionButton(
                    Icons.Default.Delete,
                    tint = MaterialTheme.colors.error,
                    onClick = { onDeleteClick(route) })
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    tint: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        androidx.compose.material3.Icon(icon, null, Modifier.size(18.dp), tint = tint)
    }
}

@Composable
fun EditRouteDialog(
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