package com.paul.protocol.todevice

import com.paul.infrastructure.connectiq.IConnection.Companion.ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID
import com.paul.viewmodels.settingsAliases
import io.github.aakira.napier.Napier

class SaveSettings(val settings: Map<String, Any>, val currentAppId: String) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_SAVE_SETTINGS
    }

    override fun payload(): List<Any> {
        var settingsToSend: Map<String, Any> = settings

        if (currentAppId == ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID) {
            val aliasedSettings = mutableMapOf<String, Any>()
            settings.forEach { (key, value) ->
                val alias = settingsAliases[key]
                // Use the alias if it exists, otherwise use the full key
                aliasedSettings[alias ?: key] = value
            }
            settingsToSend = aliasedSettings
            Napier.d("Saving settings for ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID with aliases: $aliasedSettings")
        } else {
            Napier.d("Saving settings for $currentAppId without aliases.")
        }

        return mutableListOf(settingsToSend)
    }
}