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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.paul.infrastructure.protocol.Colour
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

            var x by remember { mutableStateOf("0") }
            var y by remember { mutableStateOf("0") }
            var red by remember { mutableStateOf("255") }
            var green by remember { mutableStateOf("0") }
            var blue by remember { mutableStateOf("0") }
            val color = previewColor(red.toIntOrNull() ?: 0, green.toIntOrNull() ?: 0, blue.toIntOrNull() ?: 0)

            Row {
                TileInput("X", x) { x = it }
                TileInput("Y", y) { y = it }
            }
            Row {
                ColorInput("Red", red) { red = it }
                ColorInput("Green", green) { green = it }
                ColorInput("Blue", blue) { blue = it }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(color)
                )
                Column {
                    Row {
                        Button(
                            onClick = {
                                startViewModel.sendMockTile(
                                    x.toInt(),
                                    y.toInt(),
                                    Colour(
                                        red.toUByte(),
                                        green.toUByte(),
                                        blue.toUByte()
                                    )
                                )
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("Send Tile")
                        }

                        Button(
                            onClick = {
                                startViewModel.sendAllTiles(
                                    Colour(
                                        red.toUByte(),
                                        green.toUByte(),
                                        blue.toUByte()
                                    )
                                )
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("Send All Tiles")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                startViewModel.sendImage()
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("Send background")
                        }

                        Button(
                            onClick = {
                                startViewModel.loadImageToTemp()
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("Load image to temp file")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = {
                                startViewModel.requestTileLoad()
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("requestTileLoad()")
                        }

                        Button(
                            onClick = {
                                startViewModel.tryWebReq()
                            },
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Text("tryWebReq()")
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
fun ColorInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label: ")
        TextField(
            value = value,
            onValueChange = {
                if (it.isEmpty() || it.toIntOrNull() != null && it.toInt() in 0..255) {
                    onValueChange(it)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(100.dp)
        )
    }
}

@Composable
fun TileInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label: ")
        TextField(
            value = value,
            onValueChange = {
                if (it.isEmpty() || it.toIntOrNull() != null && it.toInt() in 0..255) {
                    onValueChange(it)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(100.dp)
        )
    }
}

fun previewColor(red: Int, green: Int, blue: Int): Color {
    return Color(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
}