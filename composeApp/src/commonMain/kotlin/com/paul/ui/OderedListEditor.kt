package com.paul.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.ListOption
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun OrderedListPopupEditor(
    property: EditableProperty<MutableList<ListOption>>,
    allAvailableOptions: List<ListOption>,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val currentList = property.state.value

    val lazyListState = rememberLazyListState()

    // Setup the reorderable state
    val state = rememberReorderableLazyListState(
        lazyListState = lazyListState, // This fixes the "No value passed" error
        onMove = { from, to ->
            val newList = currentList.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            property.state.value = newList
        }
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Edit ${property.label}", style = MaterialTheme.typography.h6)

                if (property.description != null) {
                    Text(text = "Drag to reorder. Use the 'Add' button to include more modes.", style = MaterialTheme.typography.caption)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(currentList, key = { _, item -> item.value }) { index, option ->
                        ReorderableItem(state, key = option.value) { isDragging ->
                            // Elevate the card slightly while dragging for visual feedback
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 2.dp)

                            ReorderableRow(
                                option = option,
                                elevation = elevation,
                                // This modifier enables dragging via the handle
                                dragModifier = Modifier.draggableHandle(
                                    onDragStarted = { /* Optional haptic feedback here */ },
                                    onDragStopped = { /* Optional cleanup */ }
                                ),
                                onRemove = {
                                    property.state.value = currentList.filter { it.value != option.value }.toMutableList()
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add Item")
                    }
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val remainingOptions = allAvailableOptions.filter { opt ->
            currentList.none { it.value == opt.value }
        }
        ModeSelectionDialog(
            options = remainingOptions,
            onSelect = { newMode ->
                property.state.value = (currentList + newMode).toMutableList()
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun ReorderableRow(
    option: ListOption,
    elevation: androidx.compose.ui.unit.Dp,
    dragModifier: Modifier,
    onRemove: () -> Unit
) {
    Card(
        elevation = elevation,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle Icon
            Icon(
                imageVector = Icons.Default.Reorder,
                contentDescription = "Reorder",
                modifier = dragModifier
                    .padding(end = 12.dp)
                    .size(24.dp),
                tint = Color.Gray
            )

            // Label
            Text(
                text = option.display,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.body1
            )

            // Delete Control
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = Color.Red.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ModeSelectionDialog(
    options: List<ListOption>,
    onSelect: (ListOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add Mode to Display") },
        text = {
            if (options.isEmpty()) {
                Text("All available modes are already added.")
            } else {
                // Wrap in a box with a max height so it doesn't take the whole screen
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn {
                        itemsIndexed(options) { _, option ->
                            ListItem(
                                modifier = Modifier.clickable { onSelect(option) },
                                text = { Text(option.display) },
                                secondaryText = { Text("ID: ${option.value}") }
                            )
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OrderedListSummary(
    property: EditableProperty<MutableList<ListOption>>,
    onEditClick: () -> Unit
) {
    val currentItems = property.state.value

    PropertyEditorRow(
        label = property.label,
        description = property.description
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.clickable { onEditClick() }
        ) {
            // Edit Button at the top right
            OutlinedButton(
                onClick = onEditClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit Order", style = MaterialTheme.typography.caption)
            }

            Spacer(Modifier.height(4.dp))

            // Stacked Preview Tags
            val displayCount = 7
            Column(horizontalAlignment = Alignment.End) {
                currentItems.take(displayCount).forEach { option ->
                    Surface(
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = option.display,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
                if (currentItems.size > displayCount) {
                    Text(
                        text = "+${currentItems.size - displayCount} more...",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (currentItems.isEmpty()) {
                    Text("None selected", style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            }
        }
    }
}