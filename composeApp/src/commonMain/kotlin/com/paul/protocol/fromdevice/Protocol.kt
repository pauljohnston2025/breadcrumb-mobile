package com.paul.protocol.fromdevice

import android.util.Log

enum class ProtocolResponse(val value: UByte) {
    PROTOCOL_SEND_HELLO(0u),
    PROTOCOL_SEND_SETTINGS(1u),
    PROTOCOL_SEND_OPEN_APP(2u),
}

sealed class Protocol(val type: ProtocolResponse) {
    companion object {
        fun decode(data: List<Any>): Protocol?
        {
            val payload = data.subList(1, data.size)
            return when(data[0])
            {
                ProtocolResponse.PROTOCOL_SEND_HELLO.value -> Hello.decode(payload)
                ProtocolResponse.PROTOCOL_SEND_SETTINGS.value -> Settings.decode(payload)
                ProtocolResponse.PROTOCOL_SEND_OPEN_APP.value -> OpenApp.decode(payload)
                else -> {
                    Log.d("stdout","failed to decode: $data")
                    null
                }
            }
        }
    }
}

data class Hello(
    val version: Byte
): Protocol(ProtocolResponse.PROTOCOL_SEND_HELLO)
{
    companion object {
        fun decode(payload: List<Any>): Hello
        {
            return Hello(payload[0] as Byte)
        }
    }
}

data class Settings(
    val settings: Map<String, Any>
): Protocol(ProtocolResponse.PROTOCOL_SEND_SETTINGS)
{
    companion object {
        fun decode(payload: List<Any>): Settings
        {
            return Settings(payload[0] as Map<String, Any>)
        }
    }
}

class OpenApp: Protocol(ProtocolResponse.PROTOCOL_SEND_OPEN_APP)
{
    companion object {
        fun decode(payload: List<Any>): OpenApp
        {
            return OpenApp()
        }
    }
}
