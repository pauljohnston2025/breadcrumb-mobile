package com.paul.protocol.fromdevice

enum class ProtocolResponse(val value: UByte) {
    PROTOCOL_SEND_HELLO(0u),
    PROTOCOL_SEND_SETTINGS(1u),
    PROTOCOL_SEND_OPEN_APP(2u),
}

sealed class Protocol {
    companion object {
        fun decode(data: List<Any>): Protocol
        {
            val payload = data.subList(1, data.size)
            return when(data[0])
            {
                ProtocolResponse.PROTOCOL_SEND_HELLO -> Hello.decode(payload)
                ProtocolResponse.PROTOCOL_SEND_SETTINGS -> Settings.decode(payload)
                ProtocolResponse.PROTOCOL_SEND_OPEN_APP -> OpenApp.decode(payload)
                else -> throw RuntimeException("failed to deserialise")
            }
        }
    }
}

data class Hello(
    val version: Byte
): Protocol()
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
): Protocol()
{
    companion object {
        fun decode(payload: List<Any>): Settings
        {
            return Settings(payload[0] as Map<String, Any>)
        }
    }
}

class OpenApp: Protocol()
{
    companion object {
        fun decode(payload: List<Any>): OpenApp
        {
            return OpenApp()
        }
    }
}
