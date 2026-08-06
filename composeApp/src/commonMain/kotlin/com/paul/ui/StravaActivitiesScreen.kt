package com.paul.ui

import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.TileServerRepo
import androidx.activity.compose.BackHandler
import com.paul.composables.RouteMiniMap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.domain.StravaActivity
import com.paul.domain.StravaGear
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.TileServerRepo.Companion.defaultTileServer
import com.paul.infrastructure.service.formatDistance
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
    val isImporting = viewModel.isImporting.value
    var showCancelImportDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = viewModel.sendingFile.value != "" || isSyncing || isImporting) {
        if (isSyncing) {
            viewModel.stopSync()
        } else if (isImporting) {
            showCancelImportDialog = true
        }
        // prevent back handler when we are trying to do things
    }

    val rawActivities by viewModel.activities.collectAsState(emptyList())
    val status by viewModel.loginStatus.collectAsState()
    val syncErrorStatus by viewModel.syncErrorStatus.collectAsState()
    val currentRange by viewModel.currentRange.collectAsState()
    val totalCount by viewModel.totalActivityCount.collectAsState(0L)
    val allGear by viewModel.allGear.collectAsState(emptyList())

    val isChartVisible by viewModel.isChartVisible.collectAsState()
    val chartMetric by viewModel.chartMetric.collectAsState()
    val chartInterval by viewModel.chartInterval.collectAsState()
    val selectedChartYear by viewModel.selectedChartYear.collectAsState()
    val chartData by viewModel.chartData.collectAsState()

    val gearLookup = remember(allGear) { allGear.associateBy { it.id } }
    val groupedGear = remember(allGear) { allGear.groupBy { it.name } }

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("All") }
    var selectedGearName by remember { mutableStateOf<String?>(null) } // Changed to name-based filtering
    var showDatePicker by remember { mutableStateOf(false) }

    val sortOptions = listOf("Newest", "Oldest", "A-Z")
    var sortOrder by remember { mutableStateOf("Newest") }
    val tileServer by viewModel.tileServerRepo.currentServerFlow()
        .collectAsState(TileServerRepo.defaultTileServer)

    val filteredActivities =
        remember(rawActivities, searchQuery, selectedType, selectedGearName, sortOrder, gearLookup, groupedGear) {
            val selectedGearIds = selectedGearName?.let { name -> groupedGear[name]?.map { it.id } }
            rawActivities
                .filter { 
                    it.name.contains(searchQuery, ignoreCase = true) || 
                    gearLookup[it.gearId]?.name?.contains(searchQuery, ignoreCase = true) == true
                }
                .filter {
                    if (selectedType == "All") {
                        true
                    } else if (selectedType == "Unknown") {
                        !StravaActivity.SUPPORTED_TYPES.contains(it.type)
                    } else {
                        it.type == selectedType
                    }
                }.filter {
                    selectedGearIds == null || selectedGearIds.contains(it.gearId)
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
                    viewModel.toggleChart()
                },
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isChartVisible) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
                    contentColor = if (isChartVisible) Color.White else MaterialTheme.colors.primary
                ),
                modifier = Modifier.size(36.dp).border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(18.dp))
            ) {
                Icon(
                    Icons.Default.ShowChart,
                    null,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            androidx.compose.material3.FilledIconButton(
                onClick = {
                    viewModel.importStravaData()
                },
                enabled = !isSyncing,
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colors.secondary,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.FileOpen,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            androidx.compose.material3.FilledIconButton(
                onClick = {
                    viewModel.toggleChart()
                },
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isChartVisible) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
                    contentColor = if (isChartVisible) Color.White else MaterialTheme.colors.primary
                ),
                modifier = Modifier.size(36.dp).border(1.dp, MaterialTheme.colors.primary, RoundedCornerShape(18.dp))
            ) {
                Icon(
                    Icons.Default.ShowChart,
                    null,
                    modifier = Modifier.size(18.dp)
                )
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

            if (groupedGear.isNotEmpty()) {
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
                            isSelected = selectedGearName == null
                        ) { selectedGearName = null }
                    }
                    items(groupedGear.keys.toList()) { gearName ->
                        val gearList = groupedGear[gearName] ?: emptyList()
                        val firstGear = gearList.first()
                        FilterChip(
                            label = gearName,
                            icon = StravaGear.getGearIcon(firstGear.type),
                            isSelected = selectedGearName == gearName
                        ) { selectedGearName = gearName }
                    }
                }
            }

            if (isChartVisible) {
                StravaChart(
                    data = chartData,
                    metric = chartMetric,
                    interval = chartInterval,
                    selectedYear = selectedChartYear,
                    onMetricChange = viewModel::setChartMetric,
                    onIntervalChange = viewModel::setChartInterval,
                    onYearChange = viewModel::setChartYear
                )
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
                        tileServer,
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

    if (showCancelImportDialog) {
        AlertDialog(
            onDismissRequest = { showCancelImportDialog = false },
            title = { Text("Cancel Import?") },
            text = { Text("Are you sure you want to cancel the Strava import? This will stop adding new activities.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.stopImport()
                        showCancelImportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                ) {
                    Text("Cancel Import", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelImportDialog = false }) {
                    Text("Continue Import")
                }
            }
        )
    }
}

