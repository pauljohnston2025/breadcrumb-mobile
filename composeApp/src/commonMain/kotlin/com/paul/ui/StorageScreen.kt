package com.paul.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.formatBytes
import com.paul.viewmodels.StorageViewModel


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun StorageScreen(
    tileServerRepo: TileServerRepo,
    viewModel: StorageViewModel
) {

    val tilesBeingDeleted by viewModel.deletingTileServer.collectAsState()
    val routesBeingDeleted by viewModel.deletingRoutes.collectAsState()
    val loadingTileServers by viewModel.loadingTileServer.collectAsState()
    val loadingRoutes by viewModel.loadingRoutes.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val pullRefreshState = rememberPullRefreshState(isRefreshing, { viewModel.refresh() })

    Box(Modifier.pullRefresh(pullRefreshState)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Tile Servers:",
                    style = MaterialTheme.typography.h6,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                spinner(loadingTileServers)
            }

            TilesListSection(
                tiles = viewModel.tileServers,
                tileServerRepo = tileServerRepo,
                onDeleteClick = { viewModel.requestTileDelete(it) },
            )
            Row(
                Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = "All Routes:",
                    style = MaterialTheme.typography.h6,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                spinner(loadingRoutes)
            }

            Row(
                verticalAlignment = Alignment.Top,
                // Add some space between info and buttons if needed
                modifier = Modifier
                    .padding(start = 8.dp)
                    .fillMaxWidth()
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically, // Center buttons vertically within this row
                    horizontalArrangement = Arrangement.End // Arrange buttons closely together at the end
                ) {
                    Text(
                        text = "Size: ${formatBytes(viewModel.routesTotalSize.value)}",
                        style = MaterialTheme.typography.caption,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.requestRoutesDelete() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Tiles",
                            tint = MaterialTheme.colors.error
                        ) // Indicate destructive action
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        tilesBeingDeleted?.let { tileServer ->
            DeleteTilesConfirmationDialog(
                tileServerName = tileServer,
                onConfirm = { viewModel.confirmTileDelete() },
                onDismiss = { viewModel.cancelTileDelete() }
            )
        }
        if (routesBeingDeleted) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelRoutesDelete() },
                title = { Text("Confirm Delete") },
                text = { Text("Are you sure you want to delete all routes?") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmRoutesDelete() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error) // Destructive action color
                    ) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { viewModel.cancelRoutesDelete() }) { Text("Cancel") }
                }
            )
        }
        PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun TilesListSection(
    tiles: MutableState<Map<String, Long>>,
    tileServerRepo: TileServerRepo,
    onDeleteClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (tiles.value.isEmpty()) {
            Text(
                "No saved tiles found.",
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
                val list = tiles.value.keys.toList().sortedByDescending { it }
                LazyColumn(Modifier.heightIn(0.dp, 200.dp)) {
                    items(list, key = { tileServer -> tileServer }) { tileServer ->
                        TileListItem(
                            tileServer = tileServer,
                            size = tiles.value[tileServer]!!,
                            tileServerRepo = tileServerRepo,
                            onDeleteClick = { onDeleteClick(tileServer) }
                        )
                        Divider() // Separator between items
                    }
                }
            }
        }
    }
}

@Composable
private fun TileListItem(
    tileServer: String,
    size: Long,
    tileServerRepo: TileServerRepo,
    onDeleteClick: () -> Unit
) {

    Row(
        verticalAlignment = Alignment.Top,
        // Add some space between info and buttons if needed
        modifier = Modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val name = tileServerRepo.nameFromId(tileServer)
            Text(
                text = if (name != null && name.isNotBlank()) name else "<No Name> ($tileServer)",
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Size: ${formatBytes(size)}",
                style = MaterialTheme.typography.caption,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically, // Center buttons vertically within this row
            horizontalArrangement = Arrangement.End // Arrange buttons closely together at the end
        ) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Tiles",
                    tint = MaterialTheme.colors.error
                ) // Indicate destructive action
            }
        }
    }
}

@Composable
private fun DeleteTilesConfirmationDialog(
    tileServerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Delete") },
        text = { Text("Are you sure you want to delete the tiles for ${tileServerName}?") },
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


@Composable
fun spinner(isLoading: Boolean) {
    AnimatedVisibility(
        visible = isLoading,
        modifier = Modifier.size(20.dp),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.primary // Use theme color
            )
        }
    }
}