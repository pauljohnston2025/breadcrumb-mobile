package com.paul.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.paul.viewmodels.DeviceSelector
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun DeviceSelector(deviceSelector: DeviceSelector) {
    val devicesList = deviceSelector.devicesFlow().collectAsState(initial = listOf())
    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Choose device:")
            LazyColumn {
                itemsIndexed(devicesList.value) { index, item ->
                    Button(onClick = { deviceSelector.onDeviceSelected(item) }) {
                        Text("$index. ${item.friendlyName} ${item.status.name}")
                    }
                }
            }
        }
    }
}