@Composable
private fun StravaChart(
    data: Map<String, List<StravaActivitiesViewModel.ChartPoint>>,
    metric: String,
    interval: String,
    selectedYear: Int,
    onMetricChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onYearChange: (Int) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = listOf(
        Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF4CAF50),
        Color(0xFFFFEB3B), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFFFF9800), Color(0xFF795548)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (interval == "Month") "Monthly Activity ($selectedYear)" else "Yearly Activity",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    M3TextButton(
                        onClick = { onMetricChange(if (metric == "Count") "Distance" else "Count") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        M3Text(metric, fontSize = 10.sp)
                    }
                    M3TextButton(
                        onClick = { onIntervalChange(if (interval == "Month") "Year" else "Month") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        M3Text(interval, fontSize = 10.sp)
                    }
                    if (interval == "Month") {
                        M3TextButton(
                            onClick = { onYearChange(selectedYear - 1) },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.height(32.dp).width(32.dp)
                        ) { M3Text("<", fontSize = 10.sp) }
                        M3TextButton(
                            onClick = { onYearChange(selectedYear + 1) },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.height(32.dp).width(32.dp)
                        ) { M3Text(">", fontSize = 10.sp) }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.height(120.dp).fillMaxWidth()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (data.isEmpty()) return@Canvas
                    val allPoints = data.values.flatten()
                    if (allPoints.isEmpty()) return@Canvas

                    val minY = 0f
                    val maxY = allPoints.maxOf { it.y }.let { if (it == 0f) 1f else it * 1.1f }

                    val minX = if (interval == "Month") 1f else allPoints.minOf { it.x }
                    val maxX = if (interval == "Month") 12f else allPoints.maxOf { it.x }

                    val width = size.width
                    val height = size.height

                    // Draw grid lines (horizontal)
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = height - (i.toFloat() / gridLines * height)
                        drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, y), Offset(width, y), strokeWidth = 1f)

                        val labelValue = (i.toFloat() / gridLines * maxY)
                        val label = if (metric == "Distance") formatDistance(labelValue.toFloat()) else labelValue.toInt().toString()
                        drawText(
                            textMeasurer.measure(label, style = TextStyle(fontSize = 8.sp, color = Color.Gray)),
                            topLeft = Offset(2f, y - 10f)
                        )
                    }

                    data.entries.forEachIndexed { index, entry ->
                        val points = entry.value.sortedBy { it.x }
                        val color = colors[index % colors.size]

                        if (points.size >= 2) {
                            val path = Path()
                            points.forEachIndexed { pIndex, point ->
                                val x = if (maxX == minX) width / 2f else (point.x - minX) / (maxX - minX) * width
                                val y = height - (point.y / maxY * height)
                                if (pIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        // Draw dots
                        points.forEach { point ->
                             val x = if (maxX == minX) width / 2f else (point.x - minX) / (maxX - minX) * width
                             val y = height - (point.y / maxY * height)
                             drawCircle(color, radius = 3f, center = Offset(x, y))
                        }
                    }

                    // X axis labels
                    if (interval == "Month") {
                        val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
                        months.forEachIndexed { i, m ->
                            val x = (i.toFloat() / 11f) * width
                            drawText(
                                textMeasurer.measure(m, style = TextStyle(fontSize = 8.sp, color = Color.Gray)),
                                topLeft = Offset(x - 5f, height - 12f)
                            )
                        }
                    } else if (maxX != minX) {
                         val yearRange = (maxX - minX).toInt()
                         if (yearRange > 0) {
                             for (i in 0..yearRange) {
                                 val x = (i.toFloat() / yearRange) * width
                                 drawText(
                                     textMeasurer.measure((minX + i).toInt().toString(), style = TextStyle(fontSize = 8.sp, color = Color.Gray)),
                                     topLeft = Offset(x - 10f, height - 12f)
                                 )
                             }
                         }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(data.keys.toList()) { index, gearName ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(colors[index % colors.size], RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text(gearName, style = MaterialTheme.typography.caption, fontSize = 9.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun StravaActivityListItem(
    activity: StravaActivity,
    gear: StravaGear?,
    tileRepository: ITileRepository,
    onClick: () -> Unit,
    onSendClick: () -> Unit,
    openActivityInStrava: (id: Long) -> Unit,
    tileServer: TileServerInfo,
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
                modifier = Modifier.fillMaxSize(),
                tileServer,
                onClick = onClick,
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

            // Bottom Row: Date/Distance on left, Actions on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dist: ${formatDistance(activity.distance)}",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray.copy(0.7f)
                    )
                    Text(
                        text = activity.startDate.toLocalDateTime(TimeZone.currentSystemDefault())
                            .toString().replace("T", " ").substring(0, 16),
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray.copy(0.7f)
                    )
                }

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
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            val start = currentRange.start.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val end = currentRange.endInclusive.toLocalDateTime(TimeZone.currentSystemDefault()).date
            Text(
                text = "$start — $end",
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
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