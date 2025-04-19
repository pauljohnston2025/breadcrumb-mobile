package com.paul.ui

import androidx.compose.material.Text
import com.paul.infrastructure.repositories.LogEntry
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.viewmodels.DebugViewModel
import io.github.aakira.napier.LogLevel


@Composable
fun DebugScreen(viewModel: DebugViewModel) {
    val logs by viewModel.logs.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Optional: Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            // Only scroll if the user isn't actively scrolled up
            // (Check if the last visible item is close to the actual last item)
            if (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == logs.size - 2) {
                listState.animateScrollToItem(logs.size - 1)
            }
            // Or always scroll:
            // listState.animateScrollToItem(logs.size - 1)
        }
    }

    // Remember to cancel the VM scope if it's manually managed
    // DisposableEffect(Unit) { onDispose { viewModel.onCleared() } } // Use only if scope needs manual cleanup
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp), // Add some horizontal padding
        state = listState
    ) {
        items(
            items = logs,
            key = { it.id }) { logEntry -> // Use a stable key
            LogItem(logEntry)
            Divider(color = Color.Gray.copy(alpha = 0.2f)) // Optional divider
        }
    }
}

@Composable
fun LogItem(logEntry: LogEntry) {
    val textColor = when (logEntry.level) {
        LogLevel.VERBOSE.name -> Color.Gray
        LogLevel.DEBUG.name -> Color.Blue.copy(alpha = 0.8f)
        LogLevel.INFO.name -> Color.Green.copy(alpha = 0.8f)
        LogLevel.WARNING.name -> Color.Magenta.copy(red = 0.8f, green = 0.5f) // Orange-ish
        LogLevel.ERROR.name, LogLevel.ASSERT.name -> Color.Red
        else -> Color.Black
    }

    Text(
        text = logEntry.toString(), // Use the formatted string from LogEntry
        color = textColor,
        fontSize = 11.sp, // Smaller font size for logs
        fontFamily = FontFamily.Monospace, // Monospace looks good for logs
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
