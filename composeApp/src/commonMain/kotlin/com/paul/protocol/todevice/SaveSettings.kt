package com.paul.protocol.todevice

import com.paul.infrastructure.connectiq.IConnection.Companion.ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID
import com.paul.viewmodels.settingsAliases

class SaveSettings(val settings: Map<String, Any>) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_SAVE_SETTINGS
    }

    override fun payload(): List<Any> {
        return mutableListOf(settings)
    }

    override fun transform(appId: String, version: Int): Protocol {
        if (appId == ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID) {
            val transformedSettings = settings.mapNotNull { (key, value) ->
                val alias = settingsAliases[key]
                alias?.let { it to value }
            }.toMap()
            return SaveSettings(transformedSettings)
        }
        return this
    }
}
