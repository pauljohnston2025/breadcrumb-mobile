package com.paul.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
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
import com.paul.viewmodels.DataFieldPage
import com.paul.viewmodels.EditableProperty
import com.paul.viewmodels.dataFieldTypes

@Composable
fun DataFieldPagesEditor(property: EditableProperty<MutableList<DataFieldPage>>) {
    val pages by property.state

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .animateContentSize()
    ) {
        Text(
            text = property.label,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.primary
        )

        pages.forEachIndexed { pageIndex, page ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
                backgroundColor = MaterialTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // --- Page Header ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Page $pageIndex", style = MaterialTheme.typography.subtitle1)
                        IconButton(onClick = {
                            val newList = property.state.value.toMutableList()
                            newList.removeAt(pageIndex)
                            property.state.value = newList
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = Color.Red)
                        }
                    }

                    Divider(Modifier.padding(vertical = 8.dp))

                    // --- Fields List ---
                    page.fields.forEachIndexed { fieldIndex, typeId ->
                        FieldRow(
                            fieldIndex = fieldIndex,
                            currentTypeId = typeId,
                            onTypeSelected = { newType ->
                                val newList = property.state.value.toMutableList()
                                newList[pageIndex].fields[fieldIndex] = newType
                                property.state.value = newList
                            },
                            onRemoveField = {
                                val newList = property.state.value.toMutableList()
                                newList[pageIndex].fields.removeAt(fieldIndex)
                                property.state.value = newList
                            }
                        )
                    }

                    // --- Add Field Button (Limit to 4) ---
                    if (page.fields.size < 4) {
                        TextButton(
                            onClick = {
                                val newList = property.state.value.toMutableList()
                                newList[pageIndex].fields.add(0) // Default to NONE
                                property.state.value = newList
                            },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Field")
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
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add New Page")
        }
    }
}

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
            "Field ${fieldIndex + 1}",
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