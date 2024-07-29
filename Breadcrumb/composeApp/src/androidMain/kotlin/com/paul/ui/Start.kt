package com.paul.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.paul.viewmodels.DeviceSelector
import com.paul.viewmodels.StartViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun Start(startViewModel: StartViewModel, deviceSelector: DeviceSelector) {
    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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