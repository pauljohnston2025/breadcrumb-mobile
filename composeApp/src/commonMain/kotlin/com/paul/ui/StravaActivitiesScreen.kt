package com.paul.ui

import androidx.activity.compose.BackHandler
import com.paul.composables.RouteMiniMap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.domain.StravaActivity
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.viewmodels.StravaActivitiesViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton as M3TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaActivitiesScreen(viewModel: StravaActivitiesViewModel, tileRepository: ITileRepository) {
    BackHandler(enabled = viewModel.sendingFile.value != "") {
        // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
    }

    val rawActivities by viewModel.activities.collectAsState(emptyList())
    val isSyncing by viewModel.isSyncing.collectAsState()
    val status by viewModel.loginStatus.collectAsState()
    val syncErrorStatus by viewModel.syncErrorStatus.collectAsState()
    val currentRange by viewModel.currentRange.collectAsState()
    val totalCount by viewModel.totalActivityCount.collectAsState(0)

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("All") }
    var showDatePicker by remember { mutableStateOf(false) }

    val sortOptions = listOf("Newest", "Oldest", "A-Z")
    var sortOrder by remember { mutableStateOf("Newest") }

    val filteredActivities = remember(rawActivities, searchQuery, selectedType, sortOrder) {
        rawActivities
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .filter {
                if (selectedType == "All") {
                    true
                } else if (selectedType == "Unknown") {
                    !StravaActivity.SUPPORTED_TYPES.contains(it.type)
                } else {
                    it.type == selectedType
                }
            }
            .sortedWith { a, b ->
                when (sortOrder) {
                    "Newest" -> b.startDate.compareTo(a.startDate)
                    "Oldest" -> a.startDate.compareTo(b.startDate)
                    "A-Z" -> a.name.lowercase().compareTo(b.name.lowercase())
                    else -> 0
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {

        // 1. Filter Row: Date Range + Sync Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                DateRangeCard(currentRange) { showDatePicker = true }
            }

            Spacer(Modifier.width(8.dp))

            androidx.compose.material3.FilledIconButton(
                onClick = { viewModel.sync() },
                enabled = !isSyncing,
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colors.primaryVariant,
                    contentColor = MaterialTheme.colors.primary
                ),
                modifier = Modifier.size(48.dp)
            ) {
                if (isSyncing) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White
                    )
                } else {
                    Icon(Icons.Default.Sync, null, tint = Color.White)
                }
            }
        }

        // 2. Status Banners (Restored)
        if (!status.isNullOrEmpty() || !syncErrorStatus.isNullOrEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                syncErrorStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        // 3. Search & Sort & Type Filters
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search activities...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                // Sort Toggle matching Routes logic
                IconButton(onClick = {
                    val nextIndex = (sortOptions.indexOf(sortOrder) + 1) % sortOptions.size
                    sortOrder = sortOptions[nextIndex]
                }) {
                    Icon(Icons.Default.Sort, null, tint = MaterialTheme.colors.primary)
                }
                Text(
                    sortOrder,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.width(60.dp)
                )

                Spacer(Modifier.width(8.dp))

                // Type Filters with Strava Icons
                val types = StravaActivity.SUPPORTED_TYPES
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(types) { type ->
                        // Determine icon for the chip
                        val icon = StravaActivity.getActivityIcon(type)

                        FilterChip(type, icon, selectedType == type) { selectedType = type }
                    }
                }
            }
        }

        // 4. Results Count
        Text(
            text = "Showing ${filteredActivities.size} of $totalCount activities",
            style = MaterialTheme.typography.caption,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        // 5. The List Card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            elevation = 2.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
            LazyColumn {
                itemsIndexed(filteredActivities, key = { _, a -> a.id }) { index, activity ->
                    StravaActivityListItem(
                        activity,
                        tileRepository,
                        { viewModel.previewActivity(activity) },
                        { viewModel.sendActivityToDevice(activity) },
                        viewModel::openActivityInStrava,
                    )
                    if (index < filteredActivities.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = Color.Gray.copy(0.2f)
                        )
                    }
                }
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

    SendingFileOverlay(
        sendingMessage = viewModel.sendingFile
    )
}

@Composable
private fun StravaActivityListItem(
    activity: StravaActivity,
    tileRepository: ITileRepository,
    onClick: () -> Unit,
    onSendClick: () -> Unit,
    openActivityInStrava: (id: Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail (Left)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = 0.1f))
        ) {
            RouteMiniMap(
                route = activity.summaryToRoute(),
                tileRepository = tileRepository,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(16.dp))

        // Info (Middle)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = activity.getActivityIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = (activity.type ?: "Activity").uppercase(),
                    style = MaterialTheme.typography.overline,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = activity.startDate.toLocalDateTime(TimeZone.currentSystemDefault())
                    .toString().replace("T", " "),
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )

            // Actions Row (Bottom Right of the info column)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(Modifier.weight(1f))

                // Preview Button
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Preview",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Play/Send Button
                IconButton(
                    onClick = onSendClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Send to Device",
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF4CAF50) // Green
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        openActivityInStrava(
                            activity.id
                        )
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "Open in Strava",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colors.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(0.5f)),
        color = if (isSelected) MaterialTheme.colors.primary else Color.Transparent,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) Color.White else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.caption,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
fun DateRangeCard(currentRange: ClosedRange<Instant>, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                val start =
                    currentRange.start.toLocalDateTime(TimeZone.currentSystemDefault()).date
                val end =
                    currentRange.endInclusive.toLocalDateTime(TimeZone.currentSystemDefault()).date
                Text(
                    "Filter by Date",
                    color = Color.Gray
                )
                Text(
                    "$start — $end",
                    color = Color.Gray
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
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
    M3ThemeWrapper {
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
}