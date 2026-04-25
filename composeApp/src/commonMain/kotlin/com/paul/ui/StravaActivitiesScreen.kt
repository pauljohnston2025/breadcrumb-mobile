package com.paul.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paul.domain.StravaActivity
import com.paul.viewmodels.StravaActivitiesViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton as M3TextButton

@Composable
fun StravaActivitiesScreen(viewModel: StravaActivitiesViewModel) {
    val activities by viewModel.activities.collectAsState(emptyList())
    val isSyncing by viewModel.isSyncing.collectAsState()
    val status by viewModel.loginStatus.collectAsState()
    val currentRange by viewModel.currentRange.collectAsState()
    val totalCount by viewModel.totalActivityCount.collectAsState(0)
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Strava Activities", style = MaterialTheme.typography.h5)
                // Activity Count Summary
                Text(
                    text = "Showing ${activities.size} of $totalCount total activities",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            IconButton(onClick = { viewModel.sync() }, enabled = !isSyncing) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync")
                }
            }
        }

        // --- Sync Status / Progress Overlay ---
        if (isSyncing || status?.contains("Complete") == false) {
            Card(
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                elevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSyncing) {
                        LinearProgressIndicator(modifier = Modifier.width(40.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = status ?: "Ready to sync",
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- Date Range Selector ---
        Card(
            elevation = 2.dp,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { showDatePicker = true }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colors.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    val start =
                        currentRange.start.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val end =
                        currentRange.endInclusive.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    Text("Filter Range", style = MaterialTheme.typography.caption)
                    Text("$start to $end", style = MaterialTheme.typography.body1)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- List ---
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(activities, key = { it.id }) { activity ->
                StravaActivityItem(activity, { viewModel.previewActivity(activity) })
            }
        }
    }

    if (showDatePicker) {
        StravaDateRangePicker(
            initialRange = currentRange,
            onDismiss = { showDatePicker = false },
            onDateRangeSelected = { start, end ->
                viewModel.setDateRange(start, end)
                showDatePicker = false
            }
        )
    }
}

@Composable
fun StravaActivityItem(activity: StravaActivity, onPreviewClick: () -> Unit) {
    Card(
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onPreviewClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stravaOrange = Color(0xFFFC4C02)

                Icon(
                    imageVector = Icons.Default.DirectionsBike,
                    contentDescription = null,
                    tint = stravaOrange
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val dateText = activity.startDate
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date.toString()

                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }

                if (activity.map?.summaryPolyline != null) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Has Map Data",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Map Preview Button
            if (activity.map?.summaryPolyline != null) {
                IconButton(onClick = onPreviewClick) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "View on Map",
                        tint = MaterialTheme.colors.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaDateRangePicker(
    initialRange: ClosedRange<Instant>,
    onDismiss: () -> Unit,
    onDateRangeSelected: (Instant, Instant) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange.start.toEpochMilliseconds(),
        initialSelectedEndDateMillis = initialRange.endInclusive.toEpochMilliseconds()
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            M3TextButton(onClick = {
                val start = dateRangePickerState.selectedStartDateMillis
                val end = dateRangePickerState.selectedEndDateMillis
                if (start != null && end != null) {
                    onDateRangeSelected(
                        Instant.fromEpochMilliseconds(start),
                        Instant.fromEpochMilliseconds(end)
                    )
                }
            }) { M3Text("Confirm") }
        },
        dismissButton = {
            M3TextButton(onClick = onDismiss) { M3Text("Cancel") }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            modifier = Modifier.height(400.dp)
        )
    }
}