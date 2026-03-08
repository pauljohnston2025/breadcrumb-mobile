package com.paul.ui

import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

@Composable
fun DebugScreen(viewModel: DebugViewModel, fileHelper: IFileHelper) {
    val logs by viewModel.logs.collectAsState()
    val isDescending by viewModel.isDescending.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    var filterLevel by remember { mutableStateOf<String?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val isSelectionMode by remember { derivedStateOf { selectedIds.isNotEmpty() } }

    val filteredLogs by remember {
        derivedStateOf {
            val filtered = logs.filter {
                (filterLevel == null || it.level == filterLevel) &&
                        (searchQuery.isEmpty() || it.message.contains(
                            searchQuery,
                            ignoreCase = true
                        ))
            }
            if (isDescending) filtered.reversed() else filtered
        }
    }

    // Auto-scroll logic: Scroll to top (newest) when logs change,
    // but only if the user is already near the top.
    LaunchedEffect(logs.size) {
        if (isDescending && logs.isNotEmpty() && !listState.isScrollInProgress) {
            if (listState.firstVisibleItemIndex <= 1) {
                listState.animateScrollToItem(0)
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    fileHelper.writeFile(it.toString(), logs.map { l -> l.toString() })
                }
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            if (listState.firstVisibleItemIndex > 5) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Jump to Newest")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colors.background)
        ) {
            if (isSelectionMode) {
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
                    isDescending = isDescending,
                    onSortToggle = { viewModel.toggleSort() },
                    onClear = { viewModel.clear() },
                    onExport = { saveLauncher.launch("logs_${System.currentTimeMillis()}.txt") }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).background(MaterialTheme.colors.surface)
            ) {
                items(items = filteredLogs, key = { it.id }) { log ->
                    val isSelected = selectedIds.contains(log.id)

                    LogItem(
                        log = log,
                        isSelected = isSelected,
                        onLongClick = { selectedIds = selectedIds + log.id },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItem(
    log: LogEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Map colors to your theme, similar to how we did for Routes
    val severityColor = when (log.level.uppercase()) {
        "ERROR" -> MaterialTheme.colors.error
        "WARNING" -> Color(0xFFFBC02D) // A balanced amber/gold
        else -> MaterialTheme.colors.primary
    }

    // Full ISO-8601 Date Formatting
    val timeStr = remember(log.timestamp) {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(log.timestamp)
        val localDateTime = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        // Formats to: YYYY-MM-DD HH:mm:ss
        "${localDateTime.date} ${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}:${localDateTime.second.toString().padStart(2, '0')}"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (isSelected) severityColor.copy(alpha = 0.12f) else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Timestamp - Subtle and gray like the "Size" in Routes
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(Modifier.width(8.dp))

                // Level "Badge" - matching the "RouteType" overline style
                Text(
                    text = log.level.uppercase(),
                    style = MaterialTheme.typography.overline,
                    color = severityColor,
                    fontWeight = FontWeight.Bold
                )

                if (!log.tag.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "• ${log.tag}",
                        style = MaterialTheme.typography.overline,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // Message - Clean and themed like the Route Name
            Text(
                text = log.message,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
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
    isDescending: Boolean,
    onSortToggle: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    val levels = listOf("VERBOSE", "DEBUG", "INFO", "WARNING", "ERROR")

    Surface(
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search logs...", fontSize = 14.sp) },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colors.onSurface, fontSize = 14.sp),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f)
                    )
                )

                IconButton(onClick = onSortToggle) {
                    Icon(
                        if (isDescending) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignTop,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colors.primary
                    )
                }

                IconButton(onClick = onExport) {
                    Icon(Icons.Default.Share, "Export", tint = MaterialTheme.colors.primary)
                }

                IconButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteSweep, "Clear", tint = MaterialTheme.colors.error)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogChip(label = "ALL", isSelected = selectedLevel == null) { onLevelSelect(null) }
                levels.forEach { level ->
                    Spacer(Modifier.width(6.dp))
                    LogChip(label = level, isSelected = selectedLevel == level) {
                        onLevelSelect(
                            level
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionHeader(count: Int, onCopy: () -> Unit, onClose: () -> Unit) {
    Surface(
        elevation = 8.dp,
        color = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") }
            Text(
                "$count Selected",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
        }
    }
}

@Composable
fun LogChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.15f)
    else MaterialTheme.colors.onSurface.copy(alpha = 0.05f)
    val contentColor = if (isSelected) MaterialTheme.colors.primary
    else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)

    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colors.primary else Color.Transparent
        )
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