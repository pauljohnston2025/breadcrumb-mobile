package com.paul.ui

import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.paul.viewmodels.DeviceSelector
import com.paul.viewmodels.StartViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun Start(startViewModel: StartViewModel, deviceSelector: DeviceSelector) {
    var showDialog by remember { mutableStateOf(false) }

    MaterialTheme {
        if (showDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDialog = false
                },
                title = {
                    Text(text = "Confirm Action")
                },
                text = {
                    Text(text = "Are you sure you want to clear history? This cannot be undone!")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            startViewModel.clearHistory()
                            showDialog = false
                        }
                    ) {
                        Text("Clear History")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally) {

            AnimatedVisibility(
                startViewModel.errorMessage.value != "",
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            startViewModel.errorMessage.value = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.Gray,
                            contentColor = Color.Red,
                        )
                    ) {
                        Text("X", color = Color.Red)
                    }
                }
                Text("Status: " + startViewModel.errorMessage.value)
            }

            AnimatedVisibility(
                startViewModel.htmlErrorMessage.value != "",
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            startViewModel.htmlErrorMessage.value = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.Gray,
                            contentColor = Color.Red,
                        )
                    ) {
                        Text("X", color = Color.Red)
                    }
                }
                AndroidView(
                    factory = { context -> TextView(context) },
                    update = { it.text = HtmlCompat.fromHtml(startViewModel.htmlErrorMessage.value, HtmlCompat.FROM_HTML_MODE_COMPACT)}
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FloatInput("Lat", startViewModel.lat) { startViewModel.lat = it }
                FloatInput("Long", startViewModel.long) { startViewModel.long = it }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                startViewModel.clearLocation()
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("Clear location")
                        }

                        Button(
                            onClick = {
                                startViewModel.requestSettings()
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("Req Settings")
                        }

                        Button(
                            onClick = {
                                startViewModel.loadLocation(startViewModel.lat.toFloat(), startViewModel.long.toFloat())
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("Load location")
                        }
                    }
                }
            }

            Row(modifier = Modifier) {
                Button(
                    onClick = {
                        deviceSelector.selectDeviceUi()
                    },
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text("Select Device")
                }

                Button(
                    onClick = {
                        startViewModel.pickRoute()
                    },
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text("Import from file")
                }
            }

            Column(modifier = Modifier
                .fillMaxSize()
//                .weight(1F) // hack to get it to fill the remaining area, since we are in an infinite scrolling view
                              // not sure how to get a min height when doing so though (this only matters in the error case where there is a huge error message)
//                .heightIn(100.dp, 200.dp)
//                .defaultMinSize(minHeight = 100.dp),
            ) {
                Row(
                    Modifier
                    .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("History:", modifier = Modifier)
                    Button(
                        onClick = {
                            showDialog = true
                        },
                        modifier = Modifier
                    ) {
                        Text("Clear")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color.Black),
                ) {
                    itemsIndexed(startViewModel.history.reversed()) { _, item ->
                        Column(
                            Modifier
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                        {
                            Button(
                                onClick = {
                                    startViewModel.loadFile(item.uri, false)
                                },
                                modifier = Modifier
                                    .padding(0.dp)
                            ) {
                                Text(item.name)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(startViewModel.sendingFile.value != "") {
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
                        text = startViewModel.sendingFile.value,
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

@Composable
fun RowScope.FloatInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(
        modifier = Modifier.weight(1F)
    ) {
        Text(text = "$label: ")
        TextField(
            value = value,
            onValueChange = {
                if (it.isEmpty() || it.toFloatOrNull() != null) {
                    onValueChange(it)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
        )
    }
}
