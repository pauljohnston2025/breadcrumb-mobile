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
            // only keep alias keys, its all that the ultralight app supports, we do not want to
            // send a giant dictionary wen we enable a profile
            settingsToSend = settings.mapNotNull { (key, value) ->
                val alias = settingsAliases[key]
                // Only keep the entry if an alias exists, mapping it to the value.
                // mapNotNull automatically filters out nulls (keys without an alias).
                alias?.let { it to value }
            }.toMap() // Convert the resulting list of Pairs back into a Map

            Napier.d("Saving settings for ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID with aliases: $settingsToSend")
        } else {
            Napier.d("Saving settings for $currentAppId without aliases.")
        }

        return mutableListOf(settingsToSend)
    }
}