package com.paul.infrastructure.connectiq

import com.paul.domain.IqDevice
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.todevice.Protocol
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.paul.protocol.fromdevice.Protocol as Response

class ConnectIqNeedsInstall : Exception() {
    override val message: String
        get() = "Connect Iq App needs to be installed"
}

class ConnectIqNeedsUpdate : Exception() {
    override val message: String
        get() = "Connect Iq App needs to be updated"
}

data class AppInfo(val version: Int)

data class ConnectIqApp(val name: String, val id: String)

abstract class IConnection {

    companion object {
        val CONNECT_IQ_APP_ID_KEY = "CONNECT_IQ_APP_ID_KEY"
        val settings: Settings = Settings()

        val BREADCRUMB_DATAFIELD_ID = "20edd04a-9fdc-4291-b061-f49d5699394d"
        val BREADCRUMB_APP_ID = "fa3e1362-11b0-4420-90cb-9ac14591bf68"
        val BREADCRUMB_APP_GLANCE_ID = "54ea0621-918f-40ec-864e-d3fc7eb6ecbf"
        val LIGHT_WEIGHT_BREADCRUMB_DATAFIELD_ID = "65f96e86-28e7-4c92-9c5e-1bf7954bde41"
        val ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID = "ef62306c-4693-48e2-8f6b-beee5db9a2b8"
        val defaultConnectIqAppId = BREADCRUMB_DATAFIELD_ID // default to breadcrumb datafields (the original)

        fun getConnectIqAppIdOnStart(): String {
            return settings.getString(CONNECT_IQ_APP_ID_KEY, defaultConnectIqAppId)
        }

        val availableConnectIqApps = listOf(
            ConnectIqApp("BreadcrumbDataField", BREADCRUMB_DATAFIELD_ID),
            ConnectIqApp("BreadcrumbApp", BREADCRUMB_APP_ID),
            ConnectIqApp("BreadcrumbAppGlance", BREADCRUMB_APP_GLANCE_ID),
            ConnectIqApp("LWBreadcrumbDataField", LIGHT_WEIGHT_BREADCRUMB_DATAFIELD_ID),
            ConnectIqApp("ULBreadcrumbDataField", ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID),
        )
    }

    private val currentConnectIqAppId: MutableStateFlow<String> = MutableStateFlow(getConnectIqAppIdOnStart())

    fun connectIqAppIdFlow(): StateFlow<String> {
        return currentConnectIqAppId.asStateFlow()
    }

    init {
        currentConnectIqAppId.value = getConnectIqAppIdOnStart()
    }

    suspend fun updateConnectIqAppId(appId: String) {
        currentConnectIqAppId.emit(appId)
        settings.putString(CONNECT_IQ_APP_ID_KEY, appId)
    }

    abstract suspend fun start()

    abstract suspend fun appInfo(device: IqDevice): AppInfo
    abstract suspend fun send(device: IqDevice, payload: Protocol)
    abstract suspend fun <T: Response> query(device: IqDevice, payload: Protocol, type: ProtocolResponse): T
}