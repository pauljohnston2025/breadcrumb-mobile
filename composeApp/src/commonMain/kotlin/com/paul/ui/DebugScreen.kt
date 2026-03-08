package com.paul.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.infrastructure.repositories.LogEntry
import com.paul.infrastructure.service.IFileHelper
import com.paul.viewmodels.DebugViewModel


@Composable
fun DebugScreen(viewModel: DebugViewModel, fileHelper: IFileHelper) {
    val logs by viewModel.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var filterLevel by remember { mutableStateOf<String?>(null) }

    // Selection State
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode by remember { derivedStateOf { selectedIds.isNotEmpty() } }

    val filteredLogs by remember {
        derivedStateOf {
            logs.filter {
                (filterLevel == null || it.level == filterLevel) &&
                        (searchQuery.isEmpty() || it.message.contains(
                            searchQuery,
                            ignoreCase = true
                        ))
            }.reversed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        if (isSelectionMode) {
            // Contextual Header for Selection
            SelectionHeader(
                count = selectedIds.size,
                onCopy = {
                    val textToCopy = filteredLogs
                        .filter { it.id in selectedIds }
                        .joinToString("\n") { it.toString() }
                    clipboardManager.setText(AnnotatedString(textToCopy))
                    selectedIds = emptySet()
                },
                onClose = { selectedIds = emptySet() }
            )
        } else {
            HeaderSection(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                selectedLevel = filterLevel,
                onLevelSelect = { filterLevel = it },
                onExport = { /* your export launcher */ }
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = filteredLogs, key = { it.id }) { log ->
                val isSelected = selectedIds.contains(log.id)

                LogItem(
                    log = log,
                    isSelected = isSelected,
                    onLongClick = {
                        selectedIds = selectedIds + log.id
                    },
                    onClick = {
                        if (isSelectionMode) {
                            selectedIds =
                                if (isSelected) selectedIds - log.id else selectedIds + log.id
                        }
                    }
                )
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItem(
    log: LogEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val highlightColor = when (log.level.uppercase()) {
        "ERROR" -> MaterialTheme.colors.error
        else -> MaterialTheme.colors.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(
                if (isSelected) highlightColor.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = log.level.take(1),
            color = if (isSelected) highlightColor else highlightColor.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.width(12.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = log.message,
            color = MaterialTheme.colors.onSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SelectionHeader(count: Int, onCopy: () -> Unit, onClose: () -> Unit) {
    Surface(
        elevation = 8.dp,
        color = MaterialTheme.colors.primary, // Highlight the header in selection mode
        contentColor = MaterialTheme.colors.onPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
            Text(
                text = "$count Selected",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy to Clipboard")
            }
        }
    }
}

@Composable
fun HeaderSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedLevel: String?,
    onLevelSelect: (String?) -> Unit,
    onExport: () -> Unit
) {
    val levels = listOf("VERBOSE", "DEBUG", "INFO", "WARNING", "ERROR")

    // Surface provides the background color and shadow/elevation automatically
    Surface(
        elevation = 4.dp,
        color = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // --- Search Field ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Search logs...",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colors.onSurface,
                        fontSize = 14.sp
                    ),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
                        cursorColor = MaterialTheme.colors.primary
                    )
                )

                Spacer(Modifier.width(8.dp))

                // --- Export Button (Themed to Primary) ---
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Logs",
                        tint = MaterialTheme.colors.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Filter Chips ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All" Chip
                LogChip(
                    label = "ALL",
                    isSelected = selectedLevel == null,
                    onClick = { onLevelSelect(null) }
                )

                levels.forEach { level ->
                    Spacer(Modifier.width(6.dp))
                    LogChip(
                        label = level,
                        isSelected = selectedLevel == level,
                        onClick = { onLevelSelect(level) }
                    )
                }
            }
        }
    }
}

@Composable
fun LogChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
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