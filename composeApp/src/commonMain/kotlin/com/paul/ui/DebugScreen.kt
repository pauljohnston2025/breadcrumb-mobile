package com.paul.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.infrastructure.repositories.LogEntry
import com.paul.infrastructure.service.IFileHelper
import com.paul.viewmodels.DebugViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch


@Composable
fun DebugScreen(viewModel: DebugViewModel, fileHelper: IFileHelper) {
    val logs by viewModel.logs.collectAsState()
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var filterLevel by remember { mutableStateOf<String?>(null) }

    // Efficient filtering logic
    val filteredLogs by remember {
        derivedStateOf {
            logs.filter {
                (filterLevel == null || it.level == filterLevel) &&
                        (searchQuery.isEmpty() || it.message.contains(
                            searchQuery,
                            ignoreCase = true
                        ))
            }.reversed() // Latest logs at the top
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    val success =
                        fileHelper.writeFile(it.toString(), logs.map { l -> l.toString() })
                    if (success) {
                        // Optional: Show success Toast
                    }
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background) // Main background
    ) {
        // --- Search & Filter Header ---
        HeaderSection(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            selectedLevel = filterLevel,
            onLevelSelect = { filterLevel = it },
            onExport = {
                // This opens the system "Save As" dialog
                saveLauncher.launch("logs_${System.currentTimeMillis()}.txt")
            }
        )

        // The list area
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = filteredLogs, key = { it.id }) { log ->
                LogItem(log)
                // Use the same divider color logic as the OutlinedTextField border
                Spacer(
                    Modifier
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                )
            }
        }
    }
}


@Composable
fun LogItem(log: LogEntry) {
    val contentColor = MaterialTheme.colors.onSurface

    val severityColor = when (log.level.uppercase()) {
        "ERROR" -> MaterialTheme.colors.error
        "WARNING" -> Color(0xFFFBC02D)
        "INFO" -> MaterialTheme.colors.secondary
        "DEBUG" -> contentColor.copy(alpha = 0.5f)
        else -> contentColor.copy(alpha = 0.3f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(severityColor.copy(alpha = 0.03f)) // Even subtler background
            .padding(horizontal = 8.dp, vertical = 4.dp), // Reduced vertical padding
        verticalAlignment = Alignment.Top
    ) {
        // 1. Thin Marker (Fixed width, very compact)
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp) // Shorter marker
                .padding(top = 2.dp)
                .background(severityColor, RoundedCornerShape(1.dp))
        )

        Spacer(Modifier.width(8.dp))

        // 2. The Log Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Short Level Tag (e.g., "ERR", "INF")
                Text(
                    text = log.level.take(3).uppercase(),
                    color = severityColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )

                if (!log.tag.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "[$log.tag]",
                        color = contentColor.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. The Message
            Text(
                text = log.message,
                color = contentColor.copy(alpha = 0.9f),
                fontSize = 11.sp, // Slightly smaller font for compactness
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
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