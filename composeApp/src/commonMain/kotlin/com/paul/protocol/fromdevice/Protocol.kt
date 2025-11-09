package com.paul.protocol.fromdevice

import com.paul.viewmodels.reverseAliases
import io.github.aakira.napier.Napier

enum class ProtocolResponse(val value: Int) {
    PROTOCOL_SEND_OPEN_APP(0),
    PROTOCOL_SEND_SETTINGS(1),
}

sealed class Protocol(val type: ProtocolResponse) {
    companion object {
        fun decode(data: List<Any>): Protocol? {
            val payload = data.subList(1, data.size)
            return when (data[0]) {
                ProtocolResponse.PROTOCOL_SEND_OPEN_APP.value -> OpenApp.decode(payload)
                ProtocolResponse.PROTOCOL_SEND_SETTINGS.value -> Settings.decode(payload)
                else -> {
                    Napier.d("failed to decode: $data")
                    null
                }
            }
        }
    }
}

data class Settings(
    val settings: Map<String, Any>
) : Protocol(ProtocolResponse.PROTOCOL_SEND_SETTINGS) {
    companion object {
        fun decode(payload: List<Any>): Settings {
            @Suppress("UNCHECKED_CAST")
            val incomingMap = payload[0] as Map<String, Any>

            val decodedSettings =
                // Apply the mapping: iterate over incoming keys and replace with the long key if an alias is found
                incomingMap.mapKeys { (incomingKey, _) ->
                    reverseAliases[incomingKey] ?: incomingKey
                }

            return Settings(decodedSettings)
        }
    }
}

class OpenApp : Protocol(ProtocolResponse.PROTOCOL_SEND_OPEN_APP) {
    companion object {
        fun decode(payload: List<Any>): OpenApp {
            return OpenApp()
        }
    }
}
