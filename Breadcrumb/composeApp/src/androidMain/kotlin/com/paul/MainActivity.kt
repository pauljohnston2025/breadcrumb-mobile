package com.paul

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.CallSuper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.paul.infrastructure.connectiq.Connection
import com.paul.infrastructure.utils.GpxFileLoader
import com.paul.ui.App
import java.io.IOException
import java.lang.reflect.Modifier
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL


class MainActivity : ComponentActivity() {
    companion object {
        private const val GOOGLE_SHORT_URL_PREFIX = "https://"
    }

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

//        ContextCompat.checkSelfPermission(
//            applicationContext,
//            Manifest.permission.
//        ) == PackageManager.PERMISSION_GRANTED -> {
//            // You can use the API that requires the permission.
//        }
//        ActivityCompat.shouldShowRequestPermissionRationale(
//            this, Manifest.permission.REQUESTED_PERMISSION) -> {
//            // In an educational UI, explain to the user why your app requires this
//            // permission for a specific feature to behave as expected, and what
//            // features are disabled if it's declined. In this UI, include a
//            // "cancel" or "no thanks" button that lets the user continue
//            // using your app without granting the permission.
//            showInContextUI(...)
//        }
//        else -> {
//            // You can directly ask for the permission.
//            // The registered ActivityResultCallback gets the result of this request.
//            requestPermissionLauncher.launch(
//                Manifest.permission.REQUESTED_PERMISSION)
//        }

        var shortGoogleUrl: String? = null
        var initialErrorMessage: String? = null
        val fileLoad: Uri? = intent?.let {
            when (it.action) {
                Intent.ACTION_SEND -> {
                    if (it.type == "text/plain") {
                        // assume its a google maps route, deal with it
                        // see https://stackoverflow.com/a/75021893
                        val text = it.extras?.getString("android.intent.extra.TEXT")
                            ?.takeUnless { it.isBlank() }
                        shortGoogleUrl = text
                            ?.split("\n")
                            ?.lastOrNull()
                            ?.let {
                                if (it.contains("https://")) {
                                    // google links can change eg.
                                    // car: 'For the best route in current traffic visit https://maps.app.goo.gl/edemxTzkxJS6dLpi7'
                                    // walk: 'To see this route visit https://maps.app.goo.gl/msXomPPczhNuC3Uv5'
                                    // so just look for a link on the last line
                                    "https://" + it.split("https://")[1]
                                } else {
                                    null
                                }
                            }

                        if (shortGoogleUrl == null) {
                            initialErrorMessage = "Could not find google link: ${text}"
                        }

                        return@let null
                    }

                    if (!it.data.toString().contains(".gpx")) {
                        return@let null
                    }

                    it.data
                }

                Intent.ACTION_MAIN, Intent.ACTION_VIEW, Intent.ACTION_OPEN_DOCUMENT -> {
                    it.data
                }

                else -> null
            }
        }

        setContent {
            App(connection, gpxFileLoader, fileLoad, shortGoogleUrl, initialErrorMessage)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
//    App(RouteHandler(ConnectIqHandler(Context())))
}
