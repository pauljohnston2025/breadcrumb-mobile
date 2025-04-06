package com.paul

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.paul.infrastructure.connectiq.Connection
import com.paul.infrastructure.connectiq.DeviceList
import com.paul.infrastructure.service.FileHelper
import com.paul.infrastructure.service.GpxFileLoader
import com.paul.infrastructure.service.ImageProcessor
import com.paul.ui.App


class MainActivity : ComponentActivity() {
    companion object {
        private const val GOOGLE_SHORT_URL_PREFIX = "https://"
    }

    val fileHelper = FileHelper(this)
    val connection = Connection(this)
    val deviceList = DeviceList(connection)
    val gpxFileLoader = GpxFileLoader()

    // based on ActivityResultContracts.OpenDocument()
    val getFileContent =
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
            fileHelper.fileLoaded(uri)
        }

    init {
        fileHelper.setLauncher(getFileContent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var shortGoogleUrl: String? = null
        var komootUrl: String? = null
        var initialErrorMessage: String? = null
        val fileLoad: Uri? = intent?.let {
            when (it.action) {
                Intent.ACTION_SEND -> {
                    if (it.type == "text/plain") {
                        // assume its a google maps route, deal with it
                        // see https://stackoverflow.com/a/75021893
                        val text = it.extras?.getString("android.intent.extra.TEXT")
                            ?.takeUnless { it.isBlank() }
                        if (text != null && text.contains("komoot"))
                        {
                            komootUrl = text
                            return@let null
                        }

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
            PermissionHandler {
                App(
                    connection,
                    deviceList,
                    gpxFileLoader,
                    fileHelper,
                    fileLoad?.toString(),
                    shortGoogleUrl,
                    komootUrl,
                    initialErrorMessage
                )
            }
        }

        try {
            val serviceIntent = Intent(this, WebServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                this.startService(serviceIntent)
            }
        }
        catch (t: Throwable)
        {
            Log.d("stdout", "failed to start service (lets hope its because it's already running) $t")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val serviceIntent = Intent(this, WebServerService::class.java)
        stopService(serviceIntent)
    }
}

@Composable
fun PermissionHandler(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasNotificationPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasNotificationPermission.value = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Notification permission required", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(key1 = Unit) {
        if (!hasNotificationPermission.value) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // continue anyway, its not supper critical
    content()
}

@Preview
@Composable
fun AppAndroidPreview() {
//    App(RouteHandler(ConnectIqHandler(Context())))
}
