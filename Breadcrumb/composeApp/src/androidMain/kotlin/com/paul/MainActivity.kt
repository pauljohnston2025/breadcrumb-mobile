package com.paul

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContract.SynchronousResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.paul.infrastructure.connectiq.Connection
import com.paul.infrastructure.utils.GpxFileLoader
import com.paul.ui.App

class MainActivity : ComponentActivity() {
    val connection = Connection(this)
    val gpxFileLoader = GpxFileLoader(this)

    // based on ActivityResultContracts.OpenDocument()
    val getContent =
        registerForActivityResult(object : ActivityResultContract<Array<String>, Uri?>() {
            @CallSuper
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
            }

            override fun getSynchronousResult(
                context: Context,
                input: Array<String>
            ): SynchronousResult<Uri?>? = null

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
                return intent.takeIf { resultCode == RESULT_OK }?.data
            }
        }) { uri: Uri? ->
            gpxFileLoader.fileLoaded(uri)
        }

    init {
        gpxFileLoader.setLauncher(getContent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            App(connection, gpxFileLoader)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
//    App(RouteHandler(ConnectIqHandler(Context())))
}