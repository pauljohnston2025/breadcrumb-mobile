package com.paul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.paul.composables.LoadingOverlay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.paul.domain.IqDevice
import com.paul.viewmodels.DeviceSelector as DeviceSelectorViewModel

// Renamed to follow convention (Screen suffix)
@Composable
fun DeviceSelector(
    // Pass the refactored ViewModel
    viewModel: DeviceSelectorViewModel,
    navController: NavHostController,
    selectingDevice: Boolean
) {
    // --- Back Handler ---
    BackHandler {
        // Decide what back means here. Just pop? Notify ViewModel?
        // viewModel.onBackPressed() // Example if ViewModel needs to know
        navController.popBackStack()
    }

    val devicesList = viewModel.devicesFlow().collectAsState(initial = listOf())

    // Box enables stacking the loading overlay on top
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
            // Add overall padding for content if desired
            // .padding(16.dp)
        ) {
            AnimatedVisibility(
                visible = selectingDevice
            ) {
                Text(
                    text = "Please choose a device",
                    style = MaterialTheme.typography.h6,
                    // Adjust color for contrast against overlay background if needed
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            // --- Main Content: Device List ---
            DeviceListContent(
                devices = devicesList,
                onDeviceSelected = { viewModel.onDeviceSelected(it) }, // Select action
                onSettingsClicked = { viewModel.openDeviceSettings(it) }, // Settings action
                selectingDevice,
            )
        }

        // --- Loading Overlay ---
        // Draws on top when isLoadingSettings is true
        LoadingOverlay(
            isLoading = viewModel.settingsLoading.value,
            loadingText = "Loading Settings From Device.\nEnsure an activity with the datafield is running (or at least open) or this will fail."
        )
    }
}

// --- Extracted Content Composables ---

@Composable
private fun DeviceListContent(
    devices: State<List<IqDevice>>,
    onDeviceSelected: (IqDevice) -> Unit,
    onSettingsClicked: (IqDevice) -> Unit,
    selectingDevice: Boolean,
) {
    if (devices.value.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Searching for devices...") // Or "No devices found" after a timeout
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp) // Padding around the list
        ) {
            items(
                devices.value,
                key = { it.id }) { device -> // Use deviceId as stable key
                DeviceItem(
                    device = device,
                    onClick = { onDeviceSelected(device) },
                    onSettingsClick = { onSettingsClicked(device) },
                    selectingDevice
                )
                Divider(modifier = Modifier.padding(horizontal = 16.dp)) // Separator
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class) // For Card onClick
@Composable
private fun DeviceItem(
    device: IqDevice,
    onClick: () -> Unit,
    onSettingsClick: () -> Unit,
    selectingDevice: Boolean
) {
    // Determine if the device is connected and clickable
    val isConnected = device.status?.equals("CONNECTED", ignoreCase = true) == true
    // Set alpha for visual indication (slightly faded if not connected)
    val contentAlpha =
        if (isConnected) ContentAlpha.high else ContentAlpha.disabled // Use disabled alpha for non-interactive state
    // Optionally reduce elevation if not connected
    val cardElevation = if (isConnected) 2.dp else 0.5.dp // Less prominent if disabled

    // Use Card for elevation and visual grouping
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)  // Spacing around card
            .then(
                // Only add clickable modifier if connected
                if (isConnected) Modifier.clickable(onClick = onClick) else Modifier
            ),
        elevation = cardElevation,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp) // Internal padding within card
                .alpha(contentAlpha), // Fade content if not connected
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween // Pushes settings icon to end
        ) {
            // Column for Name and Status
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
            ) { // Take available space, add padding
                Text(
                    text = device.friendlyName ?: "Unknown Device",
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Status: ${device.status ?: "N/A"}", // Show status clearly
                    style = MaterialTheme.typography.body2,
                    // Optional: Color based on status
                    color = when (device.status?.uppercase()) {
                        "CONNECTED" -> Color(0xFF008800) // Dark Green for connected
                        "AVAILABLE" -> MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                        else -> MaterialTheme.colors.error
                    }
                )
            }

            if (!selectingDevice) {
                // Settings Icon Button
                IconButton(
                    onClick = {
                        if (isConnected) {
                            onSettingsClick()
                        }
                    } // let modifier control it
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Device Settings",
                        tint = MaterialTheme.colors.primary // Use theme color
                    )
                }
            }
        }
    }
}
