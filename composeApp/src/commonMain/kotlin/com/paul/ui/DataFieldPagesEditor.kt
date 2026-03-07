package com.paul.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.paul.viewmodels.DataFieldPage
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.dataFieldTypes

fun getDataTypeString(typeId: Int): String {
    // Look up the label in your provided list, fallback to ID if not found
    return dataFieldTypes.find { it.value == typeId }?.display ?: "Unknown ($typeId)"
}

@Composable
fun FieldRow(
    fieldIndex: Int,
    currentTypeId: Int,
    onTypeSelected: (Int) -> Unit,
    onRemoveField: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Field ${fieldIndex}",
            style = MaterialTheme.typography.body2,
            modifier = Modifier.weight(1f)
        )

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.defaultMinSize(minWidth = 140.dp)
            ) {
                Text(
                    text = getDataTypeString(currentTypeId),
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Use your dataFieldTypes list for the selection options
                dataFieldTypes.forEach { option ->
                    DropdownMenuItem(onClick = {
                        onTypeSelected(option.value)
                        expanded = false
                    }) {
                        Text(option.display)
                    }
                }
            }
        }

        IconButton(onClick = onRemoveField) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Remove Field",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun DataFieldPagesSummary(
    property: EditableProperty<MutableList<DataFieldPage>>,
    onEditClick: () -> Unit
) {
    val pages = property.state.value
    val totalFields = pages.sumOf { it.fields.size }

    PropertyEditorRow(
        label = property.label,
        description = property.description
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.clickable { onEditClick() }
        ) {
            OutlinedButton(
                onClick = onEditClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.Layers, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit Layout", style = MaterialTheme.typography.caption)
            }

            Spacer(Modifier.height(4.dp))

            // Visual indicator of pages
            Surface(
                color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "${pages.size} Screens • $totalFields Fields",
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
}

@Composable
fun DataFieldPagesPopupEditor(
    property: EditableProperty<MutableList<DataFieldPage>>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f) // Slightly taller to accommodate cards
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = property.label, style = MaterialTheme.typography.h6)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Scrollable content area for the Cards
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // Reuse your existing DataFieldPagesEditor internal logic here
                        // but stripped of its own outer Column/Padding to avoid nesting
                        DataFieldPagesInternalContent(property)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Layout & Close")
                }
            }
        }
    }
}

@Composable
fun DataFieldPagesInternalContent(
    property: EditableProperty<MutableList<DataFieldPage>>
) {
    val pages by property.state

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        pages.forEachIndexed { pageIndex, page ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // --- Page Header ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colors.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Screen ${pageIndex}", style = MaterialTheme.typography.subtitle1)
                        }

                        IconButton(onClick = {
                            val newList = property.state.value.toMutableList()
                            newList.removeAt(pageIndex)
                            property.state.value = newList
                        }) {
                            Icon(Icons.Default.Delete, "Delete Page", tint = Color.Red.copy(alpha = 0.6f))
                        }
                    }

                    Divider(Modifier.padding(vertical = 8.dp))

                    // --- Fields List ---
                    page.fields.forEachIndexed { fieldIndex, typeId ->
                        FieldRow(
                            fieldIndex = fieldIndex,
                            currentTypeId = typeId,
                            onTypeSelected = { newType ->
                                val newList = property.state.value.map { it.deepCopy() }.toMutableList()
                                newList[pageIndex].fields[fieldIndex] = newType
                                property.state.value = newList
                            },
                            onRemoveField = {
                                val newList = property.state.value.map { it.deepCopy() }.toMutableList()
                                newList[pageIndex].fields.removeAt(fieldIndex)
                                property.state.value = newList
                            }
                        )
                    }

                    // --- Add Field Button ---
                    if (page.fields.size < 4) {
                        TextButton(
                            onClick = {
                                val newList = property.state.value.map { it.deepCopy() }.toMutableList()
                                newList[pageIndex].fields.add(0)
                                property.state.value = newList
                            }
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Field", style = MaterialTheme.typography.caption)
                        }
                    }
                }
            }
        }

        // --- Add Page Button ---
        OutlinedButton(
            onClick = {
                val newList = property.state.value.toMutableList()
                newList.add(DataFieldPage(mutableListOf(0)))
                property.state.value = newList
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.PostAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Add New Screen")
        }
    }
}