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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.text.substringBefore
import androidx.compose.material3.Text as M3Text
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaActivitiesScreen(viewModel: StravaActivitiesViewModel, tileRepository: ITileRepository) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
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
        val chartScale by viewModel.chartScale.collectAsState()
        val selectedGearChart by viewModel.selectedGearChart.collectAsState()
        val selectedChartYear by viewModel.selectedChartYear.collectAsState()
        val yearRangeEnd by viewModel.yearRangeEnd.collectAsState()
        val chartData by viewModel.chartData.collectAsState()
        val chartYearBounds by viewModel.chartYearBounds.collectAsState()

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

        if (isChartVisible && isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .padding(8.dp)
            ) {
                StravaChart(
                    data = chartData,
                    allGear = allGear,
                    metric = chartMetric,
                    interval = chartInterval,
                    scale = chartScale,
                    selectedGearChart = selectedGearChart,
                    selectedYear = selectedChartYear,
                    yearRangeEnd = yearRangeEnd,
                    yearBounds = chartYearBounds,
                    onMetricChange = viewModel::setChartMetric,
                    onIntervalChange = viewModel::setChartInterval,
                    onScaleChange = viewModel::setChartScale,
                    onGearSelect = viewModel::selectGearChart,
                    onYearChange = viewModel::setChartYear,
                    onYearRangeShift = viewModel::shiftYearRange,
                    isFullScreen = true,
                    onClose = { viewModel.toggleChart() }
                )
            }
        } else {
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
                        DateRangeCard(currentRange) { showDatePicker = true }
                    }

                    Spacer(Modifier.width(8.dp))

                    androidx.compose.material3.FilledIconButton(
                        onClick = {
                            viewModel.toggleChart()
                        },
                        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isChartVisible) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(36.dp)
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
                                    label = type,
                                    icon = StravaActivity.getActivityIcon(type),
                                    isSelected = selectedType == type,
                                    onClick = { selectedType = type }
                                )
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
                                    isSelected = selectedGearName == null,
                                    onClick = { selectedGearName = null }
                                )
                            }
                            items(groupedGear.keys.toList()) { gearName ->
                                val gearList = groupedGear[gearName] ?: emptyList()
                                val firstGear = gearList.first()
                                FilterChip(
                                    label = gearName,
                                    icon = StravaGear.getGearIcon(firstGear.type),
                                    isSelected = selectedGearName == gearName,
                                    onClick = { selectedGearName = gearName }
                                )
                            }
                        }
                    }

                    if (isChartVisible) {
                        StravaChart(
                            data = chartData,
                            allGear = allGear,
                            metric = chartMetric,
                            interval = chartInterval,
                            scale = chartScale,
                            selectedGearChart = selectedGearChart,
                            selectedYear = selectedChartYear,
                            yearRangeEnd = yearRangeEnd,
                            yearBounds = chartYearBounds,
                            onMetricChange = viewModel::setChartMetric,
                            onIntervalChange = viewModel::setChartInterval,
                            onScaleChange = viewModel::setChartScale,
                            onGearSelect = viewModel::selectGearChart,
                            onYearChange = viewModel::setChartYear,
                            onYearRangeShift = viewModel::shiftYearRange,
                            isFullScreen = false
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
}

@Composable
private fun StravaChart(
    data: Map<String, List<StravaActivitiesViewModel.ChartPoint>>,
    allGear: List<StravaGear>,
    metric: String,
    interval: String,
    scale: String,
    selectedGearChart: String?,
    selectedYear: Int,
    yearRangeEnd: Int,
    yearBounds: Pair<Int, Int>,
    onMetricChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onScaleChange: (String) -> Unit,
    onGearSelect: (String?) -> Unit,
    onYearChange: (Int) -> Unit,
    onYearRangeShift: (Int) -> Unit,
    isFullScreen: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = listOf(
        Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF4CAF50),
        Color(0xFFFFEB3B), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFFFF9800), Color(0xFF795548)
    )
    // Create a stable mapping from gear name to color using ALL gears
    val allUniqueGearNames = remember(allGear) { 
        (allGear.map { it.name }.distinct() + "No Gear").sorted() 
    }
    val gearColorMap = remember(allUniqueGearNames) {
        allUniqueGearNames.associateWith { name ->
            if (name == "No Gear") Color.Gray
            else colors[allUniqueGearNames.indexOf(name) % colors.size]
        }
    }

    val primaryColor = MaterialTheme.colors.primary
    val labelColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)

    var hoveredX by remember { mutableStateOf<Float?>(null) }

    Card(
        modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isFullScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (interval == "Month") "Monthly Activity ($selectedYear)" else "Yearly Activity",
                        style = MaterialTheme.typography.h6,
                        color = primaryColor
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onMetricChange(if (metric == "Count") "Distance" else "Count") }) {
                            Text(metric, fontSize = 11.sp, color = primaryColor)
                        }
                        TextButton(onClick = { onIntervalChange(if (interval == "Month") "Year" else "Month") }) {
                            Text(interval, fontSize = 11.sp, color = primaryColor)
                        }
                        TextButton(onClick = { onScaleChange(if (scale == "Linear") "Log" else "Linear") }) {
                            Text(scale, fontSize = 11.sp, color = primaryColor)
                        }
                        if (interval == "Month") {
                            IconButton(
                                onClick = { onYearChange(selectedYear - 1) },
                                enabled = selectedYear > yearBounds.first
                            ) {
                                Icon(Icons.Default.ChevronLeft, null, tint = if (selectedYear > yearBounds.first) primaryColor else labelColor)
                            }
                            IconButton(
                                onClick = { onYearChange(selectedYear + 1) },
                                enabled = selectedYear < yearBounds.second
                            ) {
                                Icon(Icons.Default.ChevronRight, null, tint = if (selectedYear < yearBounds.second) primaryColor else labelColor)
                            }
                        } else {
                            IconButton(onClick = { onYearRangeShift(-1) }) {
                                Icon(Icons.Default.ChevronLeft, null, tint = primaryColor)
                            }
                            IconButton(onClick = { onYearRangeShift(1) }) {
                                Icon(Icons.Default.ChevronRight, null, tint = primaryColor)
                            }
                        }
                        if (onClose != null) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.Close, null, tint = labelColor)
                            }
                        }
                    }
                }
            } else {
                // Portrait mode: 2 rows for controls
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (interval == "Month") "$selectedYear" else "Activity",
                            style = MaterialTheme.typography.subtitle2,
                            color = primaryColor
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onMetricChange(if (metric == "Count") "Distance" else "Count") },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(metric, fontSize = 10.sp, color = primaryColor)
                            }
                            TextButton(
                                onClick = { onIntervalChange(if (interval == "Month") "Year" else "Month") },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(interval, fontSize = 10.sp, color = primaryColor)
                            }
                            TextButton(
                                onClick = { onScaleChange(if (scale == "Linear") "Log" else "Linear") },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(scale, fontSize = 10.sp, color = primaryColor)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (interval == "Month") {
                            IconButton(
                                onClick = { onYearChange(selectedYear - 1) },
                                modifier = Modifier.size(32.dp),
                                enabled = selectedYear > yearBounds.first
                            ) {
                                Icon(Icons.Default.ChevronLeft, null, tint = if (selectedYear > yearBounds.first) primaryColor else labelColor, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                text = "Year: $selectedYear",
                                style = MaterialTheme.typography.caption,
                                color = labelColor
                            )
                            IconButton(
                                onClick = { onYearChange(selectedYear + 1) },
                                modifier = Modifier.size(32.dp),
                                enabled = selectedYear < yearBounds.second
                            ) {
                                Icon(Icons.Default.ChevronRight, null, tint = if (selectedYear < yearBounds.second) primaryColor else labelColor, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            IconButton(
                                onClick = { onYearRangeShift(-1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { onYearRangeShift(1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(data, interval) {
                            detectTapGestures { offset: Offset ->
                                // Sensitivity: Only if clicked near the bottom (labels area)
                                if (interval == "Year" && offset.y > size.height - 30.dp.toPx()) {
                                    val displayedData = if (selectedGearChart != null) {
                                        data.filterKeys { it == selectedGearChart }
                                    } else data
                                    val allPoints = displayedData.values.flatten()
                                    if (allPoints.isNotEmpty()) {
                                        val minXValue = allPoints.minOf { it.x }
                                        val maxXValue = allPoints.maxOf { it.x }
                                        if (maxXValue != minXValue) {
                                            val yearRangeValue = (maxXValue - minXValue).toInt()
                                            // Adjusted for padding
                                            val leftPadding = 40.dp.toPx()
                                            val chartWidth = size.width - leftPadding
                                            val xPosValue = (offset.x - leftPadding) / chartWidth
                                            val clickedYear = (minXValue + (xPosValue * yearRangeValue)).roundToInt()
                                            onYearChange(clickedYear)
                                            onIntervalChange("Month")
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(data) {
                            detectDragGestures(
                                onDragStart = { offset: Offset -> hoveredX = offset.x },
                                onDrag = { change, _ -> hoveredX = change.position.x },
                                onDragEnd = { hoveredX = null },
                                onDragCancel = { hoveredX = null }
                            )
                        }
                ) {
                    if (data.isEmpty()) return@Canvas
                    val displayedData = if (selectedGearChart != null) {
                        data.filterKeys { it == selectedGearChart }
                    } else data

                    val allPoints = displayedData.values.flatten()
                    if (allPoints.isEmpty()) return@Canvas

                    fun transform(y: Float): Float = if (scale == "Log") log10(y + 1f) else y
                    fun inverse(ty: Float): Float = if (scale == "Log") 10f.pow(ty) - 1f else ty

                    val transformedMaxY = allPoints.maxOf { transform(it.y) }.let { if (it == 0f) 1f else it * 1.1f }

                    val minX = if (interval == "Month") 1f else allPoints.minOf { it.x }
                    val maxX = if (interval == "Month") 12f else allPoints.maxOf { it.x }

                    // PADDING
                    val leftPadding = 40.dp.toPx()
                    val bottomPadding = 20.dp.toPx()
                    val chartWidth = size.width - leftPadding
                    val chartHeight = size.height - bottomPadding

                    val labelStyle = TextStyle(fontSize = 8.sp, color = labelColor)

                    // Draw grid lines (horizontal)
                    val gridLines = if (isFullScreen) 8 else 4
                    for (i in 0..gridLines) {
                        val ty = i.toFloat() / gridLines * transformedMaxY
                        val y = chartHeight - (ty / transformedMaxY * chartHeight)
                        drawLine(Color.LightGray.copy(alpha = 0.3f), Offset(leftPadding, y), Offset(size.width, y), strokeWidth = 1f)

                        val rawValue = inverse(ty)
                        val label = if (metric == "Distance") formatDistance(rawValue) else rawValue.toInt().toString()
                        drawText(
                            textMeasurer.measure(label, style = labelStyle),
                            topLeft = Offset(2f, y - 10f)
                        )
                    }

                    data.entries.forEach { entry ->
                        if (selectedGearChart != null && entry.key != selectedGearChart) return@forEach
                        val points = entry.value.sortedBy { it.x }
                        val color = gearColorMap[entry.key] ?: Color.Gray

                        if (points.size >= 2) {
                            val path = Path()
                            points.forEachIndexed { pIndex, point ->
                                val x = if (maxX == minX) leftPadding + chartWidth / 2f else leftPadding + (point.x - minX) / (maxX - minX) * chartWidth
                                val y = chartHeight - (transform(point.y) / transformedMaxY * chartHeight)
                                if (pIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = if (isFullScreen) 5f else 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        // Draw dots
                        points.forEach { point ->
                             val x = if (maxX == minX) leftPadding + chartWidth / 2f else leftPadding + (point.x - minX) / (maxX - minX) * chartWidth
                             val y = chartHeight - (transform(point.y) / transformedMaxY * chartHeight)
                             drawCircle(color, radius = if (isFullScreen) 5f else 3f, center = Offset(x, y))
                        }
                    }

                    // X axis labels
                    if (interval == "Month") {
                        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        months.forEachIndexed { i, m ->
                            val label = if (isFullScreen) m else m.take(1)
                            val x = leftPadding + (i.toFloat() / 11f) * chartWidth
                            drawText(
                                textMeasurer.measure(label, style = labelStyle),
                                topLeft = Offset(x - 10f, size.height - 12f)
                            )
                        }
                    } else if (maxX != minX) {
                         val yearRangeValue = (maxX - minX).toInt()
                         if (yearRangeValue > 0) {
                             for (i in 0..yearRangeValue) {
                                 val x = leftPadding + (i.toFloat() / yearRangeValue) * chartWidth
                                 drawText(
                                     textMeasurer.measure((minX + i).toInt().toString(), style = labelStyle),
                                     topLeft = Offset(x - 10f, size.height - 12f)
                                 )
                             }
                         }
                    }

                    // Tooltip / Crosshair
                    hoveredX?.let { hX ->
                        if (hX < leftPadding) return@let
                        val currentXValue = if (maxX == minX) minX else minX + ((hX - leftPadding) / chartWidth) * (maxX - minX)
                        val xValRounded = currentXValue.roundToInt().toFloat().coerceIn(minX, maxX)
                        val xPos = if (maxX == minX) leftPadding + chartWidth / 2f else leftPadding + (xValRounded - minX) / (maxX - minX) * chartWidth

                        drawLine(primaryColor.copy(alpha = 0.8f), Offset(xPos, 0f), Offset(xPos, chartHeight), strokeWidth = 2f)

                        val tooltipItems = mutableListOf<Pair<String, Color?>>()
                        if (interval == "Month") {
                             val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                             tooltipItems.add((months.getOrNull(xValRounded.toInt() - 1) ?: "") to null)
                        } else {
                             tooltipItems.add(xValRounded.toInt().toString() to null)
                        }

                        data.entries.forEach { entry ->
                            if (selectedGearChart != null && entry.key != selectedGearChart) return@forEach
                            val point = entry.value.find { it.x == xValRounded }
                            if (point != null && point.y > 0) {
                                val valStr = if (metric == "Distance") formatDistance(point.y) else point.y.toInt().toString()
                                tooltipItems.add("${entry.key}: $valStr" to gearColorMap[entry.key])
                            }
                        }

                        if (tooltipItems.size > 1) {
                            val tooltipContent = buildAnnotatedString {
                                tooltipItems.forEachIndexed { index, (text, color) ->
                                    if (color != null) {
                                        withStyle(SpanStyle(color = color)) {
                                            append("● ")
                                        }
                                        append(text)
                                    } else {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(text)
                                        }
                                    }
                                    if (index < tooltipItems.lastIndex) append("\n")
                                }
                            }
                            
                            val measuredTooltip = textMeasurer.measure(tooltipContent, style = TextStyle(fontSize = 10.sp, color = Color.White))
                            val tooltipWidth = measuredTooltip.size.width + 16f
                            val tooltipHeight = measuredTooltip.size.height + 16f
                            var tx = xPos + 16f
                            if (tx + tooltipWidth > size.width) tx = xPos - tooltipWidth - 16f
                            val ty = 16f

                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.85f),
                                topLeft = Offset(tx, ty),
                                size = androidx.compose.ui.geometry.Size(tooltipWidth, tooltipHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                            )
                            drawText(measuredTooltip, topLeft = Offset(tx + 8f, ty + 8f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(data.keys.toList().sorted()) { _, gearName ->
                    val isSelected = selectedGearChart == gearName
                    val color = gearColorMap[gearName] ?: Color.Gray
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onGearSelect(gearName) }
                            .background(if (isSelected) primaryColor.copy(alpha = 0.1f) else Color.Transparent)
                            .padding(4.dp)
                    ) {
                        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = gearName,
                            style = MaterialTheme.typography.caption,
                            fontSize = 9.sp,
                            color = if (isSelected) primaryColor else labelColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
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
