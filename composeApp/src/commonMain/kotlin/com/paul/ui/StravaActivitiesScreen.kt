package com.paul.ui

import androidx.activity.compose.BackHandler
import com.paul.composables.RouteMiniMap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.domain.StravaActivity
import com.paul.domain.StravaGear
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.viewmodels.StravaActivitiesViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.text.substringBefore
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton as M3TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaActivitiesScreen(viewModel: StravaActivitiesViewModel, tileRepository: ITileRepository) {
    val isSyncing by viewModel.isSyncing.collectAsState()

    BackHandler(enabled = viewModel.sendingFile.value != "" || isSyncing) {
        if (isSyncing) {
            viewModel.stopSync()
        }
        // prevent back handler when we are trying to do things
    }

    val rawActivities by viewModel.activities.collectAsState(emptyList())
    val status by viewModel.loginStatus.collectAsState()
    val syncErrorStatus by viewModel.syncErrorStatus.collectAsState()
    val currentRange by viewModel.currentRange.collectAsState()
    val totalCount by viewModel.totalActivityCount.collectAsState(0)
    val allGear by viewModel.allGear.collectAsState(emptyList())
    val gearLookup = remember(allGear) { allGear.associateBy { it.id } }

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("All") }
    var selectedGearId by remember { mutableStateOf<String?>(null) } // New: State for filtering
    var showDatePicker by remember { mutableStateOf(false) }

    val sortOptions = listOf("Newest", "Oldest", "A-Z")
    var sortOrder by remember { mutableStateOf("Newest") }

    val filteredActivities =
        remember(rawActivities, searchQuery, selectedType, selectedGearId, sortOrder) {
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
                }.filter {
                    selectedGearId == null || it.gearId == selectedGearId
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

        // 1. Filter Row: Date Range + Sync Button (More Compact)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp), // Reduced vertical padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                // Reduced height for DateRangeCard via internal padding if possible
                DateRangeCard(currentRange) { showDatePicker = true }
            }

            Spacer(Modifier.width(8.dp))

            androidx.compose.material3.FilledIconButton(
                onClick = {
                    if (isSyncing) viewModel.stopSync() else viewModel.sync()
                },
                enabled = true,
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isSyncing) MaterialTheme.colors.error else MaterialTheme.colors.primaryVariant,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(36.dp) // Reduced from 48.dp
            ) {
                if (isSyncing) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Sync,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

// 2. Status Banners (Tighter)
        if (!status.isNullOrEmpty() || !syncErrorStatus.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.primary
                        )
                    }
                    syncErrorStatus?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.overline,
                            color = MaterialTheme.colors.error
                        )
                    }
                }
            }
        }

        // 3. Search & Sort & Type Filters (Much more compact)
        Column(Modifier.padding(vertical = 4.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(36.dp) // Professional slim height
                    .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.LightGray.copy(0.5f), RoundedCornerShape(8.dp)),
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colors.onSurface),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.Gray
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search...",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            )

            // Sort & Type Filter Row (Unified)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp)
            ) {
                // Sort Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val nextIndex = (sortOptions.indexOf(sortOrder) + 1) % sortOptions.size
                            sortOrder = sortOptions[nextIndex]
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Sort,
                        null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        sortOrder,
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(12.dp)
                )

                // Type Filters
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(StravaActivity.SUPPORTED_TYPES) { type ->
                        FilterChip(
                            type,
                            StravaActivity.getActivityIcon(type),
                            selectedType == type
                        ) {
                            selectedType = type
                        }
                    }
                }
            }

            if (allGear.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    item {
                        FilterChip(
                            label = "All Gear",
                            icon = Icons.Default.AllInclusive,
                            isSelected = selectedGearId == null
                        ) { selectedGearId = null }
                    }
                    items(allGear) { gear ->
                        FilterChip(
                            label = gear.name,
                            icon = StravaGear.getGearIcon(gear.type),
                            isSelected = selectedGearId == gear.id
                        ) { selectedGearId = gear.id }
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
                    val gear = gearLookup[activity.gearId] // Find the gear object
                    StravaActivityListItem(
                        activity,
                        gear,
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
    gear: StravaGear?,
    tileRepository: ITileRepository,
    onClick: () -> Unit,
    onSendClick: () -> Unit,
    openActivityInStrava: (id: Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Smaller Thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Gray.copy(alpha = 0.1f))
        ) {
            RouteMiniMap(
                route = activity.summaryToRoute(),
                tileRepository = tileRepository,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(12.dp))

        // 2. Info Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.subtitle2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Combined Metadata Row (Type + Gear)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = activity.getActivityIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = (activity.type ?: "Activity").uppercase(),
                    style = MaterialTheme.typography.overline,
                    color = Color.Gray
                )

                if (gear != null) {
                    Text(
                        " • ",
                        color = Color.Gray.copy(0.5f),
                        style = MaterialTheme.typography.overline
                    )
                    Icon(
                        imageVector = StravaGear.getGearIcon(gear.type),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colors.primary.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (gear.name.length > 15) gear.name.take(15) + "..." else gear.name,
                        style = MaterialTheme.typography.overline,
                        color = Color.Gray
                    )
                }
            }

            // Bottom Row: Date on left, Actions on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activity.startDate.toLocalDateTime(TimeZone.currentSystemDefault())
                        .toString(),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray.copy(0.7f)
                )

                Spacer(Modifier.weight(1f))

                // Action Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colors.primary.copy(0.7f)
                        )
                    }
                    IconButton(onClick = onSendClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    IconButton(
                        onClick = { openActivityInStrava(activity.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
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