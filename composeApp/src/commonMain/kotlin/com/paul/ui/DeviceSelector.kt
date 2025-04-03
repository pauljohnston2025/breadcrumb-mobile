package com.paul.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.paul.viewmodels.DeviceSelector
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun DeviceSelector(deviceSelector: DeviceSelector, navController: NavHostController) {
    BackHandler {
        deviceSelector.cancelSelection()
        navController.popBackStack()
    }

    val devicesList = deviceSelector.devicesFlow().collectAsState(initial = listOf())
    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Choose device:")
            LazyColumn {
                itemsIndexed(devicesList.value) { index, item ->
                    Row {
                        Button(onClick = { deviceSelector.onDeviceSelected(item) }) {
                            Column {
                                Text("name: ${item.friendlyName}")
                                Text("status: ${item.status}")
                            }
                        }
                        Button(onClick = { deviceSelector.openDeviceSettings(item) }) {
                            Text("settings")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(deviceSelector.settingsLoading.value,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(enabled = false) { /* No action, just blocks clicks */ },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(), // Make the Column fill the available space
                    horizontalAlignment = Alignment.CenterHorizontally // Center the children horizontally
                ) {
                    Text(
                        text = "Loading Settings From Device",
                        Modifier
                            .padding(top = 150.dp)
                            .align(Alignment.CenterHorizontally),
                        color = Color.White,
                        style = MaterialTheme.typography.body1.copy(
                            fontSize = 30.sp,
                            lineHeight = 35.sp,
                            textAlign = TextAlign.Center,
                        )
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.Center),
                    color = Color.Blue
                )
            }
        }
    }
}
