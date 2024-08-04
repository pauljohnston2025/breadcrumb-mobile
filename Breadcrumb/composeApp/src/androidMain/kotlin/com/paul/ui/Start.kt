package com.paul.ui

import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(startViewModel.sendingFile.value) {
                Text("Sending file...")
            }

            AnimatedVisibility(startViewModel.loadingMessage.value != "") {
                Text("Status: " + startViewModel.loadingMessage.value)
            }

            AnimatedVisibility(startViewModel.htmlMessage.value != "") {
                AndroidView(
                    factory = { context -> TextView(context) },
                    update = { it.text = HtmlCompat.fromHtml(startViewModel.htmlMessage.value, HtmlCompat.FROM_HTML_MODE_COMPACT)}
                )
            }

            Button(onClick = {
                deviceSelector.selectDeviceUi()
            }) {
                Text("Select Device")
            }

            Button(onClick = {
                startViewModel.pickRoute()
            }) {
                Text("Pick route")
            }
        }
    }
}