package com.paul.infrastructure.service

import androidx.compose.material.SnackbarHostState
import com.benasher44.uuid.uuid4
import com.paul.domain.HistoryItem
import com.paul.domain.IRoute
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.HistoryRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.DeviceSelector
import io.github.aakira.napier.Napier
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.datetime.Clock

class SendRoute {

    companion object {
        suspend fun sendRoute(
            gpxRoute: IRoute,
            deviceSelector: DeviceSelector,
            snackbarHostState: SnackbarHostState,
            connection: IConnection,
            routeRepo: RouteRepository,
            historyRepo: HistoryRepository,
            sendingMessage: suspend (msg: String, cb: suspend (updateMsg: suspend (String) -> Unit) -> Unit) -> Unit
        ) {
            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return
            }

            var route: Route? = null
            sendingMessage("Loading Route") { _ ->
                try {
                    if (!gpxRoute.isStrava() && routeRepo.getRouteEntry(gpxRoute.id) == null) {
                        routeRepo.saveRoute(gpxRoute)
                    }

                    val historyItem = HistoryItem(
                        uuid4().toString(),
                        gpxRoute.id,
                        Clock.System.now()
                    )
                    historyRepo.add(historyItem)
                    route = gpxRoute.toRouteForDevice(snackbarHostState)
                } catch (e: Exception) {
                    Napier.e("Failed to parse route: ${e.message}")
                }
            }

            if (route == null) {
                snackbarHostState.showSnackbar("Failed to convert to route")
                return
            }

            val baseMsg = "Sending file"
            sendingMessage(baseMsg) { updateMsg ->
                try {
                    // Transformations are now handled by connection.send internally
                    connection.send(device, route!!) { appName ->
                        updateMsg("$appName\n$baseMsg")
                    }
                } catch (t: TimeoutCancellationException) {
                    snackbarHostState.showSnackbar("Timed out sending file")
                    return@sendingMessage
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Failed to send to selected device")
                    return@sendingMessage
                }
                snackbarHostState.showSnackbar("Route sent")
            }
        }
    }
}
