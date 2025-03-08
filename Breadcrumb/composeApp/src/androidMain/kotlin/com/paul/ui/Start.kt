package com.paul.ui

import android.widget.TextView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.paul.viewmodels.DeviceSelector
import com.paul.viewmodels.StartViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun Start(startViewModel: StartViewModel, deviceSelector: DeviceSelector) {
    MaterialTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {

            AnimatedVisibility(startViewModel.loadingMessage.value != "") {
                Text("Status: " + startViewModel.loadingMessage.value)
            }

            AnimatedVisibility(startViewModel.htmlMessage.value != "") {
                AndroidView(
                    factory = { context -> TextView(context) },
                    update = { it.text = HtmlCompat.fromHtml(startViewModel.htmlMessage.value, HtmlCompat.FROM_HTML_MODE_COMPACT)}
                )
            }

            Row() {
                Button(
                    onClick = {
                        deviceSelector.selectDeviceUi()
                    },
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text("Select Device")
                }

                Button(
                    onClick = {
                        startViewModel.pickRoute()
                    },
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text("Import from file")
                }
            }

            Text("History")
        }

        AnimatedVisibility(startViewModel.sendingFile.value) {
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
                        text = "Sending file",
                        Modifier.padding(top=150.dp),
                        color = Color.Blue,
                        style = MaterialTheme.typography.body1.copy(fontSize = 50.sp)
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