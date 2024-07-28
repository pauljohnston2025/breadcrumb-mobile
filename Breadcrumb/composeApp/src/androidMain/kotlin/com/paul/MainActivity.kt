package com.paul

import App
import ConnectIqHandler
import Point
import RouteHandler
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    val connectIqHandler = ConnectIqHandler(this)
    val routeHandler = RouteHandler(connectIqHandler)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectIqHandler.initialize()

        setContent {
            App(routeHandler)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
//    App(RouteHandler(ConnectIqHandler(Context())))
}