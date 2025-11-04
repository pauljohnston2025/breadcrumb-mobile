package com.paul.infrastructure.service

import androidx.compose.material.SnackbarHostState
import com.benasher44.uuid.uuid4
import com.paul.domain.HistoryItem
import com.paul.domain.IRoute
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.connectiq.IConnection.Companion.BREADCRUMB_DATAFIELD_ID
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
            sendingMessage: suspend (msg: String, cb: suspend () -> Unit) -> Unit
        ) {
            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return
            }

            var route: Route? = null
            sendingMessage("Loading Route") {
                try {
                    if (routeRepo.getRouteEntry(gpxRoute.id) == null) {
                        routeRepo.saveRoute(gpxRoute)
                    }

                    val historyItem = HistoryItem(
                        uuid4().toString(),
                        gpxRoute.id,
                        Clock.System.now()
                    )
                    historyRepo.add(historyItem)
                    route = gpxRoute.toRoute(snackbarHostState)
                } catch (e: Exception) {
                    Napier.d("Failed to parse route: ${e.message}")
                }
            }

            if (route == null) {
                snackbarHostState.showSnackbar("Failed to convert to route")
                return
            }

            sendingMessage("Sending file") {
                try {
                    if (connection.connectIqAppIdFlow().value == BREADCRUMB_DATAFIELD_ID) {
                        val version = connection.appInfo(device).version
                        if (version >= 10 ||
                            version == 0 // simulator or side loaded
                        ) {
                            connection.send(device, route!!.toV2())
                        } else {
                            connection.send(device, route!!)
                        }
                    } else {
                        // all other apps were released much later after it stabalised, so all versions support v2 routes
                        connection.send(device, route!!.toV2())
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