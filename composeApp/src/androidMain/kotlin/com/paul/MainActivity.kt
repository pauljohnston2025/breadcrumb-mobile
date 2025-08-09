package com.paul

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
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
import com.paul.infrastructure.repositories.TileRepository
import com.paul.infrastructure.service.ClipboardHandler
import com.paul.infrastructure.service.FileHelper
import com.paul.infrastructure.service.GpxFileLoader
import com.paul.infrastructure.service.ImageProcessor
import com.paul.infrastructure.service.InMemoryDebugAntilog
import com.paul.infrastructure.service.IntentHandler
import com.paul.ui.App
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier


class MainActivity : ComponentActivity() {
    companion object {
        private const val GOOGLE_SHORT_URL_PREFIX = "https://"
    }

    val fileHelper = FileHelper(this)
    val clipboardHandler = ClipboardHandler(this)
    val connection = Connection(this)
    val deviceList = DeviceList(connection)
    val gpxFileLoader = GpxFileLoader()
    val webServerController = WebServerController(this)
    val intentHandler = IntentHandler()
    val tileRepo = TileRepository(
        ImageProcessor(this),
        FileHelper(this)
    )

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

        Napier.base(DebugAntilog())
        Napier.base(InMemoryDebugAntilog())

        addOnNewIntentListener({
            handleIntent(it)
        })

        setContent {
            PermissionHandler {
                App(
                    tileRepo,
                    connection,
                    deviceList,
                    gpxFileLoader,
                    fileHelper,
                    clipboardHandler,
                    webServerController,
                    intentHandler,
                )
            }
        }

        if (intent != null)
        {
            handleIntent(intent)
        }

        webServerController.onStart()
    }

    private fun handleIntent(intent: Intent)
    {
        var shortGoogleUrl: String? = null
        var komootUrl: String? = null
        var initialErrorMessage: String? = null
        val fileLoad: Uri? = intent.let {
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

                        // For ACTION_SEND, the URI is sometimes in the EXTRA_STREAM extra.
                        // We need a version check for compatibility with Android 13 (Tiramisu) and newer.
                        val gpxUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                        }

                        if (gpxUri != null) {
                            return@let gpxUri
                        }

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

        // todo make all these separate methods
        intentHandler.load(
            fileLoad?.toString(),
            shortGoogleUrl,
            komootUrl,
            initialErrorMessage,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        webServerController.changeTileServerEnabled(false)
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
