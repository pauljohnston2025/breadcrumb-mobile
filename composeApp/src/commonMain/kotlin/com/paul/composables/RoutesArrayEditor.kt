package com.paul.composables

import com.paul.ui.PropertyEditorRow
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.RouteItem
import com.paul.viewmodels.createNewRouteItem

@Composable
fun RoutesArrayEditor(property: EditableProperty<MutableList<RouteItem>>) {
    val routesList by property.state
    var showEditDialog by remember { mutableStateOf(false) }
    var editingRouteIndex by remember { mutableStateOf<Int?>(null) } // null=idle, -1=add, >=0=edit

    // *** NEW: State to hold the calculated ID for the next item to add ***
    var nextIdToAdd by remember { mutableStateOf(0) }

    // The route object to pass to the dialog (either existing or null for new)
    val routeToEdit: RouteItem? = remember(editingRouteIndex, routesList) {
        editingRouteIndex?.let { index ->
            if (index >= 0 && index < routesList.size) routesList[index] else null
        }
    }

    val maxLength = 5 // As per your comments

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        // Header Row with Add Button
        PropertyEditorRow(
            label = property.label,
            description = property.description
        ) {
            if (routesList.size < maxLength) {
                OutlinedButton(
                    onClick = {
                        // *** CALCULATE NEXT ID HERE ***
                        nextIdToAdd = findNextAvailableRouteId(routesList)
                        editingRouteIndex = -1 // Signal "Add New"
                        showEditDialog = true
                    },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Route")
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            } else {
                Text("Max routes ($maxLength)", style = MaterialTheme.typography.caption)
            }
        }

        Divider(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))

        // List Display Area
        if (routesList.isEmpty()) {
            Text(
                "No routes defined.",
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.caption
            )
        } else {
            // Using Column here instead of LazyColumn as the max number is small (5)
            // If maxLength was large, LazyColumn would be better.
            Column(modifier = Modifier.fillMaxWidth()) {
                routesList.forEachIndexed { index, routeItem ->
                    RouteItemRow(
                        route = routeItem,
                        onEditClick = {
                            editingRouteIndex = index
                            showEditDialog = true
                        },
                        onDeleteClick = {
                            // Immutable state update for delete
                            val newList = routesList.toMutableList().apply { removeAt(index) }
                            property.state.value = newList
                        }
                    )
                    if (index < routesList.lastIndex) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

// --- Edit/Add Dialog ---
    if (showEditDialog) {
        val isAddingNew = editingRouteIndex == -1
        // *** Use the calculated 'nextIdToAdd' when creating the initial item for the dialog ***
        val dialogInitialRoute = routeToEdit ?: createNewRouteItem(idSuggestion = nextIdToAdd)

        RouteEditDialog(
            initialRoute = dialogInitialRoute,
            isAdding = isAddingNew,
            onDismissRequest = { showEditDialog = false },
            onSaveRoute = { savedRoute ->
                // Immutable state update for save/add
                val currentList = routesList
                val newList = if (isAddingNew) {
                    // *** Ensure the saved route uses the ID calculated *before* the dialog ***
                    // This guards against the dialog somehow changing the ID.
                    val finalRouteToAdd = savedRoute.copy(routeId = nextIdToAdd)
                    currentList.toMutableList().apply { add(finalRouteToAdd) }
                } else { // Editing existing
                    currentList.toMutableList().apply {
                        editingRouteIndex?.let { index ->
                            if (index >= 0 && index < this.size) {
                                // When editing, keep the original ID from savedRoute (which came from initialRoute)
                                set(index, savedRoute)
                            } else { /* Handle error */ }
                        }
                    }
                }
                property.state.value = newList
                showEditDialog = false
            }
        )
    }
}

/**
 * Finds the smallest non-negative integer ID that is not currently used
 * in the provided list of RouteItems.
 */
private fun findNextAvailableRouteId(routes: List<RouteItem>): Int {
    if (routes.isEmpty()) {
        return 0 // Start with 0 if the list is empty
    }
    // Get existing non-negative IDs into a Set for efficient checking
    val existingIds = routes.mapNotNull { if (it.routeId >= 0) it.routeId else null }.toSet()

    var nextId = 0
    // Loop until we find an integer not in the set
    while (existingIds.contains(nextId)) {
        nextId++
    }
    return nextId
}